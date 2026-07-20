package dev.iyanz.cherry.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MixinClassScanner} is exercised against real (if minimal) class files, synthesized directly
 * with ASM rather than checked-in fixtures - this deliberately never needs
 * {@code org.spongepowered.asm.mixin.Mixin} to be resolvable, only its descriptor string, matching how
 * the scanner itself works (see that class's javadoc).
 */
class MixinClassScannerTest {

    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";

    @Test
    void findsOnlyAnnotatedClassesUnderPackage(@TempDir Path classesDir) throws IOException {
        writeClass(classesDir, "com/me/plugin/mixin/MixinFoo", true);
        writeClass(classesDir, "com/me/plugin/mixin/NotAMixin", false);

        List<String> found = MixinClassScanner.findMixinClasses(classesDir.toFile(), "com.me.plugin.mixin");

        assertEquals(List.of("MixinFoo"), found);
    }

    @Test
    void recursesIntoSubpackagesWithDottedRelativeNames(@TempDir Path classesDir) throws IOException {
        writeClass(classesDir, "com/me/plugin/mixin/MixinFoo", true);
        writeClass(classesDir, "com/me/plugin/mixin/sub/MixinBar", true);

        List<String> found = MixinClassScanner.findMixinClasses(classesDir.toFile(), "com.me.plugin.mixin");

        assertEquals(List.of("MixinFoo", "sub.MixinBar"), found);
    }

    @Test
    void resultsAreSortedForDeterministicOutput(@TempDir Path classesDir) throws IOException {
        writeClass(classesDir, "com/me/plugin/mixin/ZMixin", true);
        writeClass(classesDir, "com/me/plugin/mixin/AMixin", true);

        assertEquals(List.of("AMixin", "ZMixin"), MixinClassScanner.findMixinClasses(classesDir.toFile(), "com.me.plugin.mixin"));
    }

    @Test
    void classRetainedAnnotationIsStillFound(@TempDir Path classesDir) throws IOException {
        // visible=false simulates RetentionPolicy.CLASS (not RUNTIME) - the scanner must not filter on this.
        writeClass(classesDir, "com/me/plugin/mixin/MixinFoo", true, false);

        assertEquals(List.of("MixinFoo"), MixinClassScanner.findMixinClasses(classesDir.toFile(), "com.me.plugin.mixin"));
    }

    @Test
    void missingPackageDirectoryYieldsEmptyList(@TempDir Path classesDir) {
        assertTrue(MixinClassScanner.findMixinClasses(classesDir.toFile(), "does.not.exist").isEmpty());
    }

    @Test
    void emptyPackageWithNoMixinsYieldsEmptyList(@TempDir Path classesDir) throws IOException {
        writeClass(classesDir, "com/me/plugin/mixin/JustAClass", false);

        assertTrue(MixinClassScanner.findMixinClasses(classesDir.toFile(), "com.me.plugin.mixin").isEmpty());
    }

    private static void writeClass(Path classesDir, String internalName, boolean annotated) throws IOException {
        writeClass(classesDir, internalName, annotated, true);
    }

    private static void writeClass(Path classesDir, String internalName, boolean annotated, boolean visible) throws IOException {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        if (annotated) {
            cw.visitAnnotation(MIXIN_DESC, visible).visitEnd();
        }
        cw.visitEnd();

        Path classFile = classesDir.resolve(internalName + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, cw.toByteArray());
    }
}
