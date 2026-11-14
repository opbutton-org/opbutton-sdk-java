package org.opbutton.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Configuration for {@link OpButton} initialization.
 */
public final class OpButtonConfig {
    final String baseUrl;
    final String apiToken;
    final String deviceId;
    final String deviceName;
    final long pollIntervalMs;
    final long actionPollIntervalMs;
    final boolean autoConnect;
    final OpButtonLoggerListener loggerListener;
    final OpButtonSessionListener sessionListener;
    final OpButtonActionListener actionListener;
    final OpButtonSessionAttributeBuilder sessionAttributes;
    final OpButtonLogConfigBuilder logConfig;
    final OpButtonMetricConfigBuilder metricConfig;

    private OpButtonConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.apiToken = builder.apiToken;
        this.deviceId = builder.deviceId;
        this.deviceName = builder.deviceName;
        this.pollIntervalMs = builder.pollIntervalMs;
        this.actionPollIntervalMs = builder.actionPollIntervalMs;
        this.autoConnect = builder.autoConnect;
        this.loggerListener = builder.loggerListener;
        this.sessionListener = builder.sessionListener;
        this.actionListener = builder.actionListener;
        this.sessionAttributes = builder.sessionAttributes;
        this.logConfig = builder.logConfig;
        this.metricConfig = builder.metricConfig;
    }

    @NonNull
    public String getBaseUrl() {
        return baseUrl;
    }

    @NonNull
    public String getApiToken() {
        return apiToken;
    }

    @Nullable
    public String getDeviceId() {
        return deviceId;
    }

    @Nullable
    public String getDeviceName() {
        return deviceName;
    }

    public static class Builder {
        private String baseUrl;
        private String apiToken;
        private String deviceId;
        private String deviceName;
        private long pollIntervalMs = 10000;
        private long actionPollIntervalMs = 2000;
        private boolean autoConnect = true;
        private OpButtonLoggerListener loggerListener = null;
        private OpButtonSessionListener sessionListener = null;
        private OpButtonActionListener actionListener = null;
        private OpButtonSessionAttributeBuilder sessionAttributes = new OpButtonSessionAttributeBuilder();
        private OpButtonLogConfigBuilder logConfig = new OpButtonLogConfigBuilder();
        private OpButtonMetricConfigBuilder metricConfig = new OpButtonMetricConfigBuilder();

        /**
         * @param baseUrl  OpButton server URL, e.g. "https://opbutton.example.com"
         * @param apiToken Project API token starting with "opbutton_"
         */
        public Builder(@NonNull String baseUrl, @NonNull String apiToken) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.apiToken = apiToken;
        }

        public Builder setDeviceId(@NonNull String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder setDeviceName(@NonNull String deviceName) {
            this.deviceName = deviceName;
            return this;
        }

        public Builder setPollIntervalMs(long ms) {
            this.pollIntervalMs = ms;
            return this;
        }

        public Builder setActionPollIntervalMs(long ms) {
            this.actionPollIntervalMs = ms;
            return this;
        }

        /**
         * Whether to connect automatically on {@link OpButton#initialize}.
         * If false, {@link OpButton#connect()} must be called explicitly.
         * Default: true.
         */
        public Builder setAutoConnect(boolean autoConnect) {
            this.autoConnect = autoConnect;
            return this;
        }

        /**
         * Set a listener for internal SDK diagnostic logs.
         */
        public Builder setLoggerListener(@Nullable OpButtonLoggerListener listener) {
            this.loggerListener = listener;
            return this;
        }

        /**
         * Set a listener for session lifecycle events (connecting, approved, error, etc.).
         */
        public Builder setSessionListener(@Nullable OpButtonSessionListener listener) {
            this.sessionListener = listener;
            return this;
        }

        /**
         * Set a listener for actions received from the dashboard.
         */
        public Builder setActionListener(@Nullable OpButtonActionListener listener) {
            this.actionListener = listener;
            return this;
        }

        /**
         * Set session attributes (static and dynamic) via a builder.
         */
        public Builder setSessionAttributes(@NonNull OpButtonSessionAttributeBuilder builder) {
            this.sessionAttributes = builder;
            return this;
        }

        /**
         * Set the log configuration via a builder.
         *
         * <pre>{@code
         * OpButtonLogConfigBuilder logConfig = new OpButtonLogConfigBuilder()
         *     .setEnabled(true)
         *     .setLogLevel(OpButtonLogLevel.INFO)
         *     .setAttributes(new OpButtonLogAttributeBuilder()
         *         .putProvider("screen_name", () -> currentScreen));
         *
         * builder.setLogConfig(logConfig);
         * }</pre>
         */
        public Builder setLogConfig(@NonNull OpButtonLogConfigBuilder config) {
            this.logConfig = config;
            return this;
        }

        /**
         * Set the metric configuration via a builder.
         *
         * <pre>{@code
         * OpButtonMetricConfigBuilder metricConfig = new OpButtonMetricConfigBuilder()
         *     .setEnabled(true)
         *     .setSampleIntervalMs(200)
         *     .addCollector(out -> {
         *         out.add(new OpButtonMetricEntry(OpButtonMetricType.DRAW_CALLS, renderer.getDrawCalls()));
         *     });
         *
         * builder.setMetricConfig(metricConfig);
         * }</pre>
         */
        public Builder setMetricConfig(@NonNull OpButtonMetricConfigBuilder config) {
            this.metricConfig = config;
            return this;
        }

        public OpButtonConfig build() {
            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new IllegalArgumentException("baseUrl is required");
            }
            if (apiToken == null || apiToken.isEmpty()) {
                throw new IllegalArgumentException("apiToken is required");
            }
            return new OpButtonConfig(this);
        }
    }
}
