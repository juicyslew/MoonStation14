package com.juicyslew.moonstation14.entities;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import static org.junit.jupiter.api.Assertions.*;

class LooseClientPositionInterpolationTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Vec3 RAW = new Vec3(2, 3, 4);

    @Test void latestPacketWinsAndGetsAdoptedAsSingleRenderStep() {
        var state = new LooseClientPositionInterpolation();
        assertFalse(state.stage(0, 0, 0, 0.5, 0.75, 1));
        assertFalse(state.stage(0, 0, 0, 1, 1.5, 2));
        assertEquals(1, state.targetX(0));
        assertEquals(1.5, state.targetY(0));
        double[] next = state.advance(0, 0, 0);
        assertArrayEquals(new double[]{1, 1.5, 2}, next);
        assertFalse(state.pending());
        assertArrayEquals(next, state.advance(next[0], next[1], next[2]));
    }

    @Test void initialPositionAndLargeCorrectionSnapWithoutOldPositionStreak() {
        var initial = new LooseClientPositionInterpolation();
        assertTrue(initial.stage(0, 0, 0, 100, 64, -100));
        assertFalse(initial.pending());
        assertEquals(100, initial.targetX(100));
        assertArrayEquals(new double[]{100, 64, -100}, initial.advance(100, 64, -100));
        assertFalse(initial.stage(0, 0, 0, 4, 0, 0), "exact threshold remains interpolated");
        assertTrue(initial.stage(0, 0, 0, 4.001, 0, 0), "same strict threshold snaps");
    }

    @Test void successiveTickAdoptionsConvergeToServerAuthority() {
        var state = new LooseClientPositionInterpolation();
        double x = 0, y = 0, z = 0;
        for (int i = 1; i <= 20; i++) {
            assertFalse(state.stage(x, y, z, i, i * 2, -i));
            double[] next = state.advance(x, y, z);
            x = next[0]; y = next[1]; z = next[2];
        }
        assertEquals(20, x);
        assertEquals(40, y);
        assertEquals(-20, z);
    }

    @Test void exactPacketFrameWinsAndLatestPacketReplacesAnchor() {
        var state = new LooseClientPositionInterpolation();
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, RAW); // native tracking may be A
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, B, new Vec3(5, 6, 7));
        assertEquals(B, state.frameId());
        assertEquals(new Vec3(5, 6, 7), state.storagePoint());
    }

    @Test void worldClearsAnchorWithoutTakingPositionOwnership() {
        var state = new LooseClientPositionInterpolation();
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, RAW);
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.WORLD, null, null);
        assertNull(state.frameId());
        assertFalse(state.pending());
    }

    @Test void unknownStorageDropPreservesAnchorButPausesProjectionUntilNextValidPacket() {
        var state = new LooseClientPositionInterpolation();
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, RAW);
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.UNKNOWN_STORAGE, null, null);
        assertEquals(A, state.frameId());
        assertTrue(state.frameProjectionPaused());
        assertNull(state.projectFrame((id, point) -> point.add(100, 0, 0)),
                "a moving old frame cannot move presentation while an unknown packet is pending");
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, B, new Vec3(1, 2, 3));
        assertFalse(state.frameProjectionPaused());
        assertEquals(new Vec3(1, 2, 3), state.projectFrame((id, point) -> {
            assertEquals(B, id);
            return point;
        }));
    }

    @Test void exactAnchorIsProjectedAgainForPoseTranslationAndRotationAndNotRemapped() {
        var state = new LooseClientPositionInterpolation();
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, RAW);
        assertEquals(new Vec3(12, 3, 4), state.projectFrame((id, point) -> {
            assertEquals(A, id);
            return point.add(10, 0, 0);
        }));
        assertEquals(new Vec3(-4, 3, 2), state.projectFrame((id, point) -> {
            assertEquals(A, id); // a replacement frame at the same location is not selected
            return new Vec3(-point.z, point.y, point.x);
        }));
    }

    @Test void worldReleasePacketStopsFrameProjectionAcrossLaterYawTicks() {
        var state = new LooseClientPositionInterpolation();
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, RAW);
        // The same packet-local point legitimately follows the carrier while supported.
        assertEquals(new Vec3(12, 3, 4), state.projectFrame((id, point) -> point.add(10, 0, 0)));
        assertEquals(new Vec3(-4, 3, 2), state.projectFrame((id, point) ->
                new Vec3(-point.z, point.y, point.x)));

        // Once the native packet anchor changes to world ownership, later carrier
        // pose updates (with no intervening cargo packet) must not drag presentation.
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.WORLD, null, null);
        for (int tick = 0; tick < 8; tick++) {
            assertNull(state.projectFrame((id, point) -> {
                fail("released cargo must not reproject through the former frame");
                return null;
            }));
            assertFalse(state.pending());
            assertNull(state.frameId());
        }
    }

    @Test void ordinaryWorldInterpolationKeepsLatestTargetAndSnapHistoryContract() {
        var state = new LooseClientPositionInterpolation();
        assertFalse(state.stage(0, 0, 0, 1, 0, 0));
        assertFalse(state.stage(0, 0, 0, 2, 0, 0));
        assertArrayEquals(new double[]{2, 0, 0}, state.advance(0, 0, 0));
        assertTrue(state.stage(0, 0, 0, 10, 0, 0));
        assertFalse(state.pending());
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, RAW);
        assertFalse(state.shouldSnapFrameProjection(0, 0, 0, new Vec3(4, 0, 0)));
        assertTrue(state.shouldSnapFrameProjection(0, 0, 0, new Vec3(4.001, 0, 0)));
    }

    @Test void localRenderAnchorAdvancesOnlyAtTickBoundaryAndInterpolatesOnce() {
        var state = new LooseClientPositionInterpolation();
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, new Vec3(1, 0, 0));
        state.beginRenderTick(new Vec3(1, 0, 0), (id, point) -> point);
        assertEquals(new Vec3(1, 0, 0), state.renderAnchor(.5f));
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, new Vec3(3, 0, 0));
        assertEquals(new Vec3(1, 0, 0), state.renderAnchor(.5f), "packet receipt must not move current render anchor");
        state.beginRenderTick(new Vec3(1, 0, 0), (id, point) -> point);
        assertEquals(new Vec3(2, 0, 0), state.renderAnchor(.5f));
        assertEquals(new Vec3(3, 0, 0), state.renderAnchor(1f));
    }

    @Test void ownershipChangesAndUnknownStorageInvalidateRenderAnchors() {
        var state = new LooseClientPositionInterpolation();
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, RAW);
        state.beginRenderTick(RAW.add(8, 0, 0), (id, world) -> {
            assertEquals(A, id);
            return world.subtract(8, 0, 0);
        });
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, B, RAW);
        assertNull(state.renderAnchor(.5f));
        state.beginRenderTick(RAW.add(8, 0, 0), (id, world) -> world.subtract(8, 0, 0));
        assertEquals(RAW, state.renderAnchor(.5f));
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.UNKNOWN_STORAGE, null, null);
        assertNull(state.renderAnchor(.5f));
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.WORLD, null, null);
        assertNull(state.renderAnchor(.5f));
    }

    @Test void firstFrameTickPreservesOldWorldPointThenMovesToPacketAnchor() {
        var state = new LooseClientPositionInterpolation();
        Vec3 priorWorld = new Vec3(10, 0, 0);
        Vec3 latestStorage = new Vec3(2, 0, 0);
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, latestStorage);
        state.beginRenderTick(priorWorld, (id, world) -> {
            assertEquals(A, id);
            return world.subtract(8, 0, 0); // inverse of the validated entry render pose
        });
        assertEquals(new Vec3(2, 0, 0), state.renderAnchor(0));
        assertEquals(latestStorage, state.renderAnchor(1));
        assertNull(new LooseClientPositionInterpolation().renderAnchor(.5f));
    }

    @Test void firstFrameRotatingPoseFollowsArcNotWorldChordAndSnapAlignsEndpoints() {
        var state = new LooseClientPositionInterpolation();
        org.joml.Quaterniond q0 = new org.joml.Quaterniond();
        org.joml.Quaterniond q1 = new org.joml.Quaterniond().rotationY(Math.PI / 2);
        Vec3 currentLocal = new Vec3(3, 0, 0);
        org.joml.Vector3d oldWorldVector = q0.transform(new org.joml.Vector3d(2, 0, 0));
        Vec3 oldWorld = new Vec3(oldWorldVector.x, oldWorldVector.y, oldWorldVector.z);
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, currentLocal);
        state.beginRenderTick(oldWorld, (id, world) -> {
            org.joml.Vector3d v = q0.invert(new org.joml.Quaterniond()).transform(
                    new org.joml.Vector3d(world.x, world.y, world.z));
            return new Vec3(v.x, v.y, v.z);
        });
        assertEquals(oldWorld, project(state.renderAnchor(0), q0));
        assertEquals(project(currentLocal, q1), project(state.renderAnchor(1), q1));
        var qHalf = new org.joml.Quaterniond().rotationY(Math.PI / 4);
        Vec3 arcMidpoint = project(state.renderAnchor(.5f), qHalf);
        Vec3 chordMidpoint = oldWorld.lerp(project(currentLocal, q1), .5);
        assertTrue(arcMidpoint.distanceTo(chordMidpoint) > .1);

        // A spawn at the carrier's current pose has world coordinates from q1, not the
        // prior q0 pose. Seeding from inverse(q0) would introduce this full offset.
        Vec3 currentWorld = project(currentLocal, q1);
        Vec3 wrongOldPoseSeed = inverseProject(currentWorld, q0);
        assertTrue(wrongOldPoseSeed.distanceTo(currentLocal) > .1);
        var spawn = new LooseClientPositionInterpolation();
        assertTrue(spawn.initializeSpawnFrameAnchor(A, currentLocal));
        assertEquals(currentWorld, project(spawn.renderAnchor(0f), q1));
        assertEquals(currentWorld, project(spawn.renderAnchor(.5f), q1));
        assertEquals(currentWorld, project(spawn.renderAnchor(1f), q1));

        state.snapRenderAnchorToCurrent();
        assertEquals(currentLocal, state.renderAnchor(0));
        assertEquals(currentLocal, state.renderAnchor(1));
    }

    @Test void spawnFrameAnchorSeedsBothEndpointsWithoutInverseOldWorldPose() {
        var state = new LooseClientPositionInterpolation();
        Vec3 spawnAnchor = new Vec3(7, 2, -3);
        assertTrue(state.initializeSpawnFrameAnchor(A, spawnAnchor));
        state.beginRenderTick(new Vec3(100, 40, -90), (id, world) -> {
            fail("initial frame spawn must not inverse-project the unrelated world spawn pose");
            return null;
        });
        for (float partial : new float[]{0f, .25f, .5f, 1f})
            assertEquals(spawnAnchor, state.renderAnchor(partial));
        assertEquals(new Vec3(8, 2, -3), state.projectFrame((id, point) -> point.add(1, 0, 0)));
    }

    @Test void invalidSpawnAnchorFailsClosedAndLaterWorldToFrameEntryRemainsContinuous() {
        var invalid = new LooseClientPositionInterpolation();
        assertFalse(invalid.initializeSpawnFrameAnchor(A, new Vec3(Double.NaN, 0, 0)));
        assertNull(invalid.renderAnchor(.5f));

        var state = new LooseClientPositionInterpolation();
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.WORLD, null, null);
        state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, A, RAW);
        Vec3 priorWorld = new Vec3(10, 0, 0);
        state.beginRenderTick(priorWorld, (id, world) -> world.subtract(8, 0, 0));
        assertEquals(new Vec3(2, 0, 0), state.renderAnchor(0));
        assertEquals(RAW, state.renderAnchor(1));
    }

    @Test void productionSpawnAnchorCodecRoundTripsLateTrackerCurrentPointAndWorldSpawn() {
        Vec3 birthPoint = new Vec3(1, 2, 3);
        Vec3 currentPoint = new Vec3(9, -4, 7);
        var current = new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                new Vec3(40, 50, 60), currentPoint, A);
        var frameBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LooseItemEntity.writeInitialSpawnAnchor(frameBuffer, current);
            frameBuffer.readerIndex(0);
            var decoded = LooseItemEntity.readInitialSpawnAnchor(frameBuffer);
            assertEquals(LooseClientPositionInterpolation.PacketKind.FRAME, decoded.kind());
            assertEquals(A, decoded.frameId());
            assertEquals(currentPoint, decoded.storagePoint());
            assertNotEquals(birthPoint, decoded.storagePoint(), "each tracker captures the current owner point, not a cached birth anchor");
        } finally { frameBuffer.release(); }

        var worldBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LooseItemEntity.writeInitialSpawnAnchor(worldBuffer, new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                    new Vec3(3, 4, 5), new Vec3(3, 4, 5), null));
            worldBuffer.readerIndex(0);
            var decoded = LooseItemEntity.readInitialSpawnAnchor(worldBuffer);
            assertEquals(LooseClientPositionInterpolation.PacketKind.WORLD, decoded.kind());
            assertNull(decoded.frameId());
            assertNull(decoded.storagePoint());
        } finally { worldBuffer.release(); }

        var unknown = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LooseItemEntity.writeInitialSpawnAnchor(unknown, null);
            unknown.readerIndex(0);
            var decoded = LooseItemEntity.readInitialSpawnAnchor(unknown);
            assertEquals(LooseClientPositionInterpolation.PacketKind.UNKNOWN_STORAGE, decoded.kind());
            assertNull(decoded.frameId());
            assertNull(decoded.storagePoint());
        } finally { unknown.release(); }

        var invalidFrame = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            invalidFrame.writeEnum(LooseClientPositionInterpolation.PacketKind.FRAME);
            invalidFrame.writeUUID(A);
            invalidFrame.writeDouble(Double.NaN);
            invalidFrame.writeDouble(0);
            invalidFrame.writeDouble(0);
            invalidFrame.readerIndex(0);
            var decoded = LooseItemEntity.readInitialSpawnAnchor(invalidFrame);
            assertEquals(LooseClientPositionInterpolation.PacketKind.UNKNOWN_STORAGE, decoded.kind());
            var state = new LooseClientPositionInterpolation();
            state.packetMetadata(decoded.kind(), decoded.frameId(), decoded.storagePoint());
            assertTrue(state.frameProjectionPaused());
            assertNull(state.renderAnchor(.5f));
            assertNull(state.projectFrame((id, point) -> fail("invalid spawn storage must never project raw coordinates")));
            state.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, B, RAW);
            assertFalse(state.frameProjectionPaused(), "valid metadata resumes the existing path");
            assertEquals(RAW, state.storagePoint());
        } finally { invalidFrame.release(); }
    }

    private static Vec3 project(Vec3 local, org.joml.Quaterniond pose) {
        var point = pose.transform(new org.joml.Vector3d(local.x, local.y, local.z));
        return new Vec3(point.x, point.y, point.z);
    }
    private static Vec3 inverseProject(Vec3 world, org.joml.Quaterniond pose) {
        var point = pose.invert(new org.joml.Quaterniond()).transform(new org.joml.Vector3d(world.x, world.y, world.z));
        return new Vec3(point.x, point.y, point.z);
    }
}
