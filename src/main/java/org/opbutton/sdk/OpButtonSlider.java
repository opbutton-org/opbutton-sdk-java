package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import java.util.function.Consumer;

/** Numeric slider. Sends a change action when an edit is committed. */
public final class OpButtonSlider extends OpButtonWidget<OpButtonSlider> {
    OpButtonSlider(@NonNull OpButtonUI ui, @NonNull String label) {
        super(ui, "slider");
        put("label", label);
    }

    public OpButtonSlider range(double min, double max, double step) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step) || !Double.isFinite(max - min) || min >= max || step <= 0 || step > max - min) throw new IllegalArgumentException("Invalid slider range");
        put("min", min); put("max", max); put("step", step); return this;
    }
    public OpButtonSlider defaultValue(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid slider value");
        put("defaultValue", value); return this;
    }
    public OpButtonSlider unit(String value) { put("unit", value); return this; }
    public OpButtonSlider disabled(boolean value) { put("disabled", value); return this; }

    @NonNull
    public OpButtonSlider onChange(@Nullable OpButtonWidgetCallback callback) {
        m_ui.registerCallback(m_internalId, callback);
        return this;
    }

    @NonNull
    public OpButtonSlider onChange(@NonNull Consumer<OpButtonSliderAction> callback) {
        m_ui.registerCallback(m_internalId, action -> callback.accept(new OpButtonSliderAction(action)));
        return this;
    }
}
