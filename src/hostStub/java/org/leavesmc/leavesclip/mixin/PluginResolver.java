package org.leavesmc.leavesclip.mixin;

/**
 * Compile-time stand-in for {@code org.leavesmc.leavesclip.mixin.PluginResolver} from LeavesMC's
 * Leavesclip launcher (MIT licensed; see {@code NOTICE.md} at the repository root), as vendored
 * (and extended by SourbyCraft) in SourbyClip's {@code java25} module.
 *
 * <p><b>This class is NOT part of Cherry, and this is a PARTIAL reproduction.</b> The real
 * {@code PluginResolver} is several hundred lines: it scans {@code plugins/*.jar}, extracts each
 * plugin's mixin package into a cached {@code .mixins.jar} (hashed so it only re-extracts on
 * change), and populates {@code leavesPluginMetas} for the Leaves mixin engine to consume. Cherry's
 * own {@link dev.iyanz.sourbyclip.cherry.CherryPluginResolver} deliberately does none of that — it
 * only reads the two {@code public static final String} constants below off this class, so that is
 * all this stub reproduces. It exists purely so the {@code main} source set (the literal Cherry
 * source) compiles and documents standalone in this repository; it is never packaged into any
 * published artifact. At runtime inside SourbyClip, the real class supplies these same values.
 */
public final class PluginResolver {

    /** Directory (relative to the server working directory) plugin jars are read from. */
    public static final String PLUGIN_DIRECTORY = "plugins";

    /** Legacy Leaves plugin-manifest filename, still read by Cherry for back-compat. */
    public static final String LEAVES_PLUGIN_JSON_FILE = "leaves-plugin.json";

    private PluginResolver() {
    }
}
