package org.opbutton.sdk;

public enum OpButtonLogLevel {
    DEBUG("debug"),
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
    CRITICAL("critical"),
    EXCEPTION("exception");

    private final String m_value;

    OpButtonLogLevel(String value) {
        m_value = value;
    }

    public String getValue() {
        return m_value;
    }
}
