package org.opbutton.sdk;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class PanelLayoutTest {
    @Test public void replacingAndRemovingTreesDetachHandlersAndProviders() throws Exception {
        OpButtonUI ui = new OpButtonUI();
        OpButtonTab tab = ui.addTab("Lifecycle");
        OpButtonLayout old = tab.vertical();
        OpButtonSlider slider = old.addSlider("Speed");
        slider.onChange((OpButtonSliderAction action) -> fail("Removed slider dispatched"));
        final int[] samples = {0};
        old.addStat("FPS").valueProvider(() -> { samples[0]++; return "60"; });
        int id = slider.getInternalId();
        ui.markSent(ui.getRevision());
        OpButtonLayout grid = tab.grid();
        assertTrue(ui.hasUnsent());
        assertTrue(grid.getInternalId() > id);
        ui.pollValues();
        assertEquals(0, samples[0]);
        assertFalse(ui.dispatchAction(OpButtonAction.fromJson(new JSONObject().put("widget_id", id))));
        OpButtonSlider next = grid.addSlider("Next");
        next.onChange((OpButtonSliderAction action) -> fail("Removed tab dispatched"));
        ui.addTab("Spacing").vertical().addPadding(0);
        ui.removeTab(tab.getId());
        assertFalse(ui.dispatchAction(OpButtonAction.fromJson(new JSONObject().put("widget_id", next.getInternalId()))));
    }
    private void collect(JSONObject node, Set<Integer> ids) throws Exception {
        assertTrue("Every node has a unique generated ID", ids.add(node.getInt("id")));
        JSONArray children = node.optJSONArray("children");
        if (children != null) for (int i = 0; i < children.length(); i++) collect(children.getJSONObject(i), ids);
    }

    @Test public void retainedLayoutContractAndTypedEvents() throws Exception {
        OpButtonUI ui = new OpButtonUI();
        OpButtonLayout root = ui.addTab("Layout contract").vertical().gap(12).padding(8).density("compact");
        OpButtonLayout row = root.addHorizontal().gap(8).align("end").justify("space-between").wrap(true);
        OpButtonButton button = row.addButton("Apply").width(140);
        row.addSelect("Language").options(new String[][]{{"en", "English"}, {"ru", "Русский"}})
                .defaultValue("en").fill(2).minWidth(160).maxWidth(480);
        OpButtonRadioGroup radio = root.addRadioGroup("Difficulty").options(new String[][]{{"easy", "Easy"}, {"hard", "Hard"}})
                .defaultValue("easy").optionDirection("horizontal");
        OpButtonSlider slider = root.addSlider("Speed").range(0.5, 3, 0.1).defaultValue(1.5).unit("x").fill();
        root.addCollapsible("Advanced").gap(4).density("comfortable").addGroup("Group").addButton("Inside");
        OpButtonLayout grid = root.addGrid().columns(3).minCellWidth(140).gap(8);
        for (String text : new String[]{"A", "B", "C"}) grid.addButton(text);
        int identifier = button.getInternalId();
        int revision = ui.getRevision();
        ui.markSent(revision);
        button.width(160);
        assertTrue(ui.hasUnsent());
        assertEquals(identifier, button.getInternalId());
        JSONObject layout = ui.toJson();
        Set<Integer> ids = new HashSet<>();
        JSONObject tab = layout.getJSONArray("tabs").getJSONObject(0);
        ids.add(tab.getInt("id"));
        collect(tab.getJSONObject("layout"), ids);
        JSONObject collapsed = root.toJson().getJSONArray("children").getJSONObject(3);
        assertEquals(4, collapsed.getJSONArray("children").getJSONObject(0).getDouble("gap"), 0);
        final double[] received = {0};
        slider.onChange((OpButtonSliderAction action) -> received[0] = action.getValue());
        assertTrue(ui.dispatchAction(OpButtonAction.fromJson(new JSONObject().put("widget_id", slider.getInternalId())
                .put("action_type", "change").put("payload", new JSONObject().put("value", 1.7)))));
        assertEquals(1.7, received[0], 0);
        final String[] choice = {""};
        radio.onChange((OpButtonRadioGroupAction action) -> choice[0] = action.getValue());
        ui.dispatchAction(OpButtonAction.fromJson(new JSONObject().put("widget_id", radio.getInternalId())
                .put("action_type", "change").put("payload", new JSONObject().put("value", "hard"))));
        assertEquals("hard", choice[0]);
        String directory = System.getenv("OPBUTTON_LAYOUT_FIXTURE_DIR");
        if (directory != null) Files.write(Paths.get(directory, "java.json"), layout.toString().getBytes(StandardCharsets.UTF_8));
    }
}
