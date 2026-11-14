package org.opbutton.sdk;

import androidx.annotation.NonNull;

/**
 * Listener for actions dispatched from the OpButton dashboard.
 */
public interface OpButtonActionListener {
    /**
     * Called when an action is received from the dashboard (e.g. button click).
     * Called on a background thread.
     *
     * @param action The action to handle
     */
    void onOpButtonActionReceived(@NonNull OpButtonAction action);
}
