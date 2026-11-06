package com.juicyslew.moonstation14.entities;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.Objects;
import java.util.UUID;

/** Entity-owned local orientation basis for cargo presented in a moving Sable frame. */
public final class LooseFrameOrientation {
    private LooseFrameOrientation() { }

    /** Immutable, normalized frame anchor. A null frame denotes ordinary world space. */
    public record Snapshot(UUID frameId, double x, double y, double z, double w) {
        public Snapshot {
            Quaterniond normalized = normalized(x, y, z, w);
            x = normalized.x; y = normalized.y; z = normalized.z; w = normalized.w;
        }

        public Quaterniond localBasis() { return new Quaterniond(x, y, z, w); }
    }

    public static Quaterniond normalized(double x, double y, double z, double w) {
        double lengthSquared = x*x + y*y + z*z + w*w;
        if (!Double.isFinite(lengthSquared) || lengthSquared < 1.0e-20) return new Quaterniond();
        return new Quaterniond(x, y, z, w).normalize();
    }

    public static Quaterniond worldBasis(Quaterniondc frameBasis, Quaterniondc localBasis) {
        return normalized(frameBasis.x(), frameBasis.y(), frameBasis.z(), frameBasis.w())
                .mul(normalized(localBasis.x(), localBasis.y(), localBasis.z(), localBasis.w())).normalize();
    }

    public static Quaterniond localBasis(Quaterniondc frameBasis, Quaterniondc worldBasis) {
        return normalized(frameBasis.x(), frameBasis.y(), frameBasis.z(), frameBasis.w()).invert()
                .mul(normalized(worldBasis.x(), worldBasis.y(), worldBasis.z(), worldBasis.w())).normalize();
    }

    public static Snapshot snapshot(UUID frameId, Quaterniondc localBasis) {
        Quaterniond q = normalized(localBasis.x(), localBasis.y(), localBasis.z(), localBasis.w());
        return new Snapshot(frameId, q.x, q.y, q.z, q.w);
    }

    public static boolean sameFrame(Snapshot snapshot, UUID currentFrame) {
        return snapshot != null && Objects.equals(snapshot.frameId(), currentFrame);
    }

    /** Resolve only a known, committed frame anchor; paused/unknown metadata holds the last safe basis. */
    public static Quaterniond resolveRender(Snapshot snapshot, UUID committedFrame, boolean paused,
                                            Quaterniondc parentBasis, Quaterniondc lastSafeWorldBasis) {
        if (paused || !sameFrame(snapshot, committedFrame)) return copy(lastSafeWorldBasis);
        if (committedFrame == null) return snapshot.localBasis();
        return parentBasis == null ? copy(lastSafeWorldBasis) : worldBasis(parentBasis, snapshot.localBasis());
    }

    /** Resolve the departing owner's latest known pose before falling back to a prior safe world sample. */
    public static Quaterniond worldBasisAtDetach(Snapshot snapshot, Quaterniondc currentOldFrameBasis,
                                                  Quaterniondc lastSafeWorldBasis) {
        if (snapshot == null) return copy(lastSafeWorldBasis);
        if (snapshot.frameId() == null) return snapshot.localBasis();
        if (currentOldFrameBasis != null) return worldBasis(currentOldFrameBasis, snapshot.localBasis());
        return copy(lastSafeWorldBasis);
    }

    /** Shared read-only authoritative pose composition for the current frame or a departing frame. */
    public static Quaterniond resolveAuthoritative(Snapshot snapshot, UUID currentFrame,
            Quaterniondc currentParentBasis, Quaterniondc departingParentBasis, Quaterniondc lastSafeWorldBasis) {
        if (snapshot == null) return copy(lastSafeWorldBasis);
        if (Objects.equals(snapshot.frameId(), currentFrame)) {
            if (currentFrame == null) return snapshot.localBasis();
            return currentParentBasis == null ? null : worldBasis(currentParentBasis, snapshot.localBasis());
        }
        return worldBasisAtDetach(snapshot, departingParentBasis, lastSafeWorldBasis);
    }

    private static Quaterniond copy(Quaterniondc source) {
        return source == null ? null : normalized(source.x(), source.y(), source.z(), source.w());
    }
}
