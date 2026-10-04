package com.juicyslew.moonstation14.ms14.disposal.containment;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.server.level.ServerPlayer;

/** Mutation-dispatch boundary; deliberately separate from hand identity/HUD resolution. */
public final class DisposalContainedActionPolicy {
    private DisposalContainedActionPolicy() { }

    public static boolean deniesExternalMutation(ServerPlayer actor) {
        if (actor == null || actor.isPassenger()) return false;
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null || !(actor.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        return deniesMembership(DisposalContainmentService.forLevel(level).ownerOf(authority.body()).isPresent());
    }

    static boolean deniesMembership(boolean exactLiveOwnerRelation) { return exactLiveOwnerRelation; }
}
