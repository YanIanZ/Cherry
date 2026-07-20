package org.leavesmc.leavesclip.mixin;

import com.google.gson.annotations.SerializedName;

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
 * {@link dev.iyanz.sourbyclip.cherry.CherryPluginResolver} reads the plugin name and the
 * {@code access-transformers} list; {@link dev.iyanz.sourbyclip.cherry.discovery.CherryDiscovery}
 * additionally reads the {@code mixin} block below (package name, mixin config list,
 * access-widener) purely to <i>report</i> what the Leaves engine will itself load — Cherry never
 * extracts or registers a Leaves-declared mixin/widener itself. The nested {@link MixinConfig}
 * shape mirrors upstream Leavesclip's {@code LeavesPluginMeta.MixinConfig} verbatim (field names,
 * {@code @SerializedName}s, and all), which is why this reproduction is safe to extend here without
 * requiring any change on the SourbyClip side: SourbyClip's real, fuller class already carries this
 * shape today, independently of Cherry, because the Leaves engine needs it regardless of Cherry's
 * existence. See the repository README for the full manifest schema. This class is never packaged
 * into any published artifact; at runtime inside SourbyClip, the real, fuller class is used instead.
 *
 * <p><b>Sync note:</b> {@code accessTransformers} below carries an explicit
 * {@code @SerializedName("access-transformers")}, required because Gson's default field-naming
 * policy matches JSON keys to Java field names literally (no automatic kebab-case conversion) - a
 * plain {@code accessTransformers} field silently never binds to the documented
 * {@code "access-transformers"} manifest key, so {@code getAccessTransformers()} would always return
 * {@code null} even for a syntactically correct manifest. This was caught by this repository's own
 * {@code CherryDiscoveryTest} once it started asserting on parsed access-transformer lists. If
 * SourbyClip's real, vendored {@code LeavesPluginMeta} is missing the same annotation, it has the
 * identical bug and needs this same one-line fix when this repository's changes are synced in.
 */
public final class LeavesPluginMeta {
    private String name;
    private MixinConfig mixin;

    @SerializedName("access-transformers")
    private List<String> accessTransformers;

    /** @return the plugin name declared in the manifest, or {@code null} if absent. */
    public String getName() {
        return name;
    }

    /** @return the {@code mixin} block declared in the manifest, or {@code null} if absent. */
    public MixinConfig getMixin() {
        return mixin;
    }

    /** @return the {@code access-transformers} file list declared in the manifest, or {@code null} if absent. */
    public List<String> getAccessTransformers() {
        return accessTransformers;
    }

    /**
     * Compile-time stand-in for the real {@code LeavesPluginMeta.MixinConfig} nested class: the
     * {@code mixin} block a plugin manifest declares for the Leaves engine (mixin package, config
     * file list, access-widener). Field shape mirrors upstream Leavesclip exactly (see this class's
     * javadoc); Cherry only ever reads it, it never writes or extracts it.
     */
    public static final class MixinConfig {
        @SerializedName("package-name")
        private String packageName;

        private List<String> mixins;

        @SerializedName("access-widener")
        private String accessWidener;

        /** @return the dot-separated mixin package this plugin's {@code @Mixin} classes live under. */
        public String getPackageName() {
            return packageName;
        }

        /** @return the SpongePowered Mixin config file names declared for this plugin, resolved relative to the jar root. */
        public List<String> getMixins() {
            return mixins;
        }

        /** @return the Fabric access-widener file name declared for this plugin, or {@code null} if none. */
        public String getAccessWidener() {
            return accessWidener;
        }
    }
}
