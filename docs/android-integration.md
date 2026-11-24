# Android Integration

Step-by-step guide for adding OpButton SDK to your Android project.

## Requirements

- Android API 24+
- JDK 17+ and Java or Kotlin application sources

## 1. Add JitPack repository

In your root `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven { url 'https://jitpack.io' } }
            filter {
                includeGroup 'com.github.opbutton-org'
                includeGroup 'com.github.davidliu'
            }
        }
    }
}
```

## 2. Add the dependency

In your module `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.opbutton-org:opbutton-sdk-java:<reviewed-release-tag>'
}
```

Use an immutable reviewed release tag in production, not `master-SNAPSHOT`.

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
| `setDeviceSecret(String)` | generated | Host-provisioned 32–256 printable-ASCII installation credential |
| `setAllowInsecureLocalhost(boolean)` | false | Permit plain HTTP only for loopback development URLs |
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

New installations use a UUID with the `installation-v1:` prefix. The SDK saves it with the same lifetime as the device credential in `noBackupFilesDir`. Restarts keep both values. Clearing app data or reinstalling creates a new pair and requires approval.

The previous package-scoped Android identity remains usable when its credential is stored locally or supplied by the host. Upgrading from a version that never stored that credential creates a new device request.

## Device credential and transport

Every installation also owns a random credential that proves approval polling belongs to the same installation that requested access. The SDK generates it before the first request and, when initialized with an Application Context, stores it in `noBackupFilesDir`. Android backup therefore cannot clone it onto another device. The server stores only a SHA-256 digest.

Without Context, the host must load a 32–256 character printable-ASCII credential from its own secure storage and pass it to `setDeviceSecret(...)`. Supply a matching `setDeviceId(...)` for identity across processes. The default process UUID lasts until the application process exits.

Production server URLs must use HTTPS and may not contain credentials, a query, or a fragment. For local development only, call `setAllowInsecureLocalhost(true)` to permit `http://localhost`, `http://127.0.0.1`, or `http://[::1]`.

When a new installation requests access, approve it after confirming it is the expected device.

## Delivery and recovery

Panel requests acknowledge their captured revision. Edits made while a request is in flight remain pending.
Session attribute snapshots become the baseline only after a successful request, so failures leave deltas available for retry.
Recovery polls dispatch commands through the same handler as normal polls, while metric sampling continues during recovery.
