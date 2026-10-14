package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.singularity.client.SingularityWorldLensUniforms;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceSingularityLensMixin {
    @Inject(method = "apply()V", at = @At("TAIL"))
    private void moonstation14$applyLensUniforms(CallbackInfo ci) {
        SingularityWorldLensUniforms.apply((ShaderInstance) (Object) this);
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void moonstation14$forgetLensUniforms(CallbackInfo ci) {
        SingularityWorldLensUniforms.forget((ShaderInstance) (Object) this);
    }
}
