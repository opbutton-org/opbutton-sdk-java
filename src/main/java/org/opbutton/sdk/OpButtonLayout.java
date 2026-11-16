package org.opbutton.sdk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Retained row, column or adaptive grid. Every node receives an SDK-generated ID. */
public final class OpButtonLayout extends OpButtonElement<OpButtonLayout> {
    public enum Direction {
        VERTICAL("vertical"), HORIZONTAL("horizontal"), GRID("grid");
        private final String value;
        Direction(String value) { this.value = value; }
        String getValue() { return value; }
    }
    private final Direction m_direction;
    private final List<Object> m_children = new ArrayList<>();
    private final JSONObject m_settings = new JSONObject();

    OpButtonLayout(OpButtonUI ui, Direction direction) { super(ui); m_direction = direction; }
    public Direction getDirection() { return m_direction; }
    private OpButtonLayout option(String key, Object value) {
        try { m_settings.put(key, value); }
        catch (JSONException exception) { throw new IllegalArgumentException(exception); }
        m_ui.incrementRevision();
        return this;
    }
    public OpButtonLayout gap(double pixels) { return option("gap", size(pixels, false)); }
    public OpButtonLayout padding(double pixels) { return option("padding", size(pixels, false)); }
    public OpButtonLayout align(String value) { return option("align", alignment(value)); }
    public OpButtonLayout justify(String value) {
        if (!"space-between".equals(value)) alignment(value);
        if ("stretch".equals(value)) throw new IllegalArgumentException("Invalid justification");
        return option("justify", value);
    }
    public OpButtonLayout wrap(boolean value) { return option("wrap", value); }
    public OpButtonLayout density(String value) {
        if (!"compact".equals(value) && !"comfortable".equals(value)) throw new IllegalArgumentException("Invalid density");
        return option("density", value);
    }
    public OpButtonLayout columns(int value) {
        if (value < 1 || value > 12) throw new IllegalArgumentException("Invalid column count");
        return option("gridColumns", value);
    }
    public OpButtonLayout minCellWidth(double pixels) { return option("minCellWidth", size(pixels, true)); }
    public OpButtonLayout title(String value) { return option("title", value); }
    private OpButtonLayout addLayout(Direction direction) {
        OpButtonLayout child = new OpButtonLayout(m_ui, direction);
        m_children.add(child);
        m_ui.incrementRevision();
        return child;
    }
    public OpButtonLayout addVertical() { return addLayout(Direction.VERTICAL); }
    public OpButtonLayout addHorizontal() { return addLayout(Direction.HORIZONTAL); }
    public OpButtonLayout addGrid() { return addLayout(Direction.GRID); }
    public OpButtonLayout addGroup(String title) { return addVertical().title(title); }
    public OpButtonLayout addCollapsible(String title) {
        CollapsibleElement child = new CollapsibleElement(m_ui, title);
        m_children.add(child);
        m_ui.incrementRevision();
        return child.content;
    }
    public OpButtonLayout addPadding(double weight) {
        m_children.add(new PaddingElement(m_ui, weight));
        m_ui.incrementRevision();
        return this;
    }
    public OpButtonButton addButton(String label) {
        OpButtonButton widget = new OpButtonButton(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonLabel addLabel(String label) {
        OpButtonLabel widget = new OpButtonLabel(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonStat addStat(String label) {
        OpButtonStat widget = new OpButtonStat(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonSwitch addSwitch(String label) {
        OpButtonSwitch widget = new OpButtonSwitch(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonInput addInput(String label) {
        OpButtonInput widget = new OpButtonInput(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonSelect addSelect(String label) {
        OpButtonSelect widget = new OpButtonSelect(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonTextarea addTextarea(String label) {
        OpButtonTextarea widget = new OpButtonTextarea(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonTable addTable(String label) {
        OpButtonTable widget = new OpButtonTable(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonLogs addLogs(String label) {
        OpButtonLogs widget = new OpButtonLogs(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonRadioGroup addRadioGroup(String label) {
        OpButtonRadioGroup widget = new OpButtonRadioGroup(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    public OpButtonSlider addSlider(String label) {
        OpButtonSlider widget = new OpButtonSlider(m_ui, label);
        m_children.add(widget);
        m_ui.incrementRevision();
        return widget;
    }

    static OpButtonLayout fromJson(OpButtonUI ui, JSONObject source, OpButtonJsonCallback callback) throws JSONException {
        JSONObject description = new JSONObject(source.toString());

        if (!"layout".equals(description.getString("type"))) {
            throw new IllegalArgumentException("OpButton JSON root must be a layout");
        }

        Direction direction;

        switch (description.getString("direction")) {
            case "vertical": direction = Direction.VERTICAL; break;
            case "horizontal": direction = Direction.HORIZONTAL; break;
            case "grid": direction = Direction.GRID; break;
            default: throw new IllegalArgumentException("Invalid OpButton JSON layout direction");
        }
        OpButtonLayout layout = new OpButtonLayout(ui, direction);
        java.util.Iterator<String> keys = description.keys();

        while (keys.hasNext()) {
            String key = keys.next();

            if ("children".equals(key) || "id".equals(key) || "placement".equals(key)) {
                continue;
            }

            layout.m_settings.put(key, description.get(key));
        }

        layout.loadPlacement(description);
        JSONArray children = description.has("children") ? description.getJSONArray("children") : new JSONArray();

        for (int index = 0; index != children.length(); ++index) {
            layout.m_children.add(new OpButtonJsonElement(ui, children.getJSONObject(index), 0));
        }

        for (Object child : layout.m_children) {
            ((OpButtonJsonElement)child).attach(callback);
        }

        return layout;
    }

    @Override void detach() {
        for (Object child : m_children) ((OpButtonElement<?>)child).detach();
        super.detach();
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "layout");
            json.put("direction", m_direction.getValue());
            java.util.Iterator<String> keys = m_settings.keys();
            while (keys.hasNext()) { String key = keys.next(); json.put(key, m_settings.get(key)); }
            JSONArray children = new JSONArray();
            for (Object child : m_children) {
                if (child instanceof OpButtonLayout) children.put(((OpButtonLayout)child).toJson());
                else if (child instanceof OpButtonWidget) children.put(((OpButtonWidget<?>)child).toJson());
                else if (child instanceof OpButtonJsonElement) children.put(((OpButtonJsonElement)child).toJson());
                else if (child instanceof PaddingElement) children.put(((PaddingElement)child).toJson());
                else if (child instanceof CollapsibleElement) children.put(((CollapsibleElement)child).toJson());
            }
            json.put("children", children);
        } catch (JSONException exception) { throw new IllegalArgumentException(exception); }
        return decorate(json);
    }
    static final class PaddingElement extends OpButtonElement<PaddingElement> {
        final double weight;
        PaddingElement(OpButtonUI ui, double weight) { super(ui); this.weight = weight; }
        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try { json.put("type", "padding"); json.put("weight", weight); }
            catch (JSONException exception) { throw new IllegalArgumentException(exception); }
            return decorate(json);
        }
    }
    static final class CollapsibleElement extends OpButtonElement<CollapsibleElement> {
        final String title;
        @Override void detach() { content.detach(); super.detach(); }
        final OpButtonLayout content;
        CollapsibleElement(OpButtonUI ui, String title) {
            super(ui); this.title = title; content = new OpButtonLayout(ui, Direction.VERTICAL);
        }
        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("type", "collapsible"); json.put("title", title);
                json.put("children", new JSONArray().put(content.toJson()));
            } catch (JSONException exception) { throw new IllegalArgumentException(exception); }
            return decorate(json);
        }
    }
}
