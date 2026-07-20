package dev.iyanz.cherry.gradle.tasks;

import com.google.gson.JsonObject;
import dev.iyanz.cherry.gradle.MixinClassScanner;
import dev.iyanz.cherry.gradle.MixinConfigGenerator;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Generates the standalone SpongePowered Mixin config (the {@code *.mixins.json} referenced from
 * {@code cherry-plugin.json}'s {@code mixin.mixins}) at {@link #getOutputFile()}.
 *
 * <p>Unlike {@link GenerateCherryManifestTask}, this <b>does</b> depend on compilation having already
 * happened: {@link #getMixinClassesDir()} must point at {@code compileJava}'s own output directory,
 * because {@link MixinClassScanner} needs the compiled {@code .class} files to find out which classes
 * under {@link #getMixinPackage()} actually carry a {@code @Mixin} annotation - see
 * {@code dev.iyanz.cherry.gradle.CherryGradlePlugin} for how this task is wired to run after
 * {@code compileJava}.
 */
@CacheableTask
public abstract class GenerateCherryMixinConfigTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getMixinClassesDir();

    @Input
    public abstract Property<String> getMixinPackage();

    @Input
    @Optional
    public abstract Property<String> getRefmapFileName();

    @Input
    @Optional
    public abstract Property<Integer> getPriority();

    @Input
    @Optional
    public abstract Property<String> getMinVersion();

    @Input
    @Optional
    public abstract Property<String> getCompatibilityLevel();

    @Input
    @Optional
    public abstract Property<Boolean> getRequired();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() {
        String mixinPackage = getMixinPackage().get();
        List<String> mixinClasses = MixinClassScanner.findMixinClasses(getMixinClassesDir().get().getAsFile(), mixinPackage);

        if (mixinClasses.isEmpty()) {
            throw new org.gradle.api.GradleException(
                "cherry { mixinPackage = \"" + mixinPackage + "\" } but no compiled class under that " +
                    "package carries a @Mixin annotation (looked in " + getMixinClassesDir().get().getAsFile() + "). " +
                    "Check the package name matches where your @Mixin classes actually live, and that they compiled.");
        }

        JsonObject config = MixinConfigGenerator.build(
            mixinPackage,
            mixinClasses,
            getRefmapFileName().getOrNull(),
            getPriority().getOrNull(),
            getMinVersion().getOrNull(),
            getCompatibilityLevel().getOrNull(),
            getRequired().getOrNull()
        );

        java.io.File out = getOutputFile().get().getAsFile();
        try {
            Files.createDirectories(out.getParentFile().toPath());
            Files.writeString(out.toPath(), MixinConfigGenerator.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed writing " + out, e);
        }
        getLogger().info("Cherry: generated {} with {} mixin class(es): {}", out.getName(), mixinClasses.size(), mixinClasses);
    }
}
