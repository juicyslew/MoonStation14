package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SpeechRecipientPolicyTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    private static final UUID SPEAKER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    private static LocalSpeechPayload muffled(int id, UUID uuid, String name, int rgb, String text,
                                               double x, double y, double z, int sector, int band,
                                               LocalSpeechPayload.DistanceTier tier) {
        return new LocalSpeechPayload(id, uuid, name, rgb, text, x, y, z, DIMENSION,
                LocalSpeechPayload.Mode.W_MUFFLED, sector, band, tier);
    }

    private static LocalSpeechPayload muffled(int sector, int band) {
        return muffled(17, SPEAKER_UUID, "Ada Alder", LocalSpeechPayload.ANONYMOUS_RGB,
                "··", 0, 0, 0, sector, band, LocalSpeechPayload.DistanceTier.NEAR);
    }

    private static SpeechRecipientPolicy.Delivery delivery(SpeechModePolicy.Mode mode, boolean sender,
                                                            boolean sameLevel, double distance) {
        return SpeechRecipientPolicy.classify(mode, sender, sameLevel, 0, 0, 0, distance, 0, 0);
    }

    @Test void whisperDistancesAndSenderOnce() {
        assertEquals(SpeechRecipientPolicy.Delivery.CLEAR, delivery(SpeechModePolicy.Mode.WHISPER, false, true, 3));
        assertEquals(SpeechRecipientPolicy.Delivery.MUFFLED, delivery(SpeechModePolicy.Mode.WHISPER, false, true, 3.01));
        assertEquals(SpeechRecipientPolicy.Delivery.MUFFLED, delivery(SpeechModePolicy.Mode.WHISPER, false, true, 6));
        assertEquals(SpeechRecipientPolicy.Delivery.NONE, delivery(SpeechModePolicy.Mode.WHISPER, false, true, 6.01));
        assertEquals(SpeechRecipientPolicy.Delivery.CLEAR, delivery(SpeechModePolicy.Mode.WHISPER, true, true, 100));
        assertEquals(SpeechRecipientPolicy.Delivery.NONE, delivery(SpeechModePolicy.Mode.WHISPER, true, false, 0));
        assertEquals(SpeechRecipientPolicy.Delivery.CLEAR,
                delivery(LocalSpeechServerHooks.localMode(SpeechModePolicy.Mode.RADIO_ATTEMPT), true, true, 100));
        assertEquals(SpeechRecipientPolicy.Delivery.MUFFLED,
                delivery(LocalSpeechServerHooks.localMode(SpeechModePolicy.Mode.RADIO_ATTEMPT), false, true, 5));
        assertEquals(SpeechRecipientPolicy.Delivery.NONE,
                delivery(LocalSpeechServerHooks.localMode(SpeechModePolicy.Mode.RADIO_ATTEMPT), false, true, 7));
    }

    @Test void serverClearUsesValidatedBodyUuidAndRadioHasOneLocalDispatch() throws IOException {
        Path relative = Path.of("src/main/java/com/juicyslew/moonstation14/ms14/chat/server/LocalSpeechServerHooks.java");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve(relative))) root = root.getParent();
        assertNotNull(root, "LocalSpeechServerHooks source must be available for dispatch audit");
        String source = Files.readString(root.resolve(relative));
        assertTrue(source.contains("UUID speakerUuid = body.getUUID();"));
        assertTrue(source.contains("LocalSpeechPayload.NO_UUID.equals(speakerUuid)) return;"));
        assertTrue(Pattern.compile("(?:\\w+\\s*=\\s*)?new\\s+LocalSpeechPayload\\s*\\(\\s*"
                + "body\\.getId\\(\\)\\s*,\\s*speakerUuid\\s*,\\s*speaker\\.name\\(\\)\\s*,\\s*"
                + "speaker\\.rgb\\(\\)\\s*,\\s*parsed\\.body\\(\\)\\s*,\\s*"
                + "position\\.x\\s*,\\s*position\\.y\\s*,\\s*position\\.z\\s*,\\s*"
                + "body\\.level\\(\\)\\.dimension\\(\\)\\.location\\(\\)\\s*,\\s*mode\\s*,\\s*"
                + "LocalSpeechPayload\\.NO_BEARING\\s*,\\s*LocalSpeechPayload\\.NO_BEARING\\s*,\\s*"
                + "LocalSpeechPayload\\.DistanceTier\\.NONE\\s*\\)", Pattern.DOTALL)
                .matcher(source).find(), "clear payload must use the validated body identity, parsed text, position, dimension, mode, and no bearings");
        assertEquals(1, source.split(Pattern.quote("LocalSpeechNetworking.send(recipient, outgoing);"), -1).length - 1);
        assertEquals(1, source.split(Pattern.quote("for (ServerPlayer recipient : level.getServer().getPlayerList().getPlayers())"), -1).length - 1);
        assertEquals(1, source.split(Pattern.quote("RADIO_ATTEMPT ? SpeechModePolicy.Mode.WHISPER : attempted"), -1).length - 1);
    }

    @Test void recipientSpecificDistanceTiersAreBounded() {
        for (double distance : new double[] {3.01, 4.5, 4.50001, 6}) {
            assertEquals(SpeechRecipientPolicy.Delivery.MUFFLED,
                    delivery(SpeechModePolicy.Mode.WHISPER, false, true, distance));
            var expected = distance <= 4.5 ? LocalSpeechPayload.DistanceTier.NEAR : LocalSpeechPayload.DistanceTier.FAR;
            assertEquals(expected, SpeechRecipientPolicy.distanceTier(0, 0, 0, distance, 0, 0));
            assertEquals(expected, SpeechRecipientPolicy.distanceTier(0, 0, 0, 0, -distance, 0));
        }
        for (double distance : new double[] {0, 3, 6.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> SpeechRecipientPolicy.distanceTier(0, 0, 0, distance, 0, 0));
        }
    }

    @Test void shoutNeverWidensSayAndUnroutableModesNeverDeliver() {
        for (SpeechModePolicy.Mode mode : new SpeechModePolicy.Mode[] {SpeechModePolicy.Mode.SAY, SpeechModePolicy.Mode.SHOUT}) {
            assertEquals(SpeechRecipientPolicy.Delivery.CLEAR, delivery(mode, false, true, 15));
            assertEquals(SpeechRecipientPolicy.Delivery.NONE, delivery(mode, false, true, 15.01));
        }
        for (SpeechModePolicy.Mode mode : new SpeechModePolicy.Mode[] {
                SpeechModePolicy.Mode.RADIO_ATTEMPT, SpeechModePolicy.Mode.INVALID}) {
            assertEquals(SpeechRecipientPolicy.Delivery.NONE, delivery(mode, true, true, 0));
            assertEquals(SpeechRecipientPolicy.Delivery.NONE, delivery(mode, false, true, 0));
        }
    }

    @Test void namedMuffledPacketContainsNeitherPreciseMetadataNorClearText() {
        String clear = "PIN 7!";
        String muffled = SpeechRecipientPolicy.muffle(clear);
        assertEquals("··· ··", muffled);
        LocalSpeechPayload packet = muffled(17, SPEAKER_UUID, "Ada Alder",
                LocalSpeechPayload.ANONYMOUS_RGB, muffled, 0, 0, 0, 4,
                LocalSpeechPayload.BAND_LEVEL, LocalSpeechPayload.DistanceTier.NEAR);
        assertEquals(17, packet.speakerEntityId());
        assertEquals(SPEAKER_UUID, packet.speakerUuid());
        assertEquals("Ada Alder", packet.actualName());
        assertEquals(4, packet.azimuthSector());
        assertEquals(LocalSpeechPayload.BAND_LEVEL, packet.verticalBand());
        assertEquals(LocalSpeechPayload.DistanceTier.NEAR, packet.distanceTier());
        assertEquals(LocalSpeechPayload.ANONYMOUS_RGB, packet.rgb());
        assertEquals(0, packet.x());
        assertEquals(0, packet.y());
        assertEquals(0, packet.z());
        assertFalse(packet.toString().contains(clear));
        assertFalse(packet.text().matches(".*[A-Za-z0-9].*"));
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17, "Ada Alder", 0xFFFFFF,
                muffled, 0, 0, 0, DIMENSION, LocalSpeechPayload.Mode.W_MUFFLED));
        assertThrows(IllegalArgumentException.class, () -> muffled(17, SPEAKER_UUID, "Someone", 0xFFFFFF,
                muffled, 0, 0, 0, 4, 1, LocalSpeechPayload.DistanceTier.NEAR));
        assertThrows(IllegalArgumentException.class, () -> muffled(17, SPEAKER_UUID, "Ada Alder", 0x123456,
                muffled, 0, 0, 0, 4, 1, LocalSpeechPayload.DistanceTier.NEAR));
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(0, "Someone", 0xFFFFFF,
                clear, 1, 2, 3, DIMENSION, LocalSpeechPayload.Mode.WHISPER));
        for (double[] position : new double[][] {{1, 0, 0}, {0, 2, 0}, {0, 0, 3}, {-0.0, 0, 0}}) {
            assertThrows(IllegalArgumentException.class, () -> muffled(17, SPEAKER_UUID,
                     "Ada Alder", LocalSpeechPayload.ANONYMOUS_RGB,
                     muffled, position[0], position[1], position[2], 4, 1,
                     LocalSpeechPayload.DistanceTier.NEAR));
        }
        for (LocalSpeechPayload.Mode mode : LocalSpeechPayload.Mode.values()) {
            LocalSpeechPayload sent = mode == LocalSpeechPayload.Mode.W_MUFFLED ? packet
                    : new LocalSpeechPayload(17, SPEAKER_UUID, "Ada Alder", 0xFFFFFF, clear, 1, 2, 3,
                            DIMENSION, mode, LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING,
                            LocalSpeechPayload.DistanceTier.NONE);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                LocalSpeechPayload.STREAM_CODEC.encode(buffer, sent);
                LocalSpeechPayload decoded = LocalSpeechPayload.STREAM_CODEC.decode(buffer);
                assertEquals(sent, decoded);
                assertEquals(SPEAKER_UUID, decoded.speakerUuid());
            } finally {
                buffer.release();
            }
        }
    }

    @Test void recipientSpecificWorldBearingAndBoundaries() {
        var mode = SpeechModePolicy.Mode.WHISPER;
        assertEquals(SpeechRecipientPolicy.Delivery.MUFFLED,
                SpeechRecipientPolicy.classify(mode, false, true, 0, 0, 0, -4, 0, 0));
        assertEquals(new SpeechRecipientPolicy.Bearing(4, LocalSpeechPayload.BAND_LEVEL),
                SpeechRecipientPolicy.bearing(0, 0, 0, -4, 0, 0));
        assertEquals(new SpeechRecipientPolicy.Bearing(12, LocalSpeechPayload.BAND_LEVEL),
                SpeechRecipientPolicy.bearing(0, 0, 0, 4, 0, 0));
        assertEquals(new SpeechRecipientPolicy.Bearing(0, LocalSpeechPayload.BAND_LEVEL),
                SpeechRecipientPolicy.bearing(0, 0, 0, 0, 0, -4));
        assertEquals(new SpeechRecipientPolicy.Bearing(8, LocalSpeechPayload.BAND_LEVEL),
                SpeechRecipientPolicy.bearing(0, 0, 0, 0, 0, 4));
        assertEquals(LocalSpeechPayload.BAND_HIGH, SpeechRecipientPolicy.bearing(0, 3, 0, 0, 0, -4).verticalBand());
        assertEquals(LocalSpeechPayload.BAND_LOW, SpeechRecipientPolicy.bearing(0, -3, 0, 0, 0, -4).verticalBand());
        assertEquals(LocalSpeechPayload.BAND_LEVEL,
                SpeechRecipientPolicy.bearing(0, 4 / Math.sqrt(3), 0, 0, 0, -4).verticalBand());
        assertThrows(IllegalArgumentException.class, () -> SpeechRecipientPolicy.bearing(0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> SpeechRecipientPolicy.bearing(Double.NaN, 0, 0, 0, 0, 0));
        assertEquals(SpeechRecipientPolicy.Delivery.NONE,
                SpeechRecipientPolicy.classify(mode, false, false, 0, 0, 0, -4, 0, 0));
        assertEquals(SpeechRecipientPolicy.Delivery.NONE,
                SpeechRecipientPolicy.classify(mode, false, true, 0, 0, 0, Double.NaN, 0, 0));
    }

    @Test void verticalMuffledWhispersHaveStableBearingWithoutCoordinates() {
        for (double height : new double[] {3.01, 4, 6}) {
            assertEquals(SpeechRecipientPolicy.Delivery.MUFFLED,
                    SpeechRecipientPolicy.classify(SpeechModePolicy.Mode.WHISPER, false, true,
                            0, height, 0, 0, 0, 0));
            assertEquals(new SpeechRecipientPolicy.Bearing(0, LocalSpeechPayload.BAND_UP),
                    SpeechRecipientPolicy.bearing(0, height, 0, 0, 0, 0));
            assertEquals(new SpeechRecipientPolicy.Bearing(0, LocalSpeechPayload.BAND_DOWN),
                    SpeechRecipientPolicy.bearing(0, -height, 0, 0, 0, 0));
        }
        // A tiny but nonzero horizontal displacement still selects one of the 16 actual sectors.
        assertEquals(new SpeechRecipientPolicy.Bearing(4, LocalSpeechPayload.BAND_HIGH),
                SpeechRecipientPolicy.bearing(1e-12, 4, 0, 0, 0, 0));
        assertEquals(new SpeechRecipientPolicy.Bearing(8, LocalSpeechPayload.BAND_LOW),
                SpeechRecipientPolicy.bearing(0, -4, -1e-12, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> SpeechRecipientPolicy.bearing(0, Double.POSITIVE_INFINITY, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> SpeechRecipientPolicy.bearing(0, 4, 0, 0, 0, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> SpeechRecipientPolicy.bearing(0, Double.MAX_VALUE, 0, 0, -Double.MAX_VALUE, 0));
    }

    @Test void bearingCodecRejectsInvalidFields() {
        for (int id : new int[] {0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> muffled(id, SPEAKER_UUID, "Ada Alder",
                    0xffffff, "··", 0, 0, 0, 4, 1, LocalSpeechPayload.DistanceTier.NEAR));
        }
        for (UUID uuid : new UUID[] {null, LocalSpeechPayload.NO_UUID}) {
            assertThrows(IllegalArgumentException.class, () -> muffled(17, uuid, "Ada Alder",
                    0xffffff, "··", 0, 0, 0, 4, 1, LocalSpeechPayload.DistanceTier.NEAR));
        }
        for (LocalSpeechPayload.DistanceTier tier : new LocalSpeechPayload.DistanceTier[] {
                null, LocalSpeechPayload.DistanceTier.NONE}) {
            assertThrows(IllegalArgumentException.class, () -> muffled(17, SPEAKER_UUID, "Ada Alder",
                    0xffffff, "··", 0, 0, 0, 4, 1, tier));
        }
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17, null,
                "Ada Alder", 0xffffff, "hello", 1, 2, 3, DIMENSION, LocalSpeechPayload.Mode.SAY,
                LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.DistanceTier.NONE));
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(17, LocalSpeechPayload.NO_UUID,
                "Ada Alder", 0xffffff, "hello", 1, 2, 3, DIMENSION, LocalSpeechPayload.Mode.SAY,
                LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.DistanceTier.FAR));
        for (int sector : new int[] {-1, 16, 255}) {
            assertThrows(IllegalArgumentException.class, () -> muffled(sector, 1));
        }
        for (int band : new int[] {-1, 5, 255}) {
            assertThrows(IllegalArgumentException.class, () -> muffled(4, band));
        }
        for (int band : new int[] {LocalSpeechPayload.BAND_UP, LocalSpeechPayload.BAND_DOWN}) {
            for (int sector : new int[] {1, 15}) {
                assertThrows(IllegalArgumentException.class, () -> muffled(sector, band));
            }
            LocalSpeechPayload vertical = muffled(0, band);
            RegistryFriendlyByteBuf verticalBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                LocalSpeechPayload.STREAM_CODEC.encode(verticalBuf, vertical);
                assertEquals(vertical, LocalSpeechPayload.STREAM_CODEC.decode(verticalBuf));
                verticalBuf.readerIndex(0);
                verticalBuf.setByte(verticalBuf.writerIndex() - 3, 4);
                assertThrows(IllegalArgumentException.class, () -> LocalSpeechPayload.STREAM_CODEC.decode(verticalBuf));
            } finally {
                verticalBuf.release();
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(1, "Ada Alder", 0xffffff,
                "hello", 1, 2, 3, DIMENSION, LocalSpeechPayload.Mode.SAY, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> new LocalSpeechPayload(1, "Ada Alder", 0xffffff,
                "hello", 1, 2, 3, DIMENSION, LocalSpeechPayload.Mode.WHISPER, 0, LocalSpeechPayload.BAND_UP));
        var valid = muffled(15, 2);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LocalSpeechPayload.STREAM_CODEC.encode(buf, valid);
            buf.setByte(buf.writerIndex() - 3, 16);
            assertThrows(IllegalArgumentException.class, () -> LocalSpeechPayload.STREAM_CODEC.decode(buf));
            buf.readerIndex(0);
            buf.setByte(buf.writerIndex() - 3, 15);
            buf.setByte(buf.writerIndex() - 2, 5);
            assertThrows(IllegalArgumentException.class, () -> LocalSpeechPayload.STREAM_CODEC.decode(buf));
            buf.readerIndex(0);
            buf.setByte(buf.writerIndex() - 2, 2);
            buf.setByte(buf.writerIndex() - 1, 0);
            assertThrows(IllegalArgumentException.class, () -> LocalSpeechPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
        RegistryFriendlyByteBuf identityBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LocalSpeechPayload.STREAM_CODEC.encode(identityBuf, valid);
            identityBuf.setByte(0, 0); // first byte is the positive one-byte entity ID
            assertThrows(IllegalArgumentException.class, () -> LocalSpeechPayload.STREAM_CODEC.decode(identityBuf));
            identityBuf.readerIndex(0);
            identityBuf.setByte(0, 17);
            for (int i = 1; i <= 16; i++) identityBuf.setByte(i, 0); // nil UUID
            assertThrows(IllegalArgumentException.class, () -> LocalSpeechPayload.STREAM_CODEC.decode(identityBuf));
        } finally {
            identityBuf.release();
        }
    }

    @Test void mufflePreservesOnlyWhitespaceAndEveryFifthAsciiAlphanumeric() {
        assertEquals("····", SpeechRecipientPolicy.muffle("1234"));
        assertEquals("····5", SpeechRecipientPolicy.muffle("12345"));
        assertEquals("····e ····0", SpeechRecipientPolicy.muffle("abcde 67890"));
        assertEquals("·····", SpeechRecipientPolicy.muffle("!!!?!"));
        assertEquals("···", SpeechRecipientPolicy.muffle("!!!"));
        assertEquals("· · ··", SpeechRecipientPolicy.muffle("é 𝟠 中!"));
        assertEquals("· · ·", SpeechRecipientPolicy.muffle("😀 ★ ©"));
        assertEquals("••••·", SpeechRecipientPolicy.muffle("····a"));
        assertEquals("·····", SpeechRecipientPolicy.muffle("12-34"));
        assertEquals("·\t·\n·\u00a0·", SpeechRecipientPolicy.muffle("!\t😀\n#\u00a0$"));
    }

    @Test void nonblankWhispersAtMuffledRangeNeverPassThroughOrBecomeBlank() {
        assertEquals(SpeechRecipientPolicy.Delivery.MUFFLED,
                delivery(SpeechModePolicy.Mode.WHISPER, false, true, 6));
        for (String text : new String[] {"!!!", "😀", "★ ©", "1234", "12345", "a", "····a", " \t!\n ", "\u200b"}) {
            String muffled = SpeechRecipientPolicy.muffle(text);
            assertNotEquals(text, muffled, text);
            assertFalse(muffled.isBlank(), text);
        }
    }
}
