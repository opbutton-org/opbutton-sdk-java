package org.opbutton.sdk;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpButton SDK — remote debug panel and logging for Android applications.
 *
 * <p>Singleton entry point. Initialize once, then call methods from anywhere in your app.</p>
 *
 * <pre>{@code
 * // 1. Initialize
 * OpButtonConfig config = new OpButtonConfig.Builder("https://opbutton.example.com", "opbutton_your_token")
 *     .setDeviceId("my-device")
 *     .setDeviceName("Pixel 8 (Android 14)")
 *     .build();
 * OpButton.initialize(config, applicationContext);
 *
 * // 2. Listen for actions
 * OpButton.addActionListener(action -> {
 *     if ("btn-restart".equals(action.getWidgetId())) {
 *         restartGame();
 *         OpButton.acknowledgeAction(action.getId(), null);
 *     }
 * });
 *
 * // 3. Build UI
 * OpButtonUI ui = OpButton.getUI();
 * OpButtonTab tab = ui.addTab("Controls");
 * OpButtonLayout root = tab.vertical();
 * root.addButton("Restart")
 *     .variant("contained").color("error")
 *     .onClick(action -> restartGame());
 *
 * // 4. Connect
 * OpButton.connect();
 *
 * // 5. Send logs anytime
 * OpButton.log(OpButtonLogLevel.INFO, "Game started");
 *
 * // 6. Shutdown on exit
 * OpButton.shutdown();
 * }</pre>
 */
public final class OpButton {
    public static final String VERSION = "1.0.42";

    private static volatile OpButtonRuntime s_instance;

    private OpButton() {}

    // ══════════════════════════════════════════════════════
    //  Static API
    // ══════════════════════════════════════════════════════

    /**
     * Initialize the OpButton SDK. Must be called before any other method.
     * Safe to call multiple times — subsequent calls are ignored.
     *
     * <p>Use {@link #initialize(OpButtonConfig, Context)} to enable battery metrics and app stream capture.</p>
     */
    public static void initialize(@NonNull OpButtonConfig config) {
        initialize(config, null);
    }

    /**
     * Initialize the OpButton SDK with Android Context for battery metrics and app stream support.
     *
     * @param config SDK configuration
     * @param context Android Context. When available, the SDK uses it for battery metrics and
     *                resolves Application lifecycle callbacks required for app stream capture and
     *                touch overlay support. Can be null.
     */
    public static void initialize(@NonNull OpButtonConfig config, @Nullable Context context) {
        if (context == null && config.deviceSecret == null) {
            throw new IllegalArgumentException(
                    "Android Context is required unless setDeviceSecret() uses caller-owned secure storage");
        }
        if (s_instance != null) {
            OpButtonLog.w("OpButton.initialize() called more than once, ignoring");
            return;
        }

        synchronized (OpButton.class) {
            if (s_instance == null) {
                OpButtonRuntime p = new OpButtonRuntime(config, context);
                s_instance = p;
                OpButtonLog.i("OpButton SDK initialized (server: %s)", config.baseUrl);
                p.logConfiguration();

                if (config.autoConnect) {
                    p.startConnection();
                }
            }
        }
    }

    /**
     * Whether the SDK has been initialized.
     */
    public static boolean isInitialized() {
        return s_instance != null;
    }

    /**
     * Current session status.
     */
    @NonNull
    public static OpButtonSessionStatus getStatus() {
        OpButtonRuntime p = s_instance;
        if (p == null) {
            return OpButtonSessionStatus.DISCONNECTED;
        }
        return p.getStatus();
    }

    /**
     * Add a listener for actions from the dashboard.
     */
    public static void addActionListener(@NonNull OpButtonActionListener listener) {
        OpButtonRuntime p = requireInstance();
        p.addActionListener(listener);
    }

