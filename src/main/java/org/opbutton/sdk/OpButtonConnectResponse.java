package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Response from connect / poll-status endpoints.
 */
public final class OpButtonConnectResponse {
    private final String m_requestId;
    private final String m_status;
    private final String m_sessionToken;
    private final String m_deviceSecret;

    OpButtonConnectResponse(@NonNull String requestId, @NonNull String status,
                            @Nullable String sessionToken, @Nullable String deviceSecret) {
        m_requestId = requestId;
        m_status = status;
        m_sessionToken = sessionToken;
        m_deviceSecret = deviceSecret;
    }

    @NonNull
    public String getRequestId() {
        return m_requestId;
    }

    @NonNull
    public String getStatus() {
        return m_status;
    }

    @Nullable
    public String getSessionToken() {
        return m_sessionToken;
    }

    @Nullable public String getDeviceSecret() { return m_deviceSecret; }

    public boolean isPending() {
        return "pending".equals(m_status);
    }

    public boolean isApproved() {
        return "approved".equals(m_status);
    }

    public boolean isRejected() {
        return "rejected".equals(m_status);
    }

    static OpButtonConnectResponse fromJson(@NonNull JSONObject json) {
        String token = json.isNull("session_token") ? null : json.optString("session_token", null);
        String deviceSecret = json.isNull("device_secret") ? null : json.optString("device_secret", null);
        return new OpButtonConnectResponse(
                json.optString("request_id", ""),
                json.optString("status", ""),
                token,
                deviceSecret
        );
    }
}
