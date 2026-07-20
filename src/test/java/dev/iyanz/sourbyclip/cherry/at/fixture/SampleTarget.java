package dev.iyanz.sourbyclip.cherry.at.fixture;

/**
 * Test-only fixture: a plain class with package-private and private members, used as a real
 * access-transform target in {@code CherryAccessTransformersTest} (loaded by reading its own
 * compiled {@code .class} bytes off the test classpath, then run through the actual ASM-based
 * transform pipeline, not a hand-built byte array).
 */
public class SampleTarget {

    int hiddenField;
    private int privateField;

    void hiddenMethod() {
    }

    @SuppressWarnings("unused")
    private SampleTarget(String ignored) {
    }
}
