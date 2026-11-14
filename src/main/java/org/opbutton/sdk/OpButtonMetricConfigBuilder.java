package org.opbutton.sdk;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for metric subsystem configuration.
 * Controls whether metrics are enabled, sampling rate, buffer sizes, and allows registering custom collectors.
 *
 * <pre>{@code
 * OpButtonMetricConfigBuilder metricConfig = new OpButtonMetricConfigBuilder()
 *     .setEnabled(true)
 *     .setSampleIntervalMs(200)
 *     .addCollector(out -> {
 *         out.add(new OpButtonMetricEntry(OpButtonMetricType.DRAW_CALLS, renderer.getDrawCalls()));
 *         out.add(new OpButtonMetricEntry(OpButtonMetricType.VIDEO_MEMORY, renderer.getVramUsage()));
 *     });
 *
 * OpButtonConfig config = new OpButtonConfig.Builder(url, token)
 *     .setMetricConfig(metricConfig)
 *     .build();
 * }</pre>
 */
public final class OpButtonMetricConfigBuilder {
    private boolean m_enabled = true;
    private long m_sampleIntervalMs = 200;
    private int m_bufferSize = 2000;
    private int m_batchSize = 200;
    private final List<OpButtonMetricCollector> m_collectors = new ArrayList<>();

    /**
     * Enable or disable the metrics subsystem.
     * When enabled, built-in collectors automatically gather FPS, memory,
     * network, battery, and other system metrics.
     * Default: true.
     */
    @NonNull
    public OpButtonMetricConfigBuilder setEnabled(boolean enabled) {
        m_enabled = enabled;
        return this;
    }

    /**
     * Set the metric sample interval in milliseconds.
     * Controls how often metric collectors are polled.
     * Range: 100..1000 ms. Default: 200 ms.
     */
    @NonNull
    public OpButtonMetricConfigBuilder setSampleIntervalMs(long ms) {
        m_sampleIntervalMs = Math.max(100, Math.min(1000, ms));
        return this;
    }

    /**
     * Set the maximum number of metric entries to buffer before dropping oldest.
     * Default: 2000.
     */
    @NonNull
    public OpButtonMetricConfigBuilder setBufferSize(int size) {
        m_bufferSize = size;
        return this;
    }

    /**
     * Set the max batch size for sending to the server in one request.
     * Default: 200.
     */
    @NonNull
    public OpButtonMetricConfigBuilder setBatchSize(int size) {
        m_batchSize = size;
        return this;
    }

    /**
     * Add a custom metric collector.
     * Collectors are called on a background thread at the configured sample interval.
     *
     * <pre>{@code
     * metricConfig.addCollector(out -> {
     *     out.add(new OpButtonMetricEntry(OpButtonMetricType.DRAW_CALLS, renderer.getDrawCalls()));
     *     out.add(new OpButtonMetricEntry(OpButtonMetricType.VIDEO_MEMORY, renderer.getVramUsage()));
     * });
     * }</pre>
     */
    @NonNull
    public OpButtonMetricConfigBuilder addCollector(@NonNull OpButtonMetricCollector collector) {
        m_collectors.add(collector);
        return this;
    }

    // ── Package-private getters for SDK internals ──

    boolean isEnabled() {
        return m_enabled;
    }

    long getSampleIntervalMs() {
        return m_sampleIntervalMs;
    }

    int getBufferSize() {
        return m_bufferSize;
    }

    int getBatchSize() {
        return m_batchSize;
    }

    @NonNull
    List<OpButtonMetricCollector> getCollectors() {
        return m_collectors;
    }
}
