package dev.iyanz.cherry.gradle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every valid-line example here is taken verbatim from the main Cherry repository README's
 * "Access-transformer (.at) file format" section and its worked {@code myplugin.at} example, so this
 * test doubles as a guarantee that this validator's grammar has not drifted from the documented (and
 * {@code CherryAccessTransformers}-implemented) one.
 */
class AccessTransformerValidatorTest {

    @Test
    void readmeWorkedExampleIsFullyValid() {
        List<String> lines = List.of(
            "# widen a package-private field to public",
            "public net.minecraft.world.entity.Mob goalSelector",
            "",
            "# widen a method to public and drop `final` so it can be overridden",
            "public-f net.minecraft.world.entity.SomeMob tickLeash()V",
            "",
            "# narrow an accidentally-public internal class back to package-private",
            "default com.example.internal.Helper"
        );

        assertEquals(List.of(), AccessTransformerValidator.validate(lines));
    }

    @Test
    void classFieldConstructorAndMethodTargetsAllValidate() {
        List<String> lines = List.of(
            "public com.example.SomeInternalClass",
            "public net.minecraft.world.entity.Mob goalSelector",
            "public com.example.Foo <init>(Ljava/lang/String;)V",
            "public-f net.minecraft.world.entity.Mob tickLeash()V",
            "protected+f com.example.Bar someMethod(I)Z"
        );

        assertEquals(List.of(), AccessTransformerValidator.validate(lines));
    }

    @Test
    void blankLinesAndFullLineCommentsAreIgnored() {
        assertEquals(List.of(), AccessTransformerValidator.validate(List.of("", "   ", "# just a comment")));
    }

    @Test
    void trailingCommentAfterAValidDefinitionIsStripped() {
        assertEquals(List.of(),
            AccessTransformerValidator.validate(List.of("public com.example.Foo # widen this one")));
    }

    @Test
    void unknownModifierIsRejected() {
        List<AccessTransformerValidator.Failure> failures =
            AccessTransformerValidator.validate(List.of("internal com.example.Foo"));
        assertEquals(1, failures.size());
        assertEquals(1, failures.get(0).lineNumber());
    }

    @Test
    void garbageLineIsRejected() {
        List<AccessTransformerValidator.Failure> failures =
            AccessTransformerValidator.validate(List.of("this is not an AT line at all"));
        assertEquals(1, failures.size());
    }

    @Test
    void malformedMethodDescriptorIsRejected() {
        // missing closing paren / return type
        List<AccessTransformerValidator.Failure> failures =
            AccessTransformerValidator.validate(List.of("public com.example.Foo someMethod("));
        assertEquals(1, failures.size());
    }

    @Test
    void lineNumbersAreOneBasedAndReportedInFileOrder() {
        List<AccessTransformerValidator.Failure> failures = AccessTransformerValidator.validate(List.of(
            "public com.example.Foo",
            "bogus line one",
            "public com.example.Bar",
            "bogus line two"
        ));

        assertEquals(2, failures.size());
        assertEquals(2, failures.get(0).lineNumber());
        assertEquals(4, failures.get(1).lineNumber());
        assertTrue(failures.get(0).toString().contains("bogus line one"));
    }
}
