package org.opbutton.sdk;

import androidx.annotation.NonNull;

public enum OpButtonActionType {
    CLICK("click"),
    CHANGE("change"),
    TOGGLE("toggle"),
    LIVE_START("live_start"),
    LIVE_UPDATE("live_update"),
    LIVE_STOP("live_stop"),
    LIVE_TAP("live_tap"),
    LIVE_LONG_PRESS("live_long_press"),
    UNKNOWN("");

    private final String m_value;

    OpButtonActionType(@NonNull String value) {
        m_value = value;
    }

    @NonNull
    public String getValue() {
        return m_value;
    }

    @NonNull
    static OpButtonActionType fromValue(@NonNull String value) {
        for (OpButtonActionType type : values()) {
            if (type.m_value.equals(value)) {
                return type;
            }
        }

        return UNKNOWN;
    }
}