    /**
     * Remove a previously added action listener.
     */
    public static void removeActionListener(@NonNull OpButtonActionListener listener) {
        OpButtonRuntime p = s_instance;
        if (p != null) {
            p.removeActionListener(listener);
        }
    }

    /**
     * Add a listener for session lifecycle events.
     */
    public static void addSessionListener(@NonNull OpButtonSessionListener listener) {
        OpButtonRuntime p = requireInstance();
        p.addSessionListener(listener);
    }

    /**
     * Remove a previously added session listener.
     */
    public static void removeSessionListener(@NonNull OpButtonSessionListener listener) {
        OpButtonRuntime p = s_instance;
        if (p != null) {
            p.removeSessionListener(listener);
        }
    }

    /**
     * Get the central UI data source. Each service/module adds its own tab here.
     * Changes are automatically sent to the server on the next poll cycle.
     *
     * @return The shared OpButtonUI instance
     */
    @NonNull
    public static OpButtonUI getUI() {
        return requireInstance().getUI();
    }

    /**
     * Get the metrics subsystem. Use to add collectors or record metrics manually.
     *
     * @return The shared OpButtonMetrics instance
     */
    @NonNull
    public static OpButtonMetrics getMetrics() {
        return requireInstance().getMetrics();
    }

    /**
     * Connect to the OpButton server. Runs asynchronously.
     */
    public static void connect() {
        OpButtonRuntime p = requireInstance();
        p.startConnection();
    }

    /**
     * Disconnect from the server and stop all background tasks.
     */
    public static void disconnect() {
        OpButtonRuntime p = s_instance;
        if (p != null) {
            p.stopConnection();
        }
    }

    /**
     * Send a log entry to the OpButton server. Logs are buffered and sent in batches.
     * Log attributes are resolved at call time.
     */
    public static void log(@NonNull OpButtonLogLevel level, @NonNull String message) {
        OpButtonRuntime p = s_instance;
        if (p != null && p.isLoggingEnabled()) {
            p.m_telemetry.bufferLog(new OpButtonLogEntry(level, message, null, null, null, p.m_telemetry.resolveLogAttributes()));
        }
    }

    /**
     * Send a log entry with category and thread.
     */
    public static void log(@NonNull OpButtonLogLevel level, @NonNull String message,
                           @Nullable String category, @Nullable String thread) {
        OpButtonRuntime p = s_instance;
        if (p != null && p.isLoggingEnabled()) {
            p.m_telemetry.bufferLog(new OpButtonLogEntry(level, message, category, thread, null, p.m_telemetry.resolveLogAttributes()));
        }
    }

    /**
     * Send a log entry with metadata.
     */
    public static void log(@NonNull OpButtonLogLevel level, @NonNull String message, @Nullable Map<String, Object> metadata) {
        OpButtonRuntime p = s_instance;
        if (p != null && p.isLoggingEnabled()) {
            p.m_telemetry.bufferLog(new OpButtonLogEntry(level, message, null, null, metadata, p.m_telemetry.resolveLogAttributes()));
        }
    }

    /**
     * Send a log entry with category, thread, and metadata.
     */
    public static void log(@NonNull OpButtonLogLevel level, @NonNull String message,
                           @Nullable String category, @Nullable String thread,
                           @Nullable Map<String, Object> metadata) {
        OpButtonRuntime p = s_instance;
        if (p != null && p.isLoggingEnabled()) {
            p.m_telemetry.bufferLog(new OpButtonLogEntry(level, message, category, thread, metadata, p.m_telemetry.resolveLogAttributes()));
        }
    }

    /**
     * Send a pre-built log entry.
     */
    public static void log(@NonNull OpButtonLogEntry entry) {
        OpButtonRuntime p = s_instance;
        if (p != null && p.isLoggingEnabled()) {
            p.m_telemetry.bufferLog(entry);
        }
    }

    /**
     * Send a structured event entry.
     */
    public static void event(@NonNull String message) {
        event(message, null, null, System.currentTimeMillis());
    }

