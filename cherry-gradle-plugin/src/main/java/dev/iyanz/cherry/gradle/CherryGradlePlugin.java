package dev.iyanz.cherry.gradle;

import dev.iyanz.cherry.gradle.tasks.GenerateCherryManifestTask;
import dev.iyanz.cherry.gradle.tasks.GenerateCherryMixinConfigTask;
import dev.iyanz.cherry.gradle.tasks.StageAccessTransformersTask;
import dev.iyanz.cherry.gradle.tasks.StageAccessWidenersTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gradle plugin id {@code dev.iyanz.cherry}. Applying this to a Paper-plugin project's
 * {@code build.gradle.kts} replaces the manual "hand-wire the SpongePowered Mixin annotation
 * processor, hand-write {@code cherry-plugin.json}, hand-write a {@code *.mixins.json}, hand-generate
 * a refmap" path with a single {@code cherry { }} DSL block - see {@link CherryExtension} for every
 * property it exposes, and the main Cherry repository README's new "Authoring a Cherry plugin
 * (Gradle)" section for a full worked example.
 *
 * <p>Concretely, this:
 * <ol>
 *   <li>applies {@code java} (if not already applied) and adds the SpongePowered Mixin repository;</li>
 *   <li>when {@link CherryExtension#getMixinPackage()} is set, adds {@code net.fabricmc:sponge-mixin}
 *       (pinned to {@link #MIXIN_VERSION}) to both {@code compileOnly} <i>and</i>
 *       {@code annotationProcessor|, and {@code io.github.llamalad7:mixinextras-common} (pinned to
 *       {@link #MIXIN_EXTRAS_VERSION}) to {@code compileOnly} - both pinned to match exactly what
 *       Cherry's runtime (vendored into SourbyClip's launcher) itself bundles, so an author's mixins
 *       are compiled against the identical Mixin/refmap machinery they will actually run under;</li>
 *   <li>wires {@code -AoutRefMapFile=...} onto {@code compileJava}, so the Mixin annotation processor
 *       generates a real refmap as a side effect of normal compilation - this is the part an author
 *       cannot easily hand-roll (see the class javadoc on
 *       {@link dev.iyanz.cherry.gradle.tasks.GenerateCherryMixinConfigTask});</li>
 *   <li>registers generation tasks that write the SpongePowered Mixin config JSON (scanning compiled
 *       classes for {@code @Mixin}, via {@link MixinClassScanner}) and {@code cherry-plugin.json}
 *       (via {@link ManifestGenerator}) into a staging directory wired as an extra {@code main}
 *       resources source directory, so both end up at the built jar's root automatically;</li>
 *   <li>validates and stages {@code cherry.accessTransformers} files (see
 *       {@link dev.iyanz.cherry.gradle.tasks.StageAccessTransformersTask}) and
 *       {@code cherry.accessWideners} files (merging more than one, see
 *       {@link dev.iyanz.cherry.gradle.tasks.StageAccessWidenersTask}) into the same staging directory.</li>
 * </ol>
 */
public final class CherryGradlePlugin implements Plugin<Project> {

    /**
     * Must match the {@code net.fabricmc:sponge-mixin} version pinned in this repository's own root
     * {@code build.gradle.kts} - that is exactly the Mixin build Cherry's runtime (vendored into
     * SourbyClip's launcher) compiles and runs against. A plugin author's {@code @Mixin} classes and
     * refmap must be produced by the identical Mixin/annotation-processor version to avoid refmap or
     * mixin-application drift at runtime - see the task brief's "MUST match to avoid refmap/version
     * drift" requirement.
     */
    public static final String MIXIN_VERSION = "0.17.3+mixin.0.8.7";

    /**
     * Must match the {@code io.github.llamalad7:mixinextras-common} version LeavesMC/Leavesclip's own
     * {@code java21} module bundles (Cherry's mixin base, and the module SourbyClip's launcher
     * ultimately vendors) - see that module's {@code build.gradle.kts}. Cherry's own repository does
     * not depend on MixinExtras directly (only plugin authors' {@code @Mixin} classes do, via
     * {@code @ModifyExpressionValue}/{@code @WrapOperation}/etc. annotations), so this version is not
     * duplicated in the root {@code build.gradle.kts} - it is pinned here instead, at the one place a
     * plugin author's compile classpath actually needs it.
     */
    public static final String MIXIN_EXTRAS_VERSION = "0.5.0";

    public static final String SPONGEPOWERED_MAVEN_URL = "https://repo.spongepowered.org/maven/";

    static final String GENERATED_RESOURCES_PATH = "generated/cherry/resources";

    /**
     * Every {@code org.spongepowered.tools.obfuscation.interfaces.IMessagerEx.MessageType} whose name
     * starts with {@code NO_OBFDATA_FOR_} - see {@link #configureAfterEvaluate} for why these are all
     * downgraded to a non-fatal note unconditionally for a Cherry plugin's compilation.
     */
    private static final List<String> NO_OBFDATA_MESSAGE_TYPES = List.of(
        "NO_OBFDATA_FOR_ACCESSOR", "NO_OBFDATA_FOR_CLASS", "NO_OBFDATA_FOR_CTOR", "NO_OBFDATA_FOR_FIELD",
        "NO_OBFDATA_FOR_METHOD", "NO_OBFDATA_FOR_OVERWRITE", "NO_OBFDATA_FOR_SHADOW",
        "NO_OBFDATA_FOR_SIMULATED_SHADOW", "NO_OBFDATA_FOR_SOFT_IMPLEMENTS", "NO_OBFDATA_FOR_STATIC_OVERWRITE",
        "NO_OBFDATA_FOR_TARGET"
    );

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        CherryExtension extension = project.getExtensions().create("cherry", CherryExtension.class, project);

        project.getRepositories().maven(repo -> {
            repo.setName("spongepowered");
            repo.setUrl(SPONGEPOWERED_MAVEN_URL);
        });

        Provider<Directory> stagingDir = project.getLayout().getBuildDirectory().dir(GENERATED_RESOURCES_PATH);

        SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        main.getResources().srcDir(stagingDir);

        TaskProvider<JavaCompile> compileJava = project.getTasks().named("compileJava", JavaCompile.class);

        TaskProvider<StageAccessTransformersTask> stageAtTask = project.getTasks().register(
            "stageCherryAccessTransformers", StageAccessTransformersTask.class, t -> {
                t.setGroup("cherry");
                t.setDescription("Validates and stages cherry.accessTransformers files into the built jar.");
                t.getAccessTransformerFiles().from(extension.getAccessTransformers());
                t.getOutputDir().set(stagingDir);
            });

        Provider<RegularFile> accessWidenerOutput = stagingDir.zip(extension.getAccessWidenerName(), Directory::file);
        TaskProvider<StageAccessWidenersTask> stageAwTask = project.getTasks().register(
            "stageCherryAccessWideners", StageAccessWidenersTask.class, t -> {
                t.setGroup("cherry");
                t.setDescription("Stages (merging if more than one) cherry.accessWideners files into the built jar.");
                t.getAccessWidenerFiles().from(extension.getAccessWideners());
                t.getOutputFile().set(accessWidenerOutput);
            });

        TaskProvider<GenerateCherryManifestTask> generateManifestTask = project.getTasks().register(
            "generateCherryManifest", GenerateCherryManifestTask.class, t -> {
                t.setGroup("cherry");
                t.setDescription("Generates cherry-plugin.json from the cherry { } extension.");
                t.getPluginName().set(extension.getPluginName());
                t.getOutputFile().set(stagingDir.map(d -> d.file("cherry-plugin.json")));
                t.getMixinConfigNames().set(List.of());
                t.dependsOn(stageAtTask);
            });

        project.afterEvaluate(p -> configureAfterEvaluate(p, extension, stagingDir, compileJava,
            stageAtTask, stageAwTask, generateManifestTask));

        project.getTasks().named("processResources").configure(t ->
            t.dependsOn(generateManifestTask, stageAtTask, stageAwTask));
    }

    private void configureAfterEvaluate(
        Project project,
        CherryExtension extension,
        Provider<Directory> stagingDir,
        TaskProvider<JavaCompile> compileJava,
        TaskProvider<StageAccessTransformersTask> stageAtTask,
        TaskProvider<StageAccessWidenersTask> stageAwTask,
        TaskProvider<GenerateCherryManifestTask> generateManifestTask
    ) {
        boolean hasMixins = extension.getMixinPackage().isPresent();
        boolean hasWideners = !extension.getAccessWideners().isEmpty();

        if (hasWideners && !hasMixins) {
            throw new GradleException(
                "cherry.accessWideners is set but cherry.mixinPackage is not - Cherry's manifest schema " +
                    "only carries an access-widener inside the mixin block (see cherry-plugin.json's " +
                    "mixin.access-widener field), so an access-widener with no mixin package makes no sense. " +
                    "Set cherry.mixinPackage, or remove cherry.accessWideners.");
        }

        generateManifestTask.configure(t -> t.getAccessTransformerFileNames().set(
            project.provider(() -> extension.getAccessTransformers().getFiles().stream()
                .map(File::getName)
                .collect(Collectors.toList()))));

        if (!hasMixins) {
            return;
        }

        String mixinPackage = extension.getMixinPackage().get();

        project.getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, mixinDependency(project));
        project.getDependencies().add("annotationProcessor", mixinDependency(project));
        project.getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
            "io.github.llamalad7:mixinextras-common:" + MIXIN_EXTRAS_VERSION);

        Provider<RegularFile> refmapFile = stagingDir.zip(extension.getRefmapName(), Directory::file);
        File refmapOutputFile = refmapFile.get().getAsFile();
        compileJava.configure(t -> {
            t.getOutputs().file(refmapFile).withPropertyName("cherryRefmapFile");
            List<String> args = t.getOptions().getCompilerArgs();
            args.add("-AoutRefMapFile=" + refmapOutputFile.getAbsolutePath());
            // The Mixin AP's built-in ObfuscationServiceMCP unconditionally registers a "notch"
            // obfuscation environment (see that class's getObfuscationTypes()) regardless of any
            // option - there is no way to opt out of the environment itself. Since Cherry/Leavesclip
            // never remaps mixins (targets are plain Mojang-mapped Paper/NMS classes, not
            // Forge/MCP-obfuscated ones - see the main repository README's "How it works"), every
            // @Inject/@Shadow/etc. target will always be reported as missing obfuscation data for
            // that irrelevant environment. javac's own default severity for that diagnostic
            // (NO_OBFDATA_FOR_*) is ERROR unless the annotation processor happens to detect an IDE
            // compiler environment (which downgrades it to a Note) - that detection was observed to
            // be non-deterministic across otherwise-identical Gradle invocations in this repository
            // (passed once, failed on every subsequent clean build), so every NO_OBFDATA_FOR_*
            // message type is explicitly downgraded here instead, via the Mixin AP's own
            // "-AMSG_<MessageType>=<level>" mechanism (see
            // org.spongepowered.tools.obfuscation.interfaces.IMessagerEx$MessageType#applyOptions,
            // decompiled while wiring this since it is not documented anywhere public). "note" is used
            // rather than "disabled": empirically (see this class's own compiling example-plugin),
            // "disabled" does not stop the injector validator from still failing the compilation with
            // this message at ERROR kind, but overriding the kind directly to "note" does.
            for (String messageType : NO_OBFDATA_MESSAGE_TYPES) {
                args.add("-AMSG_" + messageType + "=note");
            }
            t.doFirst(task -> {
                try {
                    Files.createDirectories(refmapOutputFile.getParentFile().toPath());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        });

        TaskProvider<GenerateCherryMixinConfigTask> generateConfigTask = project.getTasks().register(
            "generateCherryMixinConfig", GenerateCherryMixinConfigTask.class, t -> {
                t.setGroup("cherry");
                t.setDescription("Generates the SpongePowered Mixin config JSON from compiled @Mixin classes.");
                t.dependsOn(compileJava);
                t.getMixinClassesDir().set(compileJava.flatMap(JavaCompile::getDestinationDirectory));
                t.getMixinPackage().set(mixinPackage);
                t.getRefmapFileName().set(extension.getRefmapName());
                t.getPriority().set(extension.getPriority());
                t.getMinVersion().set(extension.getMinVersion());
                t.getCompatibilityLevel().set(extension.getCompatibilityLevel());
                t.getRequired().set(extension.getRequired());
                t.getOutputFile().set(stagingDir.zip(extension.getMixinConfigName(), Directory::file));
            });

        List<String> extraConfigs = new ArrayList<>(extension.getMixinConfigs().getOrElse(List.of()));
        Provider<List<String>> mixinConfigNames = extension.getMixinConfigName().map(name -> {
            List<String> all = new ArrayList<>();
            all.add(name);
            all.addAll(extraConfigs);
            return List.copyOf(all);
        });

        generateManifestTask.configure(t -> {
            t.dependsOn(generateConfigTask, stageAwTask);
            t.getMixinPackage().set(mixinPackage);
            t.getMixinConfigNames().set(mixinConfigNames);
            if (hasWideners) {
                t.getAccessWidenerFileName().set(extension.getAccessWidenerName());
            }
        });
    }

    private static Dependency mixinDependency(Project project) {
        Dependency dependency = project.getDependencies().create("net.fabricmc:sponge-mixin:" + MIXIN_VERSION);
        if (dependency instanceof ModuleDependency moduleDependency) {
            moduleDependency.exclude(Map.of("group", "com.google.code.gson", "module", "gson"));
            moduleDependency.exclude(Map.of("group", "com.google.guava", "module", "guava"));
        }
        return dependency;
    }
}
