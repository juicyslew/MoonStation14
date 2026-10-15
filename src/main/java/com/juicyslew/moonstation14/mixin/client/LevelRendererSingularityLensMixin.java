package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.singularity.client.SingularityWorldLensClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Final;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSingularityLensMixin {
    @Shadow @Final private SectionOcclusionGraph sectionOcclusionGraph;
    @Shadow private void applyFrustum(Frustum frustum) { }

    @WrapMethod(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V")
    private void moonstation14$worldLensFrame(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                               GameRenderer gameRenderer, LightTexture lightTexture,
                                               Matrix4f view, Matrix4f projection, Operation<Void> original) {
        try {
            SingularityWorldLensClient.begin(deltaTracker, camera, view, projection);
            if (SingularityWorldLensClient.consumeOcclusionInvalidation()) sectionOcclusionGraph.invalidate();
            original.call(deltaTracker, renderBlockOutline, camera, gameRenderer, lightTexture, view, projection);
        } finally {
            SingularityWorldLensClient.end();
        }
    }

    @ModifyArg(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)V"), index = 0)
    private boolean moonstation14$disableDirectionalSmartCullOnlyForLens(boolean smartCull) {
        return SingularityWorldLensClient.isActive() ? false : smartCull;
    }

    @Inject(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V", at = @At("RETURN"))
    private void moonstation14$refreshLensFrustum(Camera camera, Frustum frustum, boolean hasCapturedFrustum,
                                                   boolean spectator, CallbackInfo ci) {
        if (SingularityWorldLensClient.isActive()) applyFrustum(LevelRenderer.offsetFrustum(frustum));
    }
}
