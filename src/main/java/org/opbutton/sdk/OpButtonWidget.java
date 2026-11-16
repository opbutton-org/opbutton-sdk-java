package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * Base class for all OpButton UI widgets.
 * Stores properties as a JSONObject for direct serialization.
 */
public class OpButtonWidget<T extends OpButtonWidget<T>> extends OpButtonElement<T> {
    protected String m_publicId;
    protected final String m_type;
    protected final JSONObject m_json;

    private String m_providerKey;
    private OpButtonValueProvider m_provider;
    private String m_cachedValue;

    OpButtonWidget(@NonNull OpButtonUI ui, @NonNull String type) {
        super(ui);
        m_publicId = type + "-" + m_internalId;
        m_type = type;
        m_json = new JSONObject();
        try {
            m_json.put("type", type);
            m_json.put("id", m_internalId);
        } catch (JSONException ignored) {
        }
    }

    @NonNull
    public String getId() {
        return m_publicId;
    }

    public int getInternalId() {
        return m_internalId;
    }

    @NonNull
    public String getType() {
        return m_type;
    }

    protected void put(@NonNull String key, @Nullable Object value) {
        try {
            m_json.put(key, value);
            m_ui.incrementRevision();
        } catch (JSONException ignored) {
        }
    }

    /**
     * Set a public ID for finding/accessing the widget programmatically.
     * Does not affect internal communication with the dashboard.
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public T setId(@NonNull String id) {
        m_publicId = id;
        return (T) this;
    }

    /**
     * Set a value provider for a JSON property.
     * On each poll cycle the provider is called; if the value changed, the widget
     * updates and marks dirty. If unchanged — nothing happens.
     */
    protected void setProvider(@NonNull String key, @Nullable OpButtonValueProvider provider) {
        m_providerKey = key;
        m_provider = provider;
        if (provider != null) {
            m_ui.registerProvider(this);
        } else {
            m_ui.unregisterProvider(this);
        }
    }

    /**
     * Called by OpButtonUI on the poll thread.
     * @return true if the value changed
     */
    boolean pollProvider() {
        if (m_provider == null) return false;
        try {
            String newValue = String.valueOf(m_provider.getValue());
            if (!Objects.equals(newValue, m_cachedValue)) {
                m_cachedValue = newValue;
                try {
                    m_json.put(m_providerKey, newValue);
                } catch (JSONException ignored) {
                }
                return true;
            }
        } catch (Exception e) {
            OpButtonLog.e("Value provider failed for widget " + m_internalId, e);
        }
        return false;
    }

    @Override void detach() { m_ui.unregisterProvider(this); super.detach(); }

    @NonNull
    JSONObject toJson() {
        return decorate(m_json);
    }
}
