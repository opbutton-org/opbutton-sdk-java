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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Coordinates one initialized SDK instance behind the public facade. */
final class OpButtonRuntime {
    private final OpButtonConfig m_config;
    private final OpButtonHttpClient m_httpClient;

    private final AtomicReference<String> m_sessionToken = new AtomicReference<>(null);
    private final AtomicReference<String> m_requestId = new AtomicReference<>(null);
    private final AtomicReference<OpButtonSessionStatus> m_status = new AtomicReference<>(OpButtonSessionStatus.DISCONNECTED);
    private final AtomicBoolean m_running = new AtomicBoolean(false);
    private final AtomicBoolean m_actionPollInFlight = new AtomicBoolean(false);

    final OpButtonTelemetry m_telemetry;
    private final OpButtonHostState m_hostState;
    private final OpButtonPollDelivery m_delivery;
    private final OpButtonDeviceIdentity m_deviceIdentity;

    private final CopyOnWriteArrayList<OpButtonActionListener> m_actionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OpButtonSessionListener> m_sessionListeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService m_executor;
    private ScheduledFuture<?> m_actionPollFuture;
    private ScheduledFuture<?> m_metricSampleFuture;

    private final OpButtonUI m_ui = new OpButtonUI();
    private final OpButtonMetrics m_metrics = new OpButtonMetrics();
    private final OpButtonFpsTracker m_fpsTracker = new OpButtonFpsTracker();
    private final OpButtonLiveManager m_liveManager;
    private final OpButtonActionRouter m_actions;
    private final Handler m_mainHandler = new Handler(Looper.getMainLooper());
    private volatile long m_currentActionPollIntervalMs;

    OpButtonRuntime(@NonNull OpButtonConfig config, @Nullable Context context) {
        m_config = config;
        m_deviceIdentity = OpButtonDeviceIdentity.forContext(context);
        m_httpClient = new OpButtonHttpClient(config.baseUrl, config.apiToken);
        m_telemetry = new OpButtonTelemetry(config.logConfig, m_httpClient, m_metrics);
        m_hostState = new OpButtonHostState(config.sessionAttributes, m_ui, m_mainHandler);
        m_delivery = new OpButtonPollDelivery(m_httpClient, m_hostState, m_telemetry, this::isCurrentSession, this::notifyError);
        m_liveManager = new OpButtonLiveManager(context, m_httpClient, this::updateLiveMode);
        OpButtonLiveCommands liveCommands = new OpButtonLiveCommands(m_liveManager,
            this::executeBackground, this::isCurrentSession, this::acknowledgeForSession);
        m_actions = new OpButtonActionRouter(liveCommands.commands(), m_ui::dispatchAction,
            () -> new ArrayList<>(m_actionListeners));
        m_currentActionPollIntervalMs = config.actionPollIntervalMs;
        OpButtonLog.setLevel(config.logConfig.getLogLevel());
        OpButtonLog.setLoggerListener(config.loggerListener);

        OpButtonMetricConfigBuilder mc = config.metricConfig;
        if (mc.isEnabled()) {
            m_metrics.setSampleIntervalMs(mc.getSampleIntervalMs());
            m_metrics.setBufferSize(mc.getBufferSize());
            m_metrics.setBatchSize(mc.getBatchSize());
        }
        if (config.sessionListener != null) {
            m_sessionListeners.add(config.sessionListener);
        }
        if (config.actionListener != null) {
            m_actionListeners.add(config.actionListener);
        }

        // Register built-in metric collector and user collectors
        if (mc.isEnabled()) {
            m_metrics.addCollector(new OpButtonDefaultMetricCollector(m_fpsTracker, context));
            for (OpButtonMetricCollector collector : mc.getCollectors()) {
                m_metrics.addCollector(collector);
            }
        }

    }

    void logConfiguration() {
        if (m_config.metricConfig.isEnabled())
            OpButtonLog.i("Built-in metrics enabled (sample interval: %dms)", m_config.metricConfig.getSampleIntervalMs());
    }

    boolean isLoggingEnabled() { return m_config.logConfig.isEnabled(); }
    OpButtonSessionStatus getStatus() { return m_status.get(); }
    OpButtonUI getUI() { return m_ui; }
    OpButtonMetrics getMetrics() { return m_metrics; }
    void addActionListener(OpButtonActionListener listener) { m_actionListeners.add(listener); }
    void removeActionListener(OpButtonActionListener listener) { m_actionListeners.remove(listener); }
    void addSessionListener(OpButtonSessionListener listener) { m_sessionListeners.add(listener); }
    void removeSessionListener(OpButtonSessionListener listener) { m_sessionListeners.remove(listener); }

