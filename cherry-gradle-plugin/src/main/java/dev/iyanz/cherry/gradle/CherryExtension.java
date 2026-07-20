package dev.iyanz.cherry.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * The {@code cherry { }} DSL a Cherry plugin author configures in their own {@code build.gradle.kts},
 * instead of hand-writing {@code cherry-plugin.json} and a SpongePowered Mixin config JSON, and
 * instead of hand-wiring the Mixin annotation processor themselves.
 *
 * <p>Every property here maps directly onto a field of the two files
 * {@link dev.iyanz.cherry.gradle.CherryGradlePlugin} generates at build time - see that class and
 * {@link ManifestGenerator}/{@link MixinConfigGenerator} for exactly how. Nothing here is read by
 * Cherry's runtime directly; Cherry only ever reads the generated {@code cherry-plugin.json} and
 * {@code *.mixins.json} files this plugin writes into the jar - see the Cherry repository README's
 * "Manifest schema" section for the exact contract this extension's output must satisfy.
 */
public abstract class CherryExtension {

    private final Project project;

    @Inject
    public CherryExtension(Project project) {
        this.project = project;

        getPluginName().convention(project.getName());
        getMixinConfigName().convention(getMixinPackage().map(pkg -> "mixins." + safeName(project) + ".json"));
        getRefmapName().convention(getMixinConfigName().map(CherryExtension::refmapNameFor));
        getAccessWidenerName().convention(safeName(project) + ".accesswidener");
        // priority/minVersion/compatibilityLevel are intentionally left with no convention at all
        // (not even null) - an unset Property<T> is simply absent, which is exactly what "omit this
        // field from the generated config, let Mixin apply its own default" needs.
        getRequired().convention(true);
    }

    /**
     * The dot-separated Java package (inside this project's compiled output) that holds the
     * author's {@code @Mixin} classes. Required for a plugin to carry a mixin block at all; a
     * plugin that declares only {@link #getAccessTransformers()} may leave this unset.
     *
     * <p>Written verbatim into the generated {@code cherry-plugin.json}'s {@code mixin.package-name}
     * and the generated mixin config's {@code package} field.
     */
    public abstract Property<String> getMixinPackage();

    /**
     * The plugin name written into {@code cherry-plugin.json}'s {@code name} field (used by Cherry
     * for logging and to namespace registered access-transformer definitions). Defaults to the
     * Gradle project's name.
     */
    public abstract Property<String> getPluginName();

    /**
     * The file name (resolved relative to the jar root) the generated SpongePowered Mixin config is
     * written to and referenced from {@code cherry-plugin.json}'s {@code mixin.mixins} list.
     * Defaults to {@code mixins.<project-name>.json}.
     */
    public abstract Property<String> getMixinConfigName();

    /**
     * Extra, already-existing SpongePowered Mixin config file names (e.g. a config the author hand
     * wrote and ships as a resource under {@code src/main/resources/}) to reference in the generated
     * {@code cherry-plugin.json}'s {@code mixin.mixins} list <b>alongside</b> the one this plugin
     * generates from {@link #getMixinPackage()}. Cherry's manifest schema allows any number of mixin
     * config entries, so these are simply appended - see the repository README's manifest schema.
     * Empty by default; the author is responsible for making sure each name resolves to a real jar
     * resource (this plugin does not generate or validate these).
     */
    public abstract ListProperty<String> getMixinConfigs();

    /**
     * The SpongePowered Mixin config {@code priority} field (ascending; Mixin's own default is 1000
     * when omitted - see {@code dev.iyanz.sourbyclip.cherry.manifest.MixinConfigMeta.DEFAULT_PRIORITY}
     * in the main Cherry module). Left unset by default, which omits the field entirely so Mixin's
     * own default applies.
     */
    public abstract Property<Integer> getPriority();

    /**
     * The SpongePowered Mixin config {@code minVersion} field (the minimum Mixin version this config
     * requires, e.g. {@code "0.8"}). Omitted from the generated config unless set.
     */
    public abstract Property<String> getMinVersion();

