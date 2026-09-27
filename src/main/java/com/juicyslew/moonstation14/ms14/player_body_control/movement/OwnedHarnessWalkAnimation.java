package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Updates vanilla walk animation from displacement accepted by an owned harness movement step. */
public final class OwnedHarnessWalkAnimation {
    private OwnedHarnessWalkAnimation() { }

    public static void update(LivingEntity body, Vec3 acceptedDisplacement) {
        float horizontalLength = (float) acceptedDisplacement.horizontalDistance();
        float speed = Math.min(horizontalLength * 4f, 1f);
        body.walkAnimation.update(speed, .4f);
    }
}