    /**
     * Send a structured event entry with metadata.
     */
    public static void event(@NonNull String message, @Nullable Map<String, Object> metadata) {
        event(message, null, metadata, System.currentTimeMillis());
    }

    /**
     * Send a structured event entry with metadata and an explicit client timestamp.
     */
    public static void event(@NonNull String message,
                             @Nullable Map<String, Object> metadata,
                             long clientTimestampMs) {
        event(message, null, metadata, clientTimestampMs);
    }

    /**
     * Send a structured event entry with an optional subtype.
     */
    public static void event(@NonNull String message,
                             @Nullable String category,
                             @Nullable Map<String, Object> metadata) {
        event(message, category, metadata, System.currentTimeMillis());
    }

    /**
     * Send a structured event entry with an explicit client timestamp.
     */
    public static void event(@NonNull String message,
                             @Nullable String category,
                             @Nullable Map<String, Object> metadata,
                             long clientTimestampMs) {
        bufferStructuredLog("event", message, category, metadata, clientTimestampMs);
    }

    /**
     * Send a structured revenue entry.
     */
    public static void revenue(@NonNull String message) {
        revenue(message, null, null, System.currentTimeMillis());
    }

    /**
     * Send a structured revenue entry with metadata.
     */
    public static void revenue(@NonNull String message, @Nullable Map<String, Object> metadata) {
        revenue(message, null, metadata, System.currentTimeMillis());
    }

    /**
     * Send a structured revenue entry with metadata and an explicit client timestamp.
     */
    public static void revenue(@NonNull String message,
                               @Nullable Map<String, Object> metadata,
                               long clientTimestampMs) {
        revenue(message, null, metadata, clientTimestampMs);
    }

    /**
     * Send a structured revenue entry with an optional subtype.
     */
    public static void revenue(@NonNull String message,
                               @Nullable String category,
                               @Nullable Map<String, Object> metadata) {
        revenue(message, category, metadata, System.currentTimeMillis());
    }

    /**
     * Send a structured revenue entry with an explicit client timestamp.
     */
    public static void revenue(@NonNull String message,
                               @Nullable String category,
                               @Nullable Map<String, Object> metadata,
                               long clientTimestampMs) {
        bufferStructuredLog("revenue", message, category, metadata, clientTimestampMs);
    }

    /**
     * Record a scene/screen change marker that can be used by the dashboard as a timeline slice.
     */
    public static void changeScreen(@NonNull String screenType, @NonNull String screenName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("opbutton_command", "change_screen");
        metadata.put("opbutton_slice_type", "screen");
        metadata.put("opbutton_slice_name", screenName);
        metadata.put("screen_type", screenType);
        metadata.put("screen_name", screenName);

        event("change_screen", "change_screen", metadata);
    }

