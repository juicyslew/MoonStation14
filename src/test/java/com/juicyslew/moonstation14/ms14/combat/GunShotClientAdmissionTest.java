package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;

class GunShotClientAdmissionTest {
    @Test void sourceDeadlinesAndClockProofMatchLiveCadencePolicy() {
        for (float rate : new float[]{1f, 2f}) {
            long shot = GunFirePolicy.nextFireTick(100, rate).orElseThrow();
            assertEquals(rate == 1f ? 20 : 10, shot - 100);
            long empty = GunFirePolicy.emptyDeadline(100, shot, .25f).orElseThrow();
            assertEquals(rate == 1f ? 25 : 15, empty - 100);
            assertTrue(MeleeAttackClientPolicy.sourceDeadlineReady(shot,
                    OptionalDouble.of(shot)), "server anchor equality admits cadence");
            assertFalse(MeleeAttackClientPolicy.sourceDeadlineReady(shot, OptionalDouble.empty()));
        }
        assertFalse(GunInputRoute.matches(true, false));
        assertTrue(GunInputRoute.matches(true, true));
        assertTrue(GunInputRoute.matches(false, false));
        assertFalse(GunInputRoute.matches(false, true));
    }
}
