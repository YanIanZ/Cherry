package dev.iyanz.cherry.gradle;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure logic: merges one or more Fabric access-widener (v1/v2) file bodies into a single one, since
 * Cherry's manifest schema (see {@code mixin.access-widener} in the main repository README) only has
 * room for exactly one access-widener file per plugin, while {@code cherry.accessWideners} in the
 * {@code cherry { }} DSL accepts any number of source files for author convenience.
 *
 * <p>The access-widener text format (see
 * <a href="https://github.com/FabricMC/fabric-loom/wiki/Access-Wideners">Fabric's format
 * documentation</a>) is: the first non-blank, non-{@code #}-comment line is a header
 * {@code accessWidener <version> <namespace>}, and every following non-blank/non-comment line is one
 * widening entry. Merging is therefore just: keep one header (from the first file), and concatenate
 * every file's entry lines beneath it - as long as every file agrees on the same header.
 */
public final class AccessWidenerMerger {

    private AccessWidenerMerger() {
    }

    /**
     * @param fileLines the lines of each source file, in the order they should be merged (this
     *                  method never reorders entries)
     * @return the merged file's lines (one header line, then every input file's entry lines, in order)
     * @throws IllegalArgumentException if {@code fileLines} is empty, or if any two files disagree
     *                                   on their header line (different access-widener version or
     *                                   namespace - these cannot be silently merged)
     */
    public static List<String> merge(List<List<String>> fileLines) {
        if (fileLines.isEmpty()) {
            throw new IllegalArgumentException("cannot merge zero access-widener files");
        }

        String header = null;
        List<String> merged = new ArrayList<>();
        for (List<String> lines : fileLines) {
            HeaderAndEntries parsed = split(lines);
            if (header == null) {
                header = parsed.header();
                merged.add(header);
            } else if (parsed.header() != null && !parsed.header().equals(header)) {
                throw new IllegalArgumentException(
                    "cannot merge access-widener files with different headers: \"" + header +
                        "\" vs \"" + parsed.header() + "\"");
            }
            merged.addAll(parsed.entries());
        }
        return List.copyOf(merged);
    }

    private static HeaderAndEntries split(List<String> lines) {
        String header = null;
        List<String> entries = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (header == null) {
                header = trimmed;
            } else {
                entries.add(line);
            }
        }
        return new HeaderAndEntries(header, entries);
    }

    private record HeaderAndEntries(String header, List<String> entries) {
    }
}
