package com.juicyslew.moonstation14.ms14.player_body_control.network;

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
    void allFivePayloadsHaveDistinctIdsAndRoundTrip() {
        var begin = new GhostControlPayloads.Begin(1, 42);
        var ready = new GhostControlPayloads.Ready(2);
        var commit = new GhostControlPayloads.Commit(3);
        var intent = new GhostControlPayloads.Intent(4, 5, (short) -1000, (short) 1000,
                (byte) -1, GhostControlPayloads.BUTTON_JUMP | GhostControlPayloads.BUTTON_SPRINT,
                -180.0f, 90.0f);
        var stop = new GhostControlPayloads.Stop(6);

        Set<CustomPacketPayload.Type<?>> ghostTypes = Set.of(begin.type(), ready.type(), commit.type(),
                intent.type(), stop.type());
        assertEquals(5, ghostTypes.size());
        Set<CustomPacketPayload.Type<?>> movementTypes = Set.of(MovementPayloads.Begin.TYPE, MovementPayloads.Acknowledge.TYPE,
                MovementPayloads.Commit.TYPE, MovementPayloads.Disable.TYPE,
                MovementPayloads.DisableAcknowledge.TYPE, MovementPayloads.ResumeVanilla.TYPE,
                MovementPayloads.ResumeAcknowledge.TYPE, MovementPayloads.Intent.TYPE,
                MovementPayloads.Snapshot.TYPE);
        assertEquals(0, ghostTypes.stream().filter(movementTypes::contains).count());

        assertEquals(begin, roundTrip(GhostControlPayloads.Begin.STREAM_CODEC, begin));
        assertEquals(ready, roundTrip(GhostControlPayloads.Ready.STREAM_CODEC, ready));
        assertEquals(commit, roundTrip(GhostControlPayloads.Commit.STREAM_CODEC, commit));
        assertEquals(intent, roundTrip(GhostControlPayloads.Intent.STREAM_CODEC, intent));
        assertEquals(stop, roundTrip(GhostControlPayloads.Stop.STREAM_CODEC, stop));
    }

    @Test
    void constructorsAndDecoderRejectInvalidFields() {
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Begin(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Begin(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Ready(0));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Commit(0));
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Stop(0));
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

        RegistryFriendlyByteBuf malformedIntent = buffer();
        try {
            malformedIntent.writeLong(1); malformedIntent.writeLong(1);
            malformedIntent.writeShort(0); malformedIntent.writeShort(0); malformedIntent.writeByte(0);
            malformedIntent.writeByte(8); malformedIntent.writeFloat(0); malformedIntent.writeFloat(0);
            assertThrows(IllegalArgumentException.class, () -> GhostControlPayloads.Intent.STREAM_CODEC.decode(malformedIntent));
        } finally {
            malformedIntent.release();
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
                .flatMap(payload -> Arrays.stream(payload.getRecordComponents()))
                .anyMatch(component -> component.getType().getName().equals("java.util.UUID")
                        || component.getName().equalsIgnoreCase("position")
                        || component.getName().equalsIgnoreCase("target")));
    }

    private static GhostControlPayloads.Intent intent(long epoch, long sequence, short x, short z,
                                                       byte vertical, int buttons, float yaw, float pitch) {
        return new GhostControlPayloads.Intent(epoch, sequence, x, z, vertical, buttons, yaw, pitch);
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
