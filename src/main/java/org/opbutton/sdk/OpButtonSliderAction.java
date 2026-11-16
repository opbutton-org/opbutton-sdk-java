package org.opbutton.sdk;

import androidx.annotation.NonNull;

/**
 * Typed action for {@link OpButtonSlider} change events.
 * Provides a strongly-typed {@link #getValue()} instead of raw payload access.
 */
public final class OpButtonSliderAction {
    private final OpButtonAction m_action;
    private final double m_value;

    OpButtonSliderAction(@NonNull OpButtonAction action) {
        m_action = action;
        m_value = action.getPayload() != null ? action.getPayload().optDouble("value", 0.0) : 0.0;
    }

    @NonNull
    public OpButtonAction getAction() {
        return m_action;
    }

    public double getValue() {
        return m_value;
    }
}
