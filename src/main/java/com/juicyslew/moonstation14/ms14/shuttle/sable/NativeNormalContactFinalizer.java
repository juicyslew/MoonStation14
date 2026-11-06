package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.world.phys.Vec3;

/** Applies world-oriented contact responses after the owning motor has finalized velocity in its current basis. */
public final class NativeNormalContactFinalizer {
    private NativeNormalContactFinalizer() { }

    /** Baseline and frame correction are m/s components in the current velocity basis. */
    public static MovementVector finalizeWorld(MovementVector baseline, MovementVector frameDelta,
                                               NativeNormalContactPolicy.Target target) {
        return finalizeWorld(baseline, frameDelta, target, null);
    }

    /** The optional witness is frozen by the exact native move and only resolves a grounded landing. */
    public static MovementVector finalizeWorld(MovementVector baseline, MovementVector frameDelta,
            NativeNormalContactPolicy.Target target, LandingWitness landing) {
        MovementVector absolute = baseline.add(frameDelta);
        NativeNormalContactPolicy.Target response = target;
        if (response == null && landing != null && landing.valid()
                && !java.util.Objects.equals(landing.fromFrame(), landing.toFrame())
                && landing.incomingDestinationNormalSpeed() < 0d) {
            Vec3 normal = landing.worldNormal();
            response = new NativeNormalContactPolicy.Target(normal, Vec3.ZERO, 0d);
        }
        if (response == null) return absolute;
        Vec3 corrected = NativeNormalContactPolicy.replaceNormal(
                new Vec3(absolute.x(), absolute.y(), absolute.z()), response);
        return new MovementVector(corrected.x, corrected.y, corrected.z);
    }

    public record LandingWitness(java.util.UUID fromFrame, java.util.UUID toFrame, Vec3 worldNormal,
                                 double incomingDestinationNormalSpeed, boolean jumpInitiated) {
        public boolean valid() {
            return toFrame != null && worldNormal != null && Double.isFinite(worldNormal.x)
                    && Double.isFinite(worldNormal.y) && Double.isFinite(worldNormal.z)
                    && Math.abs(worldNormal.length() - 1d) < 1.0e-6
                    && Double.isFinite(incomingDestinationNormalSpeed) && !jumpInitiated;
        }
    }

    /** Admit only the one captured move and matching known actor endpoints; null source ID is known WORLD. */
    public static LandingWitness landingForStep(LandingWitness witness, int moveCount,
            com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint before,
            com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint after) {
        return moveCount == 1 && witness != null && witness.valid() && before != null && after != null
                && before.worldPoint() != null && after.worldPoint() != null
                && finite(before.worldPoint()) && finite(after.worldPoint())
                && after.frameId() != null
                && !java.util.Objects.equals(before.frameId(), after.frameId())
                && java.util.Objects.equals(witness.fromFrame(), before.frameId())
                && java.util.Objects.equals(witness.toFrame(), after.frameId()) ? witness : null;
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    /** Admit a captured target only for one actual native move with known world-space endpoints. */
    public static NativeNormalContactPolicy.Target targetForStep(NativeNormalContactPolicy.Target target,
            int nativeMoveCount, com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint before,
            com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint after) {
        return nativeMoveCount == 1 && target != null
                && before != null && after != null
                && NativeHullContactEligibility.worldPoint(before.worldPoint(), before.frameId())
                && NativeHullContactEligibility.worldPoint(after.worldPoint(), after.frameId()) ? target : null;
    }
}
