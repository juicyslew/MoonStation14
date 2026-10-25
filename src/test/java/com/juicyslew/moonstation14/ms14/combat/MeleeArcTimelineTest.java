package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeArcTimelineTest {
    private static MeleeArcProfile profile(MeleeArcProfile.Animation animation, boolean fade, double lifetime) {
        return new MeleeArcProfile(ResourceLocation.fromNamespaceAndPath("moonstation14", "test"), animation, fade, lifetime);
    }

    @Test void thrustUsesRelativeSegmentKeyTimesAndHoldsAtAnimationLength() {
        var p = profile(MeleeArcProfile.Animation.THRUST, true, 2);
        double l = .6;
        assertEquals(0, MeleeArcTimeline.sample(p, 1, 1, 0).offsetMultiplier());
        assertEquals(.39, MeleeArcTimeline.sample(p, 1, 1, .03).offsetMultiplier(), 1e-12);
        assertEquals(.78, MeleeArcTimeline.sample(p, 1, 1, .06).offsetMultiplier(), 1e-12);
        assertEquals(1.2, MeleeArcTimeline.sample(p, 1, 1, .18).offsetMultiplier(), 1e-12);
        assertEquals(1.08, MeleeArcTimeline.sample(p, 1, 1, .36).offsetMultiplier(), 1e-12);
        double held = 1.08 + (.84 - 1.08) * (1 - Math.pow(1.0 / 3, 4));
        assertEquals(held, MeleeArcTimeline.sample(p, 1, 1, l).offsetMultiplier(), 1e-12);
        assertNotEquals(.84, held, 1e-6);
    }

    @Test void fadeAndRateAndNoneBehavior() {
        var thrust = profile(MeleeArcProfile.Animation.THRUST, true, 2);
        assertEquals(.7, MeleeArcTimeline.sample(thrust, 1, 1, .525).alpha(), 1e-12);
        assertFalse(MeleeArcTimeline.sample(thrust, 1, 1, .525).expired());
        assertTrue(MeleeArcTimeline.sample(thrust, 1, 1, .75).expired());
        assertEquals(1.2, MeleeArcTimeline.sample(thrust, 2, 1, .09).offsetMultiplier(), 1e-12);
        var fist = profile(MeleeArcProfile.Animation.NONE, true, 2);
        assertEquals(0, MeleeArcTimeline.sample(fist, 1, 9, 0).offsetMultiplier());
        assertEquals(.5, MeleeArcTimeline.sample(fist, 1, 9, .075).alpha(), 1e-12);
        assertTrue(MeleeArcTimeline.sample(fist, 1, 9, .15).expired());
        assertTrue(MeleeArcTimeline.sample(profile(MeleeArcProfile.Animation.NONE, true, .1), 1, 9, .1).expired());
        assertFalse(MeleeArcTimeline.sample(profile(MeleeArcProfile.Animation.THRUST, false, 2), 1, 1, 1).expired());
    }

    @Test void slashUsesSourceRelativeRotationAndMovementTracksAndMirrors() {
        var slash=profile(MeleeArcProfile.Animation.SLASH,true,2);
        double l=.6;
        double startF=.9*Math.cos(Math.toRadians(30)), startS=.45;
        var start=MeleeArcTimeline.sample(slash,1,1,60,false,0);
        assertEquals(startF,start.offsetMultiplier(),1e-12); assertEquals(startS,start.lateralOffset(),1e-12); assertEquals(30,start.rotationDelta(),1e-12);
        var middle=MeleeArcTimeline.sample(slash,1,1,60,false,.1*l);
        assertEquals(1.1,middle.offsetMultiplier(),1e-12); assertEquals(0,middle.lateralOffset(),1e-12); assertEquals(0,middle.rotationDelta(),1e-12);
        var end=MeleeArcTimeline.sample(slash,1,1,60,false,.25*l);
        assertEquals(startF,end.offsetMultiplier(),1e-12); assertEquals(-.45,end.lateralOffset(),1e-12); assertEquals(-30,end.rotationDelta(),1e-12);
        var leftEnd=MeleeArcTimeline.sample(slash,1,1,60,true,.25*l);
        assertEquals(startF,leftEnd.offsetMultiplier(),1e-12); assertEquals(.45,leftEnd.lateralOffset(),1e-12); assertEquals(30,leftEnd.rotationDelta(),1e-12);
        var blend=MeleeArcTimeline.sample(slash,1,1,60,false,.85*l);
        assertEquals(startF,blend.offsetMultiplier(),1e-12); assertEquals(-.27,blend.lateralOffset(),1e-12);
        var ease=MeleeArcTimeline.sample(slash,1,1,60,false,.55*l);
        double u=(.55-.45)/.6, out=1-Math.pow(1-u,4);
        assertEquals(30-60*(.9-.1*out),ease.rotationDelta(),1e-12);
        var finalRotation=MeleeArcTimeline.sample(slash,1,1,60,false,1.05*l);
        assertEquals(-18,finalRotation.rotationDelta(),1e-12);
        assertEquals(startF*.5,MeleeArcTimeline.sample(slash,1,.5,60,false,0).offsetMultiplier(),1e-12);
        assertEquals(startS*.5,MeleeArcTimeline.sample(slash,1,.5,60,false,0).lateralOffset(),1e-12);
        assertEquals(.7, MeleeArcTimeline.sample(slash,1,1,60,false,.525).alpha(),1e-12);
        assertEquals(MeleeArcTimeline.sample(slash,1,1,60,false,.65).lateralOffset(), MeleeArcTimeline.sample(slash,1,1,60,false,1).lateralOffset(),1e-12);
        assertTrue(MeleeArcTimeline.sample(slash,1,1,60,false,.75).expired());
        assertEquals(MeleeArcTimeline.sample(slash,2,1,60,false,.3).lateralOffset(), MeleeArcTimeline.sample(slash,1,1,60,false,.6).lateralOffset(),1e-12);
    }

    @Test void rejectsInvalidTimingInputs() {
        var p = profile(MeleeArcProfile.Animation.THRUST, true, 2);
        assertThrows(IllegalArgumentException.class, () -> MeleeArcTimeline.sample(p, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> MeleeArcTimeline.sample(p, 1, 1, Double.NaN));
    }

    @Test void lungeUsesFixedAbsolutePeakAndReturnTimes() {
        assertEquals(0, MeleeArcTimeline.lunge(0));
        assertEquals(.075, MeleeArcTimeline.lunge(.02), 1e-12);
        assertEquals(.15, MeleeArcTimeline.lunge(.04), 1e-12);
        assertEquals(.075, MeleeArcTimeline.lunge(.07), 1e-12);
        assertEquals(0, MeleeArcTimeline.lunge(.10), 1e-12);
        assertEquals(0, MeleeArcTimeline.lunge(.11), 1e-12);
    }
}
