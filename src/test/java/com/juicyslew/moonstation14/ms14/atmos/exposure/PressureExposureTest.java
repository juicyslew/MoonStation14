package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PressureExposureTest {
    private static final BarotraumaComponent HUMAN = new BarotraumaComponent(
            new BarotraumaComponent.Damage(Map.of("blunt", 0.5, "heat", 0.1)), 200);
    private static final BarotraumaComponent PIG = new BarotraumaComponent(
            new BarotraumaComponent.Damage(Map.of("blunt", 0.15)), 200);

    @Test void lowThresholdAndHighScale() {
        assertEquals(Map.of("blunt", 2f, "heat", 0.4f), PressureExposure.damageAt(0, HUMAN));
        assertEquals(Map.of("blunt", 2f, "heat", 0.4f), PressureExposure.damageAt(20, HUMAN));
        assertEquals(Map.of("blunt", 0.6f), PressureExposure.damageAt(20, PIG));
        assertTrue(PressureExposure.damageAt(20.001, HUMAN).isEmpty());
        assertTrue(PressureExposure.damageAt(550, HUMAN).isEmpty());
        assertEquals(Map.of("blunt", 0.5f, "heat", 0.1f), PressureExposure.damageAt(687.5, HUMAN));
        assertEquals(Map.of("blunt", 2f, "heat", 0.4f), PressureExposure.damageAt(1100, HUMAN));
        assertEquals(Map.of("blunt", 0.6f), PressureExposure.damageAt(Double.MAX_VALUE, PIG));
        assertTrue(PressureExposure.damageAt(Double.NaN, HUMAN).isEmpty());
        assertTrue(PressureExposure.damageAt(-1, HUMAN).isEmpty());
    }

    @Test void ceilingCountsOnlyMatchingTypedDamageAcrossBothMaps() {
        var low = PressureExposure.damageAt(0, HUMAN);
        assertEquals(low, PressureExposure.limitMatchingDamage(low, Map.of("poison", 900f), HUMAN));
        assertEquals(low, PressureExposure.limitMatchingDamage(
                low, Map.of("blunt", 198f, "heat", 0.8f, "poison", 999f), HUMAN));
        assertTrue(PressureExposure.limitMatchingDamage(low, Map.of("blunt", 199f, "heat", 1f), HUMAN).isEmpty());
        assertEquals(Map.of("blunt", 0.6f), PressureExposure.limitMatchingDamage(
                PressureExposure.damageAt(0, PIG), Map.of("heat", 300f), PIG));
    }

    @Test void preCheckAllowsFullCrossingHitThenStopsUntilHealing() {
        var low = PressureExposure.damageAt(0, HUMAN);
        assertEquals(Map.of("blunt", 2f, "heat", 0.4f), low);
        var nearCeiling = Map.of("blunt", 199.9f, "poison", 800f);
        assertEquals(low, PressureExposure.limitMatchingDamage(low, nearCeiling, HUMAN));
        var afterHit = Map.of("blunt", 201.9f, "heat", 0.4f, "poison", 800f);
        assertTrue(PressureExposure.limitMatchingDamage(low, afterHit, HUMAN).isEmpty());
        var afterHealing = Map.of("blunt", 198.9f, "heat", 0.4f, "poison", 800f);
        assertEquals(low, PressureExposure.limitMatchingDamage(low, afterHealing, HUMAN));
    }

    @Test void cadenceRoundsToAtLeastOneTickWithoutOverflow() {
        assertEquals(20, BarotraumaAtmospherePolicy.CADENCE_TICKS);
    }
}
