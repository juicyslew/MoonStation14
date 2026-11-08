package com.juicyslew.moonstation14.ms14.player_body_control.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded, dependency-free recorder for opt-in owned-body movement observations. */
public final class MovementTraceRecorder {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_DURATION_SECONDS = 60;
    public static final int MAX_SAMPLES = 60_000;
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();

    public record Context(long epoch, int bodyEntityId, UUID bodyUuid, String dimension, String harnessKind) {
        public Context {
            if (epoch <= 0 || bodyEntityId < 0 || bodyUuid == null || dimension == null || harnessKind == null)
                throw new IllegalArgumentException("invalid movement trace context");
        }
    }

    /** Values are primitive JSON scalars only; keys are copied at record time. */
    public record Sample(String kind, long relativeNanos, long epoch, long logicalTick, long sequence,
                         Map<String, Object> values) { }
    public record Trace(int formatVersion, UUID traceId, Context context, int durationSeconds, long startedNanos,
                        long attempted, long dropped, long invalidValues, long appendOverheadNanos,
                        String finishReason, String serverStream, List<Sample> samples) { }

    private final UUID traceId = UUID.randomUUID();
    private final Context context;
    private final long startedNanos;
    private final long durationNanos;
    private final int capacity;
    private final ArrayList<Sample> samples;
    private long attempted, dropped, invalidValues, appendOverheadNanos;
    private boolean finished;
    private String finishReason;

    public MovementTraceRecorder(Context context, long startedNanos, int durationSeconds, int capacity) {
        if (durationSeconds < 1 || durationSeconds > MAX_DURATION_SECONDS || capacity < 1 || capacity > MAX_SAMPLES)
            throw new IllegalArgumentException("duration/capacity outside trace bounds");
        this.context = java.util.Objects.requireNonNull(context);
        this.startedNanos = startedNanos;
        this.durationNanos = durationSeconds * 1_000_000_000L;
        this.capacity = capacity;
        this.samples = new ArrayList<>(Math.min(capacity, 4096));
    }

    public boolean record(Context observed, String kind, long nowNanos, long logicalTick, long sequence,
                          Map<String, ?> values) {
        long before = System.nanoTime();
        synchronized (this) {
            try {
                if (finished) return false;
                if (observed == null || !context.equals(observed)) return false;
                if (nowNanos - startedNanos >= durationNanos) { finishLocked("expired"); return false; }
                attempted++;
                if (samples.size() >= capacity) { dropped++; return false; }
                Map<String, Object> safe = new java.util.LinkedHashMap<>();
                if (values != null) for (var entry : values.entrySet()) {
                    Object v = entry.getValue();
                    if (entry.getKey() != null && (v instanceof String || v instanceof Boolean || v instanceof Number)) {
                        if (!(v instanceof Number n) || Double.isFinite(n.doubleValue())) safe.put(entry.getKey(), v);
                        else invalidValues++;
                    } else invalidValues++;
                }
                samples.add(new Sample(kind == null ? "UNKNOWN" : kind, Math.max(0, nowNanos - startedNanos),
                        context.epoch(), logicalTick, sequence, Map.copyOf(safe)));
                return true;
            } finally { appendOverheadNanos += Math.max(0, System.nanoTime() - before); }
        }
    }

    public synchronized boolean expired(long nowNanos) { return finished || nowNanos - startedNanos >= durationNanos; }
    public Context context() { return context; }

    public synchronized Trace stop(String reason, String serverStream) {
        if (!finished) finishLocked(reason == null ? "stopped" : reason);
        return new Trace(FORMAT_VERSION, traceId, context, (int) (durationNanos / 1_000_000_000L), startedNanos,
                attempted, dropped, invalidValues, appendOverheadNanos, finishReason,
                serverStream == null ? "UNAVAILABLE" : serverStream, List.copyOf(samples));
    }

    private void finishLocked(String reason) { finished = true; finishReason = reason; }

    /** Writes only beneath the fixed ignored project report root; never accepts a caller-selected path. */
    public static Path export(Trace trace, Path gameDirectory) throws IOException {
        java.util.Objects.requireNonNull(trace);
        Path root = gameDirectory.toAbsolutePath().normalize().resolve("build/reports/nonhostile-animals/movement-traces").normalize();
        Path directory = root.resolve(trace.traceId().toString()).normalize();
        if (!directory.startsWith(root)) throw new IOException("unsafe trace output path");
        Files.createDirectories(directory);
        Path target = directory.resolve("trace.json");
        Path temporary = Files.createTempFile(directory, "trace", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(trace));
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return target;
    }
}
