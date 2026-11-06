package com.juicyslew.moonstation14.ms14.player_body_control.network;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyView;
import net.minecraft.core.BlockPos;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostControlPayloadsTest {
    @Test
    void snapshotCarriesCompletedSableStepAndFrozenAirReference() {
        var frameId = java.util.UUID.randomUUID();
        var reference = new com.juicyslew.moonstation14.ms14.movement.AirMotionReference(frameId,
                new com.juicyslew.moonstation14.ms14.movement.MovementVector(2.25, -1.5, 4));
        var airborne = new GhostControlPayloads.Snapshot(3, 7, MobHarnessKind.CHARACTER, 99, 4,
                1, 2, 3, 0, 0, 2, 0, 4, false, 1, 1, false, 0,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED, java.util.Optional.empty(),
                java.util.Optional.empty(), 0).withServerTemporalMetadata(java.util.OptionalLong.of(12), java.util.Optional.of(reference));
        var decodedAir = roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, airborne);
        assertEquals(java.util.OptionalLong.of(12), decodedAir.completedSableSimulationStep());
        assertEquals(java.util.Optional.of(reference), decodedAir.airMotionReference());

        var grounded = airborne.withServerTemporalMetadata(java.util.OptionalLong.of(13), java.util.Optional.empty());
        assertEquals(grounded, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, grounded));
        assertTrue(new GhostControlPayloads.Snapshot(1, 1, 0, 0, 0, 0, 0, 0, 0)
                .completedSableSimulationStep().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new com.juicyslew.moonstation14.ms14.movement.AirMotionReference(
                frameId, new com.juicyslew.moonstation14.ms14.movement.MovementVector(Double.NaN, 0, 0)));
    }
    @Test
    void allEightPayloadsHaveDistinctIdsAndRoundTrip() {
        var begin = new GhostControlPayloads.Begin(1, 42);
        var ready = new GhostControlPayloads.Ready(2);
        var offer = new GhostControlPayloads.Offer(2, 50, MobHarnessKind.CHARACTER);
        var offerReady = new GhostControlPayloads.OfferReady(2, 50, MobHarnessKind.CHARACTER);
        var commit = new GhostControlPayloads.Commit(3);
        var intent = new GhostControlPayloads.Intent(4, 5, (short) -1000, (short) 1000,
                (byte) -1, GhostControlPayloads.BUTTON_JUMP | GhostControlPayloads.BUTTON_SPRINT,
                -180.0f, 90.0f);
        var stop = new GhostControlPayloads.Stop(6);
        var snapshot = new GhostControlPayloads.Snapshot(7, 42, 8, 5,
                30_000_000.0, -30_000_000.0, 12.5, 180.0f, -90.0f);
        var characterBegin = new GhostControlPayloads.Begin(9, 43, MobHarnessKind.CHARACTER);
        var configuredGhostBegin = new GhostControlPayloads.Begin(11, 45, MobHarnessKind.GHOST, 3, 7);
        var characterSnapshot = new GhostControlPayloads.Snapshot(10, 44,
                MobHarnessKind.CHARACTER, 11, 8, 1, 2, 3, 4, 5,
                6.5, -7.5, 8.5, true, 1.5f, 1.5f, true,
                GhostControlPayloads.Snapshot.MovementMode.TRANSIT);

        Set<CustomPacketPayload.Type<?>> ghostTypes = Set.of(begin.type(), ready.type(), offer.type(), offerReady.type(), commit.type(),
                intent.type(), stop.type(), snapshot.type());
        assertEquals(8, ghostTypes.size());
        assertEquals(begin, roundTrip(GhostControlPayloads.Begin.STREAM_CODEC, begin));
        assertEquals(ready, roundTrip(GhostControlPayloads.Ready.STREAM_CODEC, ready));
        assertEquals(offer, roundTrip(GhostControlPayloads.Offer.STREAM_CODEC, offer));
        assertEquals(offerReady, roundTrip(GhostControlPayloads.OfferReady.STREAM_CODEC, offerReady));
        assertEquals(commit, roundTrip(GhostControlPayloads.Commit.STREAM_CODEC, commit));
        assertEquals(intent, roundTrip(GhostControlPayloads.Intent.STREAM_CODEC, intent));
        assertEquals(stop, roundTrip(GhostControlPayloads.Stop.STREAM_CODEC, stop));
        assertEquals(snapshot, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, snapshot));
        assertEquals(characterBegin, roundTrip(GhostControlPayloads.Begin.STREAM_CODEC, characterBegin));
        assertEquals(configuredGhostBegin, roundTrip(GhostControlPayloads.Begin.STREAM_CODEC, configuredGhostBegin));
        assertEquals(characterSnapshot, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, characterSnapshot));
        var flightSnapshot = new GhostControlPayloads.Snapshot(10, 44, MobHarnessKind.CHARACTER, 11, 8,
                1, 2, 3, 4, 5, 6.5, -7.5, 8.5, true, 1, 1, false, 6,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED, java.util.Optional.empty());
        assertEquals(6, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, flightSnapshot).externalFlightTicks());
        assertEquals(MobHarnessKind.GHOST, begin.harnessKind());
        assertEquals(42, begin.harnessEntityId());
        assertEquals(42, begin.ghostEntityId());
        assertEquals(0, snapshot.velocityX());
        assertFalse(snapshot.onGround());
        assertEquals(1, snapshot.surfaceFactor());
        assertEquals(1, snapshot.voluntarySpeedFactor());
        assertFalse(snapshot.movementBlocked());
        assertTrue(characterSnapshot.movementBlocked());
        assertTrue(roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, characterSnapshot).movementBlocked(),
                "authoritative movement-blocked capability survives the wire round trip");
        assertEquals(snapshot.harnessEntityId(), snapshot.ghostEntityId());
        assertEquals(1.5f, characterSnapshot.surfaceFactor());
        assertEquals(1.5f, characterSnapshot.voluntarySpeedFactor());
        assertEquals(GhostControlPayloads.Snapshot.MovementMode.TRANSIT,
                roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, characterSnapshot).movementMode());
        var unitSnapshot = new GhostControlPayloads.Snapshot(10, 44, MobHarnessKind.CHARACTER,
                12, 8, 1, 2, 3, 4, 5, 0, 0, 0, true, 1, 1, false,
                GhostControlPayloads.Snapshot.MovementMode.UNIT);
        assertEquals(unitSnapshot, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, unitSnapshot));
        var view = new ContainedBodyView(java.util.UUID.randomUUID(), java.util.Optional.of(new BlockPos(2, 64, -3)), false);
        var viewSnapshot = new GhostControlPayloads.Snapshot(11, 44, MobHarnessKind.CHARACTER, 13, 9,
                1, 2, 3, 4, 5, 0, 0, 0, true, 1, 1, false,
                GhostControlPayloads.Snapshot.MovementMode.UNIT, java.util.Optional.of(view));
        var decodedViewSnapshot = roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, viewSnapshot);
        assertEquals(viewSnapshot, decodedViewSnapshot);
        assertTrue(decodedViewSnapshot.containedView().orElseThrow().worldTarget().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(1, 1,
                MobHarnessKind.GHOST, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, false,
                GhostControlPayloads.Snapshot.MovementMode.UNIT));
        var frame = new GhostControlPayloads.Snapshot.FrameState(java.util.UUID.randomUUID(), 123.125, -4.5, 8.75);
        var framed = new GhostControlPayloads.Snapshot(12, 44, MobHarnessKind.CHARACTER, 14, 10,
                1, 2, 3, 4, 5, 0, 0, 0, true, 1, 1, false,
                0, GhostControlPayloads.Snapshot.MovementMode.GROUNDED, java.util.Optional.empty(), java.util.Optional.of(frame));
        assertEquals(framed, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, framed));
        var movementRebase = new GhostControlPayloads.Snapshot(12, 44, MobHarnessKind.CHARACTER, 15, 11,
                1, 2, 3, 4, 5, 0, 0, 0, true, 1, 1, false, 0,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED, java.util.Optional.empty(),
                java.util.Optional.of(frame), 1);
        assertEquals(1, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, movementRebase).movementRebaseRevision());
        assertEquals(java.util.Optional.empty(), snapshot.frameState());
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot.FrameState(
                java.util.UUID.randomUUID(), Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot.FrameState(
                java.util.UUID.randomUUID(), 0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot.FrameState(
                java.util.UUID.randomUUID(), 0, 0, Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(12, 44, MobHarnessKind.GHOST,
                14, 10, 1, 2, 3, 4, 5, 0, 0, 0, true, 1, 1, false,
                0, GhostControlPayloads.Snapshot.MovementMode.GROUNDED, java.util.Optional.empty(), java.util.Optional.of(frame)));
    }

    @Test
    void angularRecoverySnapshotRoundTripsAndValidates() {
        var state = new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State(
                new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Quat(1, .1, .2, .3),
                new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(1, 2, 3), .75, true);
        var legacy = new GhostControlPayloads.Snapshot(12, 44, MobHarnessKind.CHARACTER, 15, 11,
                1, 2, 3, 4, 5, 0, 0, 0, true, 1, 1, false, 0,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED, java.util.Optional.empty(), java.util.Optional.empty(), 0);
        assertTrue(legacy.angularRecoveryState().isEmpty());
        var snapshot = legacy.withAngularRecoveryState(java.util.Optional.of(state));
        assertEquals(snapshot, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, snapshot));
        assertThrows(IllegalArgumentException.class, () -> new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Quat(0, 0, 0, 0));
        var transit = new GhostControlPayloads.Snapshot(12, 44, MobHarnessKind.CHARACTER, 15, 11,
                1, 2, 3, 4, 5, 0, 0, 0, true, 1, 1, false,
                GhostControlPayloads.Snapshot.MovementMode.TRANSIT);
        assertThrows(IllegalArgumentException.class, () -> transit.withAngularRecoveryState(java.util.Optional.of(state)));
        var ghost = new GhostControlPayloads.Snapshot(12, 44, 15, 11, 1, 2, 3, 4, 5);
        assertThrows(IllegalArgumentException.class, () -> ghost.withAngularRecoveryState(java.util.Optional.of(state)));
    }

    @Test
    void supportedMovementReferenceRoundTripsWithoutChangingLookAngles() {
        var emptyIntent = new GhostControlPayloads.Intent(1, 2, (short) 3, (short) -4,
                (byte) 0, 0, 179.5f, -45.25f);
        assertTrue(emptyIntent.supportedMovementReference().isEmpty());
        assertEquals(emptyIntent, roundTrip(GhostControlPayloads.Intent.STREAM_CODEC, emptyIntent));
        var frame = java.util.UUID.randomUUID();
        var reference = new GhostControlPayloads.SupportedMovementReference(frame, -90f);
        var intent = new GhostControlPayloads.Intent(1, 3, (short) 3, (short) -4,
                (byte) 0, 0, 179.5f, -45.25f, java.util.Optional.of(reference));
        var decoded = roundTrip(GhostControlPayloads.Intent.STREAM_CODEC, intent);
        assertEquals(intent, decoded);
        assertEquals(179.5f, decoded.yaw());
        assertEquals(-45.25f, decoded.pitch());
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.SupportedMovementReference(frame, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.SupportedMovementReference(frame, 180.1f));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.SupportedMovementReference(frame, -180.1f));
        assertThrows(NullPointerException.class, () -> new GhostControlPayloads.SupportedMovementReference(null, 0));
        assertThrows(NullPointerException.class, () -> new GhostControlPayloads.Intent(1, 3,
                (short) 0, (short) 0, (byte) 0, 0, 0, 0, null));
    }

    @Test
    void constructorsAndDecoderRejectInvalidFields() {
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Begin(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Begin(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Begin(1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Ready(0));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Offer(0, 1, MobHarnessKind.CHARACTER));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.OfferReady(1, -1, MobHarnessKind.GHOST));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Commit(0));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Stop(0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(0, 1, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, -1, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, -1, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, -1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, Double.NaN, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, Double.POSITIVE_INFINITY, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, 0, Double.NEGATIVE_INFINITY, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, 0, 0, 30_000_001, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, 0, -30_000_001, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, 0, 0, 0, Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, 0, 0, 0, Float.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, 0, 0, 0, 180.1f, 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, 0, 0, 0, 0, Float.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 0, 0, 0, 0, 0, -90.1f));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(1, 1,
                MobHarnessKind.GHOST, 0, 0, 0, 0, 0, 0, 0,
                Double.NaN, 0, 0, false, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(1, 1,
                MobHarnessKind.GHOST, 0, 0, 0, 0, 0, 0, 0,
                512.1, 0, 0, false, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(1, 1,
                MobHarnessKind.GHOST, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, Float.NaN, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(1, 1,
                MobHarnessKind.GHOST, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, 1, Float.POSITIVE_INFINITY, false));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(1, 1,
                MobHarnessKind.CHARACTER, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, -0.1f, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(1, 1,
                MobHarnessKind.CHARACTER, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, 1, -0.1f, false));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(1, 1,
                MobHarnessKind.CHARACTER, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, 64.1f, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Snapshot(1, 1,
                MobHarnessKind.CHARACTER, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, 1, 8.1f, false));
        assertThrows(IllegalArgumentException.class, () -> intent(1, 0, (short) 0, (short) 0, (byte) 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> intent(1, 1, (short) 1001, (short) 0, (byte) 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> intent(1, 1, (short) 0, (short) 0, (byte) 2, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> intent(1, 1, (short) 0, (short) 0, (byte) 0, 8, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> intent(1, 1, (short) 0, (short) 0, (byte) 0, 0, Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> intent(1, 1, (short) 0, (short) 0, (byte) 0, 0, 180.1f, 0));
        assertThrows(IllegalArgumentException.class, () -> intent(1, 1, (short) 0, (short) 0, (byte) 0, 0, 0, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> intent(1, 1, (short) 0, (short) 0, (byte) 0, 0, 0, -90.1f));

        RegistryFriendlyByteBuf malformedBegin = buffer();
        try {
            malformedBegin.writeLong(1);
            malformedBegin.writeInt(-1);
            assertThrows(IllegalArgumentException.class, () -> GhostControlPayloads.Begin.STREAM_CODEC.decode(malformedBegin));
        } finally {
            malformedBegin.release();
        }

        RegistryFriendlyByteBuf invalidBeginKind = buffer();
        try {
            invalidBeginKind.writeLong(1); invalidBeginKind.writeInt(1); invalidBeginKind.writeByte(2);
            assertThrows(IllegalArgumentException.class,
                    () -> GhostControlPayloads.Begin.STREAM_CODEC.decode(invalidBeginKind));
        } finally {
            invalidBeginKind.release();
        }

        RegistryFriendlyByteBuf malformedIntent = buffer();
        try {
            malformedIntent.writeLong(1); malformedIntent.writeLong(1);
            malformedIntent.writeShort(0); malformedIntent.writeShort(0); malformedIntent.writeByte(0);
            malformedIntent.writeByte(8); malformedIntent.writeFloat(0); malformedIntent.writeFloat(0);
            malformedIntent.writeBoolean(false);
            assertThrows(IllegalArgumentException.class, () -> GhostControlPayloads.Intent.STREAM_CODEC.decode(malformedIntent));
        } finally {
            malformedIntent.release();
        }

        RegistryFriendlyByteBuf malformedSnapshot = buffer();
        try {
            malformedSnapshot.writeLong(1); malformedSnapshot.writeInt(1); malformedSnapshot.writeByte(0);
            malformedSnapshot.writeLong(0); malformedSnapshot.writeLong(-1);
            malformedSnapshot.writeDouble(0); malformedSnapshot.writeDouble(0); malformedSnapshot.writeDouble(0);
            malformedSnapshot.writeFloat(0); malformedSnapshot.writeFloat(0);
            malformedSnapshot.writeDouble(0); malformedSnapshot.writeDouble(0); malformedSnapshot.writeDouble(0);
            malformedSnapshot.writeBoolean(false); malformedSnapshot.writeFloat(1); malformedSnapshot.writeFloat(1);
            malformedSnapshot.writeBoolean(false); malformedSnapshot.writeBoolean(false);
            malformedSnapshot.writeVarInt(0); malformedSnapshot.writeEnum(GhostControlPayloads.Snapshot.MovementMode.GROUNDED);
            malformedSnapshot.writeBoolean(false); malformedSnapshot.writeBoolean(false); malformedSnapshot.writeLong(0); malformedSnapshot.writeBoolean(false);
            assertThrows(IllegalArgumentException.class,
                    () -> GhostControlPayloads.Snapshot.STREAM_CODEC.decode(malformedSnapshot));
        } finally {
            malformedSnapshot.release();
        }

        RegistryFriendlyByteBuf invalidSnapshotKind = buffer();
        try {
            invalidSnapshotKind.writeLong(1); invalidSnapshotKind.writeInt(1); invalidSnapshotKind.writeByte(255);
            assertThrows(IllegalArgumentException.class,
                    () -> GhostControlPayloads.Snapshot.STREAM_CODEC.decode(invalidSnapshotKind));
        } finally {
            invalidSnapshotKind.release();
        }

        RegistryFriendlyByteBuf ghostFrameSnapshot = buffer();
        try {
            ghostFrameSnapshot.writeLong(1); ghostFrameSnapshot.writeInt(1); ghostFrameSnapshot.writeByte(0);
            ghostFrameSnapshot.writeLong(1); ghostFrameSnapshot.writeLong(0);
            ghostFrameSnapshot.writeDouble(0); ghostFrameSnapshot.writeDouble(0); ghostFrameSnapshot.writeDouble(0);
            ghostFrameSnapshot.writeFloat(0); ghostFrameSnapshot.writeFloat(0);
            ghostFrameSnapshot.writeDouble(0); ghostFrameSnapshot.writeDouble(0); ghostFrameSnapshot.writeDouble(0);
            ghostFrameSnapshot.writeBoolean(false); ghostFrameSnapshot.writeFloat(1); ghostFrameSnapshot.writeFloat(1);
            ghostFrameSnapshot.writeBoolean(false); ghostFrameSnapshot.writeVarInt(0); ghostFrameSnapshot.writeEnum(GhostControlPayloads.Snapshot.MovementMode.GROUNDED);
            ghostFrameSnapshot.writeBoolean(false); ghostFrameSnapshot.writeBoolean(true);
            ghostFrameSnapshot.writeUUID(java.util.UUID.randomUUID());
            ghostFrameSnapshot.writeDouble(0); ghostFrameSnapshot.writeDouble(0); ghostFrameSnapshot.writeDouble(0);
            ghostFrameSnapshot.writeLong(0);
            ghostFrameSnapshot.writeBoolean(false);
            ghostFrameSnapshot.writeBoolean(false); ghostFrameSnapshot.writeBoolean(false);
            assertThrows(IllegalArgumentException.class,
                    () -> GhostControlPayloads.Snapshot.STREAM_CODEC.decode(ghostFrameSnapshot));
        } finally {
            ghostFrameSnapshot.release();
        }
    }

    @Test
    void clientPayloadsContainNoArbitraryUuidPositionOrBodyTarget() {
        Set<String> componentNames = Arrays.stream(GhostControlPayloads.Intent.class.getRecordComponents())
                .map(component -> component.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("epoch", "sequence", "wishX", "wishZ", "verticalWish", "buttons", "yaw", "pitch", "supportedMovementReference"),
                componentNames);
        assertEquals(Set.of("frameId", "localMovementYaw"), Arrays.stream(GhostControlPayloads.SupportedMovementReference.class.getRecordComponents())
                .map(component -> component.getName()).collect(Collectors.toSet()));
        assertEquals(Set.of("epoch"), Arrays.stream(GhostControlPayloads.Stop.class.getRecordComponents())
                .map(component -> component.getName()).collect(Collectors.toSet()));
        assertFalse(Arrays.stream(GhostControlPayloads.class.getDeclaredClasses())
                .filter(Class::isRecord)
                .flatMap(payload -> Arrays.stream(payload.getRecordComponents()))
                .anyMatch(component -> (component.getType().getName().equals("java.util.UUID")
                        && !component.getDeclaringRecord().equals(GhostControlPayloads.SupportedMovementReference.class))
                        || component.getName().equalsIgnoreCase("position")
                        || component.getName().equalsIgnoreCase("target")));
    }

    private static GhostControlPayloads.Intent intent(long epoch, long sequence, short x, short z,
                                                       byte vertical, int buttons, float yaw, float pitch) {
        return new GhostControlPayloads.Intent(epoch, sequence, x, z, vertical, buttons, yaw, pitch);
    }

    private static GhostControlPayloads.Snapshot snapshot(long epoch, int ghostId, long tick, long acknowledged,
                                                           double x, double y, double z, float yaw, float pitch) {
        return new GhostControlPayloads.Snapshot(epoch, ghostId, tick, acknowledged, x, y, z, yaw, pitch);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
