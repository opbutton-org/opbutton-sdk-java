package org.opbutton.sdk;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A layout container for OpButton UI widgets.
 * Supports vertical/horizontal arrangement and nesting, similar to LayoutBox.
 *
 * <pre>{@code
 * OpButtonLayout root = tab.vertical();
 *
 * root.addButton("Click Me").onClick(action -> { ... });
 * root.addStat("FPS").value("60").unit("fps");
 *
 * OpButtonLayout row = root.addHorizontal();
 * row.addButton("A").onClick(action -> doA());
 * row.addPadding(1.0);
 * row.addButton("B").onClick(action -> doB());
 * }</pre>
 */
public final class OpButtonLayout {

    public enum Direction {
        VERTICAL("vertical"),
        HORIZONTAL("horizontal");

        private final String m_value;

        Direction(String value) {
            m_value = value;
        }

        String getValue() {
            return m_value;
        }
    }

    private final OpButtonUI m_ui;
    private final Direction m_direction;
    private final List<Object> m_children = new ArrayList<>();

    OpButtonLayout(@NonNull OpButtonUI ui, @NonNull Direction direction) {
        m_ui = ui;
        m_direction = direction;
    }

    @NonNull
    public Direction getDirection() {
        return m_direction;
    }

    // ── Sub-layouts ──

    @NonNull
    public OpButtonLayout addVertical() {
        OpButtonLayout sub = new OpButtonLayout(m_ui, Direction.VERTICAL);
        m_children.add(sub);
        m_ui.incrementRevision();
        return sub;
    }

    @NonNull
    public OpButtonLayout addHorizontal() {
        OpButtonLayout sub = new OpButtonLayout(m_ui, Direction.HORIZONTAL);
        m_children.add(sub);
        m_ui.incrementRevision();
        return sub;
    }

    /**
     * Add a collapsible section with a title. Children are hidden until expanded.
     * Returns a vertical layout inside the collapsible.
     */
    @NonNull
    public OpButtonLayout addCollapsible(@NonNull String title) {
        CollapsibleElement collapsible = new CollapsibleElement(m_ui, title);
        m_children.add(collapsible);
        m_ui.incrementRevision();
        return collapsible.m_content;
    }

    // ── Padding ──

    @NonNull
    public OpButtonLayout addPadding(double weight) {
        m_children.add(new PaddingElement(weight));
        m_ui.incrementRevision();
        return this;
    }

    // ── Widgets ──

    @NonNull
    public OpButtonButton addButton(@NonNull String label) {
        OpButtonButton w = new OpButtonButton(m_ui, label);
        m_children.add(w);
        m_ui.incrementRevision();
        return w;
    }

    @NonNull
    public OpButtonLabel addLabel(@NonNull String text) {
        OpButtonLabel w = new OpButtonLabel(m_ui, text);
        m_children.add(w);
        m_ui.incrementRevision();
        return w;
    }

    @NonNull
    public OpButtonStat addStat(@NonNull String label) {
        OpButtonStat w = new OpButtonStat(m_ui, label);
        m_children.add(w);
        m_ui.incrementRevision();
        return w;
    }

    @NonNull
    public OpButtonSwitch addSwitch(@NonNull String label) {
        OpButtonSwitch w = new OpButtonSwitch(m_ui, label);
        m_children.add(w);
        m_ui.incrementRevision();
        return w;
    }

    @NonNull
    public OpButtonInput addInput(@NonNull String label) {
        OpButtonInput w = new OpButtonInput(m_ui, label);
        m_children.add(w);
        m_ui.incrementRevision();
        return w;
    }

    @NonNull
    public OpButtonSelect addSelect(@NonNull String label) {
        OpButtonSelect w = new OpButtonSelect(m_ui, label);
        m_children.add(w);
        m_ui.incrementRevision();
        return w;
    }

    @NonNull
    public OpButtonTextarea addTextarea(@NonNull String label) {
        OpButtonTextarea w = new OpButtonTextarea(m_ui, label);
        m_children.add(w);
        m_ui.incrementRevision();
        return w;
    }

    @NonNull
    public OpButtonTable addTable(@NonNull String label) {
        OpButtonTable w = new OpButtonTable(m_ui, label);
        m_children.add(w);
        m_ui.incrementRevision();
        return w;
    }

    @NonNull
    public OpButtonLogs addLogs(@NonNull String label) {
        OpButtonLogs w = new OpButtonLogs(m_ui, label);
        m_children.add(w);
        m_ui.incrementRevision();
        return w;
    }

    // ── Serialization ──

    @NonNull
    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "layout");
            json.put("direction", m_direction.getValue());

            JSONArray childrenArr = new JSONArray();
            for (Object child : m_children) {
                if (child instanceof OpButtonLayout) {
                    childrenArr.put(((OpButtonLayout) child).toJson());
                } else if (child instanceof OpButtonWidget) {
                    childrenArr.put(((OpButtonWidget<?>) child).toJson());
                } else if (child instanceof PaddingElement) {
                    childrenArr.put(((PaddingElement) child).toJson());
                } else if (child instanceof CollapsibleElement) {
                    childrenArr.put(((CollapsibleElement) child).toJson());
                }
            }
            json.put("children", childrenArr);
        } catch (JSONException ignored) {
        }
        return json;
    }

    // ── Padding helper ──

    static final class PaddingElement {
        final double weight;

        PaddingElement(double weight) {
            this.weight = weight;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("type", "padding");
                json.put("weight", weight);
            } catch (JSONException ignored) {
            }
            return json;
        }
    }

    // ── Collapsible helper ──

    static final class CollapsibleElement {
        final String title;
        final OpButtonLayout m_content;

        CollapsibleElement(@NonNull OpButtonUI ui, @NonNull String title) {
            this.title = title;
            this.m_content = new OpButtonLayout(ui, Direction.VERTICAL);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("type", "collapsible");
                json.put("title", title);
                json.put("children", m_content.toJson().optJSONArray("children"));
            } catch (JSONException ignored) {
            }
            return json;
        }
    }
}
