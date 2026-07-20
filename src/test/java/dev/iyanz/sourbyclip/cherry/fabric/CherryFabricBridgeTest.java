package dev.iyanz.sourbyclip.cherry.fabric;

import dev.iyanz.sourbyclip.cherry.discovery.CherryDiscovery;
import dev.iyanz.sourbyclip.cherry.discovery.DiscoveryReport;
import dev.iyanz.sourbyclip.cherry.testutil.JarBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CherryFabricBridgeTest {

    @Test
    void extractsPackageConfigRefmapAndWidenerIntoOneCacheJar(@TempDir File pluginsDir, @TempDir File cacheDir) throws IOException {
        buildFabricPlugin(pluginsDir, "FabricOne.jar", "fabricone", "com.fabricone.mixin", "1.0-hash-a");

        DiscoveryReport report = CherryDiscovery.scan(pluginsDir);
        FabricResolution resolution = CherryFabricBridge.resolve(report, cacheDir);

        assertEquals(1, resolution.jarUrls().size());
        assertEquals(1, resolution.mixinConfigNames().size());
        assertEquals("fabricone.mixins.json", resolution.mixinConfigNames().get(0));
        assertEquals(1, resolution.accessWidenerConfigs().size());
        assertEquals("fabricone.accesswidener", resolution.accessWidenerConfigs().get(0));
        assertTrue(resolution.skipped().isEmpty(), "unexpected skips: " + resolution.skipped());

        Set<String> entries = readEntryNames(toFile(resolution.jarUrls().get(0)));
        assertTrue(entries.contains("fabricone.mixins.json"));
        assertTrue(entries.contains("fabricone.refmap.json"));
        assertTrue(entries.contains("com/fabricone/mixin/MixinFoo.class"));
        assertTrue(entries.contains("fabricone.accesswidener"));
        assertTrue(entries.contains(CherryFabricBridge.HASH_MARKER_ENTRY));
        assertFalse(entries.contains("some/other/Unrelated.class"), "files outside the mixin package must not be copied");
        assertFalse(entries.contains("fabric.mod.json"), "the manifest itself is not part of the extracted set");
    }

    @Test
    void missingRefmapIsNonFatal(@TempDir File pluginsDir, @TempDir File cacheDir) throws IOException {
        JarBuilder.create()
            .file("fabric.mod.json", "{\"id\": \"norefmap\", \"mixins\": [\"norefmap.mixins.json\"]}")
            .file("norefmap.mixins.json", "{\"package\": \"com.norefmap.mixin\", \"refmap\": \"does-not-exist.json\"}")
            .file("com/norefmap/mixin/MixinFoo.class", "fake-bytes")
            .writeTo(pluginsDir, "NoRefmap.jar");

        DiscoveryReport report = CherryDiscovery.scan(pluginsDir);
        FabricResolution resolution = CherryFabricBridge.resolve(report, cacheDir);

        assertEquals(1, resolution.jarUrls().size());
        assertEquals(1, resolution.mixinConfigNames().size());
        assertTrue(resolution.skipped().isEmpty(), "a missing refmap must degrade gracefully, not fail extraction");
    }

    @Test
    void unchangedPluginJarReusesTheCachedExtractionWithoutRewriting(
        @TempDir File pluginsDir, @TempDir File cacheDir
    ) throws IOException {
        buildFabricPlugin(pluginsDir, "Stable.jar", "stable", "com.stable.mixin", "hash-1");
        DiscoveryReport report = CherryDiscovery.scan(pluginsDir);

        FabricResolution first = CherryFabricBridge.resolve(report, cacheDir);
        File cacheFile = toFile(first.jarUrls().get(0));

        // Poison the cache jar with a marker entry that only survives if the bridge truly reuses
        // the cached jar rather than rebuilding it on the second call.
        appendPoisonEntry(cacheFile);
        assertTrue(readEntryNames(cacheFile).contains("poison-marker"));

        FabricResolution second = CherryFabricBridge.resolve(CherryDiscovery.scan(pluginsDir), cacheDir);
        assertEquals(1, second.jarUrls().size());
        assertTrue(readEntryNames(cacheFile).contains("poison-marker"),
            "cache jar should not have been rewritten for an unchanged source plugin jar");
    }

    @Test
    void changedPluginJarInvalidatesTheCache(@TempDir File pluginsDir, @TempDir File cacheDir) throws IOException {
        buildFabricPlugin(pluginsDir, "Changing.jar", "changing", "com.changing.mixin", "hash-1");
        DiscoveryReport firstReport = CherryDiscovery.scan(pluginsDir);
        FabricResolution first = CherryFabricBridge.resolve(firstReport, cacheDir);
        File cacheFile = toFile(first.jarUrls().get(0));
        appendPoisonEntry(cacheFile);

        // Rebuild the same-named plugin jar with different content (different class body bytes),
        // which changes its MD5 and must force re-extraction.
        Files.deleteIfExists(new File(pluginsDir, "Changing.jar").toPath());
        buildFabricPlugin(pluginsDir, "Changing.jar", "changing", "com.changing.mixin", "hash-2-different");

        FabricResolution second = CherryFabricBridge.resolve(CherryDiscovery.scan(pluginsDir), cacheDir);
        assertEquals(1, second.jarUrls().size());
        assertFalse(readEntryNames(cacheFile).contains("poison-marker"),
            "cache jar must be rebuilt (poison entry gone) once the source plugin jar changes");
    }

    @Test
    void staleCacheFilesForRemovedPluginsAreCleanedUp(@TempDir File pluginsDir, @TempDir File cacheDir) throws IOException {
        buildFabricPlugin(pluginsDir, "Keep.jar", "keep", "com.keep.mixin", "hash-keep");
        CherryFabricBridge.resolve(CherryDiscovery.scan(pluginsDir), cacheDir);

        File orphan = new File(cacheDir, "orphan.cherry-fabric.mixins.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(orphan.toPath()))) {
            out.putNextEntry(new JarEntry("dummy"));
            out.write("dummy".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertTrue(orphan.isFile());

        // Re-resolve with only "keep" present - the orphan (from a plugin no longer discovered) must go.
        CherryFabricBridge.resolve(CherryDiscovery.scan(pluginsDir), cacheDir);
        assertFalse(orphan.exists(), "stale cache jar for a plugin no longer discovered should be removed");
    }

    @Test
    void onePluginsExtractionFailureDoesNotAffectAnother(@TempDir File pluginsDir, @TempDir File cacheDir) throws IOException {
        buildFabricPlugin(pluginsDir, "Good.jar", "good", "com.good.mixin", "good-hash");
        File brokenJar = buildFabricPlugin(pluginsDir, "Broken.jar", "broken", "com.broken.mixin", "broken-hash");

        DiscoveryReport report = CherryDiscovery.scan(pluginsDir);
        // Simulate the plugin jar vanishing between discovery and extraction (e.g. an operator
        // deleting/replacing it mid-boot) - extraction re-opens the jar, so this must fail only for
        // "Broken", not for "Good".
        assertTrue(brokenJar.delete());

        FabricResolution resolution = CherryFabricBridge.resolve(report, cacheDir);
        assertEquals(1, resolution.jarUrls().size());
        assertEquals(1, resolution.mixinConfigNames().size());
        assertEquals("good.mixins.json", resolution.mixinConfigNames().get(0));
        assertEquals(1, resolution.skipped().size());
        assertEquals("broken", resolution.skipped().get(0).pluginName());
    }

    private static File buildFabricPlugin(File pluginsDir, String jarName, String id, String mixinPackage, String marker) throws IOException {
        String packagePath = mixinPackage.replace('.', '/');
        return JarBuilder.create()
            .file("fabric.mod.json", "{\"id\": \"" + id + "\", \"mixins\": [\"" + id + ".mixins.json\"], "
                + "\"accessWidener\": \"" + id + ".accesswidener\"}")
            .file(id + ".mixins.json", "{\"package\": \"" + mixinPackage + "\", \"refmap\": \"" + id + ".refmap.json\"}")
            .file(id + ".refmap.json", "{\"mappings\": {}}")
            .file(id + ".accesswidener", "accessWidener\tv2\tnamed\n")
            .file(packagePath + "/MixinFoo.class", "fake-bytes-" + marker)
            .file("some/other/Unrelated.class", "should-not-be-copied")
            .writeTo(pluginsDir, jarName);
    }

    private static void appendPoisonEntry(File jar) throws IOException {
        File tmp = new File(jar.getParentFile(), jar.getName() + ".rewrite");
        try (JarFile in = new JarFile(jar); JarOutputStream out = new JarOutputStream(Files.newOutputStream(tmp.toPath()))) {
            in.entries().asIterator().forEachRemaining(entry -> {
                try {
                    out.putNextEntry(new JarEntry(entry.getName()));
                    try (InputStream is = in.getInputStream(entry)) {
                        is.transferTo(out);
                    }
                    out.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOExceptionForTest(e);
                }
            });
            out.putNextEntry(new JarEntry("poison-marker"));
            out.write("poison".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        } catch (UncheckedIOExceptionForTest e) {
            throw e.cause;
        }
        Files.move(tmp.toPath(), jar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static File toFile(java.net.URL url) throws IOException {
        try {
            return new File(url.toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static Set<String> readEntryNames(File jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar)) {
            Set<String> names = new java.util.HashSet<>();
            jarFile.entries().asIterator().forEachRemaining(e -> names.add(e.getName()));
            return names;
        }
    }

    /** Wraps an IOException so it can cross a lambda boundary (forEachRemaining doesn't declare checked exceptions). */
    private static final class UncheckedIOExceptionForTest extends RuntimeException {
        private final IOException cause;

        UncheckedIOExceptionForTest(IOException cause) {
            this.cause = cause;
        }
    }
}
