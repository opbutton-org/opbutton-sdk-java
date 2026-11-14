package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.function.Consumer;

/**
 * Select (dropdown) widget. Triggers a "change" action with payload {@code {"value": "..."}}.
 */
public final class OpButtonSelect extends OpButtonWidget<OpButtonSelect> {
    OpButtonSelect(@NonNull OpButtonUI ui, @NonNull String label) {
        super(ui, "select");
        put("label", label);
    }

    /**
     * Set available options. Each element is {@code {value, label}}.
     */
    @NonNull
    public OpButtonSelect options(@NonNull String[][] options) {
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
    public OpButtonSelect defaultValue(@NonNull String value) {
        put("defaultValue", value);
        return this;
    }

    @NonNull
    public OpButtonSelect onChange(@Nullable OpButtonWidgetCallback callback) {
        m_ui.registerCallback(m_internalId, callback);
        return this;
    }

    @NonNull
    public OpButtonSelect onChange(@NonNull Consumer<OpButtonSelectAction> callback) {
        m_ui.registerCallback(m_internalId, action -> callback.accept(new OpButtonSelectAction(action)));
        return this;
    }
}
