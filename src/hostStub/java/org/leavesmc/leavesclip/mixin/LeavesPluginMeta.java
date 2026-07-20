package org.leavesmc.leavesclip.mixin;

import java.util.List;

/**
 * Compile-time stand-in for {@code org.leavesmc.leavesclip.mixin.LeavesPluginMeta} from LeavesMC's
 * Leavesclip launcher (MIT licensed; see {@code NOTICE.md} at the repository root), as vendored
 * (and extended by SourbyCraft) in SourbyClip's {@code java25} module.
 *
 * <p><b>This class is NOT part of Cherry, and this is a PARTIAL reproduction.</b> The real class is
 * the Gson deserialization target for a plugin's {@code cherry-plugin.json} /
 * {@code leaves-plugin.json}, and additionally carries the {@code mixin} block (package name,
 * mixin config list, access-widener file) consumed by the Leaves mixin engine. Cherry's own
 * {@link dev.iyanz.sourbyclip.cherry.CherryPluginResolver} only reads the plugin name and the
 * {@code access-transformers} list off a parsed manifest, so that is all this stub reproduces. See
 * the repository README for the full manifest schema (both the Leaves {@code mixin} block and
 * Cherry's {@code access-transformers} list). This class is never packaged into any published
 * artifact; at runtime inside SourbyClip, the real, fuller class is used instead.
 */
public final class LeavesPluginMeta {
    private String name;
    private List<String> accessTransformers;

    /** @return the plugin name declared in the manifest, or {@code null} if absent. */
    public String getName() {
        return name;
    }

    /** @return the {@code access-transformers} file list declared in the manifest, or {@code null} if absent. */
    public List<String> getAccessTransformers() {
        return accessTransformers;
    }
}
