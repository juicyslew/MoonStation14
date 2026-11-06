package com.juicyslew.moonstation14.entities;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LooseFrameOrientationTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test void anchorAndFrameSwapPreserveTheFullWorldBasis() {
        Quaterniond initialWorld = new Quaterniond().rotationYXZ(.4, -.2, .7);
        Quaterniond yaw90 = new Quaterniond().rotationY(Math.PI / 2);
        var a = LooseFrameOrientation.snapshot(A, LooseFrameOrientation.localBasis(yaw90, initialWorld));
        Quaterniond afterA = LooseFrameOrientation.worldBasis(yaw90, a.localBasis());
        assertTrue(initialWorld.equals(afterA, 1.0e-10));

        Quaterniond pitchRollFrame = new Quaterniond().rotationYXZ(-.3, .6, -.5);
        var b = LooseFrameOrientation.snapshot(B, LooseFrameOrientation.localBasis(pitchRollFrame, afterA));
        Quaterniond afterB = LooseFrameOrientation.worldBasis(pitchRollFrame, b.localBasis());
        assertTrue(afterA.equals(afterB, 1.0e-10));

        var world = LooseFrameOrientation.snapshot(null, afterB);
        assertTrue(afterB.equals(world.localBasis(), 1.0e-10));
    }

    @Test void snapshotIsNormalizedAndFrameMatchFailsClosed() {
        var basis = new LooseFrameOrientation.Snapshot(A, 0, 0, 0, 2);
        var normalized = basis.localBasis();
        assertEquals(1, Math.sqrt(normalized.x*normalized.x + normalized.y*normalized.y
                + normalized.z*normalized.z + normalized.w*normalized.w), 1.0e-12);
        assertTrue(LooseFrameOrientation.sameFrame(basis, A));
        assertFalse(LooseFrameOrientation.sameFrame(basis, B));
        assertFalse(LooseFrameOrientation.sameFrame(basis, null));
    }

    @Test void invalidQuaternionFallsBackToIdentity() {
        assertTrue(new LooseFrameOrientation.Snapshot(A, Double.NaN, 0, 0, 0)
                .localBasis().equals(new Quaterniond(), 1.0e-12));
    }

    @Test void scalarSpinRemainsLocalWhenBasisHasPitchAndRoll() {
        Quaterniond frame = new Quaterniond().rotationYXZ(.7, .4, -.3);
        Quaterniond local = new Quaterniond().rotationYXZ(-.2, .3, .6);
        Quaterniond spin = new Quaterniond().rotationY(.8);
        Quaterniond baseWorld = LooseFrameOrientation.worldBasis(frame, local);
        Quaterniond before = new Quaterniond(baseWorld).mul(spin).normalize();
        Quaterniond movedLocal = LooseFrameOrientation.localBasis(new Quaterniond(), baseWorld);
        Quaterniond after = new Quaterniond(movedLocal).mul(spin).normalize();
        assertTrue(before.equals(after, 1.0e-10));
    }

    @Test void invalidOrIncompleteSerializedQuaternionIsRejectedRatherThanIdentity() {
        var incomplete = new net.minecraft.nbt.CompoundTag();
        incomplete.putDouble("W", 1);
        assertNull(LooseItemEntity.decodeOrientation(incomplete));
        var invalid = new net.minecraft.nbt.CompoundTag();
        invalid.putDouble("X", Double.NaN); invalid.putDouble("Y", 0);
        invalid.putDouble("Z", 0); invalid.putDouble("W", 0);
        assertNull(LooseItemEntity.decodeOrientation(invalid));
    }

    @Test void snapshotCompoundRoundTripsFrameAndNormalizedQuaternion() {
        Quaterniond source = new Quaterniond().rotationYXZ(.2, -.4, .8).mul(2);
        var snapshot = LooseFrameOrientation.snapshot(A, source);
        var decoded = LooseItemEntity.decodeOrientation(LooseItemEntity.encodeOrientation(snapshot));
        assertNotNull(decoded);
        assertEquals(A, decoded.frameId());
        assertTrue(snapshot.localBasis().equals(decoded.localBasis(), 1.0e-12));
    }

    @Test void detachUsesDepartingFramesLatestPoseInsteadOfStaleWorldSample() {
        Quaterniond savedLocal = new Quaterniond().rotationYXZ(.2, -.3, .1);
        var snapshot = LooseFrameOrientation.snapshot(A, savedLocal);
        Quaterniond stale = LooseFrameOrientation.worldBasis(new Quaterniond().rotationY(.1), savedLocal);
        Quaterniond latestOldFrame = new Quaterniond().rotationYXZ(1.1, .4, -.7);
        Quaterniond expected = LooseFrameOrientation.worldBasis(latestOldFrame, savedLocal);
        Quaterniond detached = LooseFrameOrientation.worldBasisAtDetach(snapshot, latestOldFrame, stale);
        assertTrue(expected.equals(detached, 1.0e-12));
        assertFalse(stale.equals(detached, 1.0e-4));
    }

    @Test void authoritativeResolutionCoversCurrentDepartureAndUnknownPoseBranches() {
        Quaterniond local = new Quaterniond().rotationYXZ(.2, -.3, .1);
        var currentFrameSnapshot = LooseFrameOrientation.snapshot(A, local);
        Quaterniond parent = new Quaterniond().rotationYXZ(.8, .4, -.2);
        Quaterniond current = LooseFrameOrientation.resolveAuthoritative(currentFrameSnapshot, A, parent,
                null, null);
        assertTrue(LooseFrameOrientation.worldBasis(parent, local).equals(current, 1e-12));
        assertNotSame(currentFrameSnapshot.localBasis(), current);

        Quaterniond oldParent = new Quaterniond().rotationYXZ(-.5, .2, .7);
        Quaterniond safe = new Quaterniond().rotationYXZ(.3, -.1, .2);
        Quaterniond detached = LooseFrameOrientation.resolveAuthoritative(currentFrameSnapshot, B,
                new Quaterniond(), oldParent, safe);
        assertTrue(LooseFrameOrientation.worldBasis(oldParent, local).equals(detached, 1e-12));
        Quaterniond held = LooseFrameOrientation.resolveAuthoritative(currentFrameSnapshot, B,
                new Quaterniond(), null, safe);
        assertTrue(safe.equals(held, 1e-12));
        assertNotSame(safe, held);
        assertNull(LooseFrameOrientation.resolveAuthoritative(currentFrameSnapshot, A, null, null, null));

        var worldSnapshot = LooseFrameOrientation.snapshot(null, local);
        Quaterniond world = LooseFrameOrientation.resolveAuthoritative(worldSnapshot, null, new Quaterniond(),
                null, null);
        assertTrue(local.equals(world, 1e-12));
        assertNotSame(worldSnapshot.localBasis(), world);
    }

    @Test void pausedMismatchedAndUnknownAnchorsHoldLastSafeBasisThenRecover() {
        var snapshot = LooseFrameOrientation.snapshot(A, new Quaterniond().rotationY(.5));
        Quaterniond safe = new Quaterniond().rotationYXZ(.3, -.1, .2);
        assertTrue(safe.equals(LooseFrameOrientation.resolveRender(snapshot, null, true,
                null, safe), 1.0e-12));
        assertTrue(safe.equals(LooseFrameOrientation.resolveRender(snapshot, B, false,
                new Quaterniond(), safe), 1.0e-12));
        assertTrue(LooseFrameOrientation.worldBasis(new Quaterniond().rotationY(1), snapshot.localBasis())
                .equals(LooseFrameOrientation.resolveRender(snapshot, A, false,
                        new Quaterniond().rotationY(1), safe), 1.0e-12));
    }
}
