package com.juicyslew.moonstation14.ms14.projectile;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileFlyByProfileTest {
    @Test void codecUsesUpstreamDefaultsAndResolvesOverrides() {
        var defaults = ProjectileFlyByProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .getOrThrow(IllegalStateException::new);
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "projectile/fly_by"), defaults.sound());
        assertEquals(0.10f, defaults.probability());
        assertEquals(1.5f, defaults.range());

        var overridden = ProjectileFlyByProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"probability\":0.25,\"range\":2.0}" )).getOrThrow(IllegalStateException::new);
        assertEquals(0.25f, overridden.probability());
        assertEquals(2f, overridden.range());
    }

    @Test void probabilityAndRangeRejectValuesOutsideComponentBounds() {
        assertTrue(ProjectileFlyByProfile.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"probability\":1.01}")).error().isPresent());
        assertTrue(ProjectileFlyByProfile.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"range\":-0.1}")).error().isPresent());
    }
}
