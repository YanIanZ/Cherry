package dev.iyanz.cherry.gradle;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds which already-compiled classes under a given package actually carry a {@code @Mixin}
 * annotation, so the generated SpongePowered Mixin config's {@code mixins} list (see
 * {@link MixinConfigGenerator}) can be populated automatically - this is the one piece of "author
 * never hand-writes the config" that cannot be done from source alone (annotations are easiest to
 * read back reliably from the compiled {@code .class} files the Java compiler already produced,
 * which is also exactly what SpongePowered Mixin's own annotation processor does internally).
 *
 * <p>This inspects raw class bytes with ASM's {@link ClassReader}/{@link ClassVisitor} and only ever
 * checks for the {@code @Mixin} annotation's descriptor - it does not require
 * {@code org.spongepowered.asm.mixin.Mixin} itself to be resolvable on this module's own classpath,
 * and it matches the annotation whether it is runtime- or class-retained (SpongePowered Mixin's
 * {@code @Mixin} only needs {@link java.lang.annotation.RetentionPolicy#CLASS} to be visible to both
 * the annotation processor and this scan, so both {@link ClassVisitor#visitAnnotation}'s
 * {@code visible=true} and {@code visible=false} cases are treated identically here).
 */
public final class MixinClassScanner {

    private static final String MIXIN_ANNOTATION_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Mixin;";

    private MixinClassScanner() {
    }

    /**
     * @param classesDir  the compiled-classes output directory (e.g. {@code compileJava}'s
     *                    {@code destinationDirectory}), i.e. the classpath root the package below is
     *                    resolved relative to
     * @param packageName the dot-separated package to scan (recursively, including subpackages -
     *                    SpongePowered Mixin config entries may reference a subpackage member via a
     *                    dotted relative path, e.g. {@code "sub.MixinBar"})
     * @return the dot-separated names of every {@code @Mixin}-annotated class found, relative to
     * {@code packageName} (so directly usable as SpongePowered Mixin config {@code mixins} entries),
     * sorted alphabetically for a reproducible/deterministic generated config; empty (never
     * {@code null}) if the package directory does not exist or contains no {@code @Mixin} classes
     */
    public static List<String> findMixinClasses(File classesDir, String packageName) {
        Path packageDir = classesDir.toPath().resolve(packageName.replace('.', File.separatorChar));
        if (!Files.isDirectory(packageDir)) {
            return List.of();
        }

        List<String> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(packageDir)) {
            walk.filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.getFileName().toString().contains("$$")) // synthetic/lambda classes
                .forEach(classFile -> {
                    if (hasMixinAnnotation(classFile)) {
                        found.add(relativeDottedName(packageDir, classFile));
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException("failed scanning " + packageDir + " for @Mixin classes", e);
        }

        Collections.sort(found);
        return List.copyOf(found);
    }

    private static boolean hasMixinAnnotation(Path classFile) {
        try (var in = Files.newInputStream(classFile)) {
            boolean[] isMixin = {false};
            ClassReader reader = new ClassReader(in);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (MIXIN_ANNOTATION_DESCRIPTOR.equals(descriptor)) {
                        isMixin[0] = true;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return isMixin[0];
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading class file " + classFile, e);
        }
    }

    private static String relativeDottedName(Path packageDir, Path classFile) {
        String relative = packageDir.relativize(classFile).toString();
        String withoutExt = relative.substring(0, relative.length() - ".class".length());
        return withoutExt.replace(File.separatorChar, '.');
    }
}
