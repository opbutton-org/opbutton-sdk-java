package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Label widget. Displays text with an optional color indicator.
 *
 * <p>Use {@link #textProvider} for automatic updates:</p>
 * <pre>{@code
 * root.addLabel("Idle")
 *     .color("info")
 *     .textProvider(() -> game.getStatus());
 * }</pre>
 */
public final class OpButtonLabel extends OpButtonWidget<OpButtonLabel> {
    OpButtonLabel(@NonNull OpButtonUI ui, @NonNull String text) {
        super(ui, "label");
        put("text", text);
    }

    @NonNull
    public OpButtonLabel text(@NonNull String text) {
        put("text", text);
        return this;
    }

    @NonNull
    public OpButtonLabel color(@NonNull String color) {
        put("color", color);
        return this;
    }

    /**
     * Set a provider that returns the current text on each poll cycle.
     * The SDK will compare with the cached value and only send an update if changed.
     */
    @NonNull
    public OpButtonLabel textProvider(@Nullable OpButtonValueProvider provider) {
        setProvider("text", provider);
        return this;
    }
}
