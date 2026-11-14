package org.opbutton.sdk;

import androidx.annotation.NonNull;

/**
 * Callback for widget actions from the OpButton dashboard (button click, switch toggle, etc.).
 * Registered per-widget via {@link OpButtonButton#onClick}, {@link OpButtonSwitch#onChange}, etc.
 */
@FunctionalInterface
public interface OpButtonWidgetCallback {
    void onOpButtonWidgetAction(@NonNull OpButtonAction action);
}
