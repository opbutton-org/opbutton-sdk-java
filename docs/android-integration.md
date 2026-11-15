# Android Integration

Step-by-step guide for adding OpButton SDK to your Android project.

## Requirements

- Android API 24+
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

## Device identity

Pass an Application (or its Context) to `OpButton.initialize(config, application)` for automatic persistent identity.
An explicit `setDeviceId(...)` always takes precedence.

The default resolver tries these strategies in order:

1. SHA-256 of `ANDROID_ID`, scoped to the application package (`android-v1:` prefix). This normally survives app restarts, data clearing and reinstalling the same signed application on the same Android user/device. The raw system identifier is not transmitted.
2. A UUID saved in the app's `noBackupFilesDir` (`installation-v1:` prefix), when the system identifier is unavailable. It survives SDK/app restarts. Clearing app data or uninstalling resets this fallback. Excluding it from backup prevents a restored backup on another phone from duplicating the device identity.
3. A process UUID (`process-v1:` prefix), if Context or persistent storage is unavailable. It survives SDK reconnects and reinitialization within that process only. Supply Context or an explicit ID when persistence is needed.

Factory resets, signing-key changes and Android user/profile changes can change `ANDROID_ID`. There is also a documented Android 8 OTA/reinstall exception. See the [Android documentation](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID).

Upgrading from the old model-based fallback creates a new device request and requires approval once. The SDK does not infer which physical phone owned a shared model ID.

## Delivery and recovery

Panel requests acknowledge their captured revision. Edits made while a request is in flight remain pending.
Session attribute snapshots become the baseline only after a successful request, so failures leave deltas available for retry.
Recovery polls dispatch commands through the same handler as normal polls, while metric sampling continues during recovery.
