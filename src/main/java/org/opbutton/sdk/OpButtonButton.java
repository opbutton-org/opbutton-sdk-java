package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Button widget. Triggers a "click" action when pressed on the dashboard.
 *
 * <pre>{@code
 * layout.addButton("Restart")
 *     .variant("contained").color("error")
 *     .onClick(action -> restartGame());
 * }</pre>
 */
public final class OpButtonButton extends OpButtonWidget<OpButtonButton> {
    OpButtonButton(@NonNull OpButtonUI ui, @NonNull String label) {
        super(ui, "button");
        put("label", label);
    }

    @NonNull
    public OpButtonButton variant(@NonNull String variant) {
        put("variant", variant);
        return this;
    }

    @NonNull
    public OpButtonButton color(@NonNull String color) {
        put("color", color);
        return this;
    }

    @NonNull
    public OpButtonButton disabled(boolean disabled) {
        put("disabled", disabled);
        return this;
    }

    @NonNull
    public OpButtonButton onClick(@Nullable OpButtonWidgetCallback callback) {
        m_ui.registerCallback(m_internalId, callback);
        return this;
    }
}
