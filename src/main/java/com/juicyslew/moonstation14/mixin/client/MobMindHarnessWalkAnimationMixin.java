package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animates remotely tracked possessed mobs from their client-side tick displacement. */
@Mixin(Mob.class)
public abstract class MobMindHarnessWalkAnimationMixin {
    @Unique
    private Vec3 moonstation14$positionAtTickStart;

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void moonstation14$snapshotTickPosition(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        moonstation14$positionAtTickStart = mob.level().isClientSide ? mob.position() : null;
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void moonstation14$animateRemoteMovement(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        Vec3 start = moonstation14$positionAtTickStart;
        moonstation14$positionAtTickStart = null;
        if (start == null || !mob.level().isClientSide
                || !((MindControlledMob) mob).moonstation14$isMovementOwned()
                || GhostControlClient.ownedHarnessForTracker(mob) != null) return;

        OwnedHarnessWalkAnimation.update(mob, mob.position().subtract(start));
    }
}
