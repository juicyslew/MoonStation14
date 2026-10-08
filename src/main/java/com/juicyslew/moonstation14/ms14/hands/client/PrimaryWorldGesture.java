package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;

/** Routing for the left-button world gesture; GUI clicks never enter this policy. */
public final class PrimaryWorldGesture {
    public enum Route { TARGET_USE, BLOCK_USE, HELD_USE, PICKUP, MEDICAL_USE, CONTROLLED_NONE, VANILLA }

    private PrimaryWorldGesture() { }

    public static Route route(boolean ownedWorldInput, boolean aimedBlock, boolean handOccupied, boolean aimedItem) {
        return route(ownedWorldInput, aimedBlock, handOccupied, aimedItem, false);
    }

    public static Route route(boolean ownedWorldInput, boolean aimedBlock, boolean handOccupied, boolean aimedItem,
                              boolean aimedMedicalBody) {
        if (!ownedWorldInput) return Route.VANILLA;
        if (aimedMedicalBody && handOccupied) return Route.MEDICAL_USE;
        // The item target has already passed reach and occlusion admission. It is
        // the primary target even when the ray also reports a background block.
        if (aimedItem) return handOccupied ? Route.TARGET_USE : Route.PICKUP;
        if (aimedBlock) return Route.BLOCK_USE;
        if (handOccupied) return Route.HELD_USE;
        return Route.CONTROLLED_NONE;
    }

    public static BodyHandActionRequest.Action action(Route route) {
        return switch (route) {
            case MEDICAL_USE -> BodyHandActionRequest.Action.USE_BODY;
            case HELD_USE -> BodyHandActionRequest.Action.USE_HELD;
            case TARGET_USE -> BodyHandActionRequest.Action.USE_TARGET;
            case PICKUP -> BodyHandActionRequest.Action.PICKUP;
            default -> null;
        };
    }
}
