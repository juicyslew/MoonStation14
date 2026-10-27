package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExplosionPresentationTest {
    @Test
    void smallFarAudioRequiresPlannerExpansionRatherThanOnlySmallSoundClassification() {
        var source = new BlastPlanner.Pos(0, 0, 0);
        var sampler = (BlastPlanner.Sampler) p -> BlastPlanner.Cell.OPEN;
        var defaultSmall = BlastPlanner.plan(source, new BlastPlanner.Proposal(.1, 1, .001), sampler);
        assertEquals(0, defaultSmall.expansionIterations());
        assertFalse(ExplosionFeedbackService.smallFarEligible(9, defaultSmall.expansionIterations()));

        var expandingSmall = BlastPlanner.plan(source, new BlastPlanner.Proposal(.6, 1, 1), sampler);
        assertEquals(1, expandingSmall.expansionIterations());
        assertTrue(ExplosionFeedbackService.smallFarEligible(9, expandingSmall.expansionIterations()));
    }

    @Test
    void emissionFailureIsolatedAndLaterRecipientStillRuns() {
        boolean[] laterRecipient = {false};
        ExplosionPresentation.sendBestEffort(() -> { throw new IllegalStateException("packet failed"); });
        ExplosionPresentation.sendBestEffort(() -> laterRecipient[0] = true);
        assertTrue(laterRecipient[0]);
    }

    @Test
    void smallNearAndFarRangesOverlapIndependently() {
        assertTrue(ExplosionFeedbackService.smallNearEligible(1));
        assertFalse(ExplosionFeedbackService.smallFarEligible(1, 1));
        assertTrue(ExplosionFeedbackService.smallNearEligible(9));
        assertTrue(ExplosionFeedbackService.smallFarEligible(9, 1));
        assertTrue(ExplosionFeedbackService.smallNearEligible(36));
        assertFalse(ExplosionFeedbackService.smallFarEligible(36, 1));
        assertFalse(ExplosionFeedbackService.smallFarEligible(9, 6));
    }
}
