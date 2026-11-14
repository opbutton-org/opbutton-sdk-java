package org.opbutton.sdk;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Metrics subsystem for the OpButton SDK.
 *
 * <p>Collects metric samples at a configurable interval (default 200ms)
 * and buffers them for batch sending to the server.</p>
 *
 * <pre>{@code
 * OpButtonMetrics metrics = OpButton.getMetrics();
 *
 * // Add a custom collector
 * metrics.addCollector(out -> {
 *     out.add(new OpButtonMetricEntry(OpButtonMetricType.FPS, myFpsCounter.getFps()));
 *     out.add(new OpButtonMetricEntry(OpButtonMetricType.MEMORY,
 *         Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
 * });
 *
 * // Or record metrics manually
 * metrics.record(OpButtonMetricType.CPU_USAGE, 45.2);
 * }</pre>
 */
public final class OpButtonMetrics {
    private final CopyOnWriteArrayList<OpButtonMetricCollector> m_collectors = new CopyOnWriteArrayList<>();
    private final List<OpButtonMetricEntry> m_buffer = Collections.synchronizedList(new ArrayList<>());

    private long m_sampleIntervalMs = 200;
    private int m_bufferSize = 2000;
    private int m_batchSize = 200;

    OpButtonMetrics() {
    }

    /**
     * Set the sample interval in milliseconds (how often collectors are polled).
     * Range: 100..1000 ms. Default: 200 ms.
     */
    public void setSampleIntervalMs(long intervalMs) {
        m_sampleIntervalMs = Math.max(100, Math.min(1000, intervalMs));
    }

    /**
     * Get the configured sample interval.
     */
    public long getSampleIntervalMs() {
        return m_sampleIntervalMs;
    }

    /**
     * Set the maximum number of metric entries to buffer before dropping oldest.
     */
    public void setBufferSize(int size) {
        m_bufferSize = size;
    }

    /**
     * Set the max batch size for sending to the server.
     */
    public void setBatchSize(int size) {
        m_batchSize = size;
    }

    int getBatchSize() {
        return m_batchSize;
    }

    /**
     * Add a metric collector. Collectors are called on a background thread
     * at the configured sample interval.
     */
    public void addCollector(@NonNull OpButtonMetricCollector collector) {
        m_collectors.add(collector);
    }

    /**
     * Remove a collector.
     */
    public void removeCollector(@NonNull OpButtonMetricCollector collector) {
        m_collectors.remove(collector);
    }

    /**
     * Record a single metric entry manually.
     * Timestamp is set to current time.
     */
    public void record(@NonNull OpButtonMetricType metricType, double value) {
        bufferEntry(new OpButtonMetricEntry(metricType, value));
    }

    /**
     * Record a metric entry with explicit timestamp.
     */
    public void record(@NonNull OpButtonMetricType metricType, double value, long timestampMs) {
        bufferEntry(new OpButtonMetricEntry(metricType, value, timestampMs));
    }

    /**
     * Called by the SDK on the sample timer to invoke all collectors.
     */
    void sample() {
        List<OpButtonMetricEntry> collected = new ArrayList<>();
        for (OpButtonMetricCollector collector : m_collectors) {
            try {
                collector.collect(collected);
            } catch (Exception e) {
                OpButtonLog.e("Metric collector threw exception", e);
            }
        }
        for (OpButtonMetricEntry entry : collected) {
            bufferEntry(entry);
        }
    }

    /**
     * Drain up to batchSize entries from the buffer for sending.
     * Returns empty list if nothing to send.
     */
    @NonNull
    List<OpButtonMetricEntry> drain() {
        if (m_buffer.isEmpty()) return Collections.emptyList();

        synchronized (m_buffer) {
            int count = Math.min(m_buffer.size(), m_batchSize);
            List<OpButtonMetricEntry> chunk = new ArrayList<>(m_buffer.subList(0, count));
            m_buffer.subList(0, count).clear();
            return chunk;
        }
    }

    /**
     * Re-add entries to the front of the buffer (e.g., on send failure).
     */
    void requeue(@NonNull List<OpButtonMetricEntry> entries) {
        synchronized (m_buffer) {
            m_buffer.addAll(0, entries);
            while (m_buffer.size() > m_bufferSize) {
                m_buffer.remove(m_buffer.size() - 1);
            }
        }
    }

    boolean hasData() {
        return !m_buffer.isEmpty();
    }

    void clear() {
        m_buffer.clear();
        m_collectors.clear();
    }

    private void bufferEntry(@NonNull OpButtonMetricEntry entry) {
        synchronized (m_buffer) {
            if (m_buffer.size() >= m_bufferSize) {
                m_buffer.remove(0);
            }
            m_buffer.add(entry);
        }
    }
}
