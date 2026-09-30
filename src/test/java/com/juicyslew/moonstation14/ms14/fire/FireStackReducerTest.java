package com.juicyslew.moonstation14.ms14.fire;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireStackReducerTest {
    @Test
    void stateValidationAndEmptyInvariantAreClosed() {
        assertEquals(FireStackComponent.EMPTY, new FireStackComponent());
        assertThrows(IllegalArgumentException.class, () -> new FireStackComponent(Float.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> new FireStackComponent(Float.POSITIVE_INFINITY, false));
        assertThrows(IllegalArgumentException.class, () -> new FireStackComponent(-11f, false));
        assertThrows(IllegalArgumentException.class, () -> new FireStackComponent(11f, false));
        assertThrows(IllegalArgumentException.class, () -> new FireStackComponent(0f, true));
        assertThrows(IllegalArgumentException.class, () -> new FireStackComponent(-1f, true));
        assertTrue(FireStackComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonOps.INSTANCE.createMap(java.util.Map.of())).error().isPresent());
    }

    @Test
    void flammableSelectsExistingMultiplierAndClampsBothDirections() {
        assertEquals(2f, FireStackReducer.flammable(
                FireStackComponent.EMPTY, 2f, 9f, 1f).stacks());
        assertEquals(10f, FireStackReducer.flammable(
                new FireStackComponent(1f, false), 2f, 9f, 1f).stacks());
        assertEquals(10f, FireStackReducer.flammable(
                new FireStackComponent(9f, true), 9f, null, 1f).stacks());
        assertEquals(-10f, FireStackReducer.flammable(
                new FireStackComponent(-9f, false), -9f, null, 1f).stacks());
        assertEquals(FireStackComponent.EMPTY, FireStackReducer.flammable(
                new FireStackComponent(1f, true), -2f, null, 1f));
        assertTrue(FireStackReducer.flammable(
                new FireStackComponent(1f, true), 2f, null, 1f).ignited());
        assertFalse(FireStackReducer.flammable(
                new FireStackComponent(1f, true), -2f, null, 1f).ignited());
    }

    @Test
    void ignitionAndExtinguishingAreStateOnlyAndIdempotent() {
        assertEquals(FireStackComponent.EMPTY, FireStackReducer.ignite(FireStackComponent.EMPTY));
        FireStackComponent lit = FireStackReducer.ignite(new FireStackComponent(2f, false));
        assertTrue(lit.ignited());
        assertEquals(lit, FireStackReducer.ignite(lit));

        assertEquals(FireStackComponent.EMPTY, FireStackReducer.extinguish(lit, -1.5f, 0f));
        assertEquals(new FireStackComponent(0.5f, false),
                FireStackReducer.extinguish(new FireStackComponent(2f, false), -1.5f, 1f));
        assertEquals(new FireStackComponent(-1.5f, false),
                FireStackReducer.extinguish(new FireStackComponent(-1f, false), -0.5f, 1f));
    }

    @Test
    void dryingRecoversOneStackPerIntervalAndCleansAtZero() {
        FireStackComponent state = new FireStackComponent(-2f, false);
        state = FireStackReducer.dry(state);
        assertEquals(-1f, state.stacks());
        state = FireStackReducer.dry(state);
        assertEquals(FireStackComponent.EMPTY, state);
        assertEquals(FireStackComponent.EMPTY, FireStackReducer.dry(FireStackComponent.EMPTY));
    }

    @Test
    void ignitedFadePreservesIgnitionUntilClampedAtZero() {
        assertEquals(new FireStackComponent(1.9f, true), FireStackReducer.fadeIgnited(
                new FireStackComponent(2f, true), -0.1f));
        assertEquals(FireStackComponent.EMPTY, FireStackReducer.fadeIgnited(
                new FireStackComponent(0.5f, true), -1f));
        assertEquals(new FireStackComponent(2f, false), FireStackReducer.fadeIgnited(
                new FireStackComponent(2f, false), -0.1f));
    }

    @Test
    void codecRoundTripsAndRejectsInvalidStackValues() {
        FireStackComponent value = new FireStackComponent(3.5f, true);
        var encoded = FireStackComponent.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, FireStackComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertTrue(FireStackComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"firestacks\":11,\"ignited\":false}")).error().isPresent());

        FireStackAttachment attachment = new FireStackAttachment(value);
        var attachmentEncoded = FireStackAttachment.CODEC
                .encodeStart(JsonOps.INSTANCE, attachment).getOrThrow();
        assertEquals(attachment,
                FireStackAttachment.CODEC.parse(JsonOps.INSTANCE, attachmentEncoded).getOrThrow());
        assertTrue(new FireStackAttachment().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> FireStackComponent.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"firestacks\":0,\"ignited\":true}")));

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);
        FireStackAttachment.STREAM_CODEC.encode(buffer, attachment);
        assertEquals(attachment, FireStackAttachment.STREAM_CODEC.decode(buffer));
        buffer.release();

        RegistryFriendlyByteBuf invalid = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);
        invalid.writeFloat(0f);
        invalid.writeBoolean(true);
        assertThrows(IllegalArgumentException.class,
                () -> FireStackAttachment.STREAM_CODEC.decode(invalid));
        invalid.release();
    }
}
