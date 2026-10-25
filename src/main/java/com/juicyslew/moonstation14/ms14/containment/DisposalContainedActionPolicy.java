package com.juicyslew.moonstation14.ms14.containment;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import net.minecraft.server.level.ServerPlayer;

/** Fail-closed boundary between body-owned HUD operations and world operations while contained. */
public final class DisposalContainedActionPolicy {
    private DisposalContainedActionPolicy() { }

    public static boolean isContained(ServerPlayer actor) {
        if (actor == null || !(actor.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        return authority != null && BodyContainmentService.forLevel(level).ownerOf(authority.body()).isPresent();
    }

    /** Compatibility name used by existing external-world mutation dispatchers. */
    public static boolean deniesExternalMutation(ServerPlayer actor) { return isContained(actor); }

    /** Generic permission proof for owner-locked world block use; target validation is repeated by the probe. */
    public static boolean permitsOwnerBlockTarget(ServerPlayer actor,
            com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest request) {
        if (actor == null || request == null || !(actor.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) return false;
        var owner = BodyContainmentService.forLevel(level).ownerOf(authority.body()).orElse(null);
        if (owner == null || !owner.isValid()) return false;
        var view = owner.view();
        return view != null && owner.identity().equals(view.ownerIdentity())
                && view.worldTarget().filter(request.target()::equals).isPresent()
                && (request.intent() == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE
                    || request.intent() == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.ACTIVATE_WORLD
                    || request.intent() == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.CLIMB_INSIDE);
    }

    /** Only operations whose complete source and destination are the body may pass this gate. */
    public static boolean permitsBodyHandAction(BodyHandActionRequest.Action action) {
        if (action == null) return false;
        return switch (action) {
            case SELECT_HAND, TRANSFER_HAND, EQUIP_BELT, EQUIP_BACK, UNEQUIP_BELT, UNEQUIP_BACK, EQUIP_SLOT, UNEQUIP_SLOT, MOVE_EQUIPMENT, QUICK_EQUIP,
                    STORE_BELT, STORE_BACK, TAKE_BELT, TAKE_BACK,
                    GRID_STORE_BELT, GRID_STORE_BACK, GRID_TAKE_BELT, GRID_TAKE_BACK,
                    GRID_MOVE_BELT, GRID_MOVE_BACK, HELD_GRID_STORE, HELD_GRID_TAKE, HELD_GRID_MOVE,
                    SMART_STORE_BELT, SMART_STORE_BACK, SMART_TAKE_BELT, SMART_TAKE_BACK,
                    GRID_CROSS_BELT_TO_BACK, GRID_CROSS_BACK_TO_BELT,
                    GRID_BODY_WORN_TO_HELD, GRID_BODY_HELD_TO_WORN, GRID_BODY_HELD_TO_HELD, GRID_BODY_TO_EQUIPMENT, GRID_EQUIPMENT_TO_BODY,
                    USE_HELD, USE_IN_HAND, PRIME_GRENADE, STOP_PULL, DROP, CYCLE_SYRINGE_AMOUNT -> true;
            // World targets, external throws/pickups, pouch legacy actions and unknown
            // future actions fail closed while the body's world position is owner-locked.
            case PICKUP, PICKUP_SELECTED, THROW_GRENADE, THROW_ITEM, USE_TARGET, BUTCHER, GUN_EMPTY, GUN_SPIN, ACTIVATE_TARGET,
                    ALTERNATE_TARGET, TOGGLE_PULL, SIP_PUDDLE, INSERT_POUCH, EXTRACT_POUCH, USE_BODY -> false;
        };
    }
}

