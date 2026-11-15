package org.opbutton.sdk;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tracks acknowledged values. Taking a snapshot never commits a delivery. */
final class OpButtonAttributeJournal {
    private final Map<String, String> acknowledged = new LinkedHashMap<>();

    synchronized Map<String, Object> snapshot(Map<String, Object> values) {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : capture(values).entrySet()) {
            if (!encode(entry.getValue()).equals(acknowledged.get(entry.getKey()))) {
                changed.put(entry.getKey(), entry.getValue());
            }
        }
        return changed;
    }

    static Map<String, Object> capture(Map<String, Object> values) {
        Map<String, Object> captured = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            try {
                String encoded = encode(entry.getValue());
                captured.put(entry.getKey(), new JSONObject(encoded).get("value"));
            } catch (JSONException e) {
                OpButtonLog.e("Invalid session attribute: " + entry.getKey(), e);
            }
        }
        return captured;
    }

    synchronized void acknowledge(Map<String, Object> snapshot) {
        if (snapshot == null) return;
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            acknowledged.put(entry.getKey(), encode(entry.getValue()));
        }
    }

    private static String encode(Object value) {
        return new JSONObject(Collections.singletonMap("value", value != null ? value : JSONObject.NULL)).toString();
    }
}
