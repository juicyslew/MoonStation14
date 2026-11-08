package com.juicyslew.moonstation14.ms14.construction.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** Client-side, continuous-look debounce. Does not perform targeting or trust guidance data. */
public final class ConstructionGuidanceDwell {
    public static final long DWELL_NANOS = Duration.ofMillis(500).toNanos();

    public record Target(ResourceKey<Level> dimension, BlockPos position, UUID body, long bodyEpoch) {
        public Target {
            Objects.requireNonNull(dimension);
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(body);
            if (bodyEpoch <= 0) throw new IllegalArgumentException("invalid body epoch");
        }
    }

    private Target target;
    private long startedAt;
    private boolean requested;

    /** Returns true once per continuous target dwell, after the full real-time interval. */
    public boolean update(Target current, long nowNanos) {
        if (current == null) {
            reset();
            return false;
        }
        if (!current.equals(target)) {
            target = current;
            startedAt = nowNanos;
            requested = false;
            return false;
        }
        if (!requested && nowNanos - startedAt >= DWELL_NANOS) {
            requested = true;
            return true;
        }
        return false;
    }

    /** A refresh may be explicitly re-armed while the same target remains under the ray. */
    public void rearm() { if (target != null) requested = false; }

    public boolean accepts(Target responseTarget, Target currentlyAimedAt) {
        return target != null && target.equals(responseTarget) && target.equals(currentlyAimedAt);
    }

    public void reset() { target = null; startedAt = 0; requested = false; }
    public Target target() { return target; }
}

