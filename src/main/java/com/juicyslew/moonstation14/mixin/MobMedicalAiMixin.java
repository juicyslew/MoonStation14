package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops AI intent/navigation for incapacitated mobs, leaving their entity tick and physics intact. */
@Mixin(Mob.class)
public abstract class MobMedicalAiMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$stopIncapacitatedAi(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        if (!MobStateSystem.isIncapacitated(mob)) return;
        mob.getNavigation().stop();
        callback.cancel();
    }
}

