package org.opbutton.sdk;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Interface for custom metric collectors.
 * Implement this to provide application-specific metrics (FPS, memory, etc.).
 *
 * <p>Collectors are called on a background thread at the configured sample interval.</p>
 *
 * <pre>{@code
 * OpButton.getMetrics().addCollector(new OpButtonMetricCollector() {
 *     @Override
 *     public void collect(List<OpButtonMetricEntry> out) {
 *         out.add(new OpButtonMetricEntry(OpButtonMetricType.FPS, getCurrentFps()));
 *         out.add(new OpButtonMetricEntry(OpButtonMetricType.MEMORY, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
 *     }
 * });
 * }</pre>
 */
public interface OpButtonMetricCollector {
    /**
     * Called periodically to collect metric samples.
     * Add entries to the output list.
     *
     * @param out list to add metric entries to
     */
    void collect(@NonNull List<OpButtonMetricEntry> out);
}
