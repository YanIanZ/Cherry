package dev.iyanz.sourbyclip.cherry.discovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CherryPluginFilterTest {

    private static final String PROPERTY = "cherry.disable.plugins";

    @AfterEach
    void clearProperty() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void nothingDisabledByDefault() {
        System.clearProperty(PROPERTY);
        assertFalse(CherryPluginFilter.isDisabled("AnyPlugin"));
    }

    @Test
    void exactCaseSensitiveMatch() {
        System.setProperty(PROPERTY, "PluginA,PluginB");
        assertTrue(CherryPluginFilter.isDisabled("PluginA"));
        assertTrue(CherryPluginFilter.isDisabled("PluginB"));
        assertFalse(CherryPluginFilter.isDisabled("pluginb"));
        assertFalse(CherryPluginFilter.isDisabled("PluginC"));
    }

    @Test
    void toleratesWhitespaceAroundEntries() {
        System.setProperty(PROPERTY, " PluginA ,  PluginB");
        assertTrue(CherryPluginFilter.isDisabled("PluginA"));
        assertTrue(CherryPluginFilter.isDisabled("PluginB"));
    }

    @Test
    void blankPropertyDisablesNothing() {
        System.setProperty(PROPERTY, "   ");
        assertFalse(CherryPluginFilter.isDisabled("PluginA"));
    }
}
