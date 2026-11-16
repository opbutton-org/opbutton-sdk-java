package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A tab in the OpButton dashboard UI.
 * Each service/module adds its own tab via {@link OpButtonUI#addTab(String)}.
 * Call {@link #vertical()} or {@link #horizontal()} to set the root layout direction.
 */
public final class OpButtonTab {
    private final OpButtonUI m_ui;
    private final int m_internalId;
    private String m_publicId;
    private final String m_title;
    private OpButtonLayout m_layout;

    OpButtonTab(@NonNull OpButtonUI ui, @NonNull String title) {
        m_ui = ui;
        m_internalId = ui.nextId();
        m_publicId = "tab-" + m_internalId;
        m_title = title;
    }

    @NonNull
    public String getId() {
        return m_publicId;
    }

    @NonNull
    public OpButtonTab setId(@NonNull String id) {
        m_publicId = id;
        return this;
    }

    public int getInternalId() {
        return m_internalId;
    }

    @NonNull
    public String getTitle() {
        return m_title;
    }

    /**
     * Get or create the root layout.
     */
    @Nullable
    public OpButtonLayout getLayout() {
        return m_layout;
    }

    /**
     * Set the root layout to vertical and return it for building.
     */
    @NonNull
    public OpButtonLayout vertical() {
        if (m_layout != null) m_layout.detach();
        m_ui.incrementRevision();
        m_layout = new OpButtonLayout(m_ui, OpButtonLayout.Direction.VERTICAL);
        return m_layout;
    }

    /**
     * Set the root layout to horizontal and return it for building.
     */
    @NonNull
    public OpButtonLayout horizontal() {
        if (m_layout != null) m_layout.detach();
        m_ui.incrementRevision();
        m_layout = new OpButtonLayout(m_ui, OpButtonLayout.Direction.HORIZONTAL);
        return m_layout;
    }

    @NonNull
    public OpButtonLayout grid() {
        if (m_layout != null) m_layout.detach();
        m_ui.incrementRevision();
        m_layout = new OpButtonLayout(m_ui, OpButtonLayout.Direction.GRID);
        return m_layout;
    }

    @NonNull
    public OpButtonLayout setJson(@NonNull JSONObject description) {
        return setJson(description, null);
    }

    @NonNull
    public OpButtonLayout setJson(@NonNull JSONObject description, @Nullable OpButtonJsonCallback callback) {
        final OpButtonLayout layout;

        try {
            layout = OpButtonLayout.fromJson(m_ui, description, callback);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid OpButton JSON layout", exception);
        }

        if (m_layout != null) {
            m_layout.detach();
        }

        m_layout = layout;
        m_ui.incrementRevision();
        return layout;
    }

    void detach() { if (m_layout != null) m_layout.detach(); }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", m_internalId);
            json.put("title", m_title);
            if (m_layout != null) {
                json.put("layout", m_layout.toJson());
            }
        } catch (JSONException ignored) {
        }
        return json;
    }
}
