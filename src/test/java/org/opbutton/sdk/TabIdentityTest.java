package org.opbutton.sdk;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class TabIdentityTest {
    @Test
    public void namesDoNotChangeIdentityOrCallbacks() throws Exception {
        OpButtonUI ui = new OpButtonUI();
        OpButtonTab tab = ui.getOrCreateTab("controls");
        assertEquals("controls", tab.getId());
        assertEquals("controls", tab.getTitle());
        int[] calls = {0};
        OpButtonButton button = tab.vertical().addButton("Run");
        button.onClick(action -> ++calls[0]);
        OpButtonAction action = OpButtonAction.fromJson(new JSONObject().put("widget_id", button.getInternalId()));
        int revision = ui.getRevision();

        tab.setName("Game controls");
        assertTrue(ui.getRevision() > revision);
        revision = ui.getRevision();
        assertSame(tab, ui.getOrCreateTab("controls"));
        assertEquals(revision, ui.getRevision());
        assertEquals("Game controls", tab.toJson().getString("title"));
        assertTrue(ui.dispatchAction(action));
        assertEquals(1, calls[0]);

        ui.getOrCreateTab("other").setName("Game controls");
        assertEquals(2, ui.toJson().getJSONArray("tabs").length());
        ui.addTab("controls");
        assertEquals(2, ui.toJson().getJSONArray("tabs").length());
        assertFalse(ui.dispatchAction(action));
        ui.removeTab("controls");
        assertNull(ui.getTab("controls"));
        assertNotNull(ui.getTab("other"));
    }
}
