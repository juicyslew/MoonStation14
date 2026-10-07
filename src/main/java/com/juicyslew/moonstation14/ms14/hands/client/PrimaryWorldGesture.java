package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;

/** Routing for the left-button world gesture; GUI clicks never enter this policy. */
public final class PrimaryWorldGesture {
    public enum Route { BLOCK_USE, HELD_USE, PICKUP, VANILLA }

    private PrimaryWorldGesture() { }

    public static Route route(boolean ownedWorldInput, boolean aimedBlock, boolean handOccupied, boolean aimedItem) {
        if (!ownedWorldInput) return Route.VANILLA;
        // An eligible item nominated by lookTarget has already passed the item
        // ray's reach and solid-block occlusion checks. Let it win over the
        // floor/background block hit when the hand is empty.
        if (!handOccupied && aimedItem) return Route.PICKUP;
        if (aimedBlock) return Route.BLOCK_USE;
        if (handOccupied) return Route.HELD_USE;
        return Route.VANILLA;
    }

    public static BodyHandActionRequest.Action action(Route route) {
        return switch (route) {
            case HELD_USE -> BodyHandActionRequest.Action.USE_HELD;
            case PICKUP -> BodyHandActionRequest.Action.PICKUP;
            default -> null;
        };
    }
}
