package com.juicyslew.moonstation14.ms14.combat.client;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Small injectable wait/freeze state used by live resolution and unit tests. */
final class MuzzleSourceResolution {
    private int firstGeneration = Integer.MIN_VALUE;
    private ActualMuzzleFrame frozen;
    ActualMuzzleFrame resolve(int generation, BooleanSupplier currentSource, Supplier<ActualMuzzleFrame> capture) {
        if (frozen != null) return frozen;
        if (!currentSource.getAsBoolean()) return null;
        if (firstGeneration == Integer.MIN_VALUE) firstGeneration = generation;
        if (generation - firstGeneration > 1) return null;
        frozen = capture.get();
        return frozen;
    }
    boolean expired(int generation) { return frozen == null && firstGeneration != Integer.MIN_VALUE && generation-firstGeneration>1; }
    boolean frozen() { return frozen != null; }
}
