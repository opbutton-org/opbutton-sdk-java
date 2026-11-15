package org.opbutton.sdk;

import org.json.JSONObject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A poll is a delivery transaction; an unfinished telemetry receipt rolls back. */
final class OpButtonPollDelivery {
    interface Transport {
        JSONObject submitPanel(String token, JSONObject panel) throws OpButtonException;
        JSONObject pollActions(String token, Map<String, Object> attributes,
                List<OpButtonLogEntry> logs, List<OpButtonMetricEntry> metrics) throws OpButtonException;
    }

    interface State {
        Snapshot capture();
        void panelSent(Snapshot snapshot);
        void attributesSent(Snapshot snapshot);
    }

    static final class Snapshot {
        final Map<String, Object> attributes;
        final JSONObject panel;

        Snapshot(Map<String, Object> attributes, JSONObject panel) {
            this.attributes = attributes;
            this.panel = panel;
        }
    }

    private final Transport transport;
    private final State state;
    private final OpButtonTelemetry telemetry;
    private final Predicate<String> isCurrent;
    private final Consumer<OpButtonException> onPanelError;

    OpButtonPollDelivery(Transport transport, State state, OpButtonTelemetry telemetry,
            Predicate<String> isCurrent, Consumer<OpButtonException> onPanelError) {
        this.transport = transport;
        this.state = state;
        this.telemetry = telemetry;
        this.isCurrent = isCurrent;
        this.onPanelError = onPanelError;
    }

    JSONObject exchange(String token) throws OpButtonException {
        Snapshot snapshot = state.capture();
        if (snapshot.panel != null) {
            try {
                transport.submitPanel(token, snapshot.panel);
                if (isCurrent.test(token)) state.panelSent(snapshot);
            } catch (OpButtonException error) {
                OpButtonLog.e("Failed to submit UI", error);
                onPanelError.accept(error);
            }
        }

        try (OpButtonTelemetry.Batch batch = telemetry.capture()) {
            JSONObject response = transport.pollActions(token, snapshot.attributes, batch.logs, batch.metrics);
            if (isCurrent.test(token)) state.attributesSent(snapshot);
            batch.commit();
            return response;
        }
    }

    JSONObject probe(String token) throws OpButtonException {
        return transport.pollActions(token, null, Collections.emptyList(), Collections.emptyList());
    }
}
