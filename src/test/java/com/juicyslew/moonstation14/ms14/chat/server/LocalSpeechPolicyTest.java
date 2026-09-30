package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalSpeechPolicyTest {
    private static final UUID SPEAKER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    @Test void noncommittedKeepsVanilla() {
        assertEquals(LocalSpeechPolicy.Route.VANILLA,
                LocalSpeechPolicy.route(false, false, false, false, "hello"));
    }

    @Test void committedUnavailableGhostAndDisabledPrototypeFailClosed() {
        assertEquals(LocalSpeechPolicy.Route.DROP,
                LocalSpeechPolicy.route(true, false, true, true, "hello"));
        assertEquals(LocalSpeechPolicy.Route.DROP,
                LocalSpeechPolicy.route(true, true, false, true, "hello"));
        assertEquals(LocalSpeechPolicy.Route.DROP,
                LocalSpeechPolicy.route(true, true, true, false, "hello"));
        assertEquals(LocalSpeechPolicy.Route.DROP,
                LocalSpeechPolicy.route(true, true, true, true, "   "));
    }

    @Test void exactCharacterSpeaksVerbatimWithinBounds() {
        String raw = "§aHello, <literal>!";
        assertEquals(LocalSpeechPolicy.Route.LOCAL,
                LocalSpeechPolicy.route(true, true, true, true, raw));
        assertFalse(LocalSpeechPolicy.validText("a".repeat(257)));
        ResourceLocation dimension = ResourceLocation.parse("moonstation14:station");
        LocalSpeechPayload payload = new LocalSpeechPayload(17, "Ada Alder", 0xFFFFFF, raw, 1, 2, 3, dimension);
        assertEquals(raw, payload.text());
        assertEquals("Ada Alder", payload.actualName());
        assertEquals(13, LocalSpeechPayload.class.getRecordComponents().length);
        assertEquals(dimension, payload.dimension());
        assertThrows(IllegalArgumentException.class,
                () -> new LocalSpeechPayload(17, "account-name", 0xFFFFFF, raw, 1, 2, 3, dimension));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalSpeechPayload(17, "Ada Alder", 0xFFFFFF, raw, 1, 2, 3, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalSpeechPayload(17, "Ada Alder", 0xFFFFFF, raw, 1, 2, 3,
                        ResourceLocation.fromNamespaceAndPath("test", "a".repeat(252))));
    }

    @Test void clearAndMuffledSpeechCarrySpeakerIdentityButOnlyMuffledCarriesCoarseFallback() {
        ResourceLocation dimension = ResourceLocation.parse("moonstation14:station");
        for (LocalSpeechPayload.Mode mode : new LocalSpeechPayload.Mode[] {
                LocalSpeechPayload.Mode.SAY, LocalSpeechPayload.Mode.WHISPER, LocalSpeechPayload.Mode.SHOUT}) {
            LocalSpeechPayload clear = new LocalSpeechPayload(17, SPEAKER_UUID, "Ada Alder", 0xFFFFFF,
                    "hello", 1, 2, 3, dimension, mode, LocalSpeechPayload.NO_BEARING,
                    LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.DistanceTier.NONE);
            assertEquals(SPEAKER_UUID, clear.speakerUuid());
            for (int[] bearing : new int[][] {{0, LocalSpeechPayload.NO_BEARING},
                    {LocalSpeechPayload.NO_BEARING, 0}}) {
                assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17, SPEAKER_UUID,
                        "Ada Alder", 0xFFFFFF, "hello", 1, 2, 3, dimension, mode, bearing[0], bearing[1],
                        LocalSpeechPayload.DistanceTier.NONE));
            }
            for (LocalSpeechPayload.DistanceTier tier : new LocalSpeechPayload.DistanceTier[] {
                    LocalSpeechPayload.DistanceTier.NEAR, LocalSpeechPayload.DistanceTier.FAR}) {
                assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17, SPEAKER_UUID,
                        "Ada Alder", 0xFFFFFF, "hello", 1, 2, 3, dimension, mode,
                        LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING, tier));
            }
        }
        LocalSpeechPayload muffled = new LocalSpeechPayload(17, SPEAKER_UUID, "Ada Alder",
                LocalSpeechPayload.ANONYMOUS_RGB, "muffled", 0, 0, 0, dimension,
                LocalSpeechPayload.Mode.W_MUFFLED, 15, LocalSpeechPayload.BAND_HIGH,
                LocalSpeechPayload.DistanceTier.NEAR);
        assertEquals(17, muffled.speakerEntityId());
        assertEquals(SPEAKER_UUID, muffled.speakerUuid());
        assertEquals(0.0, muffled.x());
        assertEquals(0.0, muffled.y());
        assertEquals(0.0, muffled.z());
        assertEquals(15, muffled.azimuthSector());
        assertEquals(LocalSpeechPayload.BAND_HIGH, muffled.verticalBand());
        assertEquals(LocalSpeechPayload.DistanceTier.NEAR, muffled.distanceTier());
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17, "Ada Alder",
                0xFFFFFF, "hello", 1, 2, 3, dimension, LocalSpeechPayload.Mode.SAY,
                15, LocalSpeechPayload.BAND_HIGH));
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17,
                "Ada Alder", LocalSpeechPayload.ANONYMOUS_RGB,
                "muffled", 0, 0, 0, dimension, LocalSpeechPayload.Mode.W_MUFFLED, 15,
                LocalSpeechPayload.BAND_HIGH));
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17, SPEAKER_UUID,
                "Ada Alder", LocalSpeechPayload.ANONYMOUS_RGB,
                "muffled", 1, 0, 0, dimension, LocalSpeechPayload.Mode.W_MUFFLED, 15,
                LocalSpeechPayload.BAND_HIGH, LocalSpeechPayload.DistanceTier.NEAR));
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17, SPEAKER_UUID,
                "Ada Alder", LocalSpeechPayload.ANONYMOUS_RGB,
                "muffled", 0, 0, 0, dimension, LocalSpeechPayload.Mode.W_MUFFLED, 16,
                LocalSpeechPayload.BAND_HIGH, LocalSpeechPayload.DistanceTier.NEAR));
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17, SPEAKER_UUID,
                "Ada Alder", LocalSpeechPayload.ANONYMOUS_RGB,
                "muffled", 0, 0, 0, dimension, LocalSpeechPayload.Mode.W_MUFFLED, 1,
                LocalSpeechPayload.BAND_UP, LocalSpeechPayload.DistanceTier.NEAR));
    }

    @Test void codecRejectsMalformedClearSentinelCombinationsWithVerifiedUuid() {
        var clear = new LocalSpeechPayload(17, SPEAKER_UUID, "Ada Alder", 0xFFFFFF, "hello", 1, 2, 3,
                ResourceLocation.parse("minecraft:overworld"), LocalSpeechPayload.Mode.WHISPER,
                LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.DistanceTier.NONE);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LocalSpeechPayload.STREAM_CODEC.encode(buffer, clear);
            assertEquals(clear, LocalSpeechPayload.STREAM_CODEC.decode(buffer));
            // The last three bytes are the two signed bearing sentinels and NONE tier.
            int end = buffer.writerIndex();
            buffer.readerIndex(0);
            buffer.setByte(end - 3, 0);
            assertThrows(IllegalArgumentException.class, () -> LocalSpeechPayload.STREAM_CODEC.decode(buffer));
            buffer.readerIndex(0);
            buffer.setByte(end - 3, -1);
            buffer.setByte(end - 2, 0);
            assertThrows(IllegalArgumentException.class, () -> LocalSpeechPayload.STREAM_CODEC.decode(buffer));
            buffer.readerIndex(0);
            buffer.setByte(end - 2, -1);
            buffer.setByte(end - 1, 1);
            assertThrows(IllegalArgumentException.class, () -> LocalSpeechPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test void dimensionRoundTripsAndWrongDimensionIsRejected() {
        ResourceLocation dimension = ResourceLocation.parse("moonstation14:station");
        LocalSpeechPayload sent = new LocalSpeechPayload(17, "Ada Alder", 0xAABBCC,
                "hello", 1, 2, 3, dimension);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LocalSpeechPayload.STREAM_CODEC.encode(buffer, sent);
            LocalSpeechPayload received = LocalSpeechPayload.STREAM_CODEC.decode(buffer);
            assertEquals(sent, received);
            assertTrue(received.inDimension(dimension));
            assertFalse(received.inDimension(ResourceLocation.parse("minecraft:overworld")));
        } finally {
            buffer.release();
        }
    }

    @Test void savedNumberedNpcNameRoundTripsWithoutRelaxingWireBounds() {
        ResourceLocation dimension = ResourceLocation.parse("minecraft:overworld");
        LocalSpeechPayload sent = new LocalSpeechPayload(56, "Villager (56)", 0xFFFFFF,
                "hello", 1, 2, 3, dimension);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LocalSpeechPayload.STREAM_CODEC.encode(buffer, sent);
            assertEquals(sent, LocalSpeechPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
        assertDoesNotThrow(() -> new LocalSpeechPayload(56, "Villager (1000000)", 0xFFFFFF,
                "hello", 1, 2, 3, dimension));
        for (String invalid : new String[] {"Villager (0)", "Villager (01)", "Villager (-1)",
                "Villager (1000001)", "Villager (999999999999999999999999)", "Villager (1) extra",
                "villager (1)", "Villager(1)"}) {
            assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(56, invalid,
                    0xFFFFFF, "hello", 1, 2, 3, dimension), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(56, "Villager (56)",
                0x202020, "hello", 1, 2, 3, dimension));
    }

    @Test void debugGhostAndMissingSavedIdentityNeverRouteLocally() {
        assertEquals(LocalSpeechPolicy.Route.DROP, LocalSpeechPolicy.route(true, false, true, true, "hello"));
        assertEquals(LocalSpeechPolicy.Route.DROP, LocalSpeechPolicy.route(true, true, false, true, "hello"));
        assertEquals(LocalSpeechPolicy.Route.DROP, LocalSpeechPolicy.route(true, true, true, false, "hello"));
        assertEquals(LocalSpeechPolicy.Route.LOCAL, LocalSpeechPolicy.route(true, true, true, true, "hello"));
    }

    @Test void localDeliveryHasSingleSenderAndOnlySameLevelNearbyListeners() {
        assertTrue(LocalSpeechPolicy.recipient(true, true, 0, 0, 0, 999, 0, 0));
        assertEquals(15.0, LocalSpeechPolicy.RANGE);
        assertTrue(LocalSpeechPolicy.recipient(false, true, 0, 0, 0, 9, 12, 0));
        assertFalse(LocalSpeechPolicy.recipient(false, true, 0, 0, 0, 15.01, 0, 0));
        assertFalse(LocalSpeechPolicy.recipient(false, false, 0, 0, 0, 1, 0, 0));
        assertFalse(LocalSpeechPolicy.recipient(true, false, 0, 0, 0, 0, 0, 0));
        assertFalse(LocalSpeechPolicy.validPosition(Double.NaN, 0, 0));
    }

    @Test void committedAttemptsHaveNoBurstAndDroppedAttemptsConsumeCooldown() {
        var limiter = new LocalSpeechPolicy.AttemptLimiter<Object>();
        Object sender = new Object(), other = new Object();
        long start = 100_000_000_000L;
        assertTrue(limiter.admit(sender, start)); // even if later dropped by routing
        assertEquals(LocalSpeechPolicy.Route.DROP,
                LocalSpeechPolicy.route(true, false, false, false, "hello"));
        for (int i = 0; i < 100; i++) assertFalse(limiter.admit(sender, start + i * 1_000_000L));
        assertTrue(limiter.admit(other, start)); // no global fallback or shared budget
        assertFalse(limiter.admit(sender, start + LocalSpeechPolicy.SPEECH_COOLDOWN_NANOS - 1));
        assertTrue(limiter.admit(sender, start + LocalSpeechPolicy.SPEECH_COOLDOWN_NANOS));
        assertFalse(limiter.admit(sender, start + LocalSpeechPolicy.SPEECH_COOLDOWN_NANOS));
    }

    @Test void allowedAttemptCanDispatchToSenderAndNearbyListenersAndResets() {
        var limiter = new LocalSpeechPolicy.AttemptLimiter<Object>();
        Object sender = new Object();
        assertTrue(limiter.admit(sender, 0));
        assertEquals(LocalSpeechPolicy.Route.LOCAL,
                LocalSpeechPolicy.route(true, true, true, true, "Hello"));
        assertTrue(LocalSpeechPolicy.recipient(true, true, 0, 0, 0, 0, 0, 0));
        assertTrue(LocalSpeechPolicy.recipient(false, true, 0, 0, 0, 1, 0, 0));
        limiter.remove(sender); // logout
        assertTrue(limiter.admit(sender, 1));
        limiter.clear(); // server stop
        assertTrue(limiter.admit(sender, 2));
        assertFalse(limiter.admit(null, 2));
    }
}
