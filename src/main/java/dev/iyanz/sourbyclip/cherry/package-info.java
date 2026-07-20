/**
 * Cherry — SourbyCraft's unified server-side mixin system.
 *
 * <p>This package holds the two integration points a host launcher (SourbyClip's Leaves-based
 * transforming classloader) calls into:
 * <ul>
 *   <li>{@link dev.iyanz.sourbyclip.cherry.Cherry} — the facade: the {@code -Dcherry.enable.mixin}
 *       gate, access-transformer initialization, and the per-class transform hook.</li>
 *   <li>{@link dev.iyanz.sourbyclip.cherry.CherryPluginResolver} — scans {@code plugins/*.jar} for
 *       {@code cherry-plugin.json} / {@code leaves-plugin.json} manifests and registers any
 *       {@code access-transformers} they declare.</li>
 * </ul>
 *
 * <p>The access-transformer engine itself — ported from CraftCanvasMC/Horizon's
 * {@code TransformerContainer} — lives in the child package {@link dev.iyanz.sourbyclip.cherry.at}.
 *
 * <p>See the repository README for the full merge story (LeavesMC/Leavesclip base +
 * CraftCanvasMC/Horizon access-transformer capability), the plugin-manifest schema, and the
 * honest limitations of this integration.
 */
package dev.iyanz.sourbyclip.cherry;
