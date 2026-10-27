package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeSoundDataTest {
    @Test void defaultsAndCueValidationAreSupportedAndFinite() {
        var audio = MeleeSoundData.DEFAULT;
        assertEquals("moonstation14:ss14/weapons/punch_miss", audio.swing().event().toString());
        assertEquals(-3f, audio.swing().volumeDb()); assertEquals(.025f, audio.swing().pitchVariation());
        assertNull(audio.hit());
        assertEquals("moonstation14:ss14/effects/weak_hit", audio.noDamage().event().toString());
        assertThrows(IllegalArgumentException.class, () -> new PositionalSoundCue(audio.swing().event(), Float.NaN, .1f));
        assertThrows(IllegalArgumentException.class, () -> new PositionalSoundCue(audio.swing().event(), 0f, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new PositionalSoundCue(audio.swing().event(), 0f, -0.1f));
        var decoded = MeleeSoundData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"hit\":{\"event\":\"moonstation14:ss14/weapons/blade_slice\"}}" )).getOrThrow();
        assertEquals("moonstation14:ss14/weapons/blade_slice", decoded.hit().event().toString());
        assertEquals(-3f, decoded.hit().volumeDb()); assertEquals(.025f, decoded.hit().pitchVariation());
    }
}
