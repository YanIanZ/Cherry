package dev.iyanz.sourbyclip.cherry.at.fixture;

/**
 * Test-only fixture: a class with no access-transformer declarations at all, used to verify
 * {@code CherryAccessTransformers.applyToBytes} takes its true no-op fast path (returns the same
 * array reference) for a class the registry has nothing registered for.
 */
public class UnrelatedTarget {
}
