package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops vanilla mob goals/brain from moving or acting on an exactly-contained body. */
@Mixin(Mob.class)
public abstract class MobDisposalContainmentAiMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$skipAiWhileDisposalContained(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        if (!mob.level().isClientSide && mob.level() instanceof net.minecraft.server.level.ServerLevel level
                && BodyContainmentService.forLevel(level).ownerOf(mob).isPresent()) callback.cancel();
    }
}
