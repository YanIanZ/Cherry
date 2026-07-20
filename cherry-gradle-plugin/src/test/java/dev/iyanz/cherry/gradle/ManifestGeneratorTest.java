package dev.iyanz.cherry.gradle;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts {@link ManifestGenerator}'s output matches, field-for-field, the {@code cherry-plugin.json}
 * schema documented in the main Cherry repository README and read by
 * {@code dev.iyanz.sourbyclip.cherry.discovery.CherryDiscovery} /
 * {@code org.leavesmc.leavesclip.mixin.LeavesPluginMeta} (verified directly against that class's
 * source while writing this plugin - see {@code LeavesPluginMeta}'s {@code @SerializedName}s).
 */
class ManifestGeneratorTest {

    @Test
    void fullManifestHasEveryField() {
        JsonObject manifest = ManifestGenerator.build(
            "MyPlugin",
            "com.me.myplugin.mixin",
            List.of("mixins.myplugin.json"),
            "myplugin.accesswidener",
            List.of("myplugin.at")
        );

        assertEquals("MyPlugin", manifest.get("name").getAsString());

        JsonObject mixin = manifest.getAsJsonObject("mixin");
        assertEquals("com.me.myplugin.mixin", mixin.get("package-name").getAsString());
        assertEquals("mixins.myplugin.json", mixin.getAsJsonArray("mixins").get(0).getAsString());
        assertEquals("myplugin.accesswidener", mixin.get("access-widener").getAsString());

        assertEquals("myplugin.at", manifest.getAsJsonArray("access-transformers").get(0).getAsString());
    }

    @Test
    void multipleMixinConfigsArePreservedInOrder() {
        JsonObject manifest = ManifestGenerator.build(
            "MyPlugin", "com.me.mixin", List.of("mixins.me.json", "legacy.mixins.json"), null, List.of()
        );
        var mixins = manifest.getAsJsonObject("mixin").getAsJsonArray("mixins");
        assertEquals(2, mixins.size());
        assertEquals("mixins.me.json", mixins.get(0).getAsString());
        assertEquals("legacy.mixins.json", mixins.get(1).getAsString());
    }

    @Test
    void accessTransformerOnlyPluginOmitsMixinBlock() {
        JsonObject manifest = ManifestGenerator.build("ATOnlyPlugin", null, List.of(), null, List.of("widen.at"));

        assertEquals("ATOnlyPlugin", manifest.get("name").getAsString());
        assertFalse(manifest.has("mixin"));
        assertEquals("widen.at", manifest.getAsJsonArray("access-transformers").get(0).getAsString());
    }

    @Test
    void mixinOnlyPluginOmitsAccessTransformersField() {
        JsonObject manifest = ManifestGenerator.build("MixinOnlyPlugin", "com.me.mixin", List.of("mixins.me.json"), null, List.of());

        assertFalse(manifest.has("access-transformers"));
        assertTrue(manifest.has("mixin"));
    }

    @Test
    void blankMixinPackageIsTreatedAsAbsent() {
        JsonObject manifest = ManifestGenerator.build("P", "   ", List.of("x.json"), null, List.of());
        assertFalse(manifest.has("mixin"));
    }

    @Test
    void accessWidenerOmittedWhenNull() {
        JsonObject manifest = ManifestGenerator.build("P", "com.me.mixin", List.of("mixins.me.json"), null, List.of());
        assertFalse(manifest.getAsJsonObject("mixin").has("access-widener"));
    }

    @Test
    void blankPluginNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> ManifestGenerator.build("  ", null, List.of(), null, List.of()));
    }

    @Test
    void mixinPackageWithNoConfigsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ManifestGenerator.build("P", "com.me.mixin", List.of(), null, List.of()));
    }

    @Test
    void toJsonProducesParseableUtf8Text() {
        JsonObject manifest = ManifestGenerator.build("P", null, List.of(), null, List.of());
        String json = ManifestGenerator.toJson(manifest);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"P\""));
    }
}
