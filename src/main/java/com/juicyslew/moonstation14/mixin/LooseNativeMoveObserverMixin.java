package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes loose-item movement before Sable's nonliving carry TAIL, without changing physics.
 * Runtime ordering is counterintuitive: priority 1000 precedes Sable's 1100 TAIL injection;
 * a 1200 trial ran after it and included carrier displacement.
 */
@Mixin(value = Entity.class, priority = 1000)
public abstract class LooseNativeMoveObserverMixin {
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), require = 1)
    private void moonstation14$beginLooseMove(MoverType moverType, Vec3 requested, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel && entity instanceof LooseItemEntity loose)
            loose.beginNativeMoveCapture(requested);
    }

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("TAIL"), require = 1)
    private void moonstation14$endLooseMove(MoverType moverType, Vec3 requested, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel && entity instanceof LooseItemEntity loose)
            loose.endNativeMoveCapture();
    }

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("RETURN"), require = 1)
    private void moonstation14$captureEarlyReturn(MoverType moverType, Vec3 requested, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel && entity instanceof LooseItemEntity loose) {
            loose.endNativeMoveCapture();
            loose.finishNativeMoveCapture();
        }
    }
}
