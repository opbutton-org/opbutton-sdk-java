package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Listener for OpButton SDK internal diagnostic logs.
 *
 * <p>This callback receives client-side SDK logs and is separate from the log
 * pipeline that buffers and sends application logs to the OpButton server.</p>
 *
 * <pre>{@code
 * OpButtonLogConfigBuilder logConfig = new OpButtonLogConfigBuilder()
 *     .setLoggerListener((level, tag, message, throwable) -> {
 *         MyEngine.log(tag + ": " + message);
 *     });
 * }</pre>
 */
public interface OpButtonLoggerListener {
    /**
     * Called for each internal SDK log message.
     *
     * @param level     Log level
     * @param tag       Log tag (always "OpButtonSDK")
     * @param message   Formatted log message
     * @param throwable Optional throwable (may be null)
     */
    void onOpButtonLoggerMessage(@NonNull OpButtonLogLevel level, @NonNull String tag, @NonNull String message, @Nullable Throwable throwable);
}