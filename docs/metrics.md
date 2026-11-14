# Metrics

OpButton collects time-series performance metrics from your app and displays them as charts on the dashboard.

## Configuration

```java
OpButtonMetricConfigBuilder metricConfig = new OpButtonMetricConfigBuilder()
    .setEnabled(true)                // default: true
    .setSampleIntervalMs(200)        // 100–1000 ms (default: 200)
    .setBufferSize(2000)             // max buffered samples (default: 2000)
    .setBatchSize(200)               // samples per flush (default: 200)
    .addCollector(myCollector);

OpButtonConfig config = new OpButtonConfig.Builder(url, token)
    .setMetricConfig(metricConfig)
    .build();
```

## Built-in metric types

| Type | Unit | Aggregation | Description |
|------|------|-------------|-------------|
| `FPS` | fps | GAUGE | Frames per second |
| `FRAME_TIME` | ms | GAUGE | Time per frame |
| `MEMORY` | bytes | GAUGE | RAM usage |
| `VIDEO_MEMORY` | bytes | GAUGE | GPU memory |
| `CPU_USAGE` | % | GAUGE | CPU utilization |
| `NETWORK_RX` | bytes/s | RATE | Network received |
| `NETWORK_TX` | bytes/s | RATE | Network transmitted |
| `BATTERY_LEVEL` | % | GAUGE | Battery charge |
| `BATTERY_CHARGING` | — | GAUGE | Charging state |
| `DRAW_CALLS` | — | GAUGE | Render draw calls |
| `THREAD_COUNT` | — | GAUGE | Active threads |

## Custom metric types

```java
OpButtonMetricType loadTime = OpButtonMetricType.create("scene_load_time");
OpButtonMetricType loadTimeMs = OpButtonMetricType.create("scene_load_time", "ms");
OpButtonMetricType errors = OpButtonMetricType.create("error_count", "errors", OpButtonMetricAggregation.COUNTER);
```

### Aggregation modes

| Mode | Behaviour |
|------|-----------|
| `GAUGE` | Last value in the window (FPS, memory) |
| `COUNTER` | Sum of values (error counts, events) |
| `RATE` | Average over the window (bytes/s) |

## Recording metrics

### Manual recording

```java
OpButtonMetrics metrics = OpButton.getMetrics();

metrics.record(OpButtonMetricType.FPS, 60.0);
metrics.record(OpButtonMetricType.MEMORY, Runtime.getRuntime().totalMemory());
metrics.record(myCustomType, 42.0, System.currentTimeMillis());
```

### Collectors

Collectors are called automatically on each sample interval. Use them for periodic measurements:

```java
OpButtonMetricCollector collector = out -> {
    out.add(new OpButtonMetricEntry(OpButtonMetricType.FPS, game.getFps()));
    out.add(new OpButtonMetricEntry(OpButtonMetricType.MEMORY, getUsedMemory()));
    out.add(new OpButtonMetricEntry(myCustomType, computeCustomValue()));
};

// Add via config
new OpButtonMetricConfigBuilder().addCollector(collector);

// Or at runtime
OpButton.getMetrics().addCollector(collector);
OpButton.getMetrics().removeCollector(collector);
```

## Runtime adjustments

```java
OpButtonMetrics metrics = OpButton.getMetrics();

metrics.setSampleIntervalMs(500);  // slow down sampling
metrics.setBufferSize(5000);       // increase buffer
metrics.setBatchSize(500);         // larger batches
```

---

**See also:** [Android Integration](android-integration.md) · [Widgets](widgets.md) · [Logging & Events](logging.md) · [Live Streaming](live-streaming.md)
