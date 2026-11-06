package com.juicyslew.moonstation14.ms14.doors;

import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Short-lived per-Entity.move evidence consumed by the matching generic travel return. */
public interface AirlockBumpMoveAccess {
    record Move(AABB beforeBounds, Vec3 requested, SubLevelEntityCollision.CollisionInfo previousInfo) { }
    void moonstation14$beginAirlockTravel();
    boolean moonstation14$isAirlockTravelActive();
    void moonstation14$setAirlockMove(Move move);
    Move moonstation14$getAirlockMove();
    void moonstation14$clearAirlockMove();
}
