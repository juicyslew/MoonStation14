package com.juicyslew.moonstation14.mixin.compat;

import com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures the real vanilla world-collision clip paired with the current exact tracked-body native solve. */
@Mixin(Entity.class)
public abstract class VanillaWorldCollisionObservationMixin {
    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), require = 1)
    private void moonstation14$captureWorldCollisionInput(Vec3 movement,
            CallbackInfoReturnable<Vec3> callback) {
        Entity entity=(Entity)(Object)this;
        if(!CurrentFrameCollisionScope.observesVanillaWorldCollision(entity))return;
        CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(entity,movement,
                entity.position(),entity.getBoundingBox());
    }

    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), require = 1)
    private void moonstation14$captureWorldCollisionOutput(Vec3 movement,
            CallbackInfoReturnable<Vec3> callback) {
        CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput((Entity)(Object)this,
                callback.getReturnValue());
    }
}
