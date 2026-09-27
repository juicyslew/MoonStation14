package com.juicyslew.moonstation14.ms14.player_body_control.network;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.movement.protocol.MovementPayloads;
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

class GhostControlPayloadsTest {
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
        var characterSnapshot = new GhostControlPayloads.Snapshot(10, 44,
                MobHarnessKind.CHARACTER, 11, 8, 1, 2, 3, 4, 5,
                6.5, -7.5, 8.5, true, 1.5f, 1.5f, true);

        Set<CustomPacketPayload.Type<?>> ghostTypes = Set.of(begin.type(), ready.type(), offer.type(), offerReady.type(), commit.type(),
                intent.type(), stop.type(), snapshot.type());
        assertEquals(8, ghostTypes.size());
        Set<CustomPacketPayload.Type<?>> movementTypes = Set.of(MovementPayloads.Begin.TYPE, MovementPayloads.Acknowledge.TYPE,
                MovementPayloads.Commit.TYPE, MovementPayloads.Disable.TYPE,
                MovementPayloads.DisableAcknowledge.TYPE, MovementPayloads.ResumeVanilla.TYPE,
                MovementPayloads.ResumeAcknowledge.TYPE, MovementPayloads.Intent.TYPE,
                MovementPayloads.Snapshot.TYPE);
        assertEquals(0, ghostTypes.stream().filter(movementTypes::contains).count());

        assertEquals(begin, roundTrip(GhostControlPayloads.Begin.STREAM_CODEC, begin));
        assertEquals(ready, roundTrip(GhostControlPayloads.Ready.STREAM_CODEC, ready));
        assertEquals(offer, roundTrip(GhostControlPayloads.Offer.STREAM_CODEC, offer));
        assertEquals(offerReady, roundTrip(GhostControlPayloads.OfferReady.STREAM_CODEC, offerReady));
        assertEquals(commit, roundTrip(GhostControlPayloads.Commit.STREAM_CODEC, commit));
        assertEquals(intent, roundTrip(GhostControlPayloads.Intent.STREAM_CODEC, intent));
        assertEquals(stop, roundTrip(GhostControlPayloads.Stop.STREAM_CODEC, stop));
        assertEquals(snapshot, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, snapshot));
        assertEquals(characterBegin, roundTrip(GhostControlPayloads.Begin.STREAM_CODEC, characterBegin));
        assertEquals(characterSnapshot, roundTrip(GhostControlPayloads.Snapshot.STREAM_CODEC, characterSnapshot));
        assertEquals(MobHarnessKind.GHOST, begin.harnessKind());
        assertEquals(42, begin.harnessEntityId());
        assertEquals(42, begin.ghostEntityId());
        assertEquals(0, snapshot.velocityX());
        assertFalse(snapshot.onGround());
        assertEquals(1, snapshot.surfaceFactor());
        assertEquals(1, snapshot.voluntarySpeedFactor());
        assertFalse(snapshot.stunned());
        assertEquals(snapshot.harnessEntityId(), snapshot.ghostEntityId());
        assertEquals(1.5f, characterSnapshot.surfaceFactor());
        assertEquals(1.5f, characterSnapshot.voluntarySpeedFactor());
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
            malformedSnapshot.writeBoolean(false);
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
    }

    @Test
    void clientPayloadsContainNoUuidOrPositionOrArbitraryBodyTarget() {
        Set<String> componentNames = Arrays.stream(GhostControlPayloads.Intent.class.getRecordComponents())
                .map(component -> component.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("epoch", "sequence", "wishX", "wishZ", "verticalWish", "buttons", "yaw", "pitch"),
                componentNames);
        assertEquals(Set.of("epoch"), Arrays.stream(GhostControlPayloads.Stop.class.getRecordComponents())
                .map(component -> component.getName()).collect(Collectors.toSet()));
        assertFalse(Arrays.stream(GhostControlPayloads.class.getDeclaredClasses())
                .filter(Class::isRecord)
                .flatMap(payload -> Arrays.stream(payload.getRecordComponents()))
                .anyMatch(component -> component.getType().getName().equals("java.util.UUID")
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
