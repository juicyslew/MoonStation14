package com.juicyslew.moonstation14.ms14.animals.flight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlightTransitionPolicyTest {
    @Test
    void takeoffRequiresCapabilityLivingMobileUnmountedDryUncontainedAndNoExternalLease() {
        assertTrue(FlightTransitionPolicy.takeoff(false, false, true, true, true, true, true, true, true));
        assertFalse(FlightTransitionPolicy.takeoff(false, false, false, true, true, true, true, true, true));
        assertFalse(FlightTransitionPolicy.takeoff(false, false, true, false, true, true, true, true, true));
        assertFalse(FlightTransitionPolicy.takeoff(false, false, true, true, false, true, true, true, true));
        assertFalse(FlightTransitionPolicy.takeoff(false, false, true, true, true, false, true, true, true));
        assertFalse(FlightTransitionPolicy.takeoff(false, false, true, true, true, true, false, true, true));
        assertFalse(FlightTransitionPolicy.takeoff(false, false, true, true, true, true, true, false, true));
        assertFalse(FlightTransitionPolicy.takeoff(false, false, true, true, true, true, true, true, false));
    }

    @Test
    void landingRequiresAnExistingFlightAndLivingMobileUncontainedBody() {
        assertTrue(FlightTransitionPolicy.landing(false, false, true, true, true, true, true));
        assertFalse(FlightTransitionPolicy.landing(false, false, false, true, true, true, true));
        assertFalse(FlightTransitionPolicy.landing(false, false, true, false, true, true, true));
        assertFalse(FlightTransitionPolicy.landing(false, false, true, true, false, true, true));
        assertFalse(FlightTransitionPolicy.landing(false, false, true, true, true, false, true));
        assertFalse(FlightTransitionPolicy.landing(false, false, true, true, true, true, false));
    }

    @Test
    void authoritativeAndPredictionTransitionsAreWorldSideSpecific() {
        assertTrue(FlightTransitionPolicy.takeoff(true, true, true, true, true, true, true, true, true));
        assertFalse(FlightTransitionPolicy.takeoff(true, false, true, true, true, true, true, true, true));
        assertFalse(FlightTransitionPolicy.takeoff(false, true, true, true, true, true, true, true, true));
        assertTrue(FlightTransitionPolicy.landing(true, true, true, true, true, true, true));
        assertFalse(FlightTransitionPolicy.landing(true, false, true, true, true, true, true));
        assertFalse(FlightTransitionPolicy.landing(false, true, true, true, true, true, true));
    }

    @Test
    void syncedAttachmentValueReplacesLocalPredictedState() {
        FlightAttachment local = new FlightAttachment(FlightState.GROUNDED);
        local.set(FlightState.FLYING); // same attachment mutation used by local prediction
        assertEquals(FlightState.FLYING, local.state());
        local.set(new FlightAttachment(FlightState.GROUNDED).state()); // authoritative synced value applied
        assertEquals(FlightState.GROUNDED, local.state());
        assertEquals(FlightState.LANDING,
                FlightAttachment.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                                com.mojang.serialization.JsonOps.INSTANCE.createString("LANDING"))
                        .result().orElseThrow().state());
    }

    @Test
    void heldVerticalInputCanCancelLandingAndNeutralInputHovers() {
        assertEquals(FlightState.FLYING, FlightTransitionPolicy.heldVerticalState(true, true, false));
        assertEquals(FlightState.FLYING, FlightTransitionPolicy.heldVerticalState(true, false, false));
        assertEquals(FlightState.LANDING, FlightTransitionPolicy.heldVerticalState(true, false, true));
        assertEquals(1d, FlightTransitionPolicy.verticalWish(FlightState.FLYING, true, false, false, 1d));
        assertEquals(0d, FlightTransitionPolicy.verticalWish(FlightState.FLYING, false, false, false, 1d));
        assertEquals(-1d, FlightTransitionPolicy.verticalWish(FlightState.LANDING, false, true, false, 1d));
        assertEquals(1d, FlightTransitionPolicy.verticalWish(FlightState.FLYING, true, true, false, 1d));
    }

    @Test
    void losingEnvironmentalFlightEligibilityRejectsBothTakeoffAndLanding() {
        // These are the same shared admission arguments consumed by active-flight validation.
        assertFalse(FlightTransitionPolicy.takeoff(false, false, true, true, true,
                false, true, true, true)); // mounted
        assertFalse(FlightTransitionPolicy.takeoff(false, false, true, true, true,
                true, false, true, true)); // wet
        assertFalse(FlightTransitionPolicy.takeoff(false, false, true, true, true,
                true, true, false, true)); // external flight lease
        assertFalse(FlightTransitionPolicy.landing(false, false, true, true, true, false, true));
    }

    @Test
    void explicitVerticalIntentPreservesApiLandingWithoutChangingHeldInputSemantics() {
        assertEquals(FlightState.LANDING, FlightTransitionPolicy.explicitVerticalState(true, -1d));
        assertEquals(FlightState.FLYING, FlightTransitionPolicy.explicitVerticalState(true, 0d));
        assertEquals(FlightState.FLYING, FlightTransitionPolicy.explicitVerticalState(true, 1d));
        assertEquals(FlightState.GROUNDED, FlightTransitionPolicy.explicitVerticalState(false, -1d));
        assertEquals(1d, FlightTransitionPolicy.naturalFlightVerticalWish(FlightState.FLYING, true));
        assertEquals(0d, FlightTransitionPolicy.naturalFlightVerticalWish(FlightState.FLYING, false));
        assertEquals(-1d, FlightTransitionPolicy.naturalFlightVerticalWish(FlightState.LANDING, false));
    }

    @Test
    void autonomousIntentIsIndependentOfVerticalWish() {
        assertTrue(FlightTransitionPolicy.autonomousIntentValid(FlightState.FLYING));
        assertTrue(FlightTransitionPolicy.autonomousIntentValid(FlightState.LANDING));
        assertFalse(FlightTransitionPolicy.autonomousIntentValid(FlightState.GROUNDED));
    }
}
