# Logging & Events

OpButton streams structured logs from your app to the dashboard in real time. Logs are buffered locally and flushed in batches.

## Log configuration

```java
OpButtonLogConfigBuilder logConfig = new OpButtonLogConfigBuilder()
    .setEnabled(true)                // default: true
    .setLogLevel(OpButtonLogLevel.INFO) // minimum level to capture
    .setBatchSize(100)               // logs per flush (default: 100)
    .setBufferSize(1000)             // max buffered logs before oldest are dropped (default: 1000)
    .setAttributes(logAttrs);        // attributes attached to every log entry

OpButtonConfig config = new OpButtonConfig.Builder(url, token)
    .setLogConfig(logConfig)
    .build();
```

## Log levels

`DEBUG` · `INFO` · `WARNING` · `ERROR` · `CRITICAL` · `EXCEPTION`

## Basic logging

```java
OpButton.log(OpButtonLogLevel.INFO, "Player spawned");
OpButton.log(OpButtonLogLevel.ERROR, "Failed to load asset");
```

With category and thread:

```java
OpButton.log(OpButtonLogLevel.WARNING, "Texture missing", "rendering", "GLThread");
```

With metadata (arbitrary key-value data per entry):

```java
Map<String, Object> meta = Map.of(
    "asset", "player.png",
    "size", 1024
);
OpButton.log(OpButtonLogLevel.ERROR, "Load failed", meta);
OpButton.log(OpButtonLogLevel.ERROR, "Load failed", "assets", "IOThread", meta);
```

## Structured log entries

For full control, build a `OpButtonLogEntry`:

```java
OpButtonLogEntry entry = OpButtonLogEntry.error("Crash detected")
    .setCategory("crash")
    .setThread("main")
    .setMetadata(Map.of("stack", stackTrace))
    .setTimestampMs(System.currentTimeMillis());

OpButton.log(entry);
```

Static factory methods: `.debug()`, `.info()`, `.warning()`, `.error()`, `.critical()`.

## Log attributes

Attributes are key-value pairs attached to **every** log entry. They can be static or dynamic:

```java
OpButtonLogAttributeBuilder logAttrs = new OpButtonLogAttributeBuilder()
    .put("app_version", "2.1.0")                          // static
    .putProvider("screen_name", () -> getCurrentScreen())  // dynamic
    .putProvider("user_score", () -> game.getScore());     // re-evaluated each flush
```

Pass them to the log config:

```java
new OpButtonLogConfigBuilder()
    .setAttributes(logAttrs);
```

## Events

Events are high-level structured messages with an implicit `EVENT` level:

```java
OpButton.event("level_completed");
OpButton.event("level_completed", Map.of("level", 5, "time", 42.3));
OpButton.event("level_completed", "gameplay", Map.of("level", 5));
OpButton.event("level_completed", "gameplay", Map.of("level", 5), timestampMs);
```

## Revenue events

Track in-app purchase events:

```java
OpButton.revenue("purchase_completed");
OpButton.revenue("purchase_completed", Map.of("amount", 4.99, "currency", "USD"));
OpButton.revenue("purchase_completed", "monetization", Map.of("sku", "gems_500"));
```

## Screen tracking

Report screen changes:

```java
OpButton.changeScreen("activity", "MainMenuActivity");
OpButton.changeScreen("fragment", "SettingsFragment");
```

## In-app products

Report available and owned products:

```java
OpButton.setInAppProducts(List.of(
    Map.of("id", "gems_100", "price", "$0.99", "type", "consumable"),
    Map.of("id", "no_ads",   "price", "$2.99", "type", "non-consumable")
));

OpButton.setOwnedInAppProducts(List.of("no_ads"));

OpButton.purchaseInApp("txn_123", List.of("gems_100"), Map.of("store", "google"));
```

## Custom SDK logger

Redirect the SDK's own diagnostic messages to your logging system:

```java
new OpButtonConfig.Builder(url, token)
    .setLoggerListener((level, tag, message) -> {
        Log.println(level, tag, message);
    });
```

---

**See also:** [Android Integration](android-integration.md) · [Widgets](widgets.md) · [Metrics](metrics.md)
