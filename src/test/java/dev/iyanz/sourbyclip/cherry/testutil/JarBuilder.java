package dev.iyanz.sourbyclip.cherry.testutil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Test-only helper: builds a small {@code .jar} file with arbitrary entries, so discovery/extraction
 * tests can construct fixture plugin jars without hand-rolling {@code JarOutputStream} boilerplate in
 * every test. Not part of Cherry's public API - lives under {@code src/test/java} only.
 */
public final class JarBuilder {

    private final Map<String, byte[]> entries = new LinkedHashMap<>();

    private JarBuilder() {
    }

    public static JarBuilder create() {
        return new JarBuilder();
    }

    public JarBuilder file(String name, String content) {
        entries.put(name, content.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    public JarBuilder file(String name, byte[] content) {
        entries.put(name, content);
        return this;
    }

    public File writeTo(File dir, String jarName) throws IOException {
        File jar = new File(dir, jarName);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }
}
