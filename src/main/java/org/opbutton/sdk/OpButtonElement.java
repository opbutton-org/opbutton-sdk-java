package org.opbutton.sdk;

import org.json.JSONException;
import org.json.JSONObject;

/** Common sizing for retained widgets and containers. Dimensions are CSS pixels. */
public class OpButtonElement<T extends OpButtonElement<T>> {
    protected final OpButtonUI m_ui;
    protected final int m_internalId;
    private final JSONObject m_placement = new JSONObject();

    OpButtonElement(OpButtonUI ui) {
        m_ui = ui;
        m_internalId = ui.nextId();
    }

    public int getInternalId() { return m_internalId; }

    static double size(double value, boolean positive) {
        if (!Double.isFinite(value) || value > 4096 || (positive ? value <= 0 : value < 0)) {
            throw new IllegalArgumentException("Invalid layout dimension");
        }
        return value;
    }

    static String alignment(String value) {
        if (!"start".equals(value) && !"center".equals(value)
                && !"end".equals(value) && !"stretch".equals(value)) {
            throw new IllegalArgumentException("Invalid layout alignment");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private T place(String key, Object value) {
        try { m_placement.put(key, value); }
        catch (JSONException exception) { throw new IllegalArgumentException(exception); }
        m_ui.incrementRevision();
        return (T) this;
    }

    public T width(double pixels) { return place("width", size(pixels, true)); }
    public T contentWidth() { return place("width", "content"); }
    public T fill() { return fill(1); }
    public T fill(double weight) { place("weight", size(weight, true)); return place("width", "fill"); }
    public T minWidth(double pixels) { return place("minWidth", size(pixels, false)); }
    public T maxWidth(double pixels) { return place("maxWidth", size(pixels, true)); }
    public T alignSelf(String value) { return place("alignSelf", alignment(value)); }

    void loadPlacement(JSONObject description) throws JSONException {
        JSONObject placement = description.optJSONObject("placement");

        if (placement == null) {
            return;
        }

        java.util.Iterator<String> keys = placement.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            m_placement.put(key, placement.get(key));
        }
    }

    void detach() { m_ui.registerCallback(m_internalId, null); }

    protected JSONObject decorate(JSONObject json) {
        try {
            json.put("id", m_internalId);
            if (m_placement.length() > 0) json.put("placement", m_placement);
        } catch (JSONException exception) { throw new IllegalArgumentException(exception); }
        return json;
    }
}
