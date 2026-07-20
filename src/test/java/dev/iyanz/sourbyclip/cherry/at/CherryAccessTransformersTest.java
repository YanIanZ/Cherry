package dev.iyanz.sourbyclip.cherry.at;

import dev.iyanz.sourbyclip.cherry.at.fixture.SampleTarget;
import dev.iyanz.sourbyclip.cherry.at.fixture.UnrelatedTarget;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real, process-wide {@link CherryAccessTransformers#INSTANCE} singleton end to end:
 * parsing (including malformed/invalid lines that must never throw out of {@code register}),
 * conflict resolution, locking, applying the compiled transform to real bytecode, and concurrent
 * {@code applyToBytes} calls after lock.
 *
 * <p>Deliberately the ONLY test class in this repository that touches
 * {@link CherryAccessTransformers#INSTANCE}: it is a genuine once-per-JVM singleton (by design - see
 * its class javadoc), so every sub-test here runs in a fixed order via
 * {@link TestMethodOrder}/{@link Order} against one shared, evolving state, rather than each test
 * getting a fresh instance.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CherryAccessTransformersTest {

    private static final String TARGET = SampleTarget.class.getName();

    @Test
    @Order(1)
    void registeringMixedValidAndInvalidLinesNeverThrows() throws IOException {
        String source = String.join("\n",
            "public " + TARGET + " hiddenField",
            "public " + TARGET + " privateField",
            "public " + TARGET + " hiddenMethod()V",
            "public " + TARGET + " <init>(Ljava/lang/String;)V",
            "default " + TARGET + " privateField", // conflicting - widest (public) must still win at lock()
            "public " + TARGET + " nonExistentField", // valid syntax, missing target - lazy warn, not fatal
            "public", // valid modifier prefix, nothing else - tryCompile returns null (warn path)
            "xyz totally-not-a-modifier " + TARGET // invalid modifier - CompileError path
        );

        assertFalse(CherryAccessTransformers.INSTANCE.isLocked());
        CherryAccessTransformers.INSTANCE.register("test-source.at", new BufferedReader(new StringReader(source)));

        // 6 syntactically valid lines registered; the 2 garbage lines are logged and skipped, not thrown.
        assertEquals(6, CherryAccessTransformers.INSTANCE.registeredCount());
        assertFalse(CherryAccessTransformers.INSTANCE.isEmpty());
        assertFalse(CherryAccessTransformers.INSTANCE.isLocked());
    }

    @Test
    @Order(2)
    void lockResolvesConflictsAndIsIdempotent() {
        CherryAccessTransformers.INSTANCE.lock();
        assertTrue(CherryAccessTransformers.INSTANCE.isLocked());
        // Every registered definition targets the one SampleTarget class.
        assertEquals(1, CherryAccessTransformers.INSTANCE.targetClassCount());
        int countAfterFirstLock = CherryAccessTransformers.INSTANCE.registeredCount();

        CherryAccessTransformers.INSTANCE.lock(); // no-op, must not throw or change state
        assertEquals(countAfterFirstLock, CherryAccessTransformers.INSTANCE.registeredCount());
        assertEquals(1, CherryAccessTransformers.INSTANCE.targetClassCount());
    }

    @Test
    @Order(3)
    void registerAfterLockThrows() {
        assertThrows(IllegalStateException.class, () ->
            CherryAccessTransformers.INSTANCE.register("too-late.at", new BufferedReader(new StringReader("public " + TARGET))));
    }

    @Test
    @Order(4)
    void applyToBytesTransformsRealBytecodeAndSkipsMissingTargetsSilently() throws IOException {
        byte[] transformed = CherryAccessTransformers.INSTANCE.applyToBytes(readClassBytes(SampleTarget.class));

        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);

        FieldNode hiddenField = findField(node, "hiddenField");
        assertPublic(hiddenField.access, "hiddenField");

        // privateField had two conflicting definitions (default, then public) - widest must win.
        FieldNode privateField = findField(node, "privateField");
        assertPublic(privateField.access, "privateField");

        MethodNode hiddenMethod = findMethod(node, "hiddenMethod", "()V");
        assertPublic(hiddenMethod.access, "hiddenMethod");

        MethodNode constructor = findMethod(node, "<init>", "(Ljava/lang/String;)V");
        assertPublic(constructor.access, "<init>(Ljava/lang/String;)V");

        // "nonExistentField" had no matching member - applyToBytes must have logged a warning and
        // moved on rather than throwing, which the fact that we got here at all already proves.
    }

    @Test
    @Order(5)
    void applyToBytesNoOpsForAnUnregisteredClass() throws IOException {
        byte[] original = readClassBytes(UnrelatedTarget.class);
        byte[] result = CherryAccessTransformers.INSTANCE.applyToBytes(original);
        assertSame(original, result, "a class with no AT definitions must pass through unchanged (same reference)");
    }

    @Test
    @Order(6)
    void applyToBytesIsSafeUnderConcurrentUse() throws IOException, InterruptedException, ExecutionException {
        byte[] targetBytes = readClassBytes(SampleTarget.class);
        byte[] unrelatedBytes = readClassBytes(UnrelatedTarget.class);

        int threads = 8;
        int iterationsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> task = () -> {
                    int lastLength = -1;
                    for (int j = 0; j < iterationsPerThread; j++) {
                        byte[] t = CherryAccessTransformers.INSTANCE.applyToBytes(targetBytes);
                        byte[] u = CherryAccessTransformers.INSTANCE.applyToBytes(unrelatedBytes);
                        assertSame(unrelatedBytes, u);
                        lastLength = t.length;
                    }
                    return lastLength;
                };
                futures.add(pool.submit(task));
            }
            for (Future<Integer> f : futures) {
                assertTrue(f.get() > 0);
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static void assertPublic(int access, String memberDescription) {
        assertTrue((access & Opcodes.ACC_PUBLIC) != 0, memberDescription + " should be public, access=" + access);
        assertTrue((access & Opcodes.ACC_PRIVATE) == 0, memberDescription + " should not be private, access=" + access);
        assertTrue((access & Opcodes.ACC_PROTECTED) == 0, memberDescription + " should not be protected, access=" + access);
    }

    private static FieldNode findField(ClassNode node, String name) {
        for (FieldNode f : node.fields) {
            if (f.name.equals(name)) return f;
        }
        throw new AssertionError("field not found: " + name);
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.equals(descriptor)) return m;
        }
        throw new AssertionError("method not found: " + name + descriptor);
    }

    private static byte[] readClassBytes(Class<?> clazz) throws IOException {
        String resourceName = clazz.getSimpleName() + ".class";
        try (InputStream in = clazz.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new AssertionError("could not locate compiled class resource for " + clazz);
            }
            return in.readAllBytes();
        }
    }
}
