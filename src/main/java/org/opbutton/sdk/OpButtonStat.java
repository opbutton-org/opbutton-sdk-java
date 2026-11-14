package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stat widget. Displays a numeric value with a label and unit.
 *
 * <p>Use {@link #valueProvider} for automatic updates:</p>
 * <pre>{@code
 * root.addStat("FPS")
 *     .unit("fps")
 *     .valueProvider(() -> String.valueOf(game.getFps()));
 * }</pre>
 */
public final class OpButtonStat extends OpButtonWidget<OpButtonStat> {
    OpButtonStat(@NonNull OpButtonUI ui, @NonNull String label) {
        super(ui, "stat");
        put("label", label);
    }

    @NonNull
    public OpButtonStat value(@NonNull String value) {
        put("value", value);
        return this;
    }

    @NonNull
    public OpButtonStat unit(@NonNull String unit) {
        put("unit", unit);
        return this;
    }

    /**
     * Set a provider that returns the current value on each poll cycle.
     * The SDK will compare with the cached value and only send an update if changed.
     */
    @NonNull
    public OpButtonStat valueProvider(@Nullable OpButtonValueProvider provider) {
        setProvider("value", provider);
        return this;
    }
}
