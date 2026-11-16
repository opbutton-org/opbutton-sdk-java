package org.opbutton.sdk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class OpButtonJsonElement extends OpButtonElement<OpButtonJsonElement> {
    private final JSONObject m_json = new JSONObject();
    private final String m_identifier;
    private final List<OpButtonJsonElement> m_children = new ArrayList<>();

    OpButtonJsonElement(OpButtonUI ui, JSONObject description, int depth) throws JSONException {
        super(ui);

        if (depth > 32) {
            throw new IllegalArgumentException("OpButton JSON nesting exceeds 32");
        }

        if (description.getString("type").isEmpty()) {
            throw new IllegalArgumentException("OpButton JSON element requires a type");
        }

        Iterator<String> keys = description.keys();

        while (keys.hasNext()) {
            String key = keys.next();

            if ("children".equals(key)) {
                continue;
            }

            m_json.put(key, description.get(key));
        }

        Object identifier = description.opt("id");
        m_identifier = identifier instanceof String ? (String)identifier : null;

        if (description.has("children")) {
            JSONArray children = description.getJSONArray("children");
            m_json.put("children", new JSONArray());

            for (int index = 0; index != children.length(); ++index) {
                m_children.add(new OpButtonJsonElement(ui, children.getJSONObject(index), depth + 1));
            }
        }
    }

    void attach(OpButtonJsonCallback callback) {
        if (m_identifier != null && m_identifier.isEmpty() == false && callback != null) {
            m_ui.registerCallback(m_internalId, action -> callback.onAction(m_identifier, action));
        }

        for (OpButtonJsonElement child : m_children) {
            child.attach(callback);
        }
    }

    @Override
    void detach() {
        for (OpButtonJsonElement child : m_children) {
            child.detach();
        }

        super.detach();
    }

    JSONObject toJson() {
        try {
            JSONObject json = new JSONObject(m_json.toString());

            if (m_json.has("children")) {
                JSONArray children = new JSONArray();

                for (OpButtonJsonElement child : m_children) {
                    children.put(child.toJson());
                }

                json.put("children", children);
            }

            return decorate(json);
        } catch (JSONException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
