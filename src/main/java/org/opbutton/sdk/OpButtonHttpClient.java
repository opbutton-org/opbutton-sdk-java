package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSource;

/**
 * Low-level HTTP client for the OpButton server API.
 * Thread-safe — can be shared across threads.
 */
final class OpButtonHttpClient implements OpButtonPollDelivery.Transport {
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final RequestBody EMPTY_BODY = RequestBody.create("", JSON_MEDIA);
    private static final long MAX_RESPONSE_BYTES = 2L * 1024L * 1024L;

    private final String m_baseUrl;
    private final String m_apiToken;
    private final String m_runId = UUID.randomUUID().toString();
    private final OkHttpClient m_http;
    private volatile String m_deviceSecret = UUID.randomUUID().toString() + UUID.randomUUID();

    OpButtonHttpClient(@NonNull String baseUrl, @NonNull String apiToken) {
        this(baseUrl, apiToken, new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build());
    }

    OpButtonHttpClient(@NonNull String baseUrl, @NonNull String apiToken, @NonNull OkHttpClient http) {
        m_baseUrl = baseUrl;
        m_apiToken = apiToken;
        m_http = http;
    }

    void shutdown() {
        m_http.dispatcher().executorService().shutdown();
        m_http.connectionPool().evictAll();
    }

    void setDeviceSecret(@NonNull String deviceSecret) {
        if (!isSafeDeviceSecret(deviceSecret)) {
            throw new IllegalArgumentException(
                    "deviceSecret must contain 32 to 256 printable ASCII characters");
        }
        m_deviceSecret = deviceSecret;
    }

    // ── Client endpoints ──

    OpButtonConnectResponse connect(@NonNull String deviceId, @NonNull String deviceName,
                                @NonNull Map<String, Object> sessionAttributes) throws OpButtonException {
        JSONObject body = new JSONObject();
        try {
            body.put("device_id", deviceId);
            body.put("device_name", deviceName);
            body.put("run_id", m_runId);
            if (m_deviceSecret != null) body.put("device_secret", m_deviceSecret);
            if (!sessionAttributes.isEmpty()) {
                JSONObject attrs = new JSONObject();
                for (Map.Entry<String, Object> entry : sessionAttributes.entrySet()) {
                    attrs.put(entry.getKey(), entry.getValue());
                }
                body.put("session_attributes", attrs);
            }
        } catch (JSONException e) {
            throw new OpButtonException("Failed to build connect request", e);
        }

        Request request = apiTokenRequest("/api/client/connect")
                .post(jsonBody(body))
                .build();

        return OpButtonConnectResponse.fromJson(execute(request));
    }

    OpButtonConnectResponse pollStatus(@NonNull String requestId) throws OpButtonException {
        Request request = apiTokenRequest("/api/client/poll-status/" + requestId + "?run_id=" + m_runId)
                .header("X-Device-Secret", requireDeviceSecret())
                .get()
                .build();

        return OpButtonConnectResponse.fromJson(execute(request));
    }

    boolean closeSession(@NonNull String sessionToken) throws OpButtonException {
        Request request = sessionTokenRequest("/api/client/session/close", sessionToken)
                .post(EMPTY_BODY)
                .build();

        return execute(request).optBoolean("ok", false);
    }

    @Override public JSONObject submitPanel(@NonNull String sessionToken, @NonNull JSONObject layout) throws OpButtonException {
        JSONObject body = new JSONObject();
        try {
            body.put("layout", layout);
        } catch (JSONException e) {
            throw new OpButtonException("Failed to build panel request", e);
        }

        Request request = sessionTokenRequest("/api/client/session/panel", sessionToken)
                .post(jsonBody(body))
                .build();

        return execute(request);
    }

    @Override public JSONObject pollActions(@NonNull String sessionToken,
                           @Nullable Map<String, Object> changedAttributes,
                           @NonNull List<OpButtonLogEntry> logs,
                           @NonNull List<OpButtonMetricEntry> metrics) throws OpButtonException {
        JSONObject body = new JSONObject();
        try {
            if (changedAttributes != null && !changedAttributes.isEmpty()) {
                JSONObject attrs = new JSONObject();
                for (Map.Entry<String, Object> entry : changedAttributes.entrySet()) {
                    attrs.put(entry.getKey(), entry.getValue());
                }
                body.put("session_attributes", attrs);
            }

            if (!logs.isEmpty()) {
                JSONArray logsArray = new JSONArray();
                for (OpButtonLogEntry entry : logs) {
                    logsArray.put(entry.toJson());
                }
                body.put("logs", logsArray);
            }

            if (!metrics.isEmpty()) {
                JSONArray metricsArray = new JSONArray();
                for (OpButtonMetricEntry entry : metrics) {
                    metricsArray.put(entry.toJson());
                }
                body.put("metrics", metricsArray);
            }
        } catch (JSONException e) {
            throw new OpButtonException("Failed to build action poll request", e);
        }

        Request request = sessionTokenRequest("/api/client/session/actions/poll", sessionToken)
                .post(body.length() > 0 ? jsonBody(body) : EMPTY_BODY)
                .build();

        return execute(request);
    }

