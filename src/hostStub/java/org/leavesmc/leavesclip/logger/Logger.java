package org.leavesmc.leavesclip.logger;

import org.spongepowered.asm.logging.ILogger;

/**
 * Compile-time stand-in for {@code org.leavesmc.leavesclip.logger.Logger} from LeavesMC's
 * Leavesclip launcher (MIT licensed; see {@code NOTICE.md} at the repository root).
 *
 * <p><b>This class is NOT part of Cherry.</b> Cherry's own source ({@code src/main/java}) imports
 * this type because it runs inside SourbyClip's vendored copy of Leavesclip, where the real
 * {@code Logger} lives alongside the transforming classloader. This {@code hostStub} source set
 * reproduces that small, self-contained class verbatim (down to the SpongePowered Mixin
 * {@code ILogger} interface it implements) purely so the Cherry sources in this repository compile
 * and document standalone, without checking out the rest of SourbyClip. It is compiled only into
 * the {@code compileOnly}-equivalent {@code hostStub} classpath used by the {@code main} source
 * set and the {@code javadoc} task — it is never packaged into the jar, sources jar, or javadoc
 * jar this repository publishes. At runtime inside SourbyClip, the real class is used instead.
 */
public abstract class Logger implements ILogger {
    public abstract void warn(Throwable t, String message, Object... params);

    public abstract void error(Throwable t, String message, Object... params);
}
