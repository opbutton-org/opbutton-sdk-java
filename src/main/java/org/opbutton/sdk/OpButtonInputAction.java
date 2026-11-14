package org.opbutton.sdk;

import androidx.annotation.NonNull;

/**
 * Typed action for {@link OpButtonInput} submit events.
 * Provides a strongly-typed {@link #getValue()} instead of raw payload access.
 */
public final class OpButtonInputAction {
    private final OpButtonAction m_action;
    private final String m_value;

    OpButtonInputAction(@NonNull OpButtonAction action) {
        m_action = action;
        m_value = action.getPayload() != null ? action.getPayload().optString("value", "") : "";
    }

    @NonNull
    public OpButtonAction getAction() {
        return m_action;
    }

    @NonNull
    public String getValue() {
        return m_value;
    }
}
