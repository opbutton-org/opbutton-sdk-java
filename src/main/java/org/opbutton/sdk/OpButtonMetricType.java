package org.opbutton.sdk;

import androidx.annotation.NonNull;

/**
 * Describes a type of metric that can be recorded.
 * Use the built-in constants for standard system metrics,
 * or create custom types for application-specific metrics.
 *
 * <pre>{@code
 * // Built-in types:
 * OpButtonMetricType.FPS          // aggregation = GAUGE
 * OpButtonMetricType.NETWORK_RX   // aggregation = RATE
 *
 * // Custom types:
 * OpButtonMetricType DRAW_CALLS = OpButtonMetricType.create("draw_calls");
 * OpButtonMetricType ERRORS = OpButtonMetricType.create("errors", "", OpButtonMetricAggregation.COUNTER);
 * }</pre>
 */
public final class OpButtonMetricType {
    private final String m_key;
    private final String m_unit;
    private final OpButtonMetricAggregation m_aggregation;

    // ── Built-in metric types ──

    public static final OpButtonMetricType FPS = new OpButtonMetricType("fps", "", OpButtonMetricAggregation.GAUGE);
    public static final OpButtonMetricType FRAME_TIME = new OpButtonMetricType("frame_time", "ms", OpButtonMetricAggregation.GAUGE);
    public static final OpButtonMetricType MEMORY = new OpButtonMetricType("memory", "bytes", OpButtonMetricAggregation.GAUGE);
    public static final OpButtonMetricType VIDEO_MEMORY = new OpButtonMetricType("video_memory", "bytes", OpButtonMetricAggregation.GAUGE);
    public static final OpButtonMetricType CPU_USAGE = new OpButtonMetricType("cpu_usage", "%", OpButtonMetricAggregation.GAUGE);
    public static final OpButtonMetricType NETWORK_RX = new OpButtonMetricType("network_rx", "bytes/s", OpButtonMetricAggregation.RATE);
    public static final OpButtonMetricType NETWORK_TX = new OpButtonMetricType("network_tx", "bytes/s", OpButtonMetricAggregation.RATE);
    public static final OpButtonMetricType BATTERY_LEVEL = new OpButtonMetricType("battery_level", "%", OpButtonMetricAggregation.GAUGE);
    public static final OpButtonMetricType BATTERY_CHARGING = new OpButtonMetricType("battery_charging", "", OpButtonMetricAggregation.GAUGE);
    public static final OpButtonMetricType DRAW_CALLS = new OpButtonMetricType("draw_calls", "", OpButtonMetricAggregation.GAUGE);
    public static final OpButtonMetricType THREAD_COUNT = new OpButtonMetricType("thread_count", "", OpButtonMetricAggregation.GAUGE);

    private OpButtonMetricType(@NonNull String key, @NonNull String unit, @NonNull OpButtonMetricAggregation aggregation) {
        m_key = key;
        m_unit = unit;
        m_aggregation = aggregation;
    }

    /**
     * Create a custom metric type with GAUGE aggregation.
     *
     * @param key unique key, sent to the server as metric_type
     */
    @NonNull
    public static OpButtonMetricType create(@NonNull String key) {
        return new OpButtonMetricType(key, "", OpButtonMetricAggregation.GAUGE);
    }

    /**
     * Create a custom metric type with a unit hint and GAUGE aggregation.
     *
     * @param key  unique key, sent to the server as metric_type
     * @param unit display unit (e.g. "ms", "%", "bytes")
     */
    @NonNull
    public static OpButtonMetricType create(@NonNull String key, @NonNull String unit) {
        return new OpButtonMetricType(key, unit, OpButtonMetricAggregation.GAUGE);
    }

    /**
     * Create a custom metric type with full control.
     *
     * @param key         unique key, sent to the server as metric_type
     * @param unit        display unit (e.g. "ms", "%", "bytes")
     * @param aggregation how the server should aggregate values
     */
    @NonNull
    public static OpButtonMetricType create(@NonNull String key, @NonNull String unit,
                                         @NonNull OpButtonMetricAggregation aggregation) {
        return new OpButtonMetricType(key, unit, aggregation);
    }

    /**
     * The key string sent to the server as {@code metric_type}.
     */
    @NonNull
    public String getKey() {
        return m_key;
    }

    /**
     * Display unit hint (e.g. "ms", "%", "bytes"). May be empty.
     */
    @NonNull
    public String getUnit() {
        return m_unit;
    }

    /**
     * How the server should aggregate values when compressing time ranges.
     */
    @NonNull
    public OpButtonMetricAggregation getAggregation() {
        return m_aggregation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpButtonMetricType)) return false;
        return m_key.equals(((OpButtonMetricType) o).m_key);
    }

    @Override
    public int hashCode() {
        return m_key.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return m_key;
    }
}
