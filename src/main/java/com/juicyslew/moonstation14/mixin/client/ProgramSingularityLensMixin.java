package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.singularity.client.SingularityWorldShaderSource;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.shaders.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(Program.class)
public abstract class ProgramSingularityLensMixin {
    @WrapOperation(method = "compileShaderInternal(Lcom/mojang/blaze3d/shaders/Program$Type;Ljava/lang/String;Ljava/io/InputStream;Ljava/lang/String;Lcom/mojang/blaze3d/preprocessor/GlslPreprocessor;)I",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/util/List;)V"))
    private static void moonstation14$wrapVertexShader(int shader, List<String> source, Operation<Void> original,
                                                        @Local(argsOnly = true) Program.Type type) {
        original.call(shader, type == Program.Type.VERTEX
                ? SingularityWorldShaderSource.wrapVertexSource(source) : source);
    }
}
