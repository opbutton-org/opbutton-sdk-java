# Android Integration

Step-by-step guide for adding OpButton SDK to your Android project.

## Requirements

- Android API 21+
- Java 8+ or Kotlin

## 1. Add JitPack repository

In your root `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

## 2. Add the dependency

In your module `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.opbutton-org:opbutton-sdk-java:master-SNAPSHOT'
}
```

## 3. Configure the SDK

Create a `OpButtonConfig` and initialize the SDK in your `Application.onCreate()`:

```java
OpButtonConfig config = new OpButtonConfig.Builder(
        "https://opbutton.example.com",  // your OpButton dashboard URL
        "opbutton_your_api_token"          // project API token from dashboard
    )
    .setDeviceName("Pixel 8")
    .build();

OpButton.initialize(config, this); // pass Application context
```

### Configuration options

| Method | Default | Description |
|--------|---------|-------------|
| `setDeviceId(String)` | auto | Custom device identifier |
| `setDeviceName(String)` | — | Human-readable device name shown in dashboard |
| `setPollIntervalMs(long)` | 10000 | Status polling interval (ms) |
| `setActionPollIntervalMs(long)` | 2000 | Action polling interval (ms) |
| `setAutoConnect(boolean)` | true | Connect automatically after init |
| `setLoggerListener(OpButtonLoggerListener)` | — | Redirect SDK diagnostic logs |
| `setSessionListener(OpButtonSessionListener)` | — | Session lifecycle callbacks |
| `setActionListener(OpButtonActionListener)` | — | Remote action callbacks |
| `setSessionAttributes(OpButtonSessionAttributeBuilder)` | — | Key-value pairs attached to the session |
| `setLogConfig(OpButtonLogConfigBuilder)` | — | Logging configuration |
| `setMetricConfig(OpButtonMetricConfigBuilder)` | — | Metrics configuration |

## 4. Session attributes

Attach static and dynamic key-value pairs to identify the session. Native types are preserved:

```java
OpButtonSessionAttributeBuilder sessionAttrs = new OpButtonSessionAttributeBuilder()
    .put("is_debug", BuildConfig.DEBUG)          // boolean
    .put("install_version", 42)                  // int
    .put("build_type", "debug")                  // string
    .put("referrer", null)                       // null
    .putProvider("user_id", () -> getUserId());   // dynamic — re-evaluated on each poll

OpButtonConfig config = new OpButtonConfig.Builder(url, token)
    .setSessionAttributes(sessionAttrs)
    .build();
```

## 5. Connect

If `autoConnect` is true (default), the SDK connects after `initialize()`. Otherwise call:

```java
OpButton.connect();
```

The SDK will:
1. Send a connect request to the backend.
2. Wait for approval from a dashboard user.
3. Start the debug session once approved.

## 6. Session status

Check the current state:

```java
OpButtonSessionStatus status = OpButton.getStatus();
```

Possible values: `DISCONNECTED`, `CONNECTING`, `WAITING_APPROVAL`, `ACTIVE`, `AUTH_FAILED`, `REJECTED`, `CLOSED`, `ERROR`.

## 7. Session lifecycle listener

```java
OpButton.addSessionListener(new OpButtonSessionListener() {
    @Override public void onOpButtonSessionConnecting() { }
    @Override public void onOpButtonSessionWaitingApproval(String requestId) { }
    @Override public void onOpButtonSessionStarted(String sessionToken) { }
    @Override public void onOpButtonSessionClosed() { }
    @Override public void onOpButtonSessionRejected() { }
    @Override public void onOpButtonSessionAuthFailed() { }
    @Override public void onOpButtonSessionError(OpButtonException e) { }
});
```

## 8. Disconnect & shutdown

```java
OpButton.disconnect();  // end current session
OpButton.shutdown();    // release all resources
```

## ProGuard / R8

The SDK ships its own `consumer-rules.pro`. No additional ProGuard configuration is needed.

---

**Next:** [Widgets](widgets.md) · [Logging & Events](logging.md) · [Metrics](metrics.md) · [Live Streaming](live-streaming.md)
