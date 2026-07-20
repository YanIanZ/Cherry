package dev.iyanz.cherry.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure logic: validates {@code .at} file lines against Cherry's exact access-transformer grammar,
 * at build time, so an author gets a clear failure with file/line instead of Cherry's runtime
 * behaviour for a bad line (logged and silently skipped, so the mistake would otherwise only be
 * noticed by the AT quietly never applying, at server boot, potentially long after the plugin
 * shipped).
 *
 * <p>The four patterns below are kept byte-for-byte identical to
 * {@code dev.iyanz.sourbyclip.cherry.at.CherryAccessTransformers}'s {@code CLASS_REGEX} /
 * {@code FIELD_REGEX} / {@code CONSTRUCTOR_REGEX} / {@code METHOD_REGEX} in the main Cherry module -
 * see that class for the authoritative grammar and the ASM access-flag semantics each line
 * eventually drives. This class deliberately does not depend on that one (they live in separate
 * Gradle builds - this module cannot reference the main module's package-private/private regex
 * fields anyway), so keep the two in sync if Cherry's own grammar ever changes.
 */
public final class AccessTransformerValidator {

    private static final Pattern CLASS_REGEX =
        Pattern.compile("^\\s*(public|protected|private|default)([+-]f)?\\s+([A-Za-z_][A-Za-z0-9_]*(?:[.$][A-Za-z_][A-Za-z0-9_]*)*)\\s*$");
    private static final Pattern FIELD_REGEX =
        Pattern.compile("^\\s*(public|protected|private|default)([+-]f)?\\s+([A-Za-z_][A-Za-z0-9_]*(?:[.$][A-Za-z_][A-Za-z0-9_]*)*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern CONSTRUCTOR_REGEX =
        Pattern.compile("^\\s*(public|protected|private|default)([+-]f)?\\s+([A-Za-z_][A-Za-z0-9_]*(?:[.$][A-Za-z_][A-Za-z0-9_]*)*)\\s+<init>\\s*\\(([^)]*)\\)\\s*V\\s*$");
    private static final Pattern METHOD_REGEX =
        Pattern.compile("^\\s*(public|protected|private|default)([+-]f)?\\s+([A-Za-z_][A-Za-z0-9_]*(?:[.$][A-Za-z_][A-Za-z0-9_]*)*)\\s+([A-Za-z_][A-Za-z0-9_<>]*)\\s*\\(([^)]*)\\)\\s*(\\[*(?:[VIJZBCSFD]|L[A-Za-z_][A-Za-z0-9_]*(?:/[A-Za-z_][A-Za-z0-9_]*)*;))\\s*$");

    private AccessTransformerValidator() {
    }

    /** One rejected line: its 1-based line number and its raw (untrimmed) text. */
    public record Failure(int lineNumber, String rawLine) {
        @Override
        public String toString() {
            return "line " + lineNumber + ": \"" + rawLine + "\"";
        }
    }

    /**
     * @param lines the file's lines, in order, exactly as read (not pre-trimmed / not
     *              comment-stripped - this method does that itself, matching
     *              {@code CherryAccessTransformers.register}'s own handling of {@code #} comments
     *              and blank lines)
     * @return every line that is neither blank/comment-only nor a syntactically valid AT
     * definition, in file order; empty if the whole file is valid
     */
    public static List<Failure> validate(List<String> lines) {
        List<Failure> failures = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int hashIdx = line.indexOf('#');
            String trimmed = (hashIdx == -1 ? line : line.substring(0, hashIdx)).trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (!isValidDefinition(trimmed)) {
                failures.add(new Failure(i + 1, line));
            }
        }
        return List.copyOf(failures);
    }

    private static boolean isValidDefinition(String trimmed) {
        return CLASS_REGEX.matcher(trimmed).matches()
            || FIELD_REGEX.matcher(trimmed).matches()
            || CONSTRUCTOR_REGEX.matcher(trimmed).matches()
            || METHOD_REGEX.matcher(trimmed).matches();
    }
}
