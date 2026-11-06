package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.UUID;
import java.util.Optional;

/** Small session/body keyed cache and one-physical-move adapter for angular prediction. */
final class AngularRecoveryPrediction {
    record ReplaySupport(boolean usable, AngularRecoveryModel.Vec3 omega) {}

    static ReplaySupport supportForReplay(Optional<GhostPredictionHistory.AngularSupportEvidence> evidence,
            UUID authorityFrameId) {
        if (evidence == null || evidence.isEmpty()) return new ReplaySupport(false, zero());
        var fact = evidence.get();
        if (!fact.supported()) return new ReplaySupport(true, fact.omega());
        return fact.frameId() != null && fact.frameId().equals(authorityFrameId)
                ? new ReplaySupport(true, fact.omega()) : new ReplaySupport(false, zero());
    }

    /** Selects recorded launch support only for a replayed grounded departure; existing flight owns its omega. */
    static AngularRecoveryModel.Vec3 omegaForReplayDeparture(Optional<GhostPredictionHistory.AngularSupportEvidence> evidence,
            UUID authorityFrameId, boolean wasGrounded, AngularRecoveryModel.Vec3 sampledOmega) {
        if (!wasGrounded) return sampledOmega;
        ReplaySupport support = supportForReplay(evidence, authorityFrameId);
        return support.usable() ? support.omega() : zero();
    }
    private long epoch;
    private UUID body, frame;
    private NativeFrameInterval.PosePair poses;
    private AngularRecoveryModel.Vec3 cachedOmega = zero();
    private boolean bound;

    AngularRecoveryModel.Vec3 sample(long currentEpoch, UUID currentBody, UUID currentFrame,
            boolean supported, NativeFrameInterval.PosePair currentPoses,
            Quaterniondc lastOrientation, Quaterniondc logicalOrientation, double seconds) {
        if (!supported || currentEpoch <= 0 || currentBody == null || currentFrame == null || currentPoses == null
                || lastOrientation == null || logicalOrientation == null || !(seconds > 0) || !Double.isFinite(seconds)) {
            reset(); return zero();
        }
        if (bound && epoch == currentEpoch && currentBody.equals(body) && currentFrame.equals(frame)
                && currentPoses.equals(poses)) return cachedOmega;
        boolean sameSupport = bound && epoch == currentEpoch && currentBody.equals(body) && currentFrame.equals(frame);
        epoch=currentEpoch; body=currentBody; frame=currentFrame; poses=currentPoses; bound=true;
        cachedOmega = angularVelocity(lastOrientation, logicalOrientation, seconds);
        return cachedOmega;
    }

    AngularRecoveryModel.State afterMove(long currentEpoch, UUID currentBody, boolean wasGrounded,
            boolean isGrounded, AngularRecoveryModel.State prior, AngularRecoveryModel.Vec3 sampledOmega,
            AngularRecoveryModel.Vec3 gravityUp, double dt, double grace, double recovery) {
        if (currentEpoch != epoch || !java.util.Objects.equals(body, currentBody)) reset();
        return AngularRecoveryModel.afterMove(prior, wasGrounded, isGrounded,
                prior == null ? identity() : prior.orientation(), sampledOmega, gravityUp,
                dt, grace, recovery).nextState();
    }

    void reset() { epoch=0; body=frame=null; poses=null; cachedOmega=zero(); bound=false; }

    private static AngularRecoveryModel.Vec3 angularVelocity(Quaterniondc last, Quaterniondc logical, double dt) {
        Quaterniond a = normalized(last), b = normalized(logical);
        if (a == null || b == null) return zero();
        Quaterniond delta = b.mul(new Quaterniond(a).conjugate()).normalize();
        if (delta.w < 0) delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
        double sinHalf = Math.sqrt(delta.x*delta.x + delta.y*delta.y + delta.z*delta.z);
        if (sinHalf < 1e-12) return zero();
        double angle = 2 * Math.atan2(sinHalf, Math.max(0, delta.w));
        double scale = angle / (sinHalf * dt);
        return new AngularRecoveryModel.Vec3(delta.x*scale, delta.y*scale, delta.z*scale);
    }
    private static Quaterniond normalized(Quaterniondc q) {
        Quaterniond result = new Quaterniond(q);
        double length = Math.sqrt(result.lengthSquared());
        return !Double.isFinite(length) || length < 1e-12 ? null : result.normalize();
    }
    private static AngularRecoveryModel.Quat identity() { return new AngularRecoveryModel.Quat(1,0,0,0); }
    private static AngularRecoveryModel.Vec3 zero() { return new AngularRecoveryModel.Vec3(0,0,0); }
}
