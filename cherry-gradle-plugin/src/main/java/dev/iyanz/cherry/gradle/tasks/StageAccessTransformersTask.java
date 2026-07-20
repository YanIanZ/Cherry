package dev.iyanz.cherry.gradle.tasks;

import dev.iyanz.cherry.gradle.AccessTransformerValidator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.SkipWhenEmpty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Validates every {@code cherry.accessTransformers} file against Cherry's exact {@code .at} grammar
 * (failing the build, with the offending file/line, on the first syntax error - see
 * {@link AccessTransformerValidator}) and copies each one, under its own file name, into the
 * generated-resources staging directory that ends up at the jar root - see
 * {@code dev.iyanz.cherry.gradle.CherryGradlePlugin} for how that staging directory is wired into the
 * {@code main} source set's resources.
 */
@CacheableTask
public abstract class StageAccessTransformersTask extends DefaultTask {

    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getAccessTransformerFiles();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void stage() {
        java.io.File outDir = getOutputDir().get().getAsFile();
        for (java.io.File file : getAccessTransformerFiles()) {
            List<String> lines;
            try {
                lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("failed reading access-transformer file " + file, e);
            }

            List<AccessTransformerValidator.Failure> failures = AccessTransformerValidator.validate(lines);
            if (!failures.isEmpty()) {
                StringBuilder message = new StringBuilder("invalid Cherry access-transformer syntax in ")
                    .append(file).append(":\n");
                for (AccessTransformerValidator.Failure failure : failures) {
                    message.append("  ").append(failure).append('\n');
                }
                message.append(
                    "expected one of: '<modifier>[+f|-f] <fqcn>' (class), " +
                        "'<modifier>[+f|-f] <fqcn> <field>' (field), " +
                        "'<modifier>[+f|-f] <fqcn> <init>(<params>)V' (constructor), " +
                        "'<modifier>[+f|-f] <fqcn> <name>(<params>)<ret>' (method) - " +
                        "modifier is one of public/protected/private/default.");
                throw new GradleException(message.toString());
            }

            Path target = outDir.toPath().resolve(file.getName());
            try {
                Files.createDirectories(target.getParent());
                Files.copy(file.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("failed staging access-transformer file " + file, e);
            }
        }
    }
}
