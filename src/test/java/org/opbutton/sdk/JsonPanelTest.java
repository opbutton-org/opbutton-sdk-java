package org.opbutton.sdk;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonPanelTest {
    @Test
    public void importsJSONAndPreservesNativeBuilders() throws Exception {
        OpButtonUI ui = new OpButtonUI();
        OpButtonTab tab = ui.addTab("JSON").setId("json");
        JSONObject description = new JSONObject("{\"type\":\"layout\",\"direction\":\"grid\",\"gap\":8,\"gridColumns\":3,\"future\":{\"enabled\":true},\"children\":[{\"type\":\"slider\",\"id\":\"speed\",\"label\":\"Speed\",\"min\":0.5,\"max\":3,\"step\":0.1}]}");
        String[] received = {null};
        OpButtonLayout layout = tab.setJson(description, (identifier, action) -> received[0] = identifier);
        layout.padding(4).addButton("Native");
        JSONObject json = layout.toJson();
        assertEquals(8, json.getInt("gap"));
        assertEquals(4.0, json.getDouble("padding"), 0.0);
        assertTrue(json.getJSONObject("future").getBoolean("enabled"));
        assertEquals(2, json.getJSONArray("children").length());
        int id = json.getJSONArray("children").getJSONObject(0).getInt("id");
        OpButtonAction action = OpButtonAction.fromJson(new JSONObject().put("widget_id", id));
        assertTrue(ui.dispatchAction(action));
        assertEquals("speed", received[0]);
        assertThrows(IllegalArgumentException.class, () -> tab.setJson(new JSONObject("{\"type\":\"layout\",\"direction\":\"vertical\",\"children\":[42]}"), null));
        assertTrue(ui.dispatchAction(action));
        tab.setJson(description, (identifier, next) -> ui.removeTab("json"));
        assertFalse(ui.dispatchAction(action));
        int nextId = tab.getLayout().toJson().getJSONArray("children").getJSONObject(0).getInt("id");
        assertTrue(ui.dispatchAction(OpButtonAction.fromJson(new JSONObject().put("widget_id", nextId))));
        assertFalse(ui.dispatchAction(OpButtonAction.fromJson(new JSONObject().put("widget_id", nextId))));
    }
}
