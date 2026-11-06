package com.juicyslew.moonstation14.gametest.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Package-private bounded state owner shared by the runtime observer and focused JVM tests. */
final class GameTestProgressState<K, V> {
    static final long HEARTBEAT_TICKS = 200;
    static final long HEARTBEAT_NANOS = 30_000_000_000L;
    enum Admission { NEW, DUPLICATE, OVERFLOW }
    enum Outcome { PASS, FAIL, OTHER }
    record Counts(long started, long completed, long failed, long overflow) { }

    private final int cap;
    private final IdentityHashMap<K, V> active = new IdentityHashMap<>();
    private long started, completed, failed, overflow;
    private long heartbeatTick, heartbeatNanos;
    private boolean heartbeatAnchored, overflowReported;

    GameTestProgressState(int cap) {
        if (cap < 1) throw new IllegalArgumentException("cap must be positive");
        this.cap = cap;
    }
    Admission admit(K key, V value, long tick, long nowNanos) {
        if (active.containsKey(key)) return Admission.DUPLICATE;
        if (active.size() >= cap) { overflow++; return Admission.OVERFLOW; }
        boolean newCohort = active.isEmpty();
        active.put(key, value);
        started++;
        if (newCohort || !heartbeatAnchored) {
            heartbeatTick = tick;
            heartbeatNanos = nowNanos;
            heartbeatAnchored = true;
        }
        return Admission.NEW;
    }
    V complete(K key, Outcome outcome) {
        V value = active.remove(key);
        if (value == null) return null;
        completed++;
        if (outcome == Outcome.FAIL) failed++;
        return value;
    }
    boolean contains(K key) { return active.containsKey(key); }
    List<Map.Entry<K, V>> snapshot() {
        List<Map.Entry<K, V>> copy = new ArrayList<>(active.size());
        active.forEach((key, value) -> copy.add(Map.entry(key, value)));
        return Collections.unmodifiableList(copy);
    }
    int activeSize() { return active.size(); }
    Counts counts() { return new Counts(started, completed, failed, overflow); }
    boolean markOverflowReported() { if (overflowReported) return false; overflowReported = true; return true; }
    boolean heartbeatDue(long tick, long nowNanos) {
        if (active.isEmpty()) return false;
        if (!heartbeatAnchored) { heartbeatTick = tick; heartbeatNanos = nowNanos; heartbeatAnchored = true; return false; }
        if (tick - heartbeatTick < HEARTBEAT_TICKS && nowNanos - heartbeatNanos < HEARTBEAT_NANOS) return false;
        heartbeatTick = tick;
        heartbeatNanos = nowNanos;
        return true;
    }
    void clear() {
        active.clear();
        started = completed = failed = overflow = heartbeatTick = heartbeatNanos = 0;
        heartbeatAnchored = overflowReported = false;
    }
}
