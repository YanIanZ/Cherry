package dev.iyanz.cherry.gradle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessWidenerMergerTest {

    @Test
    void singleFilePassesThroughUnchanged() {
        List<String> file = List.of(
            "accessWidener v2 named",
            "accessible class com/me/Foo"
        );

        assertEquals(file, AccessWidenerMerger.merge(List.of(file)));
    }

    @Test
    void twoFilesWithMatchingHeadersAreConcatenated() {
        List<String> first = List.of(
            "accessWidener v2 named",
            "accessible class com/me/Foo"
        );
        List<String> second = List.of(
            "accessWidener v2 named",
            "accessible field com/me/Bar someField Ljava/lang/String;"
        );

        List<String> merged = AccessWidenerMerger.merge(List.of(first, second));

        assertEquals(List.of(
            "accessWidener v2 named",
            "accessible class com/me/Foo",
            "accessible field com/me/Bar someField Ljava/lang/String;"
        ), merged);
    }

    @Test
    void commentsAndBlankLinesAreIgnoredAroundTheHeader() {
        List<String> file = List.of(
            "# a leading comment",
            "",
            "accessWidener v2 named",
            "# another comment",
            "accessible class com/me/Foo"
        );

        assertEquals(List.of("accessWidener v2 named", "accessible class com/me/Foo"),
            AccessWidenerMerger.merge(List.of(file)));
    }

    @Test
    void mismatchedHeadersFailFast() {
        List<String> named = List.of("accessWidener v2 named", "accessible class com/me/Foo");
        List<String> intermediary = List.of("accessWidener v2 intermediary", "accessible class com/me/Bar");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> AccessWidenerMerger.merge(List.of(named, intermediary)));
        assertTrue(e.getMessage().contains("named"));
        assertTrue(e.getMessage().contains("intermediary"));
    }

    @Test
    void emptyInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> AccessWidenerMerger.merge(List.of()));
    }
}
