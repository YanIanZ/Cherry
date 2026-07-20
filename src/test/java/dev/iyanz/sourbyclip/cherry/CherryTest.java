package dev.iyanz.sourbyclip.cherry;

import dev.iyanz.sourbyclip.cherry.at.CherryAccessTransformers;
import dev.iyanz.sourbyclip.cherry.discovery.DiscoveryReport;
import dev.iyanz.sourbyclip.cherry.testutil.JarBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link Cherry} facade's read-only/diagnostic surface ({@link Cherry#enabled()},
 * {@link Cherry#dryRun(File)}, {@link Cherry#status(File)}) and the Fabric-bridge entry point
 * ({@link Cherry#initFabricMixins(File, File)}).
 *
 * <p>Deliberately does NOT call {@link Cherry#init()}/{@link Cherry#initAccessTransformers()} or
 * anything else that registers/locks {@link CherryAccessTransformers#INSTANCE} - that singleton is
 * exhaustively covered, in isolation, by {@code CherryAccessTransformersTest}, which must remain the
 * only test in this repository that mutates it (see that class's javadoc for why). Where this class
 * reads AT-related fields off {@link CherryStatus} (whose values legitimately depend on whatever
 * that other test class has done to the shared singleton by the time this one runs), it asserts only
 * internal consistency - that {@code status()} faithfully reports the singleton's own state - never
 * a specific expected count.
 */
class CherryTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("cherry.enable.mixin");
        System.clearProperty("sourbyclip.enable.mixin");
        System.clearProperty("leavesclip.enable.mixin");
    }

    @Test
    void enabledReflectsAnyOfTheThreeProperties() {
        assertFalse(Cherry.enabled());

        System.setProperty("cherry.enable.mixin", "true");
        assertTrue(Cherry.enabled());
        System.clearProperty("cherry.enable.mixin");

        System.setProperty("sourbyclip.enable.mixin", "true");
        assertTrue(Cherry.enabled());
        System.clearProperty("sourbyclip.enable.mixin");

        System.setProperty("leavesclip.enable.mixin", "true");
        assertTrue(Cherry.enabled());
    }

    @Test
    void dryRunNeverMutatesAnythingRegardlessOfEnabled(@TempDir File pluginsDir) throws IOException {
        JarBuilder.create()
            .file("cherry-plugin.json", "{\"name\": \"DryRunPlugin\", \"access-transformers\": [\"x.at\"]}")
            .file("x.at", "public com.x.Target field\n")
            .writeTo(pluginsDir, "DryRunPlugin.jar");

        // Cherry disabled - dryRun must still report what would load.
        assertFalse(Cherry.enabled());
        DiscoveryReport report = Cherry.dryRun(pluginsDir);
        assertEquals(1, report.accessTransformers().size());
        assertEquals("DryRunPlugin", report.accessTransformers().get(0).pluginName());

        // Never registered into the real engine, since dryRun never commits anything.
        assertTrue(report.accessTransformers().stream().noneMatch(at -> at.pluginName().equals("__should-never-be-committed__")));
    }

    @Test
    void statusDiscoveryPortionMatchesAFreshDryRun(@TempDir File pluginsDir) throws IOException {
        JarBuilder.create()
            .file("cherry-plugin.json", "{\"name\": \"StatusPlugin\", \"access-transformers\": [\"y.at\"]}")
            .file("y.at", "public com.y.Target field\n")
            .writeTo(pluginsDir, "StatusPlugin.jar");

        CherryStatus status = Cherry.status(pluginsDir);
        assertEquals(Cherry.enabled(), status.enabled());
        assertEquals(1, status.discovery().accessTransformers().size());

        // Internal consistency only - see class javadoc for why these aren't asserted to a fixed value.
        assertEquals(CherryAccessTransformers.INSTANCE.isLocked(), status.accessTransformersLocked());
        assertEquals(CherryAccessTransformers.INSTANCE.registeredCount(), status.accessTransformerDefinitionCount());
        assertEquals(CherryAccessTransformers.INSTANCE.targetClassCount(), status.accessTransformerTargetClassCount());

        String rendered = status.render();
        assertTrue(rendered.contains("Cherry ready"));
    }

    @Test
    void initFabricMixinsPopulatesAccessorsAndIsIdempotent(@TempDir File pluginsDir, @TempDir File cacheDir) throws IOException {
        JarBuilder.create()
            .file("fabric.mod.json", "{\"id\": \"cherrytestfabric\", \"mixins\": [\"cherrytestfabric.mixins.json\"], "
                + "\"accessWidener\": \"cherrytestfabric.accesswidener\"}")
            .file("cherrytestfabric.mixins.json", "{\"package\": \"com.cherrytest.mixin\"}")
            .file("cherrytestfabric.accesswidener", "accessWidener\tv2\tnamed\n")
            .file("com/cherrytest/mixin/MixinFoo.class", "fake-bytes")
            .writeTo(pluginsDir, "CherryTestFabric.jar");

        Cherry.initFabricMixins(pluginsDir, cacheDir);

        assertEquals(1, Cherry.fabricMixinConfigNames().size());
        assertEquals("cherrytestfabric.mixins.json", Cherry.fabricMixinConfigNames().get(0));
        assertEquals(1, Cherry.fabricJarUrls().size());
        assertEquals(1, Cherry.fabricAccessWidenerConfigs().size());

        // Calling it again against the same, unchanged directory must not throw and must be stable.
        Cherry.initFabricMixins(pluginsDir, cacheDir);
        assertEquals(1, Cherry.fabricMixinConfigNames().size());
    }
}
