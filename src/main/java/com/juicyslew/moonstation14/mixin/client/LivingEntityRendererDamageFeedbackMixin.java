package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.damage.client.DamageFeedbackClientState;
import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackPresentationPolicy;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererDamageFeedbackMixin {
    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void moonstation14$redDamageFeedback(LivingEntity entity, float whiteOverlayProgress,
                                                        CallbackInfoReturnable<Integer> cir) {
        boolean medicallyManaged = entity.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()) != null;
        if (!DamageFeedbackPresentationPolicy.allowRedOverlay(DamageFeedbackClientState.profile(entity),
                entity.hurtTime, entity.deathTime, medicallyManaged,
                DamageFeedbackClientState.feedbackActive(entity)))
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(whiteOverlayProgress),
                    OverlayTexture.v(false)));
    }
}
