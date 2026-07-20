package dev.iyanz.cherry.gradle.tasks;

import com.google.gson.JsonObject;
import dev.iyanz.cherry.gradle.ManifestGenerator;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Generates {@code cherry-plugin.json} (the manifest Cherry's runtime discovers plugins from) at
 * {@link #getOutputFile()}. Thin Gradle wiring around the pure {@link ManifestGenerator} - see that
 * class for the actual field-by-field mapping and the schema it targets.
 *
 * <p>Deliberately does <b>not</b> depend on compilation: unlike
 * {@link GenerateCherryMixinConfigTask}, nothing here needs the compiled {@code @Mixin} classes -
 * this task only needs to know the mixin config's file <i>name</i> (computed by
 * {@code dev.iyanz.cherry.gradle.CherryGradlePlugin} from the {@code cherry { }} extension), not its
 * contents.
 */
@CacheableTask
public abstract class GenerateCherryManifestTask extends DefaultTask {

    @Input
    public abstract Property<String> getPluginName();

    @Input
    @Optional
    public abstract Property<String> getMixinPackage();

    @Input
    public abstract ListProperty<String> getMixinConfigNames();

    @Input
    @Optional
    public abstract Property<String> getAccessWidenerFileName();

    @Input
    public abstract ListProperty<String> getAccessTransformerFileNames();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() {
        JsonObject manifest = ManifestGenerator.build(
            getPluginName().get(),
            getMixinPackage().getOrNull(),
            getMixinConfigNames().get(),
            getAccessWidenerFileName().getOrNull(),
            getAccessTransformerFileNames().get()
        );
        java.io.File out = getOutputFile().get().getAsFile();
        try {
            Files.createDirectories(out.getParentFile().toPath());
            Files.writeString(out.toPath(), ManifestGenerator.toJson(manifest), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed writing " + out, e);
        }
        getLogger().info("Cherry: generated {} ({} mixin config(s), {} access-transformer(s))",
            out.getName(), getMixinConfigNames().get().size(), getAccessTransformerFileNames().get().size());
    }
}
