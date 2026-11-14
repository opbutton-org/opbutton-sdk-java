package org.opbutton.sdk;

import androidx.annotation.NonNull;

/**
 * Typed action for {@link OpButtonSwitch} change events.
 * Provides a strongly-typed {@link #getValue()} instead of raw payload access.
 */
public final class OpButtonSwitchAction {
    private final OpButtonAction m_action;
    private final boolean m_value;

    OpButtonSwitchAction(@NonNull OpButtonAction action) {
        m_action = action;
        m_value = action.getPayload() != null && action.getPayload().optBoolean("value", false);
    }

    @NonNull
    public OpButtonAction getAction() {
        return m_action;
    }

    public boolean getValue() {
        return m_value;
    }
}
