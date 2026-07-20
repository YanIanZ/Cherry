package dev.iyanz.sourbyclip.cherry.discovery;

import dev.iyanz.sourbyclip.cherry.manifest.ManifestSource;
import dev.iyanz.sourbyclip.cherry.testutil.JarBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CherryDiscoveryTest {

    @AfterEach
    void clearKillSwitch() {
        System.clearProperty("cherry.disable.plugins");
    }

    @Test
    void emptyOrMissingDirectoryYieldsEmptyReport(@TempDir File tempDir) {
        DiscoveryReport empty = CherryDiscovery.scan(tempDir);
        assertEquals(DiscoveryReport.empty(), empty);

        DiscoveryReport missing = CherryDiscovery.scan(new File(tempDir, "does-not-exist"));
        assertEquals(DiscoveryReport.empty(), missing);
    }

    @Test
    void cherryManifestWithMixinAndAtIsFullyDiscovered(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("cherry-plugin.json", """
                {
                  "name": "AlphaPlugin",
                  "mixin": {
                    "package-name": "com.alpha.mixin",
                    "mixins": ["alpha.mixins.json"],
                    "access-widener": "alpha.accesswidener"
                  },
                  "access-transformers": ["alpha.at"]
                }
                """)
            .file("alpha.mixins.json", """
                {"package": "com.alpha.mixin", "refmap": "alpha.refmap.json", "priority": 1500, "mixins": ["MixinOne"]}
                """)
            .file("alpha.accesswidener", "accessWidener\tv2\tnamed\n")
            .file("alpha.at", "public com.alpha.Target someField\n")
            .file("com/alpha/mixin/MixinOne.class", "fake-bytes")
            .writeTo(tempDir, "AlphaPlugin.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);

        assertEquals(1, report.scannedJarCount());
        assertEquals(1, report.mixinConfigs().size());
        DiscoveredMixinConfig config = report.mixinConfigs().get(0);
        assertEquals("AlphaPlugin", config.pluginName());
        assertEquals("alpha.mixins.json", config.configEntry());
        assertEquals("com.alpha.mixin", config.packageName());
        assertEquals(1500, config.priority());
        assertEquals("alpha.refmap.json", config.refmap());
        assertEquals(ManifestSource.CHERRY_PLUGIN_JSON, config.source());
        assertFalse(config.requiresCherryExtraction(), "a Leaves/Cherry-declared config is reporting-only");

        assertEquals(1, report.accessTransformers().size());
        DiscoveredAccessTransformer at = report.accessTransformers().get(0);
        assertEquals("AlphaPlugin", at.pluginName());
        assertEquals("alpha.at", at.entryName());

        assertEquals(1, report.accessWideners().size());
        DiscoveredAccessWidener widener = report.accessWideners().get(0);
        assertEquals("alpha.accesswidener", widener.entryName());
        assertFalse(widener.requiresCherryExtraction());

        assertTrue(report.skipped().isEmpty(), "nothing should be skipped for a fully valid manifest: " + report.skipped());
    }

    @Test
    void legacyLeavesManifestStillWorksAndIsTaggedAsSuch(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("leaves-plugin.json", """
                {"name": "LegacyPlugin", "mixin": {"package-name": "com.legacy.mixin", "mixins": ["legacy.mixins.json"]}}
                """)
            .file("legacy.mixins.json", "{\"package\": \"com.legacy.mixin\"}")
            .writeTo(tempDir, "LegacyPlugin.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        assertEquals(1, report.mixinConfigs().size());
        assertEquals(ManifestSource.LEAVES_PLUGIN_JSON, report.mixinConfigs().get(0).source());
    }

    @Test
    void cherryManifestTakesPrecedenceOverLegacyWhenBothPresent(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("cherry-plugin.json", """
                {"name": "BothPlugin", "access-transformers": ["both.at"]}
                """)
            .file("leaves-plugin.json", """
                {"name": "BothPluginLegacyName", "mixin": {"package-name": "com.both.mixin", "mixins": ["both.mixins.json"]}}
                """)
            .file("both.at", "public com.both.Target field\n")
            .writeTo(tempDir, "BothPlugin.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        // Only the cherry-plugin.json's declarations show up - the leaves-plugin.json is ignored entirely.
        assertEquals(1, report.accessTransformers().size());
        assertEquals("BothPlugin", report.accessTransformers().get(0).pluginName());
        assertTrue(report.mixinConfigs().isEmpty(), "leaves-plugin.json's mixin block must not be read when cherry-plugin.json is present");
    }

    @Test
    void fabricModJsonFiltersClientOnlyEntriesAndReadsAccessWidener(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("fabric.mod.json", """
                {
                  "id": "fabricmod",
                  "mixins": [
                    {"config": "fabricmod.common.mixins.json", "environment": "*"},
                    {"config": "fabricmod.client.mixins.json", "environment": "client"}
                  ],
                  "accessWidener": "fabricmod.accesswidener"
                }
                """)
            .file("fabricmod.common.mixins.json", "{\"package\": \"com.fabricmod.mixin\", \"priority\": 900}")
            .file("fabricmod.client.mixins.json", "{\"package\": \"com.fabricmod.mixin.client\"}")
            .file("fabricmod.accesswidener", "accessWidener\tv2\tnamed\n")
            .file("com/fabricmod/mixin/MixinCommon.class", "fake-bytes")
            .writeTo(tempDir, "FabricMod.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);

        assertEquals(1, report.mixinConfigs().size());
        DiscoveredMixinConfig config = report.mixinConfigs().get(0);
        assertEquals("fabricmod.common.mixins.json", config.configEntry());
        assertEquals("com.fabricmod.mixin", config.packageName());
        assertEquals(900, config.priority());
        assertEquals(ManifestSource.FABRIC_MOD_JSON, config.source());
        assertTrue(config.requiresCherryExtraction());

        assertEquals(1, report.accessWideners().size());
        assertEquals(ManifestSource.FABRIC_MOD_JSON, report.accessWideners().get(0).source());

        boolean clientSkipRecorded = report.skipped().stream()
            .anyMatch(s -> s.item().contains("fabricmod.client.mixins.json") && s.reason().contains("client-only"));
        assertTrue(clientSkipRecorded, "client-only entry should be recorded as skipped: " + report.skipped());
    }

    @Test
    void fabricConfigMissingPackageFieldIsSkipped(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("fabric.mod.json", "{\"id\": \"badmod\", \"mixins\": [\"badmod.mixins.json\"]}")
            .file("badmod.mixins.json", "{\"priority\": 100}") // no "package" field
            .writeTo(tempDir, "BadMod.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        assertTrue(report.mixinConfigs().isEmpty());
        assertTrue(report.skipped().stream().anyMatch(s -> s.reason().contains("package")));
    }

    @Test
    void missingDeclaredAtFileIsSkippedWithReason(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("cherry-plugin.json", "{\"name\": \"MissingAtPlugin\", \"access-transformers\": [\"missing.at\"]}")
            .writeTo(tempDir, "MissingAtPlugin.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        assertTrue(report.accessTransformers().isEmpty());
        assertEquals(1, report.skipped().size());
        assertTrue(report.skipped().get(0).reason().contains("missing from jar"));
    }

    @Test
    void duplicateConfigResourceNameAcrossPluginsKeepsFirstAndSkipsSecond(@TempDir File tempDir) throws IOException {
        // "AJar" sorts before "BJar" alphabetically, so AJar's declaration wins deterministically.
        JarBuilder.create()
            .file("cherry-plugin.json", """
                {"name": "APlugin", "mixin": {"package-name": "com.a.mixin", "mixins": ["shared.mixins.json"]}}
                """)
            .file("shared.mixins.json", "{\"package\": \"com.a.mixin\"}")
            .writeTo(tempDir, "AJar.jar");

        JarBuilder.create()
            .file("cherry-plugin.json", """
                {"name": "BPlugin", "mixin": {"package-name": "com.b.mixin", "mixins": ["shared.mixins.json"]}}
                """)
            .file("shared.mixins.json", "{\"package\": \"com.b.mixin\"}")
            .writeTo(tempDir, "BJar.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        assertEquals(1, report.mixinConfigs().size());
        assertEquals("APlugin", report.mixinConfigs().get(0).pluginName());
        assertTrue(report.skipped().stream().anyMatch(s -> s.reason().contains("duplicate resource name")));
    }

    @Test
    void corruptJarIsSkippedWithoutAbortingTheWholeScan(@TempDir File tempDir) throws IOException {
        Files.writeString(new File(tempDir, "NotActuallyAJar.jar").toPath(), "this is not a zip file");
        JarBuilder.create()
            .file("cherry-plugin.json", "{\"name\": \"GoodPlugin\", \"access-transformers\": [\"good.at\"]}")
            .file("good.at", "public com.good.Target field\n")
            .writeTo(tempDir, "ZGoodPlugin.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        assertEquals(2, report.scannedJarCount());
        assertEquals(1, report.accessTransformers().size());
        assertEquals("GoodPlugin", report.accessTransformers().get(0).pluginName());
        assertTrue(report.skipped().stream().anyMatch(s -> s.jarName().equals("NotActuallyAJar.jar")));
    }

    @Test
    void malformedManifestJsonIsSkippedNotThrown(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("cherry-plugin.json", "{ this is not valid json ")
            .writeTo(tempDir, "MalformedManifest.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        assertTrue(report.mixinConfigs().isEmpty());
        assertTrue(report.accessTransformers().isEmpty());
    }

    @Test
    void mixinConfigsAreSortedByPriorityAscending(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("cherry-plugin.json", """
                {"name": "PriorityPlugin", "mixin": {"package-name": "com.pri.mixin",
                  "mixins": ["high.mixins.json", "low.mixins.json", "mid.mixins.json"]}}
                """)
            .file("high.mixins.json", "{\"package\": \"com.pri.mixin\", \"priority\": 2000}")
            .file("low.mixins.json", "{\"package\": \"com.pri.mixin\", \"priority\": 500}")
            .file("mid.mixins.json", "{\"package\": \"com.pri.mixin\", \"priority\": 1000}")
            .writeTo(tempDir, "PriorityPlugin.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        List<Integer> priorities = report.mixinConfigs().stream().map(DiscoveredMixinConfig::priority).toList();
        assertEquals(List.of(500, 1000, 2000), priorities);
    }

    @Test
    void disabledPluginContributesNothing(@TempDir File tempDir) throws IOException {
        System.setProperty("cherry.disable.plugins", "DisabledPlugin");
        JarBuilder.create()
            .file("cherry-plugin.json", "{\"name\": \"DisabledPlugin\", \"access-transformers\": [\"x.at\"]}")
            .file("x.at", "public com.x.Target field\n")
            .writeTo(tempDir, "DisabledPlugin.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        assertTrue(report.accessTransformers().isEmpty());
        Optional<SkippedItem> skip = report.skipped().stream().findFirst();
        assertTrue(skip.isPresent());
        assertTrue(skip.get().reason().contains("disabled"));
    }

    @Test
    void pluginWithNoCherryRelevantManifestContributesNothingAndIsNotSkipped(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("plugin.yml", "name: PlainPlugin\nversion: 1.0\nmain: com.plain.Plugin\n")
            .writeTo(tempDir, "PlainPlugin.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        assertEquals(DiscoveryReport.empty().mixinConfigs(), report.mixinConfigs());
        assertTrue(report.skipped().isEmpty());
    }

    @Test
    void summaryLineCountsDistinctPluginsAndFiles(@TempDir File tempDir) throws IOException {
        JarBuilder.create()
            .file("cherry-plugin.json", """
                {"name": "SummaryPlugin", "mixin": {"package-name": "com.sum.mixin", "mixins": ["s.mixins.json"],
                  "access-widener": "s.accesswidener"}, "access-transformers": ["s.at"]}
                """)
            .file("s.mixins.json", "{\"package\": \"com.sum.mixin\"}")
            .file("s.accesswidener", "accessWidener\tv2\tnamed\n")
            .file("s.at", "public com.sum.Target field\n")
            .writeTo(tempDir, "SummaryPlugin.jar");

        DiscoveryReport report = CherryDiscovery.scan(tempDir);
        assertEquals("Cherry ready: 1 mixin plugin(s), 1 access-transformer(s), 1 widener(s)", report.summaryLine());
    }
}
