package org.opbutton.sdk;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder for log attributes — both static values and dynamic providers.
 * Static values are fixed at build time; dynamic providers are resolved at each log() call.
 *
 * <pre>{@code
 * OpButtonLogAttributeBuilder logAttrs = new OpButtonLogAttributeBuilder()
 *     .put("app_version", BuildConfig.VERSION_NAME)
 *     .putProvider("screen_type", () -> getScreenType());
 * }</pre>
 */
public final class OpButtonLogAttributeBuilder {
    private final Map<String, Object> m_staticAttributes = new LinkedHashMap<>();
    private final Map<String, OpButtonValueProvider> m_dynamicAttributes = new LinkedHashMap<>();

    @NonNull
    public OpButtonLogAttributeBuilder put(@NonNull String key, @NonNull Object value) {
        m_staticAttributes.put(key, value);
        return this;
    }

    @NonNull
    public OpButtonLogAttributeBuilder putProvider(@NonNull String key, @NonNull OpButtonValueProvider provider) {
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
