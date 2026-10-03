package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyHandActionResultCodecTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test void successfulPickupAndDropDescriptorsRoundTrip() {
        for (var direction : BodyHandActionResult.HostTransition.Direction.values()) {
            var transition = new BodyHandActionResult.HostTransition(direction,
                    UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), "host-token");
            var result = new BodyHandActionResult(1, 2, true, BodyHandActionResult.Reason.OK,
                    false, 0, null, null, null, 0, transition);
            var encoded = buffer();
            BodyHandActionResult.STREAM_CODEC.encode(encoded, result);
            assertEquals(result, BodyHandActionResult.STREAM_CODEC.decode(encoded));
        }
    }

    @Test void legacyConstructorRoundTripsWithoutDescriptor() {
        var result = new BodyHandActionResult(1, 2, true, BodyHandActionResult.Reason.OK,
                false, 0, null, null, null, 0);
        var encoded = buffer();
        BodyHandActionResult.STREAM_CODEC.encode(encoded, result);
        assertEquals(result, BodyHandActionResult.STREAM_CODEC.decode(encoded));
        assertNull(result.hostTransition());
    }

    @Test void malformedDescriptorsAndDescriptorsOnNonSuccessAreRejected() {
        UUID world = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionResult.HostTransition(null, world, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionResult.HostTransition(
                BodyHandActionResult.HostTransition.Direction.PICKUP, null, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionResult.HostTransition(
                BodyHandActionResult.HostTransition.Direction.DROP, world, " "));
        var descriptor = new BodyHandActionResult.HostTransition(
                BodyHandActionResult.HostTransition.Direction.PICKUP, world, "token");
        for (var reason : new BodyHandActionResult.Reason[]{BodyHandActionResult.Reason.DENIED,
                BodyHandActionResult.Reason.RECOVERY_REQUIRED}) {
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionResult(1, 2, false,
                    reason, false, 0, null, null, null, 0, descriptor));
        }

        var badDirection = buffer();
        badDirection.writeVarLong(1); badDirection.writeVarLong(2); badDirection.writeBoolean(true);
        badDirection.writeEnum(BodyHandActionResult.Reason.OK); badDirection.writeBoolean(true);
        badDirection.writeByte(127);
        assertThrows(RuntimeException.class, () -> BodyHandActionResult.STREAM_CODEC.decode(badDirection));

        var missingWorld = buffer();
        missingWorld.writeVarLong(1); missingWorld.writeVarLong(2); missingWorld.writeBoolean(true);
        missingWorld.writeEnum(BodyHandActionResult.Reason.OK); missingWorld.writeBoolean(true);
        missingWorld.writeEnum(BodyHandActionResult.HostTransition.Direction.PICKUP);
        assertThrows(RuntimeException.class, () -> BodyHandActionResult.STREAM_CODEC.decode(missingWorld));

        var missingToken = buffer();
        missingToken.writeVarLong(1); missingToken.writeVarLong(2); missingToken.writeBoolean(true);
        missingToken.writeEnum(BodyHandActionResult.Reason.OK); missingToken.writeBoolean(true);
        missingToken.writeEnum(BodyHandActionResult.HostTransition.Direction.PICKUP);
        missingToken.writeUUID(world);
        assertThrows(RuntimeException.class, () -> BodyHandActionResult.STREAM_CODEC.decode(missingToken));
    }

    @Test void codecLeavesTrailingBytesVisibleForPacketBoundaryValidation() {
        var result = new BodyHandActionResult(1, 2, true, BodyHandActionResult.Reason.OK,
                false, 0, null, null, null, 0);
        var encoded = buffer();
        BodyHandActionResult.STREAM_CODEC.encode(encoded, result);
        encoded.writeByte(1);
        assertEquals(result, BodyHandActionResult.STREAM_CODEC.decode(encoded));
        assertEquals(1, encoded.readableBytes());
    }
}
