package dev.iyanz.cherry.gradle.tasks;

import dev.iyanz.cherry.gradle.AccessWidenerMerger;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Stages {@code cherry.accessWideners} into a single file at {@link #getOutputFile()}, referenced
 * from {@code cherry-plugin.json}'s {@code mixin.access-widener} field (a single file name - see the
 * main repository README's manifest schema). Zero input files means this task does not run at all
 * ({@code @SkipWhenEmpty}); one file is copied through verbatim; more than one are merged - see
 * {@link AccessWidenerMerger} for exactly how, and when that fails (disagreeing headers).
 */
@CacheableTask
public abstract class StageAccessWidenersTask extends DefaultTask {

    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getAccessWidenerFiles();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void stage() {
        List<java.io.File> files = new ArrayList<>(getAccessWidenerFiles().getFiles());
        java.io.File out = getOutputFile().get().getAsFile();
        try {
            Files.createDirectories(out.getParentFile().toPath());
            if (files.size() == 1) {
                Files.copy(files.get(0).toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            List<List<String>> perFile = new ArrayList<>();
            for (java.io.File file : files) {
                perFile.add(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
            }
            List<String> merged;
            try {
                merged = AccessWidenerMerger.merge(perFile);
            } catch (IllegalArgumentException e) {
                throw new GradleException("failed merging cherry.accessWideners files " + files + ": " + e.getMessage(), e);
            }
            Files.write(out.toPath(), merged, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed staging access-widener output " + out, e);
        }
    }
}
