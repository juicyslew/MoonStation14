package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Renderer visibility bounds follow the horizontal prone model without changing collision. */
@Mixin(Entity.class)
public abstract class ProneRenderBoundsMixin {
    @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
    private void moonstation14$expandProneBounds(CallbackInfoReturnable<AABB> callback) {
        if ((Object) this instanceof LivingEntity living && PostureSystem.isDownPosture(living))
        {
            var dimensions = living.getDimensions(net.minecraft.world.entity.Pose.STANDING);
            var posture = living.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
            callback.setReturnValue(com.juicyslew.moonstation14.ms14.standing.ProneRenderBounds.calculate(
                    callback.getReturnValue(), living.position(), dimensions.width(), dimensions.height(),
                    com.juicyslew.moonstation14.ms14.standing.PronePoseGeometry.modelScale(living),
                    living.getScale(), living.getYRot(), posture != null && posture.data().faceUp()));
        }
    }
}
