package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpentCasingMotionTest {
    @Test void bounceDampsAndExpiresAtFiniteBoundedLifetime() {
        assertEquals(0.36, SpentCasingMotion.bounce(-0.6, .6f), 1e-7);
        assertEquals(.18, SpentCasingMotion.damp(.25, .72f), 1e-6);
        assertEquals(30, SpentCasingMotion.lifetimeTicks(1.5f));
        assertTrue(SpentCasingMotion.firstLanding(false,true));
        assertFalse(SpentCasingMotion.firstLanding(true,true));
        assertThrows(IllegalArgumentException.class, () -> SpentCasingMotion.lifetimeTicks(Float.NaN));
    }

    @Test void actualStepTransitionBouncesThenFallsAndSettlesWithOneLandingSignal(){
        var first=SpentCasingMotion.step(.2,-.3,.1,.04f,.7f,.32f,false,true,false,true,false);
        assertTrue(first.landedNow());assertTrue(first.vy()>0);
        var airborne=SpentCasingMotion.step(first.vx(),first.vy(),first.vz(),.04f,.7f,.32f,false,false,false,false,true);
        assertFalse(airborne.landedNow());assertTrue(airborne.vy()<first.vy());
        var settled=SpentCasingMotion.step(airborne.vx(),-.02,airborne.vz(),.04f,.7f,.32f,false,true,false,true,true);
        assertFalse(settled.landedNow());assertEquals(0,settled.vy());
    }

    @Test void rollStateAdvancesContinuouslyAndSettlesWithMovement(){
        float previous=1.2f, current=1.3f;
        float moving=SpentCasingMotion.advanceRoll(previous,current,.4,0,0,false);
        assertTrue(moving>current);
        float settled=SpentCasingMotion.advanceRoll(moving,moving,0,0,0,true);
        assertEquals(moving,settled);
        assertTrue(SpentCasingMotion.advanceRoll(current,current,.4,0,0,true)-current
                < moving-current);
    }
}
