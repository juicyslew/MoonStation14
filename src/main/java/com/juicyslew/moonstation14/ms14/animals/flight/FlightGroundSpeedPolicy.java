package com.juicyslew.moonstation14.ms14.animals.flight;

import com.juicyslew.moonstation14.ms14.character.components.FlightComponent;

/** Ground locomotion adaptation for natural flyers; external launches retain their authored momentum. */
public final class FlightGroundSpeedPolicy {
    private FlightGroundSpeedPolicy() { }

    public static double voluntaryFactor(FlightComponent capability, FlightState state, boolean externalFlight) {
        if (capability == null || externalFlight) return 1d;
        if (state == FlightState.GROUNDED) return capability.groundedSpeedMultiplier();
        if (state == FlightState.FLYING || state == FlightState.LANDING) return capability.airborneSpeedMultiplier();
        return 1d;
    }

    /** Cadence baseline follows the same grounded-flyer voluntary speed adaptation as movement. */
    public static double cadenceWalkSpeed(double walkSpeed, FlightComponent capability, FlightState state,
                                         boolean physicallyGrounded, boolean externalFlight) {
        return physicallyGrounded && state == FlightState.GROUNDED && capability != null && !externalFlight
                ? walkSpeed * capability.groundedSpeedMultiplier() : walkSpeed;
    }
}
