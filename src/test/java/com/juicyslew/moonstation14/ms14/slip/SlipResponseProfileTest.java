package com.juicyslew.moonstation14.ms14.slip;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.*;

class SlipResponseProfileTest {
    @Test
    void responseCodecDefaultsAndRoundTripsGenericAndPuddleAdapters() {
        var defaults = SlipResponseData.CODEC.codec().parse(JsonOps.INSTANCE, new JsonObject())
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(SlipResponseData.DEFAULT, defaults);
        assertEquals(SlipResponseData.DEFAULT_SOUND, defaults.soundEvent());
        assertEquals(0d, defaults.soundVolumeDb());

        var generic = SlipSystem.looseItemResponse(.5, 1.5, 1.5, .5, false);
        assertEquals(SlipResponseData.DEFAULT, generic);
        var puddle = SlipResponseData.puddle(.5, 1.5, 1.5, .05, true);
        assertEquals(0d, puddle.staminaDamage());
        assertEquals(0d, puddle.frictionDurationSeconds());
        assertTrue(puddle.affectsSliding());
        assertEquals(.05d, puddle.frictionFactor());
        for (var response : new SlipResponseData[]{generic, puddle}) {
            var encoded = SlipResponseData.CODEC.codec().encodeStart(JsonOps.INSTANCE, response)
                    .getOrThrow(message -> new AssertionError(message));
            assertEquals(response, SlipResponseData.CODEC.codec().parse(JsonOps.INSTANCE, encoded)
                    .getOrThrow(message -> new AssertionError(message)));
        }
        var explicit = SlipResponseData.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"stun_seconds\":0.7,\"friction_factor\":1.4}").getAsJsonObject())
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(.7d, explicit.stunSeconds());
        assertEquals(1.4d, explicit.frictionFactor());
        var cue = SlipResponseData.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"sound_event\":\"moonstation14:mimana\",\"sound_volume_db\":-100}").getAsJsonObject())
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "mimana"), cue.soundEvent());
        assertEquals(-100d, cue.soundVolumeDb());
        assertEquals(cue, SlipResponseData.CODEC.codec().parse(JsonOps.INSTANCE,
                SlipResponseData.CODEC.codec().encodeStart(JsonOps.INSTANCE, cue).getOrThrow()).getOrThrow());
    }

    @Test
    void responseValidationIsFiniteNonnegativeAndDoesNotClampFriction() {
        assertThrows(IllegalArgumentException.class, () -> new SlipResponseData(Double.NaN, 1, 1, 1, 1, 1, false, true, false));
        assertThrows(IllegalArgumentException.class, () -> new SlipResponseData(1, Double.POSITIVE_INFINITY, 1, 1, 1, 1, false, true, false));
        assertThrows(IllegalArgumentException.class, () -> new SlipResponseData(1, 1, -1, 1, 1, 1, false, true, false));
        assertThrows(IllegalArgumentException.class, () -> new SlipResponseData(1, 1, 1, 1, -1, 1, false, true, false));
        assertThrows(IllegalArgumentException.class, () -> new SlipResponseData(1, 1, 1, 1, 1, Double.NEGATIVE_INFINITY, false, true, false));
        assertEquals(1.4d, new SlipResponseData(1, 1, 1, 1.4, 1, 1, false, true, false).frictionFactor());
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -120.01, 24.01}) {
            assertThrows(IllegalArgumentException.class, () -> new SlipResponseData(1,1,1,1,1,1,false,true,false,
                    SlipResponseData.DEFAULT_SOUND, invalid));
        }
        assertEquals(1d, soundGain(0));
        assertEquals(.00001d, soundGain(-100), 1e-15);
        assertNotEquals(-100d, soundGain(-100));
    }

    private static double soundGain(double db) { return Math.pow(10d, db / 20d); }

    @Test
    void itemCompositionCodecDefaultsAndRoundTripsStepOverrideAndResponse() {
        var decoded = ItemSlipperyComposition.CODEC.codec().parse(JsonOps.INSTANCE, new JsonObject())
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(ItemSlipperyComposition.DEFAULT, decoded);
        var override = ItemSlipperyComposition.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"step_trigger\":{\"intersection_ratio\":0.2},\"response\":{\"stamina_damage\":20}}").getAsJsonObject())
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(.2d, override.stepTrigger().intersectionRatio());
        assertEquals(20d, override.response().staminaDamage());
        var encoded = ItemSlipperyComposition.CODEC.codec().encodeStart(JsonOps.INSTANCE, override)
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(override, ItemSlipperyComposition.CODEC.codec().parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError(message)));
        assertThrows(NullPointerException.class, () -> new ItemSlipperyComposition(null, SlipResponseData.DEFAULT));
        assertThrows(NullPointerException.class, () -> new ItemSlipperyComposition(ItemSlipperyComposition.DEFAULT.stepTrigger(), null));
    }
    @Test
    void sourceAdaptersKeepPuddleAndLooseItemPoliciesDistinct() {
        var loose = SlipSystem.looseItemResponse(.5, 1.5, 1.5, .5, false);
        assertEquals(25d, loose.staminaDamage());
        assertEquals(.5d, loose.frictionDurationSeconds());
        assertFalse(loose.affectsSliding());
        assertTrue(loose.requiresContact());

        // The puddle adapter is intentionally different: active puddles opt into sliding,
        // and puddle friction is the weighted reagent friction, not timed status friction.
        var puddle = SlipResponseData.puddle(.5, 1.5, 1.5, .05, false);
        assertEquals(0d, puddle.staminaDamage());
        assertEquals(0d, puddle.frictionDurationSeconds());
        assertEquals(.05d, puddle.frictionFactor());
        assertTrue(puddle.affectsSliding());
    }
}
