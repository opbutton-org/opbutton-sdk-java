package org.opbutton.sdk;

import org.json.JSONObject;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Composes media commands with a captured session; no command calls the global facade. */
final class OpButtonLiveCommands {
    interface Acknowledge { void send(String token, String actionId, JSONObject payload); }

    private final OpButtonLiveManager live;
    private final Consumer<Runnable> background;
    private final Predicate<String> isCurrent;
    private final Acknowledge acknowledge;

    OpButtonLiveCommands(OpButtonLiveManager live, Consumer<Runnable> background,
            Predicate<String> isCurrent, Acknowledge acknowledge) {
        this.live = live;
        this.background = background;
        this.isCurrent = isCurrent;
        this.acknowledge = acknowledge;
    }

    Map<OpButtonActionType, OpButtonActionRouter.Command> commands() {
        Map<OpButtonActionType, OpButtonActionRouter.Command> commands = new EnumMap<>(OpButtonActionType.class);
        commands.put(OpButtonActionType.LIVE_START, (action, token) -> stream(action, token, live::start));
        commands.put(OpButtonActionType.LIVE_UPDATE, (action, token) -> stream(action, token, live::update));
        commands.put(OpButtonActionType.LIVE_STOP, (action, token) -> acknowledge.send(token, action.getId(), live.stop()));
        commands.put(OpButtonActionType.LIVE_TAP, (action, token) -> acknowledge.send(token, action.getId(), live.tap(action.getPayload())));
        commands.put(OpButtonActionType.LIVE_LONG_PRESS, (action, token) -> acknowledge.send(token, action.getId(), live.longPress(action.getPayload())));
        return commands;
    }

    private void stream(OpButtonAction action, String token, BiFunction<String, JSONObject, JSONObject> operation) {
        background.accept(() -> {
            if (!isCurrent.test(token)) return;
            JSONObject result = operation.apply(token, action.getPayload());
            if (isCurrent.test(token)) acknowledge.send(token, action.getId(), result);
        });
    }
}
