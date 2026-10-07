package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirects observer look away from the authoritative local player body. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMindGhostLookMixin {
    @Redirect(method = "turnPlayer(D)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), require = 1)
    private void moonstation14$turnObserverCamera(net.minecraft.client.player.LocalPlayer player, double yaw, double pitch) {
        var observation = ObserverClient.current();
        if (ObserverClient.usesMedicalCamera() || observation != null
                && observation.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST)
            ObserverClient.turnCamera(yaw, pitch);
        else player.turn(yaw, pitch);
    }

    @Inject(method = "turnPlayer(D)V", at = @At("TAIL"), require = 1)
    private void moonstation14$projectOwnedGhostLook(double delta, CallbackInfo ci) {
        com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.projectOwnedGhostLookForRenderFrame();
        ObserverClient.projectCameraLookForRenderFrame();
    }
}
