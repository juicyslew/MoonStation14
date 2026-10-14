package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.singularity.client.SingularityNativeDrawProbe;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public abstract class VertexBufferSingularityDiagnosticMixin {
    @Inject(method="draw()V", at=@At("HEAD"))
    private void moonstation14$captureNativeSolidDraw(CallbackInfo ci) {
        SingularityNativeDrawProbe.capture(RenderSystem.getShader());
    }
}
