package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Projects owner look after vanilla applies this render frame's mouse movement. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMindGhostLookMixin {
    @Inject(method = "turnPlayer(D)V", at = @At("TAIL"), require = 1)
    private void moonstation14$projectOwnedGhostLook(double timeDelta, CallbackInfo callback) {
        GhostControlClient.projectOwnedGhostLookForRenderFrame();
    }
}
