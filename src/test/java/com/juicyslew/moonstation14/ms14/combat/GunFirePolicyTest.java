package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunFirePolicyTest {
    @Test void rateCadenceAndEmptySafetyAreTickBased() {
        assertEquals(10, GunFirePolicy.nextFireTick(0, 2f).orElseThrow());
        assertEquals(10, GunFirePolicy.emptySafetyTick(0).orElseThrow());
    }
    @Test void emptyCooldownCombinesCadenceBurstAndSafetyAndPreservesSpreadMemory() {
        assertEquals(15, GunFirePolicy.emptyDeadline(0, 10, .25f).orElseThrow());
        assertEquals(25, GunFirePolicy.emptyDeadline(0, 20, .25f).orElseThrow());
        assertEquals(10, GunFirePolicy.emptyDeadline(0, 1, .25f).orElseThrow());
        var prior = new GunRuntimeState(10, 10, 1.5f);
        var empty = new GunRuntimeState(GunFirePolicy.emptyDeadline(10, 20, .25f).orElseThrow(),
                prior.lastFireTick(), prior.currentSpreadDegrees());
        assertEquals(prior.lastFireTick(), empty.lastFireTick());
        assertEquals(prior.currentSpreadDegrees(), empty.currentSpreadDegrees());
        var futureLastShot = GunFirePolicy.recoil(new GunRuntimeState(20, 20, 1.5f), GunProfile.SpreadData.SOURCE_DEFAULTS, 10, 1f);
        assertEquals(2f, futureLastShot.currentSpreadDegrees());
    }
    @Test void recoilMatchesFutureLastFireSourceQuirkAndDecaysAfterwards() {
        var spread = GunProfile.SpreadData.SOURCE_DEFAULTS;
        var first = GunFirePolicy.recoil(GunRuntimeState.INITIAL, spread, 0, 1f);
        assertEquals(1f, first.currentSpreadDegrees());
        assertEquals(.5f, first.angleDegrees());
        var secondState = new GunRuntimeState(10, 10, first.currentSpreadDegrees());
        var second = GunFirePolicy.recoil(secondState, spread, 10, 1f);
        assertEquals(1.5f, second.currentSpreadDegrees());
        assertEquals(.75f, second.angleDegrees());
        var third = GunFirePolicy.recoil(new GunRuntimeState(20, 20, second.currentSpreadDegrees()), spread, 20, -1f);
        assertEquals(2f, third.currentSpreadDegrees());
        assertEquals(-1f, third.angleDegrees());
        var decayed = GunFirePolicy.recoil(new GunRuntimeState(20, 10, 2f), spread, 30, 0f);
        assertEquals(1f, decayed.currentSpreadDegrees());
    }
}