    /**
     * The SpongePowered Mixin config {@code compatibilityLevel} field (e.g. {@code "JAVA_21"}).
     * Omitted from the generated config unless set.
     */
    public abstract Property<String> getCompatibilityLevel();

    /**
     * The SpongePowered Mixin config {@code required} field. Defaults to {@code true} (Mixin's own
     * default), meaning a failure to apply any mixin in this config is fatal to that mixin
     * environment. Set to {@code false} to make every mixin in the generated config optional.
     */
    public abstract Property<Boolean> getRequired();

    /**
     * The file name the generated refmap (produced by the Mixin annotation processor via
     * {@code -AoutRefMapFile}) is written to and referenced from the generated mixin config's
     * {@code refmap} field. Defaults to the mixin config's own file name with {@code .json} replaced
     * by {@code .refmap.json} (e.g. {@code mixins.myplugin.json} -> {@code mixins.myplugin.refmap.json}).
     */
    public abstract Property<String> getRefmapName();

    /**
     * Access-transformer ({@code .at}) files, in Cherry/Horizon's grammar (see the repository
     * README's "Access-transformer (.at) file format" section) - use {@code .from(...)} to add one or
     * more. Each file is validated against that exact grammar at build time (failing the build with
     * the offending file/line on a syntax error, instead of Cherry's runtime "log and skip"
     * leniency - an author gets the mistake immediately, not silently at server boot) and copied,
     * under its own file name, to the jar root, then listed in the generated
     * {@code cherry-plugin.json}'s {@code access-transformers} array.
     */
    public abstract ConfigurableFileCollection getAccessTransformers();

    /**
     * Fabric access-widener files - use {@code .from(...)} to add one or more. Cherry's manifest
     * schema (see {@code mixin.access-widener} in the repository README) only has room for a single
     * access-widener file per plugin: zero files here omits the field entirely; exactly one file is
     * copied through verbatim under {@link #getAccessWidenerName()}; more than one are merged (the
     * first file's {@code accessWidener <version> <namespace>} header line is kept, and every file's
     * entry lines are concatenated beneath it - see {@code AccessWidenerMerger} - failing the build
     * if two files declare incompatible headers).
     */
    public abstract ConfigurableFileCollection getAccessWideners();

    /**
     * The file name the (possibly merged) access-widener content is written to and referenced from
     * {@code cherry-plugin.json}'s {@code mixin.access-widener} field, when {@link #getAccessWideners()}
     * is non-empty. Defaults to {@code <project-name>.accesswidener}.
     */
    public abstract Property<String> getAccessWidenerName();

    /**
     * Convenience for adding the plugin author's own server/Paper API dependency (whatever their
     * {@code @Mixin} classes actually target) as {@code compileOnly} - Cherry has no published
     * runtime API artifact of its own to add here (see the main repository README's "How Cherry is
     * actually consumed" section: Cherry is vendored source inside SourbyClip's launcher, not a
     * published library), so there is nothing else this method needs to add beyond what
     * {@link CherryGradlePlugin} already wires automatically (Mixin + MixinExtras, pinned to match
     * Cherry's runtime - see that class's {@code MIXIN_VERSION}/{@code MIXIN_EXTRAS_VERSION}).
     *
     * @param dependencyNotation any Gradle dependency notation accepted by
     *                           {@link org.gradle.api.artifacts.dsl.DependencyHandler#add(String, Object)}
     *                           (a {@code "group:artifact:version"} string, a project dependency, a
     *                           file collection, etc.)
     */
    public void serverApi(Object dependencyNotation) {
        project.getDependencies().add("compileOnly", dependencyNotation);
    }

    private static String safeName(Project project) {
        return project.getName().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static String refmapNameFor(String mixinConfigName) {
        if (mixinConfigName.endsWith(".json")) {
            return mixinConfigName.substring(0, mixinConfigName.length() - ".json".length()) + ".refmap.json";
        }
        return mixinConfigName + ".refmap.json";
    }
}
