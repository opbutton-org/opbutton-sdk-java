package org.opbutton.sdk;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Routes reserved commands exclusively, then publishes application actions to observers. */
final class OpButtonActionRouter {
    interface Command { void execute(OpButtonAction action, String token); }

    private final Map<OpButtonActionType, Command> commands;
    private final Consumer<OpButtonAction> widgets;
    private final Supplier<List<OpButtonActionListener>> listeners;

    OpButtonActionRouter(Map<OpButtonActionType, Command> commands, Consumer<OpButtonAction> widgets,
            Supplier<List<OpButtonActionListener>> listeners) {
        this.commands = new EnumMap<>(OpButtonActionType.class);
        this.commands.putAll(commands);
        this.widgets = widgets;
        this.listeners = listeners;
    }

    void dispatch(OpButtonAction action, String token) {
        Command command = commands.get(action.getActionType());
        if (command != null) {
            try { command.execute(action, token); }
            catch (Exception error) { OpButtonLog.e("Internal command failed", error); }
            return;
        }
        try { widgets.accept(action); }
        catch (Exception error) { OpButtonLog.e("Widget callback threw exception", error); }
        for (OpButtonActionListener listener : listeners.get()) {
            try { listener.onOpButtonActionReceived(action); }
            catch (Exception error) { OpButtonLog.e("Action listener threw exception", error); }
        }
    }
}
