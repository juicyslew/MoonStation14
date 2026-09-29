package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdjustTemperatureTest {
    private static final ThermalExposureMath.ThermalProfile PROFILE =
            ThermalExposureMath.ThermalProfile.HUMAN;

    @Test
    void joulesConvertToKelvinByBoundCharacterHeatCapacity() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(310.15));
        assertEquals(EffectResult.APPLIED, BodyTemperatureSystem.adjustHeat(body, PROFILE, 1470f, 2f));
        assertEquals(310.15 + 2940 / PROFILE.bodyHeatCapacityJoulesPerKelvin(), body.kelvin(), 1e-12);
    }

    @Test
    void negativeJoulesCoolAndOutOfRangeResultsFailWithoutMutation() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(310.15));
        assertEquals(EffectResult.APPLIED, BodyTemperatureSystem.adjustHeat(body, PROFILE, -2940f));
        assertEquals(310.15 - 2940 / PROFILE.bodyHeatCapacityJoulesPerKelvin(), body.kelvin(), 1e-12);
        double before = body.kelvin();
        assertEquals(EffectResult.FAILED, BodyTemperatureSystem.adjustHeat(body, PROFILE, Float.MAX_VALUE));
        assertEquals(before, body.kelvin());
        assertEquals(EffectResult.FAILED, BodyTemperatureSystem.adjustHeat(body, PROFILE, -Float.MAX_VALUE));
        assertEquals(before, body.kelvin());
        assertEquals(EffectResult.FAILED, BodyTemperatureSystem.adjustHeat(body, PROFILE, Float.NaN));
    }

    @Test
    void defaultEffectDispatcherNowOwnsAdjustTemperature() {
        var system = EffectSystem.withDefaults();
        assertTrue(system.supportsHandler(new EffectData.AdjustTemperature(EffectCommonData.DEFAULT, 1f)));
    }
}
