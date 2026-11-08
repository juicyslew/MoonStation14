package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeStrikeTimelineTest {
    private static MeleeStrikeProfile profile() { return new MeleeStrikeProfile(1, .4, .55, .2, .8); }

    @Test void extendsHoldsRetractsAndExpiresAtRateScaledDuration() {
        var p = profile();
        assertEquals(new MeleeStrikeTimeline.Sample(0, 0, .8, false), MeleeStrikeTimeline.sample(p, 1, 0));
        var middle = MeleeStrikeTimeline.sample(p, 1, .2);
        assertEquals(.5, middle.extension(), 1e-12);
        assertEquals(.8, middle.trailAlpha(), 1e-12);
        assertEquals(1, MeleeStrikeTimeline.sample(p, 1, .4).extension(), 1e-12);
        assertEquals(1, MeleeStrikeTimeline.sample(p, 1, .55).extension(), 1e-12);
        var returning = MeleeStrikeTimeline.sample(p, 1, .775);
        assertEquals(.5, returning.extension(), 1e-12);
        assertEquals(.4, returning.trailAlpha(), 1e-12);
        var expired = MeleeStrikeTimeline.sample(p, 2, .5);
        assertTrue(expired.expired()); assertEquals(0, expired.extension()); assertEquals(0, expired.trailAlpha());
    }

    @Test void negativeAgeAndInvalidInputsAreSafe() {
        var before = MeleeStrikeTimeline.sample(profile(), 1, -1);
        assertEquals(0, before.extension()); assertEquals(0, before.trailAlpha()); assertFalse(before.expired());
        assertThrows(IllegalArgumentException.class, () -> MeleeStrikeTimeline.sample(profile(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> MeleeStrikeTimeline.sample(profile(), 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MeleeStrikeTimeline.sample(null, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MeleeStrikeProfile(1, 0, .5, .2, .8));
        assertThrows(IllegalArgumentException.class, () -> new MeleeStrikeProfile(1, .4, 1, .2, .8));
    }

    @Test void wideSweepFollowsScaledSs14KeyframesAndSettles() {
        var p = profile();
        assertEquals(0, MeleeStrikeTimeline.wideSweepFraction(p, 0), 1e-12);
        assertEquals(.5, MeleeStrikeTimeline.wideSweepFraction(p, .06), 1e-12);
        assertEquals(1, MeleeStrikeTimeline.wideSweepFraction(p, .09), 1e-12);
        assertEquals(.9, MeleeStrikeTimeline.wideSweepFraction(p, .12), 1e-12);
        assertEquals(.8, MeleeStrikeTimeline.wideSweepFraction(p, .36), 1e-12);
        assertEquals(.5, MeleeStrikeTimeline.wideSweepFraction(p,
                MeleeStrikeTimeline.sample(p, 1, .06).progress()), 1e-12);
        assertEquals(.5, MeleeStrikeTimeline.wideSweepFraction(p,
                MeleeStrikeTimeline.sample(p, 2, .03).progress()), 1e-12);
        assertEquals(.8, MeleeStrikeTimeline.wideSweepFraction(p,
                MeleeStrikeTimeline.sample(p, 1, .36).progress()), 1e-12);
    }
}