    /**
     * Publish the current in-app product catalog to the dashboard.
     */
    public static void setInAppProducts(@NonNull List<Map<String, Object>> products) {
        List<Map<String, Object>> normalizedProducts = new ArrayList<>(products.size());

        for (Map<String, Object> product : products) {
            normalizedProducts.add(product == null ? new LinkedHashMap<>() : new LinkedHashMap<>(product));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("opbutton_command", "set_in_app_products");
        metadata.put("opbutton_purchase_entry_type", "catalog");
        metadata.put("in_app_products", normalizedProducts);
        metadata.put("in_app_product_count", normalizedProducts.size());

        bufferStructuredLog("purchase", "set_in_app_products", "catalog", metadata, System.currentTimeMillis());
    }

    /**
     * Publish the current owned in-app products to the dashboard.
     */
    public static void setOwnedInAppProducts(@NonNull List<String> productIds) {
        List<String> normalizedProductIds = new ArrayList<>(productIds);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("opbutton_command", "set_owned_in_app_products");
        metadata.put("opbutton_purchase_entry_type", "owned");
        metadata.put("owned_in_app_products", normalizedProductIds);
        metadata.put("owned_in_app_product_count", normalizedProductIds.size());

        bufferStructuredLog("purchase", "set_owned_in_app_products", "owned", metadata, System.currentTimeMillis());
    }

    /**
     * Record a completed in-app purchase event.
     */
    public static void purchaseInApp(@Nullable String transactionId,
                                     @NonNull List<String> productIds,
                                     @Nullable Map<String, Object> metadata) {
        List<String> normalizedProductIds = new ArrayList<>(productIds);
        Map<String, Object> purchaseMetadata = new LinkedHashMap<>();

        if (metadata != null && !metadata.isEmpty()) {
            purchaseMetadata.putAll(metadata);
        }

        purchaseMetadata.put("opbutton_command", "purchase_in_app");
        purchaseMetadata.put("opbutton_purchase_entry_type", "purchase");
        purchaseMetadata.put("in_app_products", normalizedProductIds);
        purchaseMetadata.put("in_app_product_count", normalizedProductIds.size());

        if (transactionId != null && transactionId.length() != 0) {
            purchaseMetadata.put("transaction_id", transactionId);
        }

        String message = normalizedProductIds.isEmpty()
            ? "purchase_in_app"
            : normalizedProductIds.get(0);

        bufferStructuredLog("purchase", message, "purchase", purchaseMetadata, System.currentTimeMillis());
    }

    /**
     * Acknowledge an action from the dashboard.
     *
     * @param actionId   ID of the action to acknowledge
     * @param ackPayload Optional response payload (can be null)
     */
    public static void acknowledgeAction(@NonNull String actionId, @Nullable JSONObject ackPayload) {
        OpButtonRuntime p = s_instance;
        if (p != null) p.acknowledgeAction(actionId, ackPayload);
    }

    private static void bufferStructuredLog(@NonNull String kind,
                                            @NonNull String message,
                                            @Nullable String category,
                                            @Nullable Map<String, Object> metadata,
                                            long clientTimestampMs) {
        OpButtonRuntime p = s_instance;
        if (p == null || !p.isLoggingEnabled()) {
            return;
        }

        String resolvedCategory = resolveStructuredCategory(kind, category);
        Map<String, Object> resolvedMetadata = mergeStructuredMetadata(kind, metadata);

        p.m_telemetry.bufferLog(new OpButtonLogEntry(
            OpButtonLogLevel.INFO,
            message,
            resolvedCategory,
            null,
            resolvedMetadata,
            p.m_telemetry.resolveLogAttributes(),
            clientTimestampMs
        ));
    }

    @NonNull
    private static String resolveStructuredCategory(@NonNull String kind, @Nullable String category) {
        if (category == null || category.length() == 0) {
            return kind;
        }

        if (category.equals(kind) || category.startsWith(kind + "_")) {
            return category;
        }

        return kind + "_" + category;
    }

    @NonNull
    private static Map<String, Object> mergeStructuredMetadata(@NonNull String kind,
                                                               @Nullable Map<String, Object> metadata) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (metadata != null && !metadata.isEmpty()) {
            merged.putAll(metadata);
        }

        merged.put("opbutton_kind", kind);
        return merged;
    }

    /**
     * Shut down the SDK completely. Closes the session if active, stops all threads.
     * After shutdown, {@link #initialize} can be called again.
     */
    public static void shutdown() {
        OpButtonRuntime p;
        synchronized (OpButton.class) {
            p = s_instance;
            s_instance = null;
        }
        if (p != null) {
            p.doShutdown();
        }
    }

    // ══════════════════════════════════════════════════════
    //  Internal
    // ══════════════════════════════════════════════════════

    private static OpButtonRuntime requireInstance() {
        OpButtonRuntime p = s_instance;
        if (p == null) {
            throw new IllegalStateException("OpButton.initialize() must be called first");
        }
        return p;
    }

}
