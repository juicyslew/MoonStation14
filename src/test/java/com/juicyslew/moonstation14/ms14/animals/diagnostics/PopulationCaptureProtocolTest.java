package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PopulationCaptureProtocolTest {
    @Test void idleCoverageRetainsExactBoundsAndReportsEveryUnmetPredicate() {
        PopulationCaptureProtocol metrics = new PopulationCaptureProtocol();
        for (int i=0;i<999;i++) metrics.bodySample(32);
        assertEquals(java.util.List.of("nearSamples>=1000", "acceptedInputSamples>0"),
                metrics.failureReasons("population_idle", 100, 100));
        metrics.acceptedStep(false,false,Vec3.ZERO,new Vec3(2.01,0,0));
        assertEquals(java.util.List.of("nearSamples>=1000", "acceptedDistance<=2"),metrics.failureReasons("population_idle",100,100));
        metrics.reset(); for(int i=0;i<1000;i++)metrics.bodySample(32); metrics.acceptedStep(false,false,Vec3.ZERO,new Vec3(2,0,0));
        metrics.bodySample(32);
        assertTrue(metrics.covered("population_idle",100,100));
        assertEquals(java.util.List.of(),metrics.failureReasons("population_idle",100,100));
        assertEquals(java.util.List.of("population expected=100 actual=99"),metrics.failureReasons("population_idle",99,100));
    }

    @Test void failurePredicatesUseExactOriginalComparatorsAndRemainPhaseLocal() {
        PopulationCaptureProtocol m=new PopulationCaptureProtocol();
        assertEquals(java.util.List.of("acceptedDistance>=16", "nearSamples>=100"),m.failureReasons("population_move",100,100));
        m.acceptedStep(false,false,Vec3.ZERO,new Vec3(16,0,0)); for(int i=0;i<100;i++)m.bodySample(32);
        assertTrue(m.covered("population_move",100,100));
        m.reset(); assertEquals(java.util.List.of("groundedJumps>=3", "upwardSamples>0", "nearSamples>=100"),m.failureReasons("population_jump",100,100));
        for(int i=0;i<3;i++)m.acceptedStep(true,true,Vec3.ZERO,new Vec3(0,.03,0)); for(int i=0;i<100;i++)m.bodySample(32);
        assertTrue(m.covered("population_jump",100,100));
        m.reset(); assertEquals(java.util.List.of("farSamples>=100"),m.failureReasons("population_away",100,100));
        for(int i=0;i<100;i++)m.bodySample(64); assertTrue(m.covered("population_away",100,100));
        m.reset(); assertEquals(java.util.List.of("nearSamples>=100"),m.failureReasons("population_return",100,100));
        for(int i=0;i<100;i++)m.bodySample(32); assertTrue(m.covered("population_return",100,100));
        m.reset(); assertEquals(java.util.List.of("nearSamples>=100"),m.failureReasons("population_return",100,100));
    }

    @Test void sustainedSequenceIsFiveNamedSixtySecondServerTickPhases() {
        assertEquals(java.util.List.of("population_idle", "population_move", "population_jump", "population_away", "population_return"),
                PopulationCaptureProtocol.PHASES);
        assertEquals(1200, PopulationCaptureProtocol.PHASE_TICKS);
        assertFalse(PopulationCaptureProtocol.phaseCompleteAt(1199));
        assertTrue(PopulationCaptureProtocol.phaseCompleteAt(1200));
        String phase = PopulationCaptureProtocol.PHASES.get(0);
        for (int i=1; i<PopulationCaptureProtocol.PHASES.size(); i++) phase=PopulationCaptureProtocol.next(phase);
        assertEquals("complete", PopulationCaptureProtocol.next(phase));
    }

    @Test void metricsArePhaseLocalAndRequireObservedMovementJumpAwayAndReturn() {
        PopulationCaptureProtocol metrics = new PopulationCaptureProtocol();
        for (int i=0; i<1200; i++) metrics.bodySample(10);
        assertFalse(metrics.covered("population_idle", 40, 40));
        metrics.acceptedStep(false, false, Vec3.ZERO, Vec3.ZERO);
        assertTrue(metrics.covered("population_idle", 40, 40));
        assertFalse(metrics.covered("population_move", 40, 40));
        metrics.reset();
        metrics.acceptedStep(false, false, Vec3.ZERO, new Vec3(10, 0, 0));
        assertFalse(metrics.covered("population_move", 40, 40));
        metrics.acceptedStep(false, false, new Vec3(10, 0, 0), new Vec3(17, 0, 0));
        for (int i=0; i<100; i++) metrics.bodySample(10);
        assertTrue(metrics.covered("population_move", 40, 40));

        metrics.reset();
        assertEquals(0, metrics.acceptedDistance());
        assertFalse(metrics.covered("population_jump", 40, 40));
        for (int i=0; i<3; i++) metrics.acceptedStep(true, true, Vec3.ZERO, Vec3.ZERO);
        for (int i=0; i<100; i++) metrics.bodySample(10);
        assertFalse(metrics.covered("population_jump", 40, 40));
        metrics.reset();
        for (int i=0; i<3; i++) metrics.acceptedStep(true, true, Vec3.ZERO, new Vec3(0, .1, 0));
        for (int i=0; i<100; i++) metrics.bodySample(10);
        assertTrue(metrics.covered("population_jump", 40, 40));
        assertEquals(3, metrics.groundedJumps());
        assertEquals(3, metrics.upwardSamples());

        metrics.reset();
        assertFalse(metrics.covered("population_away", 40, 40));
        for (int i=0; i<100; i++) metrics.bodySample(64);
        assertTrue(metrics.covered("population_away", 40, 40));
        metrics.reset();
        assertFalse(metrics.covered("population_return", 40, 40));
        for (int i=0; i<100; i++) metrics.bodySample(32);
        assertTrue(metrics.covered("population_return", 40, 40));
        assertFalse(metrics.covered("population_return", 39, 40));
    }

    @Test void observerRequiresExactPlayerBodyAndEpochAndIsOptInOnly() {
        UUID player = UUID.randomUUID(), body = UUID.randomUUID();
        assertTrue(PopulationCaptureProtocol.sameOwner(player, player, body, body, 8, 8));
        assertFalse(PopulationCaptureProtocol.sameOwner(player, UUID.randomUUID(), body, body, 8, 8));
        assertFalse(PopulationCaptureProtocol.sameOwner(player, player, body, UUID.randomUUID(), 8, 8));
        assertFalse(PopulationCaptureProtocol.sameOwner(player, player, body, body, 8, 9));
        assertTrue(PopulationCaptureProtocol.enabled(true, true, "JoinDiagnostic", true));
        assertFalse(PopulationCaptureProtocol.enabled(false, true, "JoinDiagnostic", true));
        assertFalse(PopulationCaptureProtocol.enabled(true, false, "JoinDiagnostic", true));
        assertFalse(PopulationCaptureProtocol.enabled(true, true, "", true));
        assertFalse(PopulationCaptureProtocol.enabled(true, true, "JoinDiagnostic", false));
    }

    @Test void sharedStationaryAndSustainedCompletionContractRequiresExpectedScenarioPopulation() {
        assertTrue(PopulationCaptureProtocol.validCompletion("parrots", 100, 100));
        assertTrue(PopulationCaptureProtocol.validCompletion("kobolds", 100, 100));
        assertTrue(PopulationCaptureProtocol.validCompletion("mixed", 40, 40));
        assertFalse(PopulationCaptureProtocol.validCompletion("mixed", 100, 40));
        assertFalse(PopulationCaptureProtocol.validCompletion("parrots", 40, 40));
        assertFalse(PopulationCaptureProtocol.validCompletion("unknown", 100, 100));
    }
}
