package com.juicyslew.moonstation14.ms14.stripping;

import com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess;
import com.juicyslew.moonstation14.ms14.interaction.InteractionReach;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Shared physical-root reach for strip nomination, action admission, and view lifetime. */
public final class StripInteractionRange {
    public static final double BLOCKS = InteractionReach.DEFAULT_BLOCKS;
    private StripInteractionRange() { }

    /** Pure root-distance gate used by both the local panel lifetime and server view lifetime. */
    public static boolean contains(Entity actor, Entity target) {
        return BodyInteractionAccess.rootWithinReach(actor, target, BLOCKS);
    }
    public static boolean contains(Vec3 actorRoot, Vec3 targetRoot) {
        return BodyInteractionAccess.rootWithinReach(actorRoot, targetRoot, BLOCKS);
    }
}
