package com.juicyslew.moonstation14.ms14.shuttle.sable;

import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;

/** Bounded test-only paired full-server-tick envelope recorder. */
final class ServerTickCostRecorder {
    record Summary(int count, long min, long max, double mean, long p50, long p95, long p99,
                   List<Long> samples) {
        Summary { samples = List.copyOf(samples); }
    }

    private final long[] samples;
    private final LongSupplier clock;
    private int size;
    private long openTick = -1;
    private long start;
    private boolean armed;

    ServerTickCostRecorder(int limit, LongSupplier clock) {
        if (limit < 1) throw new IllegalArgumentException("sample limit must be positive");
        this.samples = new long[limit];
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    void arm() {
        if (armed || openTick >= 0 || size > 0) throw new IllegalStateException("recorder already active");
        armed = true;
    }

    /** PRE event; first armed PRE only opens a complete tick, it never includes setup work. */
    void pre(long tick) {
        if (!armed) return;
        if (openTick >= 0) throw new IllegalStateException("duplicate/misordered PRE");
        if (size == samples.length) throw new IllegalStateException("sample limit exceeded");
        openTick = tick;
        start = clock.getAsLong();
    }

    void post(long tick) {
        if (!armed) return;
        if (openTick < 0 || openTick != tick) throw new IllegalStateException("unmatched/misordered POST");
        long elapsed = clock.getAsLong() - start;
        if (elapsed <= 0) throw new IllegalStateException("non-positive tick duration");
        samples[size++] = elapsed;
        openTick = -1;
    }

    Summary finish(int expected) {
        if (!armed || openTick >= 0 || expected < 1 || size != expected)
            throw new IllegalStateException("incomplete recorder phase: expected=" + expected + " actual=" + size);
        armed = false;
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        long total = 0;
        for (long sample : sorted) total = Math.addExact(total, sample);
        return new Summary(size, sorted[0], sorted[size - 1], (double) total / size,
                percentile(sorted, .50), percentile(sorted, .95), percentile(sorted, .99),
                Arrays.stream(samples, 0, size).boxed().toList());
    }

    static long percentile(long[] sorted, double p) {
        if (sorted.length == 0 || p <= 0 || p > 1) throw new IllegalArgumentException("invalid percentile");
        return sorted[(int) Math.ceil(p * sorted.length) - 1];
    }

    static int launchCount() { return 6; }
    static boolean isLaunchTick(int relativeTick) {
        return relativeTick >= 0 && relativeTick < 600 && relativeTick % 100 == 0;
    }
    static int launchOffset(int launchIndex) {
        if (launchIndex < 0 || launchIndex >= launchCount()) throw new IllegalArgumentException("launch index");
        return launchIndex * 100;
    }
}
