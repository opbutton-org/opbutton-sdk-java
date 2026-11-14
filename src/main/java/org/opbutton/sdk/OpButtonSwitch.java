package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

/**
 * Switch (toggle) widget. Triggers a "change" action with payload {@code {"value": true/false}}.
 */
public final class OpButtonSwitch extends OpButtonWidget<OpButtonSwitch> {
    OpButtonSwitch(@NonNull OpButtonUI ui, @NonNull String label) {
        super(ui, "switch");
        put("label", label);
    }

    @NonNull
    public OpButtonSwitch defaultValue(boolean value) {
        put("defaultValue", value);
        return this;
    }

    @NonNull
    public OpButtonSwitch onChange(@Nullable OpButtonWidgetCallback callback) {
        m_ui.registerCallback(m_internalId, callback);
        return this;
    }

    @NonNull
    public OpButtonSwitch onChange(@NonNull Consumer<OpButtonSwitchAction> callback) {
        m_ui.registerCallback(m_internalId, action -> callback.accept(new OpButtonSwitchAction(action)));
        return this;
    }
}
