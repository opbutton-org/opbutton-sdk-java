package org.opbutton.sdk;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
 * OpButton.initialize(config);
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
    public static final String VERSION = "1.0.33";

    private static volatile OpButton s_instance;

    private final OpButtonConfig m_config;
    private final OpButtonHttpClient m_httpClient;

    private final AtomicReference<String> m_sessionToken = new AtomicReference<>(null);
    private final AtomicReference<String> m_requestId = new AtomicReference<>(null);
    private final AtomicReference<OpButtonSessionStatus> m_status = new AtomicReference<>(OpButtonSessionStatus.DISCONNECTED);
    private final AtomicBoolean m_running = new AtomicBoolean(false);
    private final AtomicBoolean m_actionPollInFlight = new AtomicBoolean(false);

    private final List<OpButtonLogEntry> m_logBuffer = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean m_logOverflowWarned = new AtomicBoolean(false);
    private final Map<String, Object> m_sessionAttributeCache = new ConcurrentHashMap<>();

    private final CopyOnWriteArrayList<OpButtonActionListener> m_actionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OpButtonSessionListener> m_sessionListeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService m_executor;
    private ScheduledFuture<?> m_actionPollFuture;
    private ScheduledFuture<?> m_metricSampleFuture;

    private final OpButtonUI m_ui = new OpButtonUI();
    private final OpButtonMetrics m_metrics = new OpButtonMetrics();
    private final OpButtonFpsTracker m_fpsTracker = new OpButtonFpsTracker();
    private final OpButtonLiveManager m_liveManager;
    private final Handler m_mainHandler = new Handler(Looper.getMainLooper());
    private volatile long m_currentActionPollIntervalMs;

    private OpButton(@NonNull OpButtonConfig config, @Nullable Context context) {
        m_config = config;
        m_httpClient = new OpButtonHttpClient(config.baseUrl, config.apiToken);
        m_liveManager = new OpButtonLiveManager(context, m_httpClient, this::updateLiveMode);
        m_currentActionPollIntervalMs = config.actionPollIntervalMs;
        OpButtonLog.setLevel(config.logConfig.getLogLevel());
        OpButtonLog.setLoggerListener(config.loggerListener);

        OpButtonMetricConfigBuilder mc = config.metricConfig;
        if (mc.isEnabled()) {
            m_metrics.setSampleIntervalMs(mc.getSampleIntervalMs());
            m_metrics.setBufferSize(mc.getBufferSize());
            m_metrics.setBatchSize(mc.getBatchSize());
        }
    }

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
        if (s_instance != null) {
            OpButtonLog.w("OpButton.initialize() called more than once, ignoring");
            return;
        }

        synchronized (OpButton.class) {
            if (s_instance == null) {
                OpButton p = new OpButton(config, context);
                s_instance = p;
                OpButtonLog.i("OpButton SDK initialized (server: %s)", config.baseUrl);

                if (config.sessionListener != null) {
                    p.m_sessionListeners.add(config.sessionListener);
                }
                if (config.actionListener != null) {
                    p.m_actionListeners.add(config.actionListener);
                }

                // Register built-in metric collector and user collectors
                OpButtonMetricConfigBuilder mc = config.metricConfig;
                if (mc.isEnabled()) {
                    p.m_metrics.addCollector(new OpButtonDefaultMetricCollector(p.m_fpsTracker, context));
                    for (OpButtonMetricCollector collector : mc.getCollectors()) {
                        p.m_metrics.addCollector(collector);
                    }
                    OpButtonLog.i("Built-in metrics enabled (sample interval: %dms)", mc.getSampleIntervalMs());
                }

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
        OpButton p = s_instance;
        if (p == null) {
            return OpButtonSessionStatus.DISCONNECTED;
        }
        return p.m_status.get();
    }

    /**
     * Add a listener for actions from the dashboard.
     */
    public static void addActionListener(@NonNull OpButtonActionListener listener) {
        OpButton p = requireInstance();
        p.m_actionListeners.add(listener);
    }

    /**
     * Remove a previously added action listener.
     */
    public static void removeActionListener(@NonNull OpButtonActionListener listener) {
        OpButton p = s_instance;
        if (p != null) {
            p.m_actionListeners.remove(listener);
        }
    }

    /**
     * Add a listener for session lifecycle events.
     */
    public static void addSessionListener(@NonNull OpButtonSessionListener listener) {
        OpButton p = requireInstance();
        p.m_sessionListeners.add(listener);
    }

    /**
     * Remove a previously added session listener.
     */
    public static void removeSessionListener(@NonNull OpButtonSessionListener listener) {
        OpButton p = s_instance;
        if (p != null) {
            p.m_sessionListeners.remove(listener);
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
        return requireInstance().m_ui;
    }

    /**
     * Get the metrics subsystem. Use to add collectors or record metrics manually.
     *
     * @return The shared OpButtonMetrics instance
     */
    @NonNull
    public static OpButtonMetrics getMetrics() {
        return requireInstance().m_metrics;
    }

    /**
     * Connect to the OpButton server. Runs asynchronously.
     */
    public static void connect() {
        OpButton p = requireInstance();
        p.startConnection();
    }

    /**
     * Disconnect from the server and stop all background tasks.
     */
    public static void disconnect() {
        OpButton p = s_instance;
        if (p != null) {
            p.stopConnection();
        }
    }

    /**
     * Send a log entry to the OpButton server. Logs are buffered and sent in batches.
     * Log attributes are resolved at call time.
     */
    public static void log(@NonNull OpButtonLogLevel level, @NonNull String message) {
        OpButton p = s_instance;
        if (p != null && p.m_config.logConfig.isEnabled()) {
            p.bufferLog(new OpButtonLogEntry(level, message, null, null, null, p.resolveLogAttributes()));
        }
    }

    /**
     * Send a log entry with category and thread.
     */
    public static void log(@NonNull OpButtonLogLevel level, @NonNull String message,
                           @Nullable String category, @Nullable String thread) {
        OpButton p = s_instance;
        if (p != null && p.m_config.logConfig.isEnabled()) {
            p.bufferLog(new OpButtonLogEntry(level, message, category, thread, null, p.resolveLogAttributes()));
        }
    }

    /**
     * Send a log entry with metadata.
     */
    public static void log(@NonNull OpButtonLogLevel level, @NonNull String message, @Nullable Map<String, Object> metadata) {
        OpButton p = s_instance;
        if (p != null && p.m_config.logConfig.isEnabled()) {
            p.bufferLog(new OpButtonLogEntry(level, message, null, null, metadata, p.resolveLogAttributes()));
        }
    }

    /**
     * Send a log entry with category, thread, and metadata.
     */
    public static void log(@NonNull OpButtonLogLevel level, @NonNull String message,
                           @Nullable String category, @Nullable String thread,
                           @Nullable Map<String, Object> metadata) {
        OpButton p = s_instance;
        if (p != null && p.m_config.logConfig.isEnabled()) {
            p.bufferLog(new OpButtonLogEntry(level, message, category, thread, metadata, p.resolveLogAttributes()));
        }
    }

    /**
     * Send a pre-built log entry.
     */
    public static void log(@NonNull OpButtonLogEntry entry) {
        OpButton p = s_instance;
        if (p != null && p.m_config.logConfig.isEnabled()) {
            p.bufferLog(entry);
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
        OpButton p = s_instance;
        if (p == null) {
            return;
        }
        String token = p.m_sessionToken.get();
        if (token == null) {
            return;
        }
        p.m_executor.execute(() -> {
            try {
                p.m_httpClient.acknowledgeAction(token, actionId, ackPayload);
            } catch (OpButtonException e) {
                OpButtonLog.e("Failed to acknowledge action", e);
            }
        });
    }

    private static void bufferStructuredLog(@NonNull String kind,
                                            @NonNull String message,
                                            @Nullable String category,
                                            @Nullable Map<String, Object> metadata,
                                            long clientTimestampMs) {
        OpButton p = s_instance;
        if (p == null || !p.m_config.logConfig.isEnabled()) {
            return;
        }

        String resolvedCategory = resolveStructuredCategory(kind, category);
        Map<String, Object> resolvedMetadata = mergeStructuredMetadata(kind, metadata);

        p.bufferLog(new OpButtonLogEntry(
            OpButtonLogLevel.INFO,
            message,
            resolvedCategory,
            null,
            resolvedMetadata,
            p.resolveLogAttributes(),
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
        OpButton p;
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

    private static OpButton requireInstance() {
        OpButton p = s_instance;
        if (p == null) {
            throw new IllegalStateException("OpButton.initialize() must be called first");
        }
        return p;
    }

    private void startConnection() {
        if (m_running.compareAndSet(false, true) == false) {
            OpButtonLog.w("Already connecting/connected");
            return;
        }

        m_executor = Executors.newScheduledThreadPool(3);
        m_executor.execute(this::connectAndWaitApproval);
    }

    private void stopConnection() {
        if (m_running.compareAndSet(true, false) == false) {
            return;
        }

        m_liveManager.onSessionClosed();
        m_fpsTracker.stop();
        cancelScheduledTasks();

        String token = m_sessionToken.getAndSet(null);
        if (token != null && m_executor != null && !m_executor.isShutdown()) {
            m_executor.execute(() -> {
                try {
                    flushLogs(token);
                    flushMetrics(token);
                    m_httpClient.closeSession(token);
                } catch (OpButtonException ignored) {
                }
                setStatus(OpButtonSessionStatus.CLOSED);
                notifySessionClosed();
            });
        } else {
            setStatus(OpButtonSessionStatus.DISCONNECTED);
        }

        shutdownExecutor();
    }

    private void doShutdown() {
        OpButtonLog.i("Shutting down OpButton SDK");
        m_fpsTracker.stop();
        stopConnection();
        m_liveManager.shutdown();
        m_httpClient.shutdown();
        m_actionListeners.clear();
        m_sessionListeners.clear();
        m_logBuffer.clear();
        m_metrics.clear();
    }

    private void connectAndWaitApproval() {
        int retryCount = 0;
        long retryDelayMs = 2000;
        final long maxRetryDelayMs = 30000;

        while (m_running.get()) {
            try {
                doConnectAndWaitApproval();
                return; // success
            } catch (OpButtonException e) {
                if (e.isUnauthorized()) {
                    OpButtonLog.e("Authentication failed", e);
                    setStatus(OpButtonSessionStatus.AUTH_FAILED);
                    m_running.set(false);
                    notifyAuthFailed();
                    return;
                }

                if (!e.isNetworkError()) {
                    OpButtonLog.e("Server error, stopping connection: %s", e.getMessage());
                    setStatus(OpButtonSessionStatus.ERROR);
                    m_running.set(false);
                    notifyError(e);
                    return;
                }

                retryCount++;
                OpButtonLog.w("Connection attempt %d failed (network): %s, retrying in %dms", retryCount, e.getMessage(), retryDelayMs);
                setStatus(OpButtonSessionStatus.CONNECTING);

                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    m_running.set(false);
                    return;
                }

                retryDelayMs = Math.min(retryDelayMs * 2, maxRetryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                m_running.set(false);
                return;
            }
        }
    }

    private void doConnectAndWaitApproval() throws OpButtonException, InterruptedException {
        setStatus(OpButtonSessionStatus.CONNECTING);
        notifyConnecting();

        String deviceId = m_config.deviceId;
        String deviceName = m_config.deviceName;

        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = Build.BRAND + "-" + Build.MODEL;
        }
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
        }

        OpButtonConnectResponse resp = m_httpClient.connect(deviceId, deviceName, resolveAllSessionAttributes());
        m_requestId.set(resp.getRequestId());

        OpButtonLog.i("Connect request sent, request_id=%s, status=%s", resp.getRequestId(), resp.getStatus());

        if (resp.isApproved() && resp.getSessionToken() != null) {
            onApproved(resp.getSessionToken());
            return;
        }

        if (resp.isRejected()) {
            onRejected();
            return;
        }

        setStatus(OpButtonSessionStatus.WAITING_APPROVAL);
        notifyWaitingApproval(resp.getRequestId());

        // Poll loop
        while (m_running.get()) {
            Thread.sleep(m_config.pollIntervalMs);

            OpButtonConnectResponse pollResp = m_httpClient.pollStatus(resp.getRequestId());

            if (pollResp.isApproved() && pollResp.getSessionToken() != null) {
                onApproved(pollResp.getSessionToken());
                return;
            }

            if (pollResp.isRejected()) {
                onRejected();
                return;
            }
        }
    }

    private void onApproved(@NonNull String sessionToken) {
        m_sessionToken.set(sessionToken);
        setStatus(OpButtonSessionStatus.ACTIVE);

        OpButtonLog.i("Session approved and active");

        notifySessionStarted(sessionToken);

        // Send UI (tab-based) if any tabs exist
        if (m_ui.hasTabs()) {
            JSONObject snapshot = buildUISnapshotOnMainThread();
            if (snapshot != null) {
                try {
                    m_httpClient.submitPanel(sessionToken, snapshot);
                    m_ui.markSent();
                    OpButtonLog.d("Initial UI submitted (revision=%d)", m_ui.getRevision());
                } catch (OpButtonException e) {
                    OpButtonLog.e("Failed to submit initial UI", e);
                    notifyError(e);
                }
            }
        }

        // Start background tasks
        scheduleActionPolling(sessionToken, m_config.actionPollIntervalMs);

        // Start metric sampling
        if (m_config.metricConfig.isEnabled()) {
            m_fpsTracker.start();

            long sampleMs = m_metrics.getSampleIntervalMs();
            m_metricSampleFuture = m_executor.scheduleAtFixedRate(
                    () -> m_metrics.sample(),
                    sampleMs,
                    sampleMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void onRejected() {
        OpButtonLog.w("Connection request rejected");
        setStatus(OpButtonSessionStatus.REJECTED);
        m_running.set(false);
        notifyRejected();
    }

    private void doPollActions(@NonNull String sessionToken) {
        if (!m_running.get() || !m_actionPollInFlight.compareAndSet(false, true)) {
            return;
        }

        try {
            Map<String, Object> changedAttrs = resolveChangedSessionAttributes();
            List<OpButtonLogEntry> logChunk = drainLogChunk();
            List<OpButtonMetricEntry> metricChunk = m_metrics.drain();

            // Poll value providers + snapshot UI on Main Thread
            JSONObject uiSnapshot = snapshotUIOnMainThread();

            // Send UI if snapshot was produced (means there were unsent changes)
            if (uiSnapshot != null) {
                try {
                    m_httpClient.submitPanel(sessionToken, uiSnapshot);
                    m_ui.markSent();
                    OpButtonLog.d("UI submitted (revision=%d)", m_ui.getRevision());
                } catch (OpButtonException e) {
                    OpButtonLog.e("Failed to submit UI", e);
                    notifyError(e);
                }
            }

            try {
                JSONObject json = m_httpClient.pollActions(sessionToken, changedAttrs, logChunk, metricChunk);
                JSONArray actionsArr = json.optJSONArray("actions");
                if (actionsArr != null && actionsArr.length() > 0) {
                    for (int i = 0; i < actionsArr.length(); i++) {
                        JSONObject actionJson = actionsArr.optJSONObject(i);
                        if (actionJson != null) {
                            OpButtonAction action = OpButtonAction.fromJson(actionJson);
                            dispatchActionOnMainThread(action);
                        }
                    }
                }

                if (!logChunk.isEmpty()) {
                    m_logOverflowWarned.set(false);
                }
            } catch (OpButtonException e) {
                requeueLogs(logChunk);
                m_metrics.requeue(metricChunk);

                if (e.isNetworkError()) {
                    OpButtonLog.w("Network error during action poll, will retry");
                } else {
                    OpButtonLog.w("Action poll failed with server error, attempting reconnect");
                    attemptReconnect(sessionToken);
                }
            }
        } finally {
            m_actionPollInFlight.set(false);
        }
    }

    /**
     * Run pollValues + toJson on the Main Thread to avoid races with UI mutations.
     * Returns null if there are no unsent changes.
     */
    @Nullable
    private JSONObject snapshotUIOnMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            m_ui.pollValues();
            return m_ui.hasUnsent() ? m_ui.toJson() : null;
        }

        FutureTask<JSONObject> task = new FutureTask<>(() -> {
            m_ui.pollValues();
            return m_ui.hasUnsent() ? m_ui.toJson() : null;
        });
        m_mainHandler.post(task);

        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            OpButtonLog.e("Failed to snapshot UI on main thread", e);
            return null;
        }
    }

    /**
     * Build a UI snapshot on the Main Thread unconditionally (for initial submit).
     */
    @Nullable
    private JSONObject buildUISnapshotOnMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return m_ui.toJson();
        }

        FutureTask<JSONObject> task = new FutureTask<>(() -> m_ui.toJson());
        m_mainHandler.post(task);

        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            OpButtonLog.e("Failed to build UI snapshot on main thread", e);
            return null;
        }
    }

    /**
     * Dispatch an action callback on the Main Thread so that any UI mutations
     * triggered by the callback stay on the same thread as other UI operations.
     */
    private void dispatchActionOnMainThread(@NonNull OpButtonAction action) {
        m_mainHandler.post(() -> {
            boolean handled = false;

            try {
                handled = handleInternalAction(action);
                if (!handled) {
                    m_ui.dispatchAction(action);
                }
            } catch (Exception ex) {
                OpButtonLog.e("Widget callback threw exception", ex);
            }

            if (!handled) {
                for (OpButtonActionListener listener : m_actionListeners) {
                    try {
                        listener.onOpButtonActionReceived(action);
                    } catch (Exception ex) {
                        OpButtonLog.e("Action listener threw exception", ex);
                    }
                }
            }
        });
    }

    private void scheduleActionPolling(@NonNull String sessionToken, long intervalMs) {
        if (m_executor == null || m_executor.isShutdown() || !m_running.get()) {
            return;
        }

        if (m_actionPollFuture != null) {
            m_actionPollFuture.cancel(false);
            m_actionPollFuture = null;
        }

        m_currentActionPollIntervalMs = intervalMs;
        m_actionPollFuture = m_executor.scheduleAtFixedRate(
                () -> doPollActions(sessionToken),
                0,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    private void updateLiveMode(boolean enabled, long requestedPollIntervalMs) {
        String sessionToken = m_sessionToken.get();
        if (sessionToken == null || m_executor == null || m_executor.isShutdown()) {
            return;
        }

        long targetIntervalMs = enabled
                ? Math.max(200L, Math.min(requestedPollIntervalMs > 0 ? requestedPollIntervalMs : 500L, m_config.actionPollIntervalMs))
                : m_config.actionPollIntervalMs;

        if (targetIntervalMs == m_currentActionPollIntervalMs) {
            return;
        }

        m_executor.execute(() -> scheduleActionPolling(sessionToken, targetIntervalMs));
    }

    private boolean handleInternalAction(@NonNull OpButtonAction action) {
        JSONObject ackPayload;
        switch (action.getActionType()) {
            case LIVE_START:
                String sessionToken = m_sessionToken.get();
                if (sessionToken == null) {
                    ackPayload = buildInternalAck(false, "No active session available for streaming");
                    acknowledgeAction(action.getId(), ackPayload);
                } else {
                    final String actionId = action.getId();
                    final JSONObject payload = action.getPayload();
                    m_executor.execute(() -> {
                        JSONObject result = m_liveManager.start(sessionToken, payload);
                        acknowledgeAction(actionId, result);
                    });
                }
                return true;

            case LIVE_UPDATE:
                String liveUpdateSessionToken = m_sessionToken.get();
                if (liveUpdateSessionToken == null) {
                    ackPayload = buildInternalAck(false, "No active session available for streaming");
                    acknowledgeAction(action.getId(), ackPayload);
                } else {
                    final String actionId = action.getId();
                    final JSONObject payload = action.getPayload();
                    m_executor.execute(() -> {
                        JSONObject result = m_liveManager.update(liveUpdateSessionToken, payload);
                        acknowledgeAction(actionId, result);
                    });
                }
                return true;

            case LIVE_STOP:
                acknowledgeAction(action.getId(), m_liveManager.stop());
                return true;

            case LIVE_TAP:
                acknowledgeAction(action.getId(), m_liveManager.tap(action.getPayload()));
                return true;

            case LIVE_LONG_PRESS:
                acknowledgeAction(action.getId(), m_liveManager.longPress(action.getPayload()));
                return true;

            default:
                return false;
        }
    }

    @NonNull
    private static JSONObject buildInternalAck(boolean ok, @NonNull String status) {
        JSONObject ackPayload = new JSONObject();
        try {
            ackPayload.put("ok", ok);
            ackPayload.put("status", status);
        } catch (Exception ignored) {
        }
        return ackPayload;
    }

    private void bufferLog(@NonNull OpButtonLogEntry entry) {
        synchronized (m_logBuffer) {
            if (m_logBuffer.size() >= m_config.logConfig.getBufferSize()) {
                m_logBuffer.remove(0);

                if (m_logOverflowWarned.compareAndSet(false, true)) {
                    OpButtonLog.w("Log buffer overflow (" + m_config.logConfig.getBufferSize() + "), dropping oldest entries");
                }
            }

            m_logBuffer.add(entry);
        }
    }

    @NonNull
    private List<OpButtonLogEntry> drainLogChunk() {
        if (m_logBuffer.isEmpty()) return Collections.emptyList();

        synchronized (m_logBuffer) {
            int count = Math.min(m_logBuffer.size(), m_config.logConfig.getBatchSize());
            List<OpButtonLogEntry> chunk = new ArrayList<>(m_logBuffer.subList(0, count));
            m_logBuffer.subList(0, count).clear();
            return chunk;
        }
    }

    private void requeueLogs(@NonNull List<OpButtonLogEntry> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        synchronized (m_logBuffer) {
            m_logBuffer.addAll(0, chunk);

            while (m_logBuffer.size() > m_config.logConfig.getBufferSize()) {
                m_logBuffer.remove(m_logBuffer.size() - 1);
            }
        }
    }

    private void flushLogs(@NonNull String sessionToken) throws OpButtonException {
        List<OpButtonLogEntry> chunk = drainLogChunk();
        if (chunk.isEmpty()) {
            return;
        }

        try {
            m_httpClient.sendLogs(sessionToken, chunk);
        } catch (OpButtonException e) {
            requeueLogs(chunk);
            throw e;
        }
    }

    private void flushMetrics(@NonNull String sessionToken) throws OpButtonException {
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
    private JSONObject resolveLogAttributes() {
        OpButtonLogAttributeBuilder builder = m_config.logConfig.getAttributes();
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

    private Map<String, Object> resolveAllSessionAttributes() {
        OpButtonSessionAttributeBuilder builder = m_config.sessionAttributes;
        Map<String, Object> merged = new ConcurrentHashMap<>(builder.getStaticAttributes());

        for (Map.Entry<String, OpButtonValueProvider> entry : builder.getDynamicAttributes().entrySet()) {
            try {
                Object value = entry.getValue().getValue();
                merged.put(entry.getKey(), value != null ? value : JSONObject.NULL);
                m_sessionAttributeCache.put(entry.getKey(), value != null ? value : JSONObject.NULL);
            } catch (Exception e) {
                OpButtonLog.e("Session attribute provider failed: " + entry.getKey(), e);
            }
        }

        return merged;
    }

    @Nullable
    private Map<String, Object> resolveChangedSessionAttributes() {
        Map<String, OpButtonValueProvider> dynamicAttrs = m_config.sessionAttributes.getDynamicAttributes();
        if (dynamicAttrs.isEmpty()) return null;

        Map<String, Object> changed = null;

        for (Map.Entry<String, OpButtonValueProvider> entry : dynamicAttrs.entrySet()) {
            try {
                Object value = entry.getValue().getValue();
                Object resolved = value != null ? value : JSONObject.NULL;
                Object cached = m_sessionAttributeCache.get(entry.getKey());

                if (!resolved.equals(cached)) {
                    m_sessionAttributeCache.put(entry.getKey(), resolved);
                    if (changed == null) {
                        changed = new ConcurrentHashMap<>();
                    }
                    changed.put(entry.getKey(), resolved);
                }
            } catch (Exception e) {
                OpButtonLog.e("Session attribute provider failed: " + entry.getKey(), e);
            }
        }

        return changed;
    }

    private void attemptReconnect(@NonNull String token) {
        cancelScheduledTasks();
        setStatus(OpButtonSessionStatus.CONNECTING);

        long retryDelay = 2000;
        final long maxDelay = 30000;

        while (m_running.get()) {
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (!m_running.get()) return;

            try {
                m_httpClient.pollActions(token, null, Collections.emptyList(), Collections.emptyList());

                OpButtonLog.i("Reconnect successful, session still active");
                setStatus(OpButtonSessionStatus.ACTIVE);
                scheduleActionPolling(token, m_currentActionPollIntervalMs);
                return;

            } catch (OpButtonException e) {
                if (e.isNetworkError()) {
                    OpButtonLog.w("Reconnect: no network, retrying in %dms", retryDelay);
                    retryDelay = Math.min(retryDelay * 2, maxDelay);
                    continue;
                }

                OpButtonLog.w("Session lost (server error), starting fresh connection");
                resetAndRestart();
                return;
            }
        }
    }

    private void resetAndRestart() {
        m_running.set(false);
        m_sessionToken.set(null);

        m_liveManager.onSessionClosed();
        setStatus(OpButtonSessionStatus.DISCONNECTED);
        notifySessionClosed();

        startConnection();
    }

    private void setStatus(@NonNull OpButtonSessionStatus status) {
        m_status.set(status);
    }

    private void cancelScheduledTasks() {
        if (m_actionPollFuture != null) {
            m_actionPollFuture.cancel(false);
            m_actionPollFuture = null;
        }
        if (m_metricSampleFuture != null) {
            m_metricSampleFuture.cancel(false);
            m_metricSampleFuture = null;
        }
    }

    private void shutdownExecutor() {
        if (m_executor != null && !m_executor.isShutdown()) {
            m_executor.shutdown();
        }
    }

    // ── Listener notifications ──

    private void notifyConnecting() {
        for (OpButtonSessionListener l : m_sessionListeners) {
            l.onOpButtonSessionConnecting();
        }
    }

    private void notifyWaitingApproval(@NonNull String requestId) {
        for (OpButtonSessionListener l : m_sessionListeners) {
            l.onOpButtonSessionWaitingApproval(requestId);
        }
    }

    private void notifySessionStarted(@NonNull String sessionToken) {
        for (OpButtonSessionListener l : m_sessionListeners) {
            l.onOpButtonSessionStarted(sessionToken);
        }
    }

    private void notifySessionClosed() {
        for (OpButtonSessionListener l : m_sessionListeners) {
            l.onOpButtonSessionClosed();
        }
    }

    private void notifyRejected() {
        for (OpButtonSessionListener l : m_sessionListeners) {
            l.onOpButtonSessionRejected();
        }
    }

    private void notifyAuthFailed() {
        for (OpButtonSessionListener l : m_sessionListeners) {
            l.onOpButtonSessionAuthFailed();
        }
    }

    private void notifyError(@NonNull OpButtonException exception) {
        for (OpButtonSessionListener l : m_sessionListeners) {
            l.onOpButtonSessionError(exception);
        }
    }
}
