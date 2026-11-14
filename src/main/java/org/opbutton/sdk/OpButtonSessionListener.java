package org.opbutton.sdk;

import androidx.annotation.NonNull;

/**
 * Listener for session lifecycle events.
 * All methods are called on a background thread.
 */
public interface OpButtonSessionListener {
    /** SDK is connecting to the server. */
    default void onOpButtonSessionConnecting() {}

    /** Connection request sent, waiting for dashboard approval. */
    default void onOpButtonSessionWaitingApproval(@NonNull String requestId) {}

    /** Session approved and active. Ready to send panel/logs and receive actions. */
    default void onOpButtonSessionStarted(@NonNull String sessionToken) {}

    /** Session closed (either by client or server). */
    default void onOpButtonSessionClosed() {}

    /** Connection request was rejected by a dashboard user. */
    default void onOpButtonSessionRejected() {}

    /** Authentication failed (HTTP 401). The API token is invalid or expired. No retry attempts will be made. */
    default void onOpButtonSessionAuthFailed() {}

    /** An error occurred. The session may still be running (transient errors). */
    default void onOpButtonSessionError(@NonNull OpButtonException exception) {}
}
