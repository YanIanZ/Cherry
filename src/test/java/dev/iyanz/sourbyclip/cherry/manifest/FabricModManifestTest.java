package dev.iyanz.sourbyclip.cherry.manifest;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricModManifestTest {

    private static final Gson GSON = new Gson();

    @Test
    void plainStringEntriesDefaultToUniversalEnvironment() {
        FabricModManifest manifest = GSON.fromJson("""
            {"id": "myfabricmod", "mixins": ["myfabricmod.mixins.json"]}
            """, FabricModManifest.class);

        assertEquals("myfabricmod", manifest.getId());
        List<FabricMixinEntry> entries = manifest.mixinEntries();
        assertEquals(1, entries.size());
        assertEquals("myfabricmod.mixins.json", entries.get(0).config());
        assertEquals("*", entries.get(0).environment());
        assertTrue(entries.get(0).appliesToServer());
    }

    @Test
    void objectEntriesRespectDeclaredEnvironment() {
        FabricModManifest manifest = GSON.fromJson("""
            {
              "id": "myfabricmod",
              "mixins": [
                {"config": "common.mixins.json", "environment": "*"},
                {"config": "server.mixins.json", "environment": "server"},
                {"config": "client.mixins.json", "environment": "client"}
              ]
            }
            """, FabricModManifest.class);

        List<FabricMixinEntry> entries = manifest.mixinEntries();
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).appliesToServer());
        assertTrue(entries.get(1).appliesToServer());
        assertFalse(entries.get(2).appliesToServer());
    }

    @Test
    void objectEntryWithoutEnvironmentDefaultsToUniversal() {
        FabricModManifest manifest = GSON.fromJson("""
            {"mixins": [{"config": "no-env.mixins.json"}]}
            """, FabricModManifest.class);

        FabricMixinEntry entry = manifest.mixinEntries().get(0);
        assertEquals("*", entry.environment());
        assertTrue(entry.appliesToServer());
    }

    @Test
    void objectEntryMissingConfigIsSkipped() {
        FabricModManifest manifest = GSON.fromJson("""
            {"mixins": [{"environment": "server"}, {"config": "valid.mixins.json"}]}
            """, FabricModManifest.class);

        List<FabricMixinEntry> entries = manifest.mixinEntries();
        assertEquals(1, entries.size());
        assertEquals("valid.mixins.json", entries.get(0).config());
    }

    @Test
    void blankConfigStringIsSkipped() {
        FabricModManifest manifest = GSON.fromJson("""
            {"mixins": ["", "  ", "real.mixins.json"]}
            """, FabricModManifest.class);

        List<FabricMixinEntry> entries = manifest.mixinEntries();
        assertEquals(1, entries.size());
        assertEquals("real.mixins.json", entries.get(0).config());
    }

    @Test
    void nullOrMissingMixinsFieldYieldsEmptyList() {
        FabricModManifest withNull = GSON.fromJson("{\"mixins\": null}", FabricModManifest.class);
        assertTrue(withNull.mixinEntries().isEmpty());

        FabricModManifest missing = GSON.fromJson("{}", FabricModManifest.class);
        assertTrue(missing.mixinEntries().isEmpty());
        assertNull(missing.getId());
        assertNull(missing.getAccessWidener());
    }

    @Test
    void malformedArrayElementIsSkippedNotThrown() {
        FabricModManifest manifest = GSON.fromJson("""
            {"mixins": [["nested", "array"], 42, true, "valid.mixins.json"]}
            """, FabricModManifest.class);

        List<FabricMixinEntry> entries = manifest.mixinEntries();
        assertEquals(1, entries.size());
        assertEquals("valid.mixins.json", entries.get(0).config());
    }

    @Test
    void accessWidenerFieldIsRead() {
        FabricModManifest manifest = GSON.fromJson("""
            {"id": "myfabricmod", "accessWidener": "myfabricmod.accesswidener"}
            """, FabricModManifest.class);

        assertEquals("myfabricmod.accesswidener", manifest.getAccessWidener());
    }

    @Test
    void unrecognizedEnvironmentValueDoesNotApplyToServer() {
        FabricMixinEntry entry = new FabricMixinEntry("weird.mixins.json", "some-typo");
        assertFalse(entry.appliesToServer());
    }

    @Test
    void serverEnvironmentIsCaseInsensitive() {
        FabricMixinEntry entry = new FabricMixinEntry("weird.mixins.json", "SERVER");
        assertTrue(entry.appliesToServer());
    }
}
