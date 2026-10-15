package com.juicyslew.moonstation14.ms14.mobs;

import net.minecraft.world.entity.Mob;

/** Narrow package bridge for assertions against the existing NPC flee phase in lifecycle fixtures. */
public final class NpcBehaviorGameTestAccess {
    private NpcBehaviorGameTestAccess() { }

    public static boolean hasAutonomousFlee(Mob mob) {
        return NpcBehaviorSystem.debugFleeUntil(mob) > 0;
    }
}
