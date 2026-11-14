package org.opbutton.sdk;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder for session attributes — both static values and dynamic providers.
 *
 * <pre>{@code
 * OpButtonSessionAttributeBuilder attrs = new OpButtonSessionAttributeBuilder()
 *     .put("install_id", installId)
 *     .put("session_index", sessionIndex)
 *     .putProvider("acquisition_network", application::getAcquisitionNetwork);
 * }</pre>
 */
public final class OpButtonSessionAttributeBuilder {
    private final Map<String, Object> m_staticAttributes = new LinkedHashMap<>();
    private final Map<String, OpButtonValueProvider> m_dynamicAttributes = new LinkedHashMap<>();

    @NonNull
    public OpButtonSessionAttributeBuilder put(@NonNull String key, @NonNull Object value) {
        m_staticAttributes.put(key, value);
        return this;
    }

    @NonNull
    public OpButtonSessionAttributeBuilder putProvider(@NonNull String key, @NonNull OpButtonValueProvider provider) {
        m_dynamicAttributes.put(key, provider);
        return this;
    }

    @NonNull
    Map<String, Object> getStaticAttributes() {
        return m_staticAttributes;
    }

    @NonNull
    Map<String, OpButtonValueProvider> getDynamicAttributes() {
        return m_dynamicAttributes;
    }
}
