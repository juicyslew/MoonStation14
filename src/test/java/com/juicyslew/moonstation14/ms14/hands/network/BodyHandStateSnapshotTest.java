package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.combat.ClientWieldProjection;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyHandStateSnapshotTest {
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID BODY = UUID.randomUUID();
    private static final UUID RESERVATION = UUID.randomUUID();

    private static BodyHandStateSnapshot snapshot(BodyHandStateSnapshot.WieldProjection projection) {
        var hands = List.of(new BodyHandStateSnapshot.Hand("right", "weapon-token", "minecraft:stick", 1),
                new BodyHandStateSnapshot.Hand("left", null, null, 0, false, null, null, 0,
                        RESERVATION.toString()));
        return new BodyHandStateSnapshot(4, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY,
                7, 2, "right", hands, List.of(), List.of(), false, null, 10, projection);
    }

    private static BodyHandStateSnapshot roundTrip(BodyHandStateSnapshot source) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        BodyHandStateSnapshot.STREAM_CODEC.encode(buffer, source);
        return BodyHandStateSnapshot.STREAM_CODEC.decode(buffer);
    }

    @Test void oldConstructorRoundTripsNullProjection() {
        var decoded = roundTrip(snapshot(null));
        assertNull(decoded.wieldProjection());
    }

    @Test void validProjectionRoundTripsAndConfirms() {
        var projection = new BodyHandStateSnapshot.WieldProjection("right", "weapon-token", "left", RESERVATION);
        var decoded = roundTrip(snapshot(projection));
        assertEquals(projection, decoded.wieldProjection());
        assertTrue(ClientWieldProjection.confirms(decoded, 7, "right", "weapon-token"));
        assertFalse(ClientWieldProjection.confirms(decoded, 7, "right", "other-token"));
        assertFalse(ClientWieldProjection.confirms(decoded, 6, "right", "weapon-token"));
        assertFalse(ClientWieldProjection.confirms(snapshot(null), 7, "right", "weapon-token"));
    }

    @Test void rejectsProjectionNotMatchingSourceOrSupportReservation() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                new BodyHandStateSnapshot.WieldProjection("right", "wrong-token", "left", RESERVATION)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                new BodyHandStateSnapshot.WieldProjection("right", "weapon-token", "left", UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.WieldProjection(
                "right", "weapon-token", "right", RESERVATION));
    }
}
