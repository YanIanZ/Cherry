package dev.iyanz.cherry.gradle;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts {@link MixinConfigGenerator}'s output matches the standalone SpongePowered Mixin config
 * schema {@code dev.iyanz.sourbyclip.cherry.manifest.MixinConfigMeta} reads back ({@code package},
 * {@code refmap}, {@code priority}, {@code mixins}), plus the extra fields Mixin itself (not Cherry)
 * consumes at apply time ({@code minVersion}, {@code compatibilityLevel}, {@code required}).
 */
class MixinConfigGeneratorTest {

    @Test
    void fullConfigHasEveryField() {
        JsonObject config = MixinConfigGenerator.build(
            "com.me.myplugin.mixin",
            List.of("MixinSomeMob", "MixinOtherThing"),
            "mixins.myplugin.refmap.json",
            500,
            "0.8",
            "JAVA_21",
            false
        );

        assertEquals("com.me.myplugin.mixin", config.get("package").getAsString());
        assertEquals("mixins.myplugin.refmap.json", config.get("refmap").getAsString());
        assertEquals(500, config.get("priority").getAsInt());
        assertEquals("0.8", config.get("minVersion").getAsString());
        assertEquals("JAVA_21", config.get("compatibilityLevel").getAsString());
        assertFalse(config.get("required").getAsBoolean());

        var mixins = config.getAsJsonArray("mixins");
        assertEquals(2, mixins.size());
        assertEquals("MixinSomeMob", mixins.get(0).getAsString());
        assertEquals("MixinOtherThing", mixins.get(1).getAsString());
    }

    @Test
    void optionalFieldsOmittedWhenNull() {
        JsonObject config = MixinConfigGenerator.build("com.me.mixin", List.of("MixinFoo"), null, null, null, null, null);

        assertFalse(config.has("refmap"));
        assertFalse(config.has("priority"));
        assertFalse(config.has("minVersion"));
        assertFalse(config.has("compatibilityLevel"));
        assertFalse(config.has("required"));
        assertTrue(config.has("mixins"));
    }

    @Test
    void blankPackageRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> MixinConfigGenerator.build(" ", List.of("MixinFoo"), null, null, null, null, null));
    }

    @Test
    void emptyMixinListRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> MixinConfigGenerator.build("com.me.mixin", List.of(), null, null, null, null, null));
    }

    @Test
    void nullMixinListRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> MixinConfigGenerator.build("com.me.mixin", null, null, null, null, null, null));
    }

    @Test
    void toJsonProducesParseableUtf8Text() {
        JsonObject config = MixinConfigGenerator.build("com.me.mixin", List.of("MixinFoo"), null, null, null, null, null);
        String json = MixinConfigGenerator.toJson(config);
        assertTrue(json.contains("\"package\""));
        assertTrue(json.contains("MixinFoo"));
    }
}
