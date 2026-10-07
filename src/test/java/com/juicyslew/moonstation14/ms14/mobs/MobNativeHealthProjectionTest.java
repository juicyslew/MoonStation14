package com.juicyslew.moonstation14.ms14.mobs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobNativeHealthProjectionTest {
    @Test void scalesTypicalStatesIntoNativeHealth() {
        assertEquals(40, MobNativeHealthProjection.project(200, 0, true, false, false, true).maxHealth());
        assertEquals(35, MobNativeHealthProjection.project(200, 25, true, false, false, true).health());
        assertEquals(20, MobNativeHealthProjection.project(200, 100, true, false, false, true).health());
        assertEquals(0, MobNativeHealthProjection.project(200, 200, true, false, false, true).health());
    }

    @Test void disallowedDeathAndLegacyCorpsesDoNotRevive() {
        assertEquals(1, MobNativeHealthProjection.project(10, 10, false, false, false, true).health());
        assertEquals(0, MobNativeHealthProjection.project(200, 0, true, false, true, false).health());
        assertEquals(0, MobNativeHealthProjection.project(200, 0, true, true, false, true).health());
    }

    @Test void supportsSmallMaxAndDeadThreshold() {
        var result = MobNativeHealthProjection.project(10, 10, true, false, false, true);
        assertEquals(2, result.maxHealth());
        assertEquals(0, result.health());
    }
}
