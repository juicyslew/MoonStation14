package com.juicyslew.moonstation14.ms14.hands;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HandComponentTest {
    @Test
    void jsonAndNbtCodecsRoundTripImmutableState() {
        HandComponent expected = HandComponent.from(HandState.create(List.of("left", "right"), "right")
                .place("left", new ItemToken("opaque-123")).state());

        var json = HandComponent.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        assertEquals(expected, HandComponent.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        var nbt = HandComponent.CODEC.encodeStart(NbtOps.INSTANCE, expected).getOrThrow();
        assertEquals(expected, HandComponent.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());

        ArrayList<HandComponent.Slot> source = new ArrayList<>(expected.hands());
        HandComponent copied = new HandComponent(source, expected.activeHand());
        source.clear();
        assertEquals(expected, copied);
        assertThrows(UnsupportedOperationException.class, () -> copied.hands().clear());
        assertEquals("opaque-123", copied.toHandState().occupant("left").orElseThrow().value());
    }

    @Test
    void malformedSavedStateIsRejectedInsteadOfOverwritingSlots() {
        assertTrue(HandComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\",\"token\":\"same\"},{\"id\":\"right\",\"token\":\"same\"}],\"active\":\"left\"}"))
                .error().isPresent());
        assertTrue(HandComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"},{\"id\":\"left\"}],\"active\":\"left\"}"))
                .error().isPresent());
        assertTrue(HandComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"}],\"active\":\"missing\"}"))
                .error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new HandComponent(
                List.of(new HandComponent.Slot("left", Optional.empty())), "right"));
    }

    @Test
    void invalidPresentJsonTokenIsRejectedButMissingTokenIsEmpty() {
        assertTrue(HandComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\",\"token\":\" \"}],\"active\":\"left\"}"))
                .error().isPresent());
        assertTrue(HandComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\",\"token\":\"" + "x".repeat(HandComponent.MAX_TOKEN_LENGTH + 1)
                        + "\"}],\"active\":\"left\"}"))
                .error().isPresent());
        assertTrue(HandComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\",\"token\":42}],\"active\":\"left\"}"))
                .error().isPresent());

        HandComponent withoutToken = HandComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"}],\"active\":\"left\"}")).getOrThrow();
        assertEquals(Optional.empty(), withoutToken.hands().getFirst().token());
    }

    @Test
    void boundedNetworkCodecRoundTripsAndRejectsMalformedCountAndDuplicateTokens() {
        HandComponent expected = HandComponent.from(HandState.create(List.of("left", "right"))
                .place("right", new ItemToken("opaque")).state());
        RegistryFriendlyByteBuf valid = buffer();
        HandComponent.STREAM_CODEC.encode(valid, expected);
        assertEquals(expected, HandComponent.STREAM_CODEC.decode(valid));
        valid.release();

        RegistryFriendlyByteBuf tooMany = buffer();
        tooMany.writeVarInt(HandComponent.MAX_HANDS + 1);
        assertThrows(IllegalArgumentException.class, () -> HandComponent.STREAM_CODEC.decode(tooMany));
        tooMany.release();

        RegistryFriendlyByteBuf duplicate = buffer();
        duplicate.writeVarInt(2);
        duplicate.writeUtf("left"); duplicate.writeBoolean(true); duplicate.writeUtf("same");
        duplicate.writeUtf("right"); duplicate.writeBoolean(true); duplicate.writeUtf("same");
        duplicate.writeUtf("left");
        assertThrows(IllegalArgumentException.class, () -> HandComponent.STREAM_CODEC.decode(duplicate));
        duplicate.release();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
