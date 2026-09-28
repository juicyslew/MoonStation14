package com.juicyslew.moonstation14.ms14.power.cable.client;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Pure bounded retry policy for cable visual snapshots in the nearby loaded chunk window. */
public final class CableVisualResyncScheduler {
    public static final int MAX_REQUESTS_PER_SCAN = 2;
    public static final long INITIAL_RETRY_TICKS = 30;
    public static final long MAX_RETRY_TICKS = 200;
    public static final int MAX_PRIORITY_ENTRIES = 256;
    public static final int MAX_PRIORITY_INSPECTED_PER_TICK = 64;

    private final Map<Chunk, Long> cooldownUntil = new HashMap<>();
    private final Map<Chunk, Integer> attempts = new HashMap<>();
    private final LinkedHashSet<Chunk> priority = new LinkedHashSet<>();
    private ResourceLocation dimension;
    private int cursor;

    /** Queue a chunk-load candidate; dispatch remains bounded by the client tick's shared cap. */
    public void prioritize(ResourceLocation currentDimension, Chunk chunk) {
        Objects.requireNonNull(currentDimension);
        Objects.requireNonNull(chunk);
        if (!currentDimension.equals(dimension)) {
            reset();
            dimension = currentDimension;
        }
        priority.add(chunk);
        while (priority.size() > MAX_PRIORITY_ENTRIES) {
            Chunk oldest = priority.iterator().next();
            priority.remove(oldest);
            cooldownUntil.remove(oldest);
            attempts.remove(oldest);
        }
        cursor = priority.isEmpty() ? 0 : Math.floorMod(cursor, priority.size());
    }

    public List<Chunk> selectPriority(ResourceLocation currentDimension, Predicate<Chunk> isLoaded,
                                      Predicate<Chunk> hasUsableSnapshot, long tick, int limit) {
        Objects.requireNonNull(currentDimension);
        Objects.requireNonNull(isLoaded);
        Objects.requireNonNull(hasUsableSnapshot);
        if (limit < 0 || limit > MAX_REQUESTS_PER_SCAN) throw new IllegalArgumentException("priority limit outside bounds");
        if (!currentDimension.equals(dimension)) {
            reset();
            dimension = currentDimension;
        }
        List<Chunk> selected = new ArrayList<>(limit);
        if (priority.isEmpty()) { cursor = 0; return List.of(); }
        List<Chunk> candidates = new ArrayList<>(priority);
        int start = Math.floorMod(cursor, candidates.size());
        int inspected = Math.min(candidates.size(), MAX_PRIORITY_INSPECTED_PER_TICK);
        int lastIndex = start;
        for (int offset = 0; offset < inspected; offset++) {
            int index = (start + offset) % candidates.size();
            Chunk chunk = candidates.get(index);
            lastIndex = index;
            if (!isLoaded.test(chunk)) {
                priority.remove(chunk);
                cooldownUntil.remove(chunk);
                continue;
            }
            if (hasUsableSnapshot.test(chunk)) {
                priority.remove(chunk);
                continue;
            }
            if (tick < cooldownUntil.getOrDefault(chunk, Long.MIN_VALUE)) continue;
            if (selected.size() >= limit) continue;
            selected.add(chunk);
            scheduleRetry(chunk, tick);
        }
        cursor = priority.isEmpty() ? 0 : Math.floorMod(lastIndex + 1, priority.size());
        return List.copyOf(selected);
    }

    /** An actual unload makes that chunk eligible immediately on its next load. */
    public void forgetChunk(ResourceLocation unloadDimension, int x, int z) {
        if (!Objects.equals(dimension, unloadDimension)) return;
        Chunk chunk = new Chunk(x, z);
        cooldownUntil.remove(chunk);
        attempts.remove(chunk);
        boolean removedPriority = priority.remove(chunk);
        if (removedPriority) cursor = priority.isEmpty() ? 0 : Math.floorMod(cursor, priority.size());
    }

    /**
     * Selects up to two missing snapshots, rotating through the supplied window. The window must
     * contain only loaded nearby chunks; empty authoritative snapshots are reported as usable.
     */
    public List<Chunk> select(ResourceLocation currentDimension, List<Chunk> window,
                              Predicate<Chunk> hasUsableSnapshot, long tick) {
        return select(currentDimension, window, hasUsableSnapshot, tick, MAX_REQUESTS_PER_SCAN);
    }

    public List<Chunk> select(ResourceLocation currentDimension, List<Chunk> window,
                              Predicate<Chunk> hasUsableSnapshot, long tick, int limit) {
        Objects.requireNonNull(currentDimension);
        Objects.requireNonNull(window);
        Objects.requireNonNull(hasUsableSnapshot);
        if (limit < 0 || limit > MAX_REQUESTS_PER_SCAN) throw new IllegalArgumentException("scan limit outside bounds");
        if (!currentDimension.equals(dimension)) {
            reset();
            dimension = currentDimension;
        }
        if (window.isEmpty()) {
            cursor = 0;
            return List.of();
        }

        int start = Math.floorMod(cursor, window.size());
        List<Chunk> selected = new ArrayList<>(MAX_REQUESTS_PER_SCAN);
        int lastSelected = -1;
        for (int offset = 0; offset < window.size() && selected.size() < limit; offset++) {
            int index = (start + offset) % window.size();
            Chunk chunk = window.get(index);
            if (hasUsableSnapshot.test(chunk) || tick < cooldownUntil.getOrDefault(chunk, Long.MIN_VALUE)) continue;
            selected.add(chunk);
            scheduleRetry(chunk, tick);
            lastSelected = index;
        }
        cursor = lastSelected >= 0 ? (lastSelected + 1) % window.size() : (start + 1) % window.size();
        return List.copyOf(selected);
    }

    public void reset() {
        cooldownUntil.clear();
        attempts.clear();
        priority.clear();
        cursor = 0;
        dimension = null;
    }

    public int prioritySizeForTesting() { return priority.size(); }

    private void scheduleRetry(Chunk chunk, long tick) {
        int attempt = attempts.merge(chunk, 1, Integer::sum);
        long delay = Math.min(MAX_RETRY_TICKS, INITIAL_RETRY_TICKS << Math.min(attempt - 1, 3));
        cooldownUntil.put(chunk, tick + delay);
    }

    public record Chunk(int x, int z) { }
}
