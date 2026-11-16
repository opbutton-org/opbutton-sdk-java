package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.function.Consumer;

/** Radio group with a single selected string value. */
public final class OpButtonRadioGroup extends OpButtonWidget<OpButtonRadioGroup> {
    OpButtonRadioGroup(@NonNull OpButtonUI ui, @NonNull String label) {
        super(ui, "radio");
        put("label", label);
    }

    /**
     * Set available options. Each element is {@code {value, label}}.
     */
    @NonNull
    public OpButtonRadioGroup options(@NonNull String[][] options) {
        JSONArray arr = new JSONArray();
        for (String[] opt : options) {
            JSONObject o = new JSONObject();
            try {
                o.put("value", opt[0]);
                o.put("label", opt[1]);
            } catch (JSONException ignored) {
            }
            arr.put(o);
        }
        put("options", arr);
        return this;
    }

    @NonNull
    public OpButtonRadioGroup defaultValue(@NonNull String value) {
        put("defaultValue", value);
        return this;
    }

    public OpButtonRadioGroup disabled(boolean value) { put("disabled", value); return this; }
    public OpButtonRadioGroup optionDirection(String value) {
        if (!"horizontal".equals(value) && !"vertical".equals(value)) throw new IllegalArgumentException("Invalid option direction");
        put("optionDirection", value); return this;
    }

    @NonNull
    public OpButtonRadioGroup onChange(@Nullable OpButtonWidgetCallback callback) {
        m_ui.registerCallback(m_internalId, callback);
        return this;
    }

    @NonNull
    public OpButtonRadioGroup onChange(@NonNull Consumer<OpButtonRadioGroupAction> callback) {
        m_ui.registerCallback(m_internalId, action -> callback.accept(new OpButtonRadioGroupAction(action)));
        return this;
    }
}
