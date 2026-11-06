package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;

import java.util.UUID;

/** Session-local C2 frame basis/pause state; deliberately not an SDK tracking or parenting owner. */
public final class ClientFramePredictionState {
    private final NativeFrameInterval interval = new NativeFrameInterval();
    private LoadedFrameRayQuery.ActorPoint velocityBasis;
    private boolean paused;

    public boolean paused() { return paused; }
    public LoadedFrameRayQuery.ActorPoint velocityBasis() { return velocityBasis; }
    public UUID velocityBasisFrame() { return velocityBasis == null ? null : velocityBasis.frameId(); }
    public NativeFrameInterval interval() { return interval; }

    /** Uses temporal geometry only for an exact native-tracked frame in the controlled basis. */
    public com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.CollisionPosePolicy
    collisionPosePolicy(boolean exactNativeTracking, boolean nativeCarrierAdmitted) {
        if (paused || velocityBasis == null || velocityBasis.frameId() == null || !exactNativeTracking
                || nativeCarrierAdmitted) {
            return com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL;
        }
        return com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.CollisionPosePolicy.CURRENT_FRAME_GEOMETRY;
    }

    /** Compatibility for older fixture callers; the physics decision deliberately ignores animation/replay. */
    @Deprecated
    public com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.CollisionPosePolicy
    collisionPosePolicy(boolean ignoredLiveTick, boolean exactNativeTracking, boolean nativeCarrierAdmitted) {
        return collisionPosePolicy(exactNativeTracking, nativeCarrierAdmitted);
    }

    public void authorityProjected(LoadedFrameRayQuery.ActorPoint basis, long epoch,
                            NativeFrameInterval.PosePair poses) {
        velocityBasis = java.util.Objects.requireNonNull(basis);
        paused = false;
        if (basis.frameId() == null) interval.reset();
        else interval.consumeIfChanged(epoch, basis.frameId(), poses);
    }

    public void ordinaryWorldAuthority(LoadedFrameRayQuery.ActorPoint basis) {
        velocityBasis = java.util.Objects.requireNonNull(basis);
        paused = false;
        interval.reset();
    }

    public void pause() { paused = true; velocityBasis = null; }

    /** Native collision identity, not nearby geometry, commits the new actual movement basis. */
    public void acceptFreshMove(LoadedFrameRayQuery.ActorPoint actualAfterPoint) {
        if (actualAfterPoint != null) velocityBasis = actualAfterPoint;
    }

    public void reset() { velocityBasis = null; paused = false; interval.reset(); }

    /** Stock native inherited motion is admitted only from the synchronized, once-per-interval basis. */
    public boolean allowNativeCarrier(long epoch, LoadedFrameRayQuery.ActorPoint actualBefore,
                                      NativeFrameInterval.PosePair currentInterval) {
        if (paused || velocityBasis == null || actualBefore == null
                || !java.util.Objects.equals(velocityBasis.frameId(), actualBefore.frameId())) return false;
        UUID frame = velocityBasis.frameId();
        if (frame == null) return true;
        return currentInterval != null && interval.consumeIfChanged(epoch, frame, currentInterval);
    }

    /** Compatibility for older fixtures; live/replay has no effect on interval admission. */
    @Deprecated
    public boolean allowNativeCarrier(boolean ignoredLiveTick, long epoch,
                                      LoadedFrameRayQuery.ActorPoint actualBefore,
                                      NativeFrameInterval.PosePair currentInterval) {
        return allowNativeCarrier(epoch, actualBefore, currentInterval);
    }
}
