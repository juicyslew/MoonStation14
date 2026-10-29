package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.actions.client.BodyActionKeys;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftBodyActionKeysMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void moonstation14$handleBodyActionKeys(CallbackInfo callback) {
        BodyActionKeys.beforeVanillaKeybinds();
    }
}
