package dev.iyanz.cherry.gradle;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Pure logic: builds the {@code cherry-plugin.json} manifest Cherry's runtime reads, exactly
 * matching the schema documented in the main Cherry repository README ("Manifest schema") and
 * consumed by {@code dev.iyanz.sourbyclip.cherry.discovery.CherryDiscovery} /
 * {@code org.leavesmc.leavesclip.mixin.LeavesPluginMeta} (the {@code name}, {@code mixin.package-name},
 * {@code mixin.mixins}, {@code mixin.access-widener}, and top-level {@code access-transformers}
 * fields, in that exact shape - a plain JSON object, an object for {@code mixin}, string arrays for
 * the two list fields).
 *
 * <p>No Gradle types appear anywhere in this class on purpose: every input is a plain Java value, so
 * this can (and is) unit tested directly, without spinning up a {@link org.gradle.api.Project} or
 * running a build.
 */
public final class ManifestGenerator {

    private ManifestGenerator() {
    }

    /**
     * @param pluginName               the manifest's top-level {@code name} - required, never blank
     * @param mixinPackage             the {@code mixin.package-name} value, or {@code null}/blank to
     *                                 omit the whole {@code mixin} block entirely (a pure
     *                                 access-transformer-only plugin)
     * @param mixinConfigNames         the {@code mixin.mixins} list (jar-root-relative mixin config
     *                                 file names) - ignored if {@code mixinPackage} is absent; if
     *                                 present, must be non-empty (a mixin block with no config makes
     *                                 no sense)
     * @param accessWidenerFileName    the {@code mixin.access-widener} value, or {@code null}/blank
     *                                 to omit the field - ignored if {@code mixinPackage} is absent,
     *                                 since Cherry only reads it as part of a {@code mixin} block
     * @param accessTransformerFileNames the top-level {@code access-transformers} list, or
     *                                   {@code null}/empty to omit the field entirely
     * @return the manifest as a Gson {@link JsonObject}; use {@link #toJson(JsonObject)} to render it
     */
    public static JsonObject build(
        String pluginName,
        String mixinPackage,
        List<String> mixinConfigNames,
        String accessWidenerFileName,
        List<String> accessTransformerFileNames
    ) {
        if (pluginName == null || pluginName.isBlank()) {
            throw new IllegalArgumentException("cherry-plugin.json requires a non-blank plugin name");
        }

        JsonObject root = new JsonObject();
        root.addProperty("name", pluginName);

        if (mixinPackage != null && !mixinPackage.isBlank()) {
            if (mixinConfigNames == null || mixinConfigNames.isEmpty()) {
                throw new IllegalArgumentException(
                    "mixinPackage '" + mixinPackage + "' is set but no mixin config file names were provided; " +
                        "cherry-plugin.json's mixin.mixins list would be empty");
            }
            JsonObject mixin = new JsonObject();
            mixin.addProperty("package-name", mixinPackage);
            mixin.add("mixins", stringArray(mixinConfigNames));
            if (accessWidenerFileName != null && !accessWidenerFileName.isBlank()) {
                mixin.addProperty("access-widener", accessWidenerFileName);
            }
            root.add("mixin", mixin);
        }

        if (accessTransformerFileNames != null && !accessTransformerFileNames.isEmpty()) {
            root.add("access-transformers", stringArray(accessTransformerFileNames));
        }

        return root;
    }

    /** Pretty-printed (2-space indent, matching this repository's own style), UTF-8 safe JSON text. */
    public static String toJson(JsonObject manifest) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(manifest);
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
