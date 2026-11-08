package com.juicyslew.moonstation14.ms14.animals.flight;

/** Pure admission shared by authoritative and predicted flight transitions. */
public final class FlightTransitionPolicy {
    private FlightTransitionPolicy() { }

    public static boolean autonomousIntentValid(FlightState intent) {
        return intent == FlightState.FLYING || intent == FlightState.LANDING;
    }

    static boolean takeoff(boolean clientWorld, boolean prediction, boolean capable, boolean alive,
                           boolean mobile, boolean unmounted, boolean dry, boolean noExternalFlight,
                           boolean notContained) {
        return clientWorld == prediction && capable && alive && mobile && unmounted && dry
                && noExternalFlight && notContained;
    }

    static boolean landing(boolean clientWorld, boolean prediction, boolean capable, boolean alive,
                           boolean mobile, boolean flying, boolean notContained) {
        return clientWorld == prediction && capable && alive && mobile && flying && notContained;
    }

    /** Current held controls own vertical intent; LANDING is not a latched command. */
    public static FlightState heldVerticalState(boolean flying, boolean jump, boolean sneak) {
        if (!flying) return FlightState.GROUNDED;
        if (jump) return FlightState.FLYING;
        return sneak ? FlightState.LANDING : FlightState.FLYING;
    }

    public static double verticalWish(FlightState state, boolean jump, boolean sneak, boolean grounded,
                               double speed) {
        if (state == FlightState.GROUNDED) return 0d;
        return sneak && !jump ? -speed : jump || grounded ? speed : 0d;
    }

    public static FlightState explicitVerticalState(boolean flying, double verticalWish) {
        if (!flying) return FlightState.GROUNDED;
        return verticalWish < 0d ? FlightState.LANDING : FlightState.FLYING;
    }

    public static double naturalFlightVerticalWish(FlightState state, boolean grounded) {
        return state == FlightState.LANDING ? -1d : grounded ? 1d : 0d;
    }
}
