package org.opbutton.sdk;

import androidx.annotation.NonNull;

public enum OpButtonActionStatus {
    PENDING("pending"),
    DELIVERED("delivered"),
    ACKNOWLEDGED("acknowledged"),
    UNKNOWN("");

    private final String m_value;

    OpButtonActionStatus(@NonNull String value) {
        m_value = value;
    }

    @NonNull
    public String getValue() {
        return m_value;
    }

    @NonNull
    static OpButtonActionStatus fromValue(@NonNull String value) {
        for (OpButtonActionStatus status : values()) {
            if (status.m_value.equals(value)) {
                return status;
            }
        }

        return UNKNOWN;
    }
}
