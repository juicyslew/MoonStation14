package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.List;
import java.util.Optional;

/** Immutable orientation captured from the final native collision substep. */
public final class NativeActorYaw {
    private final double yaw;

    private NativeActorYaw(double yaw) { this.yaw = yaw; }

    public double yaw() { return yaw; }

    /** Return a fresh quaternion so callers cannot mutate this value. */
    public Quaterniondc orientation() { return new Quaterniond().rotationY(yaw); }

    public static Optional<NativeActorYaw> capture(Entity entity,
            CurrentFrameCollisionScope.ScopedResult<?> result) {
        if (entity == null || result == null) return Optional.empty();
        // This SDK hook is currently null for ordinary entities; a custom orientation is not modeled here.
        if (EntitySubLevelUtil.getCustomEntityOrientation(entity, 1f) != null) return Optional.empty();
        return fromSamples(result.yawSamples(), result.yawSamplesComplete());
    }

    static Optional<NativeActorYaw> fromSamples(List<CurrentFrameCollisionScope.YawSample> samples,
                                                  boolean complete) {
        if (!complete || samples == null || samples.isEmpty()) return Optional.empty();
        int n = samples.size();
        if (n > 10) return Optional.empty();
        int count = samples.get(0).substeps();
        if (count != n) return Optional.empty();
        for (int i = 0; i < n; i++) {
            var sample = samples.get(i);
            if (sample == null || sample.substep() != i + 1 || sample.substeps() != count)
                return Optional.empty();
        }
        double yaw = samples.get(n - 1).selectedBodyYaw();
        return Double.isFinite(yaw) ? Optional.of(new NativeActorYaw(yaw)) : Optional.empty();
    }
}
