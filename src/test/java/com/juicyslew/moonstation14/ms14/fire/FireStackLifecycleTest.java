package com.juicyslew.moonstation14.ms14.fire;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FireStackLifecycleTest {
    private static final FireStackComponent LIT = new FireStackComponent(2f, true);

    private static GasMixture oxygen(double moles) {
        return new GasMixture(Map.of(GasType.OXYGEN, moles), 293.15);
    }

    @Test
    void strictOxygenControlsExtinguishAndFadeAtOneMoleBoundary() {
        for (AtmosphereReading.Status status : new AtmosphereReading.Status[]{
                AtmosphereReading.Status.FINITE, AtmosphereReading.Status.EXTERIOR}) {
            assertTrue(FireStackSystem.lifecycleTransition(LIT, oxygen(0), status, -0.25f).extinguished());
            var below = FireStackSystem.lifecycleTransition(LIT, oxygen(0.999), status, -0.25f);
            assertEquals(FireStackComponent.EMPTY, below.next());
            assertEquals(0f, below.heatJoules());
            var at = FireStackSystem.lifecycleTransition(LIT, oxygen(1), status, -0.25f);
            assertEquals(new FireStackComponent(1.75f, true), at.next());
            assertEquals(25000f, at.heatJoules());
        }
    }

    @Test
    void provisionalUnknownAndDisabledStatesDeferWithoutMutationOrHeat() {
        for (AtmosphereReading.Status status : AtmosphereReading.Status.values()) {
            var transition = FireStackSystem.lifecycleTransition(LIT, oxygen(20), status, -1f);
            if (status == AtmosphereReading.Status.PROVISIONAL) {
                assertTrue(transition.deferred());
                assertEquals(LIT, transition.next());
                assertEquals(0f, transition.heatJoules());
            }
        }
        var unknown = FireStackSystem.lifecycleTransition(LIT, null,
                AtmosphereReading.Status.FINITE, -1f);
        assertTrue(unknown.deferred());
        assertEquals(LIT, unknown.next());
        var disabled = FireStackSystem.lifecycleTransition(new FireStackComponent(2f, false),
                oxygen(20), AtmosphereReading.Status.FINITE, -1f);
        assertEquals(new FireStackComponent(2f, false), disabled.next());
        assertEquals(0f, disabled.heatJoules());
    }

    @Test
    void fadeClampsToZeroAndUsesConfiguredFadeOncePerInvocation() {
        var custom = FireStackSystem.lifecycleTransition(LIT, oxygen(1),
                AtmosphereReading.Status.FINITE, -0.5f);
        assertEquals(new FireStackComponent(1.5f, true), custom.next());
        var extinguished = FireStackSystem.lifecycleTransition(
                new FireStackComponent(0.5f, true), oxygen(1),
                AtmosphereReading.Status.FINITE, -1f);
        assertEquals(FireStackComponent.EMPTY, extinguished.next());
        assertTrue(extinguished.extinguished());
        assertEquals(6250f, extinguished.heatJoules());
    }

    @Test
    void inactiveWetStateRemainsUnchanged() {
        var wet = new FireStackComponent(-2f, false);
        var transition = FireStackSystem.lifecycleTransition(wet, oxygen(20),
                AtmosphereReading.Status.FINITE, -1f);
        assertEquals(wet, transition.next());
        assertEquals(0f, transition.heatJoules());
        assertTrue(transition.directDamage().isEmpty());
    }

    @Test
    void directDamageUsesPreFadeStacksAndIsIndependentOfBodyHeat() {
        var transition = FireStackSystem.lifecycleTransition(LIT, oxygen(1),
                AtmosphereReading.Status.FINITE, -2f, Map.of("heat", 1.5f));
        assertEquals(Map.of("heat", 3f), transition.directDamage());
        assertEquals(25000f, transition.heatJoules());
        assertEquals(FireStackComponent.EMPTY, transition.next());
        assertThrows(UnsupportedOperationException.class,
                () -> transition.directDamage().put("heat", 0f));
    }

    @Test
    void directDamageScalesFractionalStacksAndSupportsMultipleTypes() {
        var transition = FireStackSystem.lifecycleTransition(new FireStackComponent(0.5f, true),
                oxygen(1), AtmosphereReading.Status.EXTERIOR, 0f,
                Map.of("heat", 1.5f, "piercing", 2f));
        assertEquals(Map.of("heat", 0.75f, "piercing", 1f), transition.directDamage());
        assertEquals(Map.of(), FireStackSystem.lifecycleTransition(LIT, oxygen(1),
                AtmosphereReading.Status.FINITE, 0f).directDamage());
    }

    @Test
    void oxygenCutoffAndDeferredStatesProduceNoDirectDamage() {
        assertTrue(FireStackSystem.lifecycleTransition(LIT, oxygen(0.999),
                AtmosphereReading.Status.FINITE, -0.1f, Map.of("heat", 1.5f)).directDamage().isEmpty());
        assertTrue(FireStackSystem.lifecycleTransition(LIT, oxygen(2),
                AtmosphereReading.Status.PROVISIONAL, -0.1f, Map.of("heat", 1.5f)).directDamage().isEmpty());
    }

    @Test
    void overflowingDirectDamageFailsClosed() {
        assertTrue(FireStackSystem.lifecycleTransition(LIT,
                oxygen(1), AtmosphereReading.Status.FINITE, 0f, Map.of("heat", Float.MAX_VALUE))
                .directDamage().isEmpty());
    }

    @Test
    void oneFireStepAppliesHeatBeforeSingleTypedDamageUsingPreFadeStacks() {
        var transition = FireStackSystem.lifecycleTransition(LIT, oxygen(1),
                AtmosphereReading.Status.FINITE, -0.1f, Map.of("heat", 1.5f));
        var calls = new java.util.ArrayList<String>();
        FireStackSystem.applyFireStep(transition, heat -> {
            calls.add("heat:" + heat);
        }, damage -> calls.add("damage:" + damage.get("heat")));
        assertEquals(java.util.List.of("heat:25000.0", "damage:3.0"), calls);
        assertEquals(new FireStackComponent(1.9f, true), transition.next());
    }

    @Test
    void deferredAndEmptyDamageStepsDoNotInvokeTypedSink() {
        var calls = new java.util.ArrayList<String>();
        var deferred = FireStackSystem.lifecycleTransition(LIT, null,
                AtmosphereReading.Status.FINITE, -0.1f, Map.of("heat", 1.5f));
        FireStackSystem.applyFireStep(deferred, heat -> calls.add("heat"), damage -> calls.add("damage"));
        var noDamage = FireStackSystem.lifecycleTransition(LIT, oxygen(1),
                AtmosphereReading.Status.FINITE, -0.1f);
        FireStackSystem.applyFireStep(noDamage, heat -> calls.add("heat"), damage -> calls.add("damage"));
        var wet = FireStackSystem.lifecycleTransition(new FireStackComponent(-1f, false), oxygen(20),
                AtmosphereReading.Status.FINITE, -0.1f, Map.of("heat", 1.5f));
        FireStackSystem.applyFireStep(wet, heat -> calls.add("heat"), damage -> calls.add("damage"));
        var oxygenCutoff = FireStackSystem.lifecycleTransition(LIT, oxygen(0.999),
                AtmosphereReading.Status.FINITE, -0.1f, Map.of("heat", 1.5f));
        FireStackSystem.applyFireStep(oxygenCutoff, heat -> calls.add("heat"), damage -> calls.add("damage"));
        assertEquals(java.util.List.of("heat"), calls);
        assertTrue(FireStackSystem.isNewFireWindow(null, 4)); // deferred samples do not stamp
        assertFalse(FireStackSystem.isNewFireWindow(4L, 4));
        assertTrue(FireStackSystem.isNewFireWindow(4L, 5)); // next interval remains eligible
    }
}
