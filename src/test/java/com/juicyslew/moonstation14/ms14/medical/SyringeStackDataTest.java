package com.juicyslew.moonstation14.ms14.medical;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyringeStackDataTest {
    @Test void persistentCodecRoundTripsModeAndDose() {
        var original = new SyringeStackData(ResourceLocation.fromNamespaceAndPath("moonstation14", "syringe_draw_mode"), 5f);
        var encoded = SyringeStackData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, SyringeStackData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test void rejectsInvalidDose() {
        assertThrows(IllegalArgumentException.class, () -> new SyringeStackData(
                ResourceLocation.fromNamespaceAndPath("moonstation14", "syringe_draw_mode"), Float.NaN));
    }
}
