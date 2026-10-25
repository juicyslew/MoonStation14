package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.character.components.EggLayerComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EggLayerPolicyTest {
    @Test void upstreamDefaultsAndDelayBounds() {
        var policy = new EggLayerComponent("minecraft:egg", 60, 120, 60);
        assertEquals(60, policy.cooldownMin());
        assertEquals(120, policy.cooldownMax());
        assertEquals(60f, policy.hungerUsage());
        for (int i = 0; i < 200; i++) {
            long ticks = EggLayerSystem.randomDelay(policy, net.minecraft.util.RandomSource.create(i));
            assertTrue(ticks >= 1200 && ticks <= 2400);
        }
    }

    @Test void rejectsInvalidIntervalsAndCosts() {
        assertThrows(IllegalArgumentException.class, () -> new EggLayerComponent("minecraft:egg", 0, 5, 60));
        assertThrows(IllegalArgumentException.class, () -> new EggLayerComponent("minecraft:egg", 60, 59, 60));
        assertThrows(IllegalArgumentException.class, () -> new EggLayerComponent("minecraft:egg", 60, 120, -1));
    }

    @Test void productionRequiresLiveAndEnoughHungerOnlyWhenHungerExists() {
        assertTrue(EggLayerSystem.eligibleForProduction(true, false, 0, 60));
        assertTrue(EggLayerSystem.eligibleForProduction(true, true, 60, 60));
        assertFalse(EggLayerSystem.eligibleForProduction(true, true, 59.99f, 60));
        assertFalse(EggLayerSystem.eligibleForProduction(false, false, 0, 60));
    }
}
