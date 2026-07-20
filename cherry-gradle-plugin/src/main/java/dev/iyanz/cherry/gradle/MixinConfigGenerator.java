package dev.iyanz.cherry.gradle;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Pure logic: builds a standalone SpongePowered Mixin config JSON (a {@code *.mixins.json}), in the
 * subset of fields {@code dev.iyanz.sourbyclip.cherry.manifest.MixinConfigMeta} reads
 * ({@code package}, {@code refmap}, {@code priority}, {@code mixins}) plus the two extra standard
 * Mixin config fields ({@code minVersion}, {@code compatibilityLevel}, {@code required}) SpongePowered
 * Mixin itself reads once the config is registered - Cherry's own code never looks at those three,
 * but the real Mixin engine applying the config does, so they still need to be correct.
 *
 * <p>No Gradle types appear here on purpose - see {@link ManifestGenerator}'s class javadoc for why.
 */
public final class MixinConfigGenerator {

    private MixinConfigGenerator() {
    }

    /**
     * @param packageName        the config's {@code package} field - required, never blank
     * @param mixinClassNames    simple (dot-relative-to-{@code packageName}) names of the
     *                           {@code @Mixin} classes this config applies - must be non-empty (a
     *                           config that declares zero mixin classes is almost always an author
     *                           mistake, e.g. a wrong {@code mixinPackage}, so this fails loudly
     *                           rather than silently shipping an empty config)
     * @param refmapFileName     the {@code refmap} field, or {@code null}/blank to omit it
     * @param priority           the {@code priority} field, or {@code null} to omit it (Mixin's own
     *                           default, 1000, then applies)
     * @param minVersion         the {@code minVersion} field, or {@code null}/blank to omit it
     * @param compatibilityLevel the {@code compatibilityLevel} field, or {@code null}/blank to omit it
     * @param required           the {@code required} field, or {@code null} to omit it (Mixin's own
     *                           default, {@code true}, then applies)
     * @return the config as a Gson {@link JsonObject}; use {@link #toJson(JsonObject)} to render it
     */
    public static JsonObject build(
        String packageName,
        List<String> mixinClassNames,
        String refmapFileName,
        Integer priority,
        String minVersion,
        String compatibilityLevel,
        Boolean required
    ) {
        if (packageName == null || packageName.isBlank()) {
            throw new IllegalArgumentException("a mixin config requires a non-blank package name");
        }
        if (mixinClassNames == null || mixinClassNames.isEmpty()) {
            throw new IllegalArgumentException(
                "no @Mixin-annotated classes were found under package '" + packageName + "'; " +
                    "a generated mixin config with an empty mixins list would never apply anything");
        }

        JsonObject root = new JsonObject();
        root.addProperty("package", packageName);
        if (refmapFileName != null && !refmapFileName.isBlank()) {
            root.addProperty("refmap", refmapFileName);
        }
        if (priority != null) {
            root.addProperty("priority", priority);
        }
        if (minVersion != null && !minVersion.isBlank()) {
            root.addProperty("minVersion", minVersion);
        }
        if (compatibilityLevel != null && !compatibilityLevel.isBlank()) {
            root.addProperty("compatibilityLevel", compatibilityLevel);
        }
        if (required != null) {
            root.addProperty("required", required);
        }
        JsonArray mixins = new JsonArray();
        for (String className : mixinClassNames) {
            mixins.add(className);
        }
        root.add("mixins", mixins);

        return root;
    }

    /** Pretty-printed (2-space indent), UTF-8 safe JSON text. */
    public static String toJson(JsonObject config) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(config);
    }
}
