package com.juicyslew.moonstation14.eventhooks;

import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SickPopupProvenanceTest {
    private static EffectData.PopupMessage popup(String subtype, String... keys) {
        return new EffectData.PopupMessage(EffectCommonData.DEFAULT, subtype, "small", List.of(keys));
    }

    @Test
    void onlyAppliedLocalSicknessPopupForServerPlayerIsTraced() {
        var sick = popup("local", "generic-reagent-effect-sick");
        assertTrue(SickPopupProvenance.shouldTrace(EffectResult.APPLIED, true, sick));
        assertFalse(SickPopupProvenance.shouldTrace(EffectResult.SKIPPED_PROBABILITY, true, sick));
        assertFalse(SickPopupProvenance.shouldTrace(EffectResult.APPLIED, false, sick));
        assertFalse(SickPopupProvenance.shouldTrace(EffectResult.APPLIED, true,
                popup("local", "another-message")));
        assertFalse(SickPopupProvenance.shouldTrace(EffectResult.APPLIED, true,
                popup("pvs", "generic-reagent-effect-sick")));
    }

    @Test
    void limiterReallowsAfterIntervalAndHandlesClockMovingBackwards() {
        var limiter = new SickPopupProvenance.Limiter(64, 10);
        UUID player = UUID.randomUUID();
        assertTrue(limiter.allow(player, 100));
        assertFalse(limiter.allow(player, 109));
        assertTrue(limiter.allow(player, 90)); // monotonic clock reset does not mute forever
        assertFalse(limiter.allow(player, 99));
        assertTrue(limiter.allow(player, 100));
    }

    @Test
    void limiterEvictsOldPlayersAndNeverExceedsBound() {
        var limiter = new SickPopupProvenance.Limiter(64, 10);
        UUID first = null;
        for (int i = 0; i < 65; i++) {
            UUID id = new UUID(0, i + 1);
            if (i == 0) first = id;
            assertTrue(limiter.allow(id, i));
        }
        assertEquals(64, limiter.size());
        assertTrue(limiter.allow(first, 100));
        assertEquals(64, limiter.size());
    }
}
