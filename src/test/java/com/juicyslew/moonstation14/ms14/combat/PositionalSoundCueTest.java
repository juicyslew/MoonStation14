package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PositionalSoundCueTest {
    @Test void codecUsesOwnerDefaultsAndKeepsAllAuthoredFields() {
        var event = ResourceLocation.parse("moonstation14:ss14/weapons/punch_miss");
        var melee = MeleeSoundData.SOUND_CUE_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"event\":\"moonstation14:ss14/weapons/punch_miss\"}")).result().orElseThrow();
        assertEquals(-3f, melee.volumeDb()); assertEquals(.025f, melee.pitchVariation());
        var gun = PositionalSoundCue.codec(0f, 0f).parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"event\":\"moonstation14:ss14/weapons/punch_miss\",\"volume_db\":-3,\"pitch_variation\":0.025}")).result().orElseThrow();
        assertEquals(new PositionalSoundCue(event, -3f, .025f), gun);
        assertEquals(Math.pow(10, -3f / 20), melee.volume(), 1e-6);
    }
    @Test void invalidFiniteValuesAreRejected() {
        var event = ResourceLocation.parse("moonstation14:ss14/weapons/punch_miss");
        assertThrows(IllegalArgumentException.class, () -> new PositionalSoundCue(event, Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new PositionalSoundCue(event, 0, Float.POSITIVE_INFINITY));
    }
}
