package com.juicyslew.moonstation14.recipe;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionRecipeSoundTest {
    @Test
    void legacyConstructorUsesDefaultAndSoundMayBeOverriddenOrMuted() {
        var legacy = new ReactionRecipe(Map.of(), Map.of(), Map.of());
        assertEquals(Optional.of(ReactionRecipe.DEFAULT_SOUND), legacy.sound());
        var override = net.minecraft.resources.ResourceLocation.parse("test:custom");
        assertEquals(Optional.of(override), new ReactionRecipe(Map.of(), Map.of(), Map.of(), Optional.of(override)).sound());
        assertEquals(Optional.empty(), new ReactionRecipe(Map.of(), Map.of(), Map.of(), Optional.empty()).sound());
    }

    @Test
    void jsonCodecDefaultsOverridesAndAcceptsExplicitNullSilence() {
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        var json = com.google.gson.JsonParser.parseString("{\"inputs\":{},\"catalysts\":{},\"outputs\":{}}");
        var decoded = ReactionRecipe.Serializer.CODEC.codec().parse(ops, json).getOrThrow();
        assertEquals(Optional.of(ReactionRecipe.DEFAULT_SOUND), decoded.sound());
        var overrideJson = com.google.gson.JsonParser.parseString("{\"inputs\":{},\"catalysts\":{},\"outputs\":{},\"sound\":\"test:custom\"}");
        assertEquals(Optional.of(net.minecraft.resources.ResourceLocation.parse("test:custom")),
                ReactionRecipe.Serializer.CODEC.codec().parse(ops, overrideJson).getOrThrow().sound());
        var mutedJson = com.google.gson.JsonParser.parseString("{\"inputs\":{},\"catalysts\":{},\"outputs\":{},\"sound\":\"\"}");
        assertTrue(ReactionRecipe.Serializer.CODEC.codec().parse(ops, mutedJson).getOrThrow().sound().isEmpty());
    }

    @Test
    void streamCodecRoundTripsDefaultOverrideAndSilence() {
        var recipes = java.util.List.of(
                new ReactionRecipe(Map.of(), Map.of(), Map.of()),
                new ReactionRecipe(Map.of(), Map.of(), Map.of(), Optional.of(net.minecraft.resources.ResourceLocation.parse("test:custom"))),
                new ReactionRecipe(Map.of(), Map.of(), Map.of(), Optional.empty()));
        for (var recipe : recipes) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
            try {
                ReactionRecipe.Serializer.STREAM_CODEC.encode(buffer, recipe);
                assertEquals(recipe, ReactionRecipe.Serializer.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
}
