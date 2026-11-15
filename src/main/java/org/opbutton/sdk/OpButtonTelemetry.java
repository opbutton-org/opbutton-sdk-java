package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the telemetry outbox and its delivery receipts. */
final class OpButtonTelemetry {
    private final OpButtonLogConfigBuilder m_logConfig;
    private final OpButtonHttpClient m_httpClient;
    private final OpButtonMetrics m_metrics;
    private final List<OpButtonLogEntry> m_logBuffer = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean m_logOverflowWarned = new AtomicBoolean(false);

    OpButtonTelemetry(OpButtonLogConfigBuilder config, OpButtonHttpClient http, OpButtonMetrics metrics) {
        m_logConfig = config;
        m_httpClient = http;
        m_metrics = metrics;
    }

    void clear() { m_logBuffer.clear(); }

    Batch capture() { return new Batch(drainLogChunk(), m_metrics.drain()); }

    final class Batch implements AutoCloseable {
        final List<OpButtonLogEntry> logs;
        final List<OpButtonMetricEntry> metrics;
        private boolean settled;

        private Batch(List<OpButtonLogEntry> logs, List<OpButtonMetricEntry> metrics) {
            this.logs = logs;
            this.metrics = metrics;
        }

        synchronized void commit() {
            if (settled) return;
            settled = true;
            if (!logs.isEmpty()) m_logOverflowWarned.set(false);
        }

        @Override public synchronized void close() {
            if (settled) return;
            settled = true;
            requeueLogs(logs);
            m_metrics.requeue(metrics);
        }
    }
    void bufferLog(@NonNull OpButtonLogEntry entry) {
        synchronized (m_logBuffer) {
            if (m_logBuffer.size() >= m_logConfig.getBufferSize()) {
                m_logBuffer.remove(0);

                if (m_logOverflowWarned.compareAndSet(false, true)) {
                    OpButtonLog.w("Log buffer overflow (" + m_logConfig.getBufferSize() + "), dropping oldest entries");
                }
            }

            m_logBuffer.add(entry);
        }
    }

    @NonNull
    private List<OpButtonLogEntry> drainLogChunk() {
        if (m_logBuffer.isEmpty()) return Collections.emptyList();

        synchronized (m_logBuffer) {
            int count = Math.min(m_logBuffer.size(), m_logConfig.getBatchSize());
            List<OpButtonLogEntry> chunk = new ArrayList<>(m_logBuffer.subList(0, count));
            m_logBuffer.subList(0, count).clear();
            return chunk;
        }
    }

    private void requeueLogs(@NonNull List<OpButtonLogEntry> chunk) {
        if (chunk.isEmpty()) return;
        synchronized (m_logBuffer) {
            m_logBuffer.addAll(0, chunk);
            while (m_logBuffer.size() > m_logConfig.getBufferSize()) {
                m_logBuffer.remove(m_logBuffer.size() - 1);
            }
        }
    }

    void flushLogs(@NonNull String token) throws OpButtonException {
        List<OpButtonLogEntry> chunk = drainLogChunk();
        if (chunk.isEmpty()) return;
        try { m_httpClient.sendLogs(token, chunk); }
        catch (OpButtonException error) { requeueLogs(chunk); throw error; }
    }

    void flushMetrics(@NonNull String sessionToken) throws OpButtonException {
        List<OpButtonMetricEntry> chunk = m_metrics.drain();
        if (chunk.isEmpty()) {
            return;
        }

        try {
            m_httpClient.sendMetrics(sessionToken, chunk);
        } catch (OpButtonException e) {
            m_metrics.requeue(chunk);
            throw e;
        }
    }

    @Nullable
    JSONObject resolveLogAttributes() {
        OpButtonLogAttributeBuilder builder = m_logConfig.getAttributes();
        Map<String, Object> staticAttrs = builder.getStaticAttributes();
        Map<String, OpButtonValueProvider> dynamicAttrs = builder.getDynamicAttributes();

        if (staticAttrs.isEmpty() && dynamicAttrs.isEmpty()) {
            return null;
        }

        JSONObject attributes = new JSONObject();
        try {
            for (Map.Entry<String, Object> entry : staticAttrs.entrySet()) {
                attributes.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception ignored) {
        }

        for (Map.Entry<String, OpButtonValueProvider> entry : dynamicAttrs.entrySet()) {
            try {
                Object value = entry.getValue().getValue();
                attributes.put(entry.getKey(), value != null ? value : JSONObject.NULL);
            } catch (Exception ignored) {
            }
        }

        if (attributes.length() == 0) {
            return null;
        }

        return attributes;
    }
}
