package com.juicyslew.moonstation14.ms14.medical;

import net.minecraft.world.entity.LivingEntity;

/** Builds an unregistered, detached visual copy for medical UI observation. */
public final class MedicalPortraitSnapshot {
    private MedicalPortraitSnapshot() { }

    public static LivingEntity create(LivingEntity source) {
        try {
            LivingEntity copy = com.juicyslew.moonstation14.ms14.character.CharacterVisualService.createDetached(source);
            if (copy == null) return null;
            copy.setHealth(copy.getMaxHealth());
            copy.deathTime = 0;
            copy.hurtTime = 0;
            copy.setPose(net.minecraft.world.entity.Pose.STANDING);
            copy.setDeltaMovement(0, 0, 0);
            copy.setOnGround(true);
            copy.setXRot(0);
            copy.setYRot(180);
            copy.setYBodyRot(180);
            copy.setYHeadRot(180);
            copy.xRotO = 0;
            copy.yRotO = copy.yBodyRotO = copy.yHeadRotO = 180;
            copy.tickCount = 0;
            return copy;
        } catch (RuntimeException unsupported) {
            return null;
        }
    }

    /** Exact visual-input comparison for the client cache; item identity/count are not appearance. */
    public static boolean sameAppearance(LivingEntity a, LivingEntity b) {
        return com.juicyslew.moonstation14.ms14.character.CharacterVisualService.same(a, b);
    }
}

