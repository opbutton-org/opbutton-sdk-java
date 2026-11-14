# Live Streaming

OpButton can stream your app's screen to the dashboard in real time via LiveKit. Dashboard users can also send remote tap and long-press events back to the device.

## Requirements

- Initialize the SDK **with an Application context** — this is required for MediaProjection:

```java
OpButton.initialize(config, getApplicationContext());
```

- A LiveKit server must be reachable from both the device and the dashboard. See [Self-Hosting](self-hosting.md) for setup.

## How it works

1. A dashboard user clicks **Start Live** on a session.
2. The SDK receives a `LIVE_START` action.
3. `OpButtonScreenCaptureActivity` launches transparently to request MediaProjection permission from the user.
4. Once granted, `OpButtonLiveKitPublisher` connects to the LiveKit room and publishes the device screen as a video track.
5. The dashboard receives the video feed and displays it.
6. When the dashboard user clicks **Stop Live**, a `LIVE_STOP` action stops publishing.

## Remote touch

While streaming, the dashboard can send touch coordinates:

| Action type | Description |
|-------------|-------------|
| `LIVE_TAP` | Single tap at screen coordinates |
| `LIVE_LONG_PRESS` | Long press at screen coordinates |

The SDK dispatches these as real touch events via `OpButtonLiveOverlayView`, a transparent overlay that shows visual feedback on the device screen.

## Action types for live streaming

These are handled automatically by the SDK. You generally do not need to handle them manually:

```java
OpButtonActionType.LIVE_START
OpButtonActionType.LIVE_STOP
OpButtonActionType.LIVE_TAP
OpButtonActionType.LIVE_LONG_PRESS
```

If you need to react to streaming lifecycle events, use the global action listener:

```java
OpButton.addActionListener(action -> {
    if (action.getActionType() == OpButtonActionType.LIVE_START) {
        // streaming started
    }
});
```

## Permissions

The SDK requires no special manifest permissions beyond what MediaProjection needs. The user will be prompted by the system to allow screen capture when streaming starts.

---

**See also:** [Self-Hosting](self-hosting.md) · [Android Integration](android-integration.md) · [Widgets](widgets.md)