    void acknowledgeAction(@NonNull String sessionToken, @NonNull String actionId, @Nullable JSONObject ackPayload) throws OpButtonException {
        JSONObject body = new JSONObject();
        try {
            body.put("action_id", actionId);
            body.put("ack_payload", ackPayload != null ? ackPayload : new JSONObject());
        } catch (JSONException e) {
            throw new OpButtonException("Failed to build ack request", e);
        }

        Request request = sessionTokenRequest("/api/client/session/actions/ack", sessionToken)
                .post(jsonBody(body))
                .build();

        execute(request);
    }

    void sendLogs(@NonNull String sessionToken, @NonNull List<OpButtonLogEntry> logs) throws OpButtonException {
        if (logs.isEmpty()) {
            return;
        }

        JSONArray logsArray = new JSONArray();
        for (OpButtonLogEntry entry : logs) {
            logsArray.put(entry.toJson());
        }

        JSONObject body = new JSONObject();
        try {
            body.put("logs", logsArray);
        } catch (JSONException e) {
            throw new OpButtonException("Failed to build logs request", e);
        }

        Request request = sessionTokenRequest("/api/client/session/logs", sessionToken)
                .post(jsonBody(body))
                .build();

        execute(request);
    }

    void sendMetrics(@NonNull String sessionToken, @NonNull List<OpButtonMetricEntry> metrics) throws OpButtonException {
        if (metrics.isEmpty()) {
            return;
        }

        JSONArray metricsArray = new JSONArray();
        for (OpButtonMetricEntry entry : metrics) {
            metricsArray.put(entry.toJson());
        }

        JSONObject body = new JSONObject();
        try {
            body.put("metrics", metricsArray);
        } catch (JSONException e) {
            throw new OpButtonException("Failed to build metrics request", e);
        }

        Request request = sessionTokenRequest("/api/client/session/metrics", sessionToken)
                .post(jsonBody(body))
                .build();

        execute(request);
    }

    JSONObject getLivePublisherState(@NonNull String sessionToken) throws OpButtonException {
        Request request = sessionTokenRequest("/api/client/session/live/publisher", sessionToken)
                .get()
                .build();

        return execute(request);
    }

    // ── Helpers ──

    private Request.Builder apiTokenRequest(@NonNull String path) {
        return new Request.Builder()
                .url(m_baseUrl + path)
                .header("X-Api-Token", m_apiToken);
    }

    private Request.Builder sessionTokenRequest(@NonNull String path, @NonNull String sessionToken) {
        return new Request.Builder()
                .url(m_baseUrl + path)
                .header("X-Session-Token", sessionToken);
    }

    private static RequestBody jsonBody(@NonNull JSONObject json) {
        return RequestBody.create(json.toString(), JSON_MEDIA);
    }

    private String requireDeviceSecret() throws OpButtonException {
        if (!isSafeDeviceSecret(m_deviceSecret)) {
            throw new OpButtonException("Device credential is unavailable");
        }
        return m_deviceSecret;
    }

    private static boolean isSafeDeviceSecret(@Nullable String value) {
        if (value == null || value.length() < 32 || value.length() > 256) return false;
        for (int index = 0; index != value.length(); ++index) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e) return false;
        }
        return true;
    }

    private JSONObject execute(@NonNull Request request) throws OpButtonException {
        try (Response response = m_http.newCall(request).execute()) {
            String responseBody = "";
            if (response.body() != null) {
                long declaredLength = response.body().contentLength();
                if (declaredLength > MAX_RESPONSE_BYTES) {
                    throw new OpButtonException("Server response exceeds 2 MiB");
                }
                BufferedSource source = response.body().source();
                Buffer buffer = new Buffer();
                while (source.read(buffer, 8192) != -1) {
                    if (buffer.size() > MAX_RESPONSE_BYTES) {
                        throw new OpButtonException("Server response exceeds 2 MiB");
                    }
                }
                responseBody = buffer.readString(StandardCharsets.UTF_8);
            }

            if (!response.isSuccessful()) {
                String detail = responseBody;
                try {
                    JSONObject errorJson = new JSONObject(responseBody);
                    detail = errorJson.optString("detail", responseBody);
                } catch (JSONException ignored) {
                }

                throw new OpButtonException(response.code(), "HTTP " + response.code() + ": " + detail);
            }

            if (responseBody.isEmpty()) {
                return new JSONObject();
            }

            return new JSONObject(responseBody);
        } catch (IOException e) {
            throw new OpButtonException("Network error: " + e.getMessage(), e);
        } catch (JSONException e) {
            throw new OpButtonException("Failed to parse server response", e);
        }
    }
}
