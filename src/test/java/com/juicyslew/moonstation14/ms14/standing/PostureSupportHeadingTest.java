package com.juicyslew.moonstation14.ms14.standing;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PostureSupportHeadingTest {
    private static Quaterniond yaw(double radians) { return new Quaterniond().rotationY(radians); }

    @Test void supportedWorldUpTurnsAccumulateAtMultipleRatesWithoutMutatingInput() {
        UUID frame = UUID.randomUUID();
        for (double rate : new double[]{.5, 1, 3}) {
            var attachment = new PostureAttachment();
            var baseline = yaw(0);
            assertEquals(0, attachment.sampleSupport(frame, baseline, true));
            var sample = yaw(rate);
            assertEquals((float)-Math.toDegrees(rate), attachment.sampleSupport(frame, sample, true), .001f);
            assertEquals(0, baseline.y, 0); // the copied basis remains the original sample
            assertEquals(0, sample.x, 0);
        }
    }

    @Test void supportLossUnknownAndIdentityChangeFreezeAndRequireFreshBaseline() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var attachment = new PostureAttachment();
        attachment.sampleSupport(first, yaw(0), true);
        assertEquals(0, attachment.sampleSupport(null, null, false));
        assertEquals(0, attachment.sampleSupport(first, yaw(2), true));
        assertEquals(0, attachment.sampleSupport(second, yaw(3), true));
        assertEquals(0, attachment.sampleSupport(second, yaw(4), false));
        assertEquals(0, attachment.sampleSupport(second, yaw(5), true));
        assertEquals(-Math.toDegrees(1), WorldUpTwist.degrees(new Quaterniond(yaw(6)).mul(yaw(5).conjugate()).normalize()), .001);
    }

    @Test void transientBasisCopiesOnlyAcrossContinuousProneUpdatesAndGetupYawSurvives() {
        UUID frame = UUID.randomUUID();
        var original = new PostureAttachment(PostureData.STANDING.facing(37));
        original.sampleSupport(frame, yaw(0), true);
        var replacement = new PostureAttachment(original.data().voluntaryToggle(1, 0));
        replacement.support(original.supportFrame, original.supportOrientation);
        assertEquals(-Math.toDegrees(.5), replacement.sampleSupport(frame, yaw(.5), true), .001);
        assertEquals(37, replacement.data().completeGetUp(true).movementFacingYaw(), .001);

        // Exercise the exact transition policy used by PostureSystem.save: an up transition
        // drops the old basis; re-prone seeds from the current orientation, never the old one.
        var up = new PostureAttachment(replacement.data().completeGetUp(true));
        PostureAttachment.carrySupportAcrossPostureUpdate(replacement, up);
        var reprone = new PostureAttachment(up.data().voluntaryToggle(2, 0));
        PostureAttachment.carrySupportAcrossPostureUpdate(up, reprone);
        assertEquals(0, reprone.sampleSupport(frame, yaw(2), true), .001);
        assertEquals(-Math.toDegrees(.25), reprone.sampleSupport(frame, yaw(2.25), true), .001);
    }
}
