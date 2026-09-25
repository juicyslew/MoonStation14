package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerStunBrainMixin {
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void moonstation14$pauseBrainWhileStunned(CallbackInfo callback) {
        if (CharacterControlSystem.isStunned((Villager) (Object) this)) {
            callback.cancel();
        }
    }
}
