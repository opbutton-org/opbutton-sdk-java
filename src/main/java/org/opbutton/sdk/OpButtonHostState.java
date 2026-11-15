package org.opbutton.sdk;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Reads host-owned state and acknowledges only the captured values. */
final class OpButtonHostState implements OpButtonPollDelivery.State {
    private final OpButtonSessionAttributeBuilder m_attributes;
    private final OpButtonUI m_ui;
    private final Handler m_mainHandler;
    private final OpButtonAttributeJournal m_attributeJournal = new OpButtonAttributeJournal();

    OpButtonHostState(OpButtonSessionAttributeBuilder attributes, OpButtonUI ui, Handler mainHandler) {
        m_attributes = attributes;
        m_ui = ui;
        m_mainHandler = mainHandler;
    }

    @Override public OpButtonPollDelivery.Snapshot capture() {
        return new OpButtonPollDelivery.Snapshot(resolveChangedSessionAttributes(), snapshotUIOnMainThread());
    }

    @Override public void panelSent(OpButtonPollDelivery.Snapshot snapshot) {
        m_ui.markSent(snapshot.panel.optInt("revision"));
    }

    @Override public void attributesSent(OpButtonPollDelivery.Snapshot snapshot) {
        acknowledgeAll(snapshot.attributes);
    }

    void acknowledgeAll(Map<String, Object> attributes) { m_attributeJournal.acknowledge(attributes); }
    @Nullable
    private JSONObject snapshotUIOnMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            m_ui.pollValues();
            return m_ui.hasUnsent() ? detachedPanelSnapshot() : null;
        }

        FutureTask<JSONObject> task = new FutureTask<>(() -> {
            m_ui.pollValues();
            return m_ui.hasUnsent() ? detachedPanelSnapshot() : null;
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

    @Nullable
    JSONObject buildUISnapshotOnMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return detachedPanelSnapshot();
        }

        FutureTask<JSONObject> task = new FutureTask<>(this::detachedPanelSnapshot);
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

    private Map<String, Object> resolveDynamicSessionAttributes() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, OpButtonValueProvider> entry : m_attributes.getDynamicAttributes().entrySet()) {
            try {
                Object value = entry.getValue().getValue();
                values.put(entry.getKey(), value != null ? value : JSONObject.NULL);
            } catch (Exception e) {
                OpButtonLog.e("Session attribute provider failed: " + entry.getKey(), e);
            }
        }
        return values;
    }

    Map<String, Object> resolveAllSessionAttributes() {
        Map<String, Object> values = new LinkedHashMap<>(m_attributes.getStaticAttributes());
        values.putAll(resolveDynamicSessionAttributes());
        return OpButtonAttributeJournal.capture(values);
    }

    private Map<String, Object> resolveChangedSessionAttributes() {
        return m_attributeJournal.snapshot(resolveDynamicSessionAttributes());
    }

    private JSONObject detachedPanelSnapshot() {
        try {
            return new JSONObject(m_ui.toJson().toString());
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("Invalid panel snapshot", e);
        }
    }
}