    void startConnection() {
        if (m_running.compareAndSet(false, true) == false) {
            OpButtonLog.w("Already connecting/connected");
            return;
        }

        m_executor = Executors.newScheduledThreadPool(3);
        m_executor.execute(this::connectAndWaitApproval);
    }

    void stopConnection() {
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
                    m_telemetry.flushLogs(token);
                    m_telemetry.flushMetrics(token);
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

    void doShutdown() {
        OpButtonLog.i("Shutting down OpButton SDK");
        m_fpsTracker.stop();
        stopConnection();
        m_liveManager.shutdown();
        m_httpClient.shutdown();
        m_actionListeners.clear();
        m_sessionListeners.clear();
        m_telemetry.clear();
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

        String deviceId = m_deviceIdentity.resolve(m_config.deviceId);
        String deviceName = m_config.deviceName;

        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
        }

        Map<String, Object> attributes = m_hostState.resolveAllSessionAttributes();
        OpButtonConnectResponse resp = m_httpClient.connect(deviceId, deviceName, attributes);
        m_hostState.acknowledgeAll(attributes);
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
            JSONObject snapshot = m_hostState.buildUISnapshotOnMainThread();
            if (snapshot != null) {
                try {
                    m_httpClient.submitPanel(sessionToken, snapshot);
                    m_ui.markSent(snapshot.optInt("revision"));
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
        if (!isCurrentSession(sessionToken) || !m_actionPollInFlight.compareAndSet(false, true)) return;
        try {
            JSONObject response = m_delivery.exchange(sessionToken);
            processPollResponse(sessionToken, response);
        } catch (OpButtonException error) {
            if (!isCurrentSession(sessionToken)) return;
            if (error.isNetworkError()) {
                OpButtonLog.w("Network error during action poll, will retry");
            } else {
                OpButtonLog.w("Action poll failed with server error, attempting reconnect");
                attemptReconnect(sessionToken);
            }
        } finally {
            m_actionPollInFlight.set(false);
        }
    }

    private boolean isCurrentSession(String token) {
        return m_running.get() && token.equals(m_sessionToken.get());
    }

    private void processPollResponse(String token, JSONObject response) {
        if (!isCurrentSession(token)) return;
        JSONArray actions = response.optJSONArray("actions");
        if (actions == null) return;
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.optJSONObject(i);
            if (action != null) dispatchActionOnMainThread(OpButtonAction.fromJson(action), token);
        }
    }

    /**
     * Dispatch an action callback on the Main Thread so that any UI mutations
     * triggered by the callback stay on the same thread as other UI operations.
     */
    private void dispatchActionOnMainThread(@NonNull OpButtonAction action, String token) {
        m_mainHandler.post(() -> {
            if (isCurrentSession(token)) m_actions.dispatch(action, token);
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

    private void attemptReconnect(@NonNull String token) {
        cancelActionPolling();
        setStatus(OpButtonSessionStatus.CONNECTING);

        long retryDelay = 2000;
        final long maxDelay = 30000;

        while (isCurrentSession(token)) {
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (!isCurrentSession(token)) return;

            try {
                JSONObject response = m_delivery.probe(token);
                if (!isCurrentSession(token)) return;
                processPollResponse(token, response);

                OpButtonLog.i("Reconnect successful, session still active");
                setStatus(OpButtonSessionStatus.ACTIVE);
                scheduleActionPolling(token, m_currentActionPollIntervalMs);
                return;

            } catch (OpButtonException e) {
                if (!isCurrentSession(token)) return;
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
        cancelScheduledTasks();
        shutdownExecutor();
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

    private void cancelActionPolling() {
        if (m_actionPollFuture != null) {
            m_actionPollFuture.cancel(false);
            m_actionPollFuture = null;
        }
    }

    private void cancelScheduledTasks() {
        cancelActionPolling();
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

    void acknowledgeAction(String actionId, JSONObject payload) {
        String token = m_sessionToken.get();
        if (token != null) acknowledgeForSession(token, actionId, payload);
    }

    private void acknowledgeForSession(String token, String actionId, JSONObject payload) {
        executeBackground(() -> {
            if (!isCurrentSession(token)) return;
            try { m_httpClient.acknowledgeAction(token, actionId, payload); }
            catch (OpButtonException error) { OpButtonLog.e("Failed to acknowledge action", error); }
        });
    }

    private void executeBackground(Runnable operation) {
        ScheduledExecutorService executor = m_executor;
        if (executor == null || executor.isShutdown()) return;
        try { executor.execute(operation); }
        catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Shutdown may race with scheduling; the closed session owns no more work.
        }
    }
}
