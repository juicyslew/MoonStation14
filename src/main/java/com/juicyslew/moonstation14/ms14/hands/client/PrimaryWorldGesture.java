package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;

import java.util.UUID;

/** Routing for the left-button world gesture; GUI clicks never enter this policy. */
public final class PrimaryWorldGesture {
    public enum Route { MELEE, TARGET_USE, BLOCK_USE, HELD_USE, PICKUP, MEDICAL_USE, CONTROLLED_NONE, VANILLA }
    public record ClickDecision(Route route, BodyHandActionRequest.Action action, UUID target) { }

    private PrimaryWorldGesture() { }

    public static Route route(boolean ownedWorldInput, boolean aimedBlock, boolean handOccupied, boolean aimedItem) {
        return route(ownedWorldInput, aimedBlock, handOccupied, aimedItem, false);
    }

    public static Route route(boolean ownedWorldInput, boolean aimedBlock, boolean handOccupied, boolean aimedItem,
                              boolean aimedMedicalBody) {
        return route(ownedWorldInput, false, aimedBlock, handOccupied, aimedItem, aimedMedicalBody);
    }

    public static Route route(boolean ownedWorldInput, boolean combatMode, boolean aimedBlock, boolean handOccupied,
                              boolean aimedItem, boolean aimedMedicalBody) {
        if (!ownedWorldInput) return Route.VANILLA;
        if (combatMode) return Route.MELEE;
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

    /** Ordinary piles keep precedence; portable living nominations are promoted only for an empty hand. */
    public static UUID pickupTarget(UUID looseItem, UUID portableLiving) {
        return looseItem != null ? looseItem : portableLiving;
    }

    /** Outline nomination follows pickup priority and only adds portable living candidates for an empty available hand. */
    public static UUID outlineTarget(boolean handAvailable, boolean handOccupied, UUID looseItem, UUID portableLiving) {
        if (looseItem != null) return looseItem;
        return handAvailable && !handOccupied ? portableLiving : null;
    }

    /** Shared left-click decision for both normal mouse input and gameplay-window input. */
    public static ClickDecision click(boolean aimedBlock, boolean handOccupied, UUID looseItem,
                                      UUID portableLiving, boolean medicalTarget, UUID medicalBody) {
        UUID target = medicalTarget ? medicalBody
                : handOccupied ? looseItem : pickupTarget(looseItem, portableLiving);
        Route route = route(true, aimedBlock, handOccupied, target != null, medicalTarget);
        BodyHandActionRequest.Action action = action(route);
        if (route == Route.PICKUP && target != null) action = BodyHandActionRequest.Action.PICKUP_SELECTED;
        return new ClickDecision(route, action, target);
    }

    /** Shared route/recipient policy for both dispatch and client-only presentation. */
    public static ClickDecision resolve(boolean aimedBlock, boolean handOccupied, UUID looseItem,
                                        UUID portableLiving, boolean medicalTarget, UUID medicalBody) {
        return click(aimedBlock, handOccupied, looseItem, portableLiving, medicalTarget, medicalBody);
    }
}
