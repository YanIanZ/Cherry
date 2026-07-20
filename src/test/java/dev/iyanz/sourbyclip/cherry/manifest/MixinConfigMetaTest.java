package dev.iyanz.sourbyclip.cherry.manifest;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MixinConfigMetaTest {

    private static final Gson GSON = new Gson();

    @Test
    void readsPackageRefmapAndPriority() {
        MixinConfigMeta meta = GSON.fromJson("""
            {
              "package": "com.example.mixin",
              "refmap": "com.example.refmap.json",
              "priority": 1200,
              "mixins": ["MixinFoo", "MixinBar"],
              "server": ["MixinServerOnly"]
            }
            """, MixinConfigMeta.class);

        assertEquals("com.example.mixin", meta.getPackageName());
        assertEquals("com.example.refmap.json", meta.getRefmap());
        assertEquals(1200, meta.priorityOrDefault());
        assertEquals(3, meta.declaredMixinCount());
    }

    @Test
    void missingPriorityFallsBackToSpongeDefault() {
        MixinConfigMeta meta = GSON.fromJson("{\"package\": \"com.example.mixin\"}", MixinConfigMeta.class);
        assertEquals(MixinConfigMeta.DEFAULT_PRIORITY, meta.priorityOrDefault());
        assertEquals(1000, meta.priorityOrDefault());
    }

    @Test
    void missingRefmapAndPackageAreNull() {
        MixinConfigMeta meta = GSON.fromJson("{}", MixinConfigMeta.class);
        assertNull(meta.getPackageName());
        assertNull(meta.getRefmap());
        assertEquals(0, meta.declaredMixinCount());
    }
}
