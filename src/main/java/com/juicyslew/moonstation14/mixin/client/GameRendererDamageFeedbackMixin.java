package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.damage.client.DamageFeedbackClientState;
import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackPresentationPolicy;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererDamageFeedbackMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void moonstation14$cameraDamageFeedback(com.mojang.blaze3d.vertex.PoseStack pose, float partialTick,
                                                    CallbackInfo ci) {
        if (Minecraft.getInstance().getCameraEntity() instanceof LivingEntity living
                && !DamageFeedbackPresentationPolicy.allowHurtCamera(DamageFeedbackClientState.profile(living),
                        living.hurtTime, living.deathTime,
                        living.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()) != null,
                        DamageFeedbackClientState.feedbackActive(living))) ci.cancel();
    }
}

