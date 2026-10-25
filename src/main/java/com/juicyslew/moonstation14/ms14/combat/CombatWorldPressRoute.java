package com.juicyslew.moonstation14.ms14.combat;

/** First-consumer policy for an ordinary right-button world press. */
public final class CombatWorldPressRoute {
    public enum Route { NONE, CONTEXT, SECONDARY }
    private CombatWorldPressRoute() { }

    public static Route route(boolean admitted, boolean uiClaimed, boolean projectedCombatMode,
                              boolean control, boolean alt, boolean shift) {
        if (!admitted || uiClaimed) return Route.NONE;
        if (control || alt || shift) return Route.CONTEXT;
        return projectedCombatMode ? Route.SECONDARY : Route.CONTEXT;
    }
}
