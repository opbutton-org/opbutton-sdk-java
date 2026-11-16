# Widgets

OpButton UI is built with tabs, layouts, and widgets. Each service in your app can add its own tab on the dashboard.

## Tabs

```java
OpButtonUI ui = OpButton.getUI();

OpButtonTab tab = ui.addTab("Game Controls").setId("game");

// Retrieve or remove tabs later
OpButtonTab existing = ui.getTab("game");
ui.removeTab("game");
```

## Layouts

Every tab starts with a layout direction:

```java
OpButtonLayout root = tab.vertical();   // top-to-bottom
OpButtonLayout root = tab.horizontal(); // left-to-right
```

Nest layouts for complex arrangements:

```java
OpButtonLayout row = root.addHorizontal();
row.addButton("Left");
row.addPadding(1.0);                 // flexible spacer
row.addButton("Right");
```

### Collapsible sections

```java
OpButtonLayout section = root.addCollapsible("Advanced");
section.addSwitch("Verbose logging");
```

## Widget types

### Button

```java
root.addButton("Restart")
    .variant("contained")   // "contained" | "outlined" | "text"
    .color("error")          // MUI color: "primary" | "error" | "warning" | "info" | "success"
    .disabled(false)
    .onClick(action -> restartGame());
```

### Label

Static or dynamic text:

```java
root.addLabel("Idle")
    .color("info")
    .textProvider(() -> game.getStatus());  // auto-updated
```

### Stat

Numeric value with unit:

```java
root.addStat("FPS")
    .unit("fps")
    .value(60)                              // static
    .valueProvider(() -> game.getFps());    // or dynamic
```

### Switch

Boolean toggle:

```java
root.addSwitch("God mode")
    .defaultValue(false)
    .onChange(action -> {
        game.setGodMode(action.getValue());
    });
```

### Input

Single-line text input:

```java
root.addInput("Command")
    .inputType("text")       // "text" | "number" | "password"
    .placeholder("type a command…")
    .defaultValue("")
    .onSubmit(action -> {
        executeCommand(action.getValue());
    });
```

### Select

Dropdown with options:

```java
root.addSelect("Level")
    .options(new String[][]{
        {"easy",   "Easy"},
        {"normal", "Normal"},
        {"hard",   "Hard"}
    })
    .defaultValue("normal")
    .onChange(action -> {
        game.setDifficulty(action.getValue());
    });
```

### Textarea

Multi-line text area:

```java
root.addTextarea("Notes")
    .rows(5)
    .defaultValue("")
    .onSubmit(action -> {
        saveNotes(action.getValue());
    });
```

### Table

Read-only data table:

```java
root.addTable("Inventory")
    .columns(new String[][]{
        {"name", "Item"},
        {"qty",  "Quantity"}
    })
    .rows(List.of(
        new JSONObject().put("name", "Sword").put("qty", 1),
        new JSONObject().put("name", "Potion").put("qty", 5)
    ));
```

### Logs

Dedicated log viewer widget:

```java
root.addLogs("App Logs")
    .maxLines(500);
```

## Value providers

Widgets that accept a `valueProvider()` or `textProvider()` are dirty-checked on each poll cycle. The dashboard updates only when the value changes.

```java
root.addStat("Score")
    .valueProvider(() -> game.getScore());
```

## Remote actions

All interactive widgets (`Button`, `Switch`, `Input`, `Select`, `Textarea`) fire callbacks when the user interacts from the dashboard. The callback receives a `OpButtonAction`:

```java
action.getId();          // unique action UUID
action.getWidgetId();    // widget that triggered it
action.getActionType();  // CLICK, CHANGE, TOGGLE
action.getPayload();     // JSONObject with action data
```

### Action types

| Type | Widgets | Payload |
|------|---------|---------|
| `CLICK` | Button | — |
| `CHANGE` | Select | `{"value": "..."}` |
| `TOGGLE` | Switch | `{"value": true/false}` |
| `CLICK` | Input, Textarea (submit) | `{"value": "..."}` |

### Global action listener

Receive all actions in one place:

```java
OpButton.addActionListener(action -> {
    Log.d("OpButton", "Action on " + action.getWidgetId());
});
```

Acknowledge actions with an optional result payload:

```java
OpButton.acknowledgeAction(action.getId(), new JSONObject().put("ok", true));
```

---

**See also:** [Android Integration](android-integration.md) · [Logging & Events](logging.md) · [Metrics](metrics.md)

## Retained layout options

Elements receive generated IDs automatically. Layout edits preserve IDs; removing
or replacing a tree detaches its callbacks and providers. Serialize tree edits on
the host UI thread. The SDK ID counter itself is thread-safe.

Rows and columns support gap, padding, align, justify, wrap and inherited density.
Use a grid for equal-width commands: columns is the maximum column count, and
minCellWidth allows automatic reduction on narrow panels. Placement supports
content width, numeric width, fill with a weight, minimum/maximum width and
alignSelf. Dimensions are dashboard CSS pixels. Radio options stay in one group;
slider changes are sent on release/keyboard commit, with a numeric value.

Install the updated backend and dashboard before using these extensions. Protocol
version 2 remains compatible with existing panels. Optional public ID aliases
retain their existing meaning and are unnecessary for creating elements.

```java
OpButtonLayout root = ui.addTab("Game").vertical().gap(12).padding(8);
OpButtonLayout commands = root.addGrid().columns(3).minCellWidth(140).gap(8);
commands.addButton("Restart");
commands.addButton("Clear logs");
OpButtonLayout row = root.addHorizontal().gap(8).align("end");
row.addSelect("Language").options(new String[][]{{"en", "English"}, {"ru", "Русский"}})
    .defaultValue("en").fill().minWidth(160);
row.addButton("Apply").width(120);
root.addRadioGroup("Difficulty").options(new String[][]{{"easy", "Easy"}, {"hard", "Hard"}})
    .defaultValue("easy").optionDirection("horizontal");
root.addSlider("Speed").range(0.5, 3, 0.1).defaultValue(1).unit("x").fill()
    .onChange((OpButtonSliderAction action) -> setSpeed(action.getValue()));
```
