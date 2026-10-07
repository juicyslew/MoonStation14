package com.juicyslew.moonstation14.ms14.mobs.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPerspectivePolicyTest {
    @Test void restoresOriginalUnlessUserCyclesPerspective() {
        var policy = new MedicalPerspectivePolicy<String>();
        assertTrue(policy.state(true, "first").pushRear());
        assertEquals("first", policy.state(false, "rear").restore());
    }

    @Test void manualCriticalPerspectiveBecomesDeadBaseline() {
        var policy = new MedicalPerspectivePolicy<String>();
        policy.state(true, "first");
        policy.manualChange();
        assertTrue(policy.dead("front").pushRear());
        assertEquals("front", policy.state(false, "rear").restore());
    }

    @Test void untouchedCriticalBaselineSurvivesDeadAndManualDeadChangeBlocksRestore() {
        var policy = new MedicalPerspectivePolicy<String>();
        policy.state(true, "first");
        policy.dead("rear");
        assertEquals("first", policy.state(false, "rear").restore());

        policy.state(true, "first");
        policy.dead("rear");
        policy.manualChange();
        assertNull(policy.state(false, "front").restore());
    }

    @Test void manualCycleEvenBackToSamePerspectivePreventsRestore() {
        var policy = new MedicalPerspectivePolicy<String>();
        policy.state(true, "first");
        policy.manualChange();
        policy.manualChange();
        assertNull(policy.state(false, "rear").restore());
    }

    @Test void observationEndRestoresWithoutMedicalMutation() {
        var policy = new MedicalPerspectivePolicy<String>();
        policy.state(true, "third");
        assertEquals("third", policy.observationChanged().restore());
        assertNull(policy.observationChanged().restore());
    }
}
