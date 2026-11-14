package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

/**
 * Text input widget. Triggers a "change" action with payload {@code {"value": "..."}}.
 */
public final class OpButtonInput extends OpButtonWidget<OpButtonInput> {
    OpButtonInput(@NonNull OpButtonUI ui, @NonNull String label) {
        super(ui, "input");
        put("label", label);
    }

    @NonNull
    public OpButtonInput inputType(@NonNull String type) {
        put("inputType", type);
        return this;
    }

    @NonNull
    public OpButtonInput defaultValue(@NonNull String value) {
        put("defaultValue", value);
        return this;
    }

    @NonNull
    public OpButtonInput placeholder(@NonNull String placeholder) {
        put("placeholder", placeholder);
        return this;
    }

    @NonNull
    public OpButtonInput onSubmit(@Nullable OpButtonWidgetCallback callback) {
        m_ui.registerCallback(m_internalId, callback);
        return this;
    }

    @NonNull
    public OpButtonInput onSubmit(@NonNull Consumer<OpButtonInputAction> callback) {
        m_ui.registerCallback(m_internalId, action -> callback.accept(new OpButtonInputAction(action)));
        return this;
    }
}
