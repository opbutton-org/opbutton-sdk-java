package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * An action dispatched from the OpButton dashboard (button click, switch toggle, etc.).
 */
public final class OpButtonAction {
    private final String m_id;
    private final String m_sessionId;
    private final int m_widgetId;
    private final OpButtonActionType m_actionType;
    private final OpButtonActionStatus m_status;
    private final JSONObject m_payload;

    OpButtonAction(@NonNull String id, @NonNull String sessionId, int widgetId,
                @NonNull OpButtonActionType actionType, @NonNull OpButtonActionStatus status,
                @Nullable JSONObject payload) {
        m_id = id;
        m_sessionId = sessionId;
        m_widgetId = widgetId;
        m_actionType = actionType;
        m_status = status;
        m_payload = payload;
    }

    /** Unique action ID (UUID). */
    @NonNull
    public String getId() {
        return m_id;
    }

    /** Session this action belongs to. */
    @NonNull
    public String getSessionId() {
        return m_sessionId;
    }

    /** Widget that triggered the action. */
    public int getWidgetId() {
        return m_widgetId;
    }

    /** Type of action: CLICK, CHANGE, TOGGLE, etc. */
    @NonNull
    public OpButtonActionType getActionType() {
        return m_actionType;
    }

    /** Status: PENDING, DELIVERED, ACKNOWLEDGED. */
    @NonNull
    public OpButtonActionStatus getStatus() {
        return m_status;
    }

    /** Arbitrary payload JSON from the dashboard action. */
    @Nullable
    public JSONObject getPayload() {
        return m_payload;
    }

    static OpButtonAction fromJson(@NonNull JSONObject json) {
        return new OpButtonAction(
                json.optString("id", ""),
                json.optString("session_id", ""),
                json.optInt("widget_id", 0),
                OpButtonActionType.fromValue(json.optString("action_type", "")),
                OpButtonActionStatus.fromValue(json.optString("status", "")),
                json.optJSONObject("payload")
        );
    }
}
