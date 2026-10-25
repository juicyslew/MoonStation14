package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.doors.AirlockBumpSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityAirlockBumpMixin {
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), require = 1)
    private void moonstation14$checkAirlockBump(MoverType moverType, Vec3 requested, CallbackInfo callbackInfo) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel) {
            AirlockBumpSystem.beforeMove(entity, requested);
        }
    }
}

