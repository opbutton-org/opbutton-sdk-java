package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

/**
 * Multi-line text input widget.
 */
public final class OpButtonTextarea extends OpButtonWidget<OpButtonTextarea> {
    OpButtonTextarea(@NonNull OpButtonUI ui, @NonNull String label) {
        super(ui, "textarea");
        put("label", label);
    }

    @NonNull
    public OpButtonTextarea rows(int rows) {
        put("rows", rows);
        return this;
    }

    @NonNull
    public OpButtonTextarea defaultValue(@NonNull String value) {
        put("defaultValue", value);
        return this;
    }

    @NonNull
    public OpButtonTextarea onSubmit(@Nullable OpButtonWidgetCallback callback) {
        m_ui.registerCallback(m_internalId, callback);
        return this;
    }

    @NonNull
    public OpButtonTextarea onSubmit(@NonNull Consumer<OpButtonTextareaAction> callback) {
        m_ui.registerCallback(m_internalId, action -> callback.accept(new OpButtonTextareaAction(action)));
        return this;
    }
}
