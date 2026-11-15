package org.opbutton.sdk;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class RuntimeContractsTest {
    private final OpButtonHttpClient http = new OpButtonHttpClient("https://opbutton.invalid", "test");
    private final OpButtonMetrics metrics = new OpButtonMetrics();
    private final OpButtonTelemetry telemetry = new OpButtonTelemetry(new OpButtonLogConfigBuilder(), http, metrics);
    private final State state = new State();
    private final Peer peer = new Peer();
    private boolean current = true;
    private int panelErrors;
    private final OpButtonPollDelivery delivery = new OpButtonPollDelivery(peer, state, telemetry,
        token -> current, error -> panelErrors++);

    @Before public void quietPlatformLogger() { OpButtonLog.setLoggerListener((level, tag, message, error) -> {}); }
    @After public void closeTransport() { http.shutdown(); OpButtonLog.setLoggerListener(null); }

    @Test public void failedPollRestoresTelemetryInOrderAndDoesNotCommitAttributes() throws Exception {
        OpButtonLogEntry first = log("first");
        OpButtonLogEntry later = log("later");
        telemetry.bufferLog(first);
        metrics.record(OpButtonMetricType.CPU_USAGE, 1);
        peer.onPoll = () -> {
            telemetry.bufferLog(later);
            metrics.record(OpButtonMetricType.CPU_USAGE, 2);
            state.values.put("level", 2);
        };
        peer.failPoll = true;
        assertThrows(OpButtonException.class, () -> delivery.exchange("session"));
        assertEquals(0, state.attributesAccepted);
        peer.onPoll = null;
        peer.failPoll = false;
        delivery.exchange("session");
        assertEquals(Arrays.asList(first, later), peer.logs);
        assertEquals(1, peer.samples.get(0).getValue(), 0);
        assertEquals(2, peer.samples.get(1).getValue(), 0);
        assertEquals(2, peer.attributes.get("level"));
        assertTrue(state.journal.snapshot(state.values).isEmpty());
        try (OpButtonTelemetry.Batch empty = telemetry.capture()) { assertTrue(empty.logs.isEmpty()); }
    }

    @Test public void failedPanelDoesNotBlockPollingOrAcknowledgeThePanel() throws Exception {
        state.panel = new JSONObject().put("revision", 7);
        peer.failPanel = true;
        assertSame(peer.response, delivery.exchange("session"));
        assertEquals(0, state.panelsAccepted);
        assertEquals(1, state.attributesAccepted);
        assertEquals(1, peer.polls);
        assertEquals(1, panelErrors);
    }

    @Test public void lateResponseDoesNotAcknowledgeAnotherSession() throws Exception {
        peer.onPoll = () -> current = false;
        delivery.exchange("old-session");
        assertEquals(0, state.attributesAccepted);
        assertFalse(state.journal.snapshot(state.values).isEmpty());
    }

    @Test public void probePreservesTheOutboxAndReturnsTheResponse() throws Exception {
        telemetry.bufferLog(log("pending"));
        assertSame(peer.response, delivery.probe("session"));
        assertEquals(0, state.captures);
        assertNull(peer.attributes);
        assertTrue(peer.logs.isEmpty());
        try (OpButtonTelemetry.Batch pending = telemetry.capture()) { assertEquals(1, pending.logs.size()); }
    }

    @Test public void batchSettlementIsIdempotentAndCommittedDataStaysConsumed() {
        telemetry.bufferLog(log("pending"));
        OpButtonTelemetry.Batch first = telemetry.capture();
        first.close();
        first.close();
        try (OpButtonTelemetry.Batch retry = telemetry.capture()) {
            assertEquals(1, retry.logs.size());
            retry.commit();
            retry.commit();
        }
        try (OpButtonTelemetry.Batch empty = telemetry.capture()) { assertTrue(empty.logs.isEmpty()); }
    }

    @Test public void unavailableHostDoesNotDrainTheOutboxOrCallTheTransport() {
        telemetry.bufferLog(log("pending"));
        state.unavailable = true;
        assertThrows(IllegalStateException.class, () -> delivery.exchange("session"));
        assertEquals(0, peer.polls);
        try (OpButtonTelemetry.Batch pending = telemetry.capture()) { assertEquals(1, pending.logs.size()); }
    }

    @Test public void reservedCommandsAreExclusiveAndKeepTheirCapturedSession() {
        List<String> calls = new ArrayList<>();
        Map<OpButtonActionType, OpButtonActionRouter.Command> commands = new EnumMap<>(OpButtonActionType.class);
        commands.put(OpButtonActionType.LIVE_START, (action, token) -> calls.add(token));
        OpButtonActionRouter router = new OpButtonActionRouter(commands, action -> calls.add("widget"),
            () -> Collections.singletonList(action -> calls.add("listener")));
        commands.clear();
        router.dispatch(action(OpButtonActionType.LIVE_START), "original-session");
        assertEquals(Collections.singletonList("original-session"), calls);
    }

    @Test public void callbackFailuresAndSelfRemovalDoNotSkipOtherObservers() {
        List<String> calls = new ArrayList<>();
        List<OpButtonActionListener> listeners = new ArrayList<>();
        listeners.add(action -> { calls.add("first"); listeners.remove(0); throw new IllegalStateException("listener"); });
        listeners.add(action -> calls.add("second"));
        OpButtonActionRouter router = new OpButtonActionRouter(Collections.emptyMap(), action -> {
            calls.add("widget"); throw new IllegalStateException("widget");
        }, () -> new ArrayList<>(listeners));
        router.dispatch(action(OpButtonActionType.CLICK), "session");
        assertEquals(Arrays.asList("widget", "first", "second"), calls);
    }

    private static OpButtonLogEntry log(String message) {
        return new OpButtonLogEntry(OpButtonLogLevel.INFO, message, null, null, null, null);
    }

    private static OpButtonAction action(OpButtonActionType type) {
        return new OpButtonAction("action", "session", 1, type, OpButtonActionStatus.DELIVERED, null);
    }

    private static final class State implements OpButtonPollDelivery.State {
        final OpButtonAttributeJournal journal = new OpButtonAttributeJournal();
        final Map<String, Object> values = new LinkedHashMap<>();
        JSONObject panel;
        int captures;
        int panelsAccepted;
        int attributesAccepted;
        boolean unavailable;

        State() { values.put("level", 1); }
        @Override public OpButtonPollDelivery.Snapshot capture() {
            if (unavailable) throw new IllegalStateException("Host paused");
            captures++;
            return new OpButtonPollDelivery.Snapshot(journal.snapshot(values), panel);
        }
        @Override public void panelSent(OpButtonPollDelivery.Snapshot snapshot) { panelsAccepted++; }
        @Override public void attributesSent(OpButtonPollDelivery.Snapshot snapshot) {
            attributesAccepted++;
            journal.acknowledge(snapshot.attributes);
        }
    }

    private static final class Peer implements OpButtonPollDelivery.Transport {
        final JSONObject response = new JSONObject();
        Runnable onPoll;
        boolean failPanel;
        boolean failPoll;
        int polls;
        Map<String, Object> attributes;
        List<OpButtonLogEntry> logs;
        List<OpButtonMetricEntry> samples;

        @Override public JSONObject submitPanel(String token, JSONObject panel) throws OpButtonException {
            if (failPanel) throw new OpButtonException(503, "Panel unavailable");
            return response;
        }
        @Override public JSONObject pollActions(String token, Map<String, Object> attributes,
                List<OpButtonLogEntry> logs, List<OpButtonMetricEntry> samples) throws OpButtonException {
            polls++;
            this.attributes = attributes;
            this.logs = new ArrayList<>(logs);
            this.samples = new ArrayList<>(samples);
            if (onPoll != null) onPoll.run();
            if (failPoll) throw new OpButtonException(503, "Poll unavailable");
            return response;
        }
    }
}
