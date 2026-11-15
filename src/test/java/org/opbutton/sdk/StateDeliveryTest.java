package org.opbutton.sdk;

import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class StateDeliveryTest {
    @Test public void failedDeliveryRemainsPendingAndSuccessfulDeliveryCommitsOnlyItsSnapshot() {
        OpButtonAttributeJournal journal = new OpButtonAttributeJournal();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("level", 1);
        Map<String, Object> first = journal.snapshot(values);
        assertEquals(1, journal.snapshot(values).get("level"));
        values.put("level", 2);
        journal.acknowledge(first);
        assertEquals(2, journal.snapshot(values).get("level"));
        journal.acknowledge(journal.snapshot(values));
        assertTrue(journal.snapshot(values).isEmpty());
    }

    @Test public void mutableValuesAreDetachedAndNullAndTypesArePreserved() throws Exception {
        OpButtonAttributeJournal journal = new OpButtonAttributeJournal();
        JSONObject nested = new JSONObject().put("level", 1);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("state", nested);
        values.put("optional", JSONObject.NULL);
        values.put("enabled", false);
        Map<String, Object> snapshot = journal.snapshot(values);
        nested.put("level", 2);
        journal.acknowledge(snapshot);
        assertEquals(1, ((JSONObject) snapshot.get("state")).getInt("level"));
        assertSame(JSONObject.NULL, snapshot.get("optional"));
        assertTrue(journal.snapshot(values).containsKey("state"));
        values.put("enabled", "false");
        assertEquals("false", journal.snapshot(values).get("enabled"));
    }

    @Test public void acknowledgingAnOlderPanelDoesNotHideNewerEdits() {
        OpButtonUI ui = new OpButtonUI();
        OpButtonButton button = ui.addTab("Controls").vertical().addButton("Run");
        int sent = ui.getRevision();
        button.disabled(true);
        ui.markSent(sent);
        assertTrue(ui.hasUnsent());
        ui.markSent(ui.getRevision());
        ui.markSent(sent);
        assertFalse(ui.hasUnsent());
    }
}
