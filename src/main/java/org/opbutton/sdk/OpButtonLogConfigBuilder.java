package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Builder for log subsystem configuration.
 * Controls whether log sending is enabled, log level, buffer/batch sizes, and log attributes.
 *
 * <pre>{@code
 * OpButtonLogConfigBuilder logConfig = new OpButtonLogConfigBuilder()
 *     .setEnabled(true)
 *     .setLogLevel(OpButtonLogLevel.INFO)
 *     .setAttributes(new OpButtonLogAttributeBuilder()
 *         .putProvider("screen_name", () -> currentScreen));
 *
 * OpButtonConfig config = new OpButtonConfig.Builder(url, token)
 *     .setLogConfig(logConfig)
 *     .build();
 * }</pre>
 */
public final class OpButtonLogConfigBuilder {
    private boolean m_enabled = true;
    private OpButtonLogLevel m_logLevel = OpButtonLogLevel.INFO;
    private int m_batchSize = 100;
    private int m_bufferSize = 1000;
    private OpButtonLogAttributeBuilder m_attributes = new OpButtonLogAttributeBuilder();

    /**
     * Enable or disable sending application logs to the OpButton server.
     * Default: true.
     */
    @NonNull
    public OpButtonLogConfigBuilder setEnabled(boolean enabled) {
        m_enabled = enabled;
        return this;
    }

    /**
     * Set the minimum log level. Messages below this level are dropped.
     * Default: {@link OpButtonLogLevel#INFO}.
     */
    @NonNull
    public OpButtonLogConfigBuilder setLogLevel(@NonNull OpButtonLogLevel level) {
        m_logLevel = level;
        return this;
    }

    /**
     * Set the max batch size for sending logs to the server in one request.
     * Default: 100.
     */
    @NonNull
    public OpButtonLogConfigBuilder setBatchSize(int size) {
        m_batchSize = size;
        return this;
    }

    /**
     * Set the maximum number of log entries to buffer before dropping oldest.
     * Default: 1000.
     */
    @NonNull
    public OpButtonLogConfigBuilder setBufferSize(int size) {
        m_bufferSize = size;
        return this;
    }

    /**
     * Set log attributes (static and dynamic) via a builder.
     * Static values are fixed; dynamic providers are resolved at each log() call.
     */
    @NonNull
    public OpButtonLogConfigBuilder setAttributes(@NonNull OpButtonLogAttributeBuilder attributes) {
        m_attributes = attributes;
        return this;
    }

    // ── Package-private getters for SDK internals ──

    boolean isEnabled() {
        return m_enabled;
    }

    OpButtonLogLevel getLogLevel() {
        return m_logLevel;
    }

    int getBatchSize() {
        return m_batchSize;
    }

    int getBufferSize() {
        return m_bufferSize;
    }

    @NonNull
    OpButtonLogAttributeBuilder getAttributes() {
        return m_attributes;
    }
}
