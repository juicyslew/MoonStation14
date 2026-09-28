package com.juicyslew.moonstation14.ms14.power.graph;

import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashSet;
import java.util.Set;

/** Rotates periodic chunk checks without turning validation work into graph mutations. */
final class ChunkValidationScheduler {
    private final Set<ChunkPos> queued = new LinkedHashSet<>();

    void schedule(ChunkPos pos) { queued.add(pos); }

    ChunkPos poll() {
        if (queued.isEmpty()) return null;
        ChunkPos next = queued.iterator().next();
        queued.remove(next);
        return next;
    }

    void remove(ChunkPos pos) { queued.remove(pos); }

    boolean isEmpty() { return queued.isEmpty(); }
}
