package org.opbutton.sdk;

import androidx.annotation.NonNull;

/**
 * Logs display widget. Shows recent log output.
 */
public final class OpButtonLogs extends OpButtonWidget<OpButtonLogs> {
    OpButtonLogs(@NonNull OpButtonUI ui, @NonNull String label) {
        super(ui, "logs");
        put("label", label);
    }

    @NonNull
    public OpButtonLogs maxLines(int maxLines) {
        put("maxLines", maxLines);
        return this;
    }
}
