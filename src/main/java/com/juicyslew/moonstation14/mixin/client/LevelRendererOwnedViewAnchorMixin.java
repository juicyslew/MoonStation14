package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.ControlledChunkDiagnostics;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import com.juicyslew.moonstation14.ms14.player_body_control.client.RendererViewAnchorPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anchors the renderer section grid to the active committed owned-body camera, not its carrier. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOwnedViewAnchorMixin {
    @Shadow private int lastCameraSectionX;
    @Shadow private int lastCameraSectionY;
    @Shadow private int lastCameraSectionZ;

    private static boolean moonstation14$hasCommittedOwnedView() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return false;
        Entity camera = minecraft.getCameraEntity();
        Entity body = GhostControlClient.ownedCharacterForPresentation();
        if (body == null) {
            Entity ghost = GhostControlClient.ownedGhostForPresentation();
            if (GhostControlClient.isOwnedMedicalObserverGhost(ghost)) body = ghost;
        }
        return body != null && body == camera;
    }

    @Inject(method = "allChanged()V", at = @At("RETURN"))
    private void moonstation14$invalidateOwnedViewSectionAnchor(CallbackInfo ci) {
        if (!moonstation14$hasCommittedOwnedView()) return;
        lastCameraSectionX = Integer.MIN_VALUE;
        lastCameraSectionY = Integer.MIN_VALUE;
        lastCameraSectionZ = Integer.MIN_VALUE;
    }

    private static boolean moonstation14$useOwnedView(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || camera == null) return false;
        if (!moonstation14$hasCommittedOwnedView() || minecraft.getCameraEntity() != camera.getEntity()) return false;
        ControlledChunkDiagnostics.observeRender(minecraft, camera);
        return true;
    }

    @Redirect(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double moonstation14$anchorX(LocalPlayer player, Camera camera, Frustum frustum, boolean captured, boolean spectator) {
        return RendererViewAnchorPolicy.coordinate(moonstation14$useOwnedView(camera), camera == null ? 0 : camera.getPosition().x, player.getX());
    }

    @Redirect(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double moonstation14$anchorY(LocalPlayer player, Camera camera, Frustum frustum, boolean captured, boolean spectator) {
        return RendererViewAnchorPolicy.coordinate(moonstation14$useOwnedView(camera), camera == null ? 0 : camera.getPosition().y, player.getY());
    }

    @Redirect(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double moonstation14$anchorZ(LocalPlayer player, Camera camera, Frustum frustum, boolean captured, boolean spectator) {
        return RendererViewAnchorPolicy.coordinate(moonstation14$useOwnedView(camera), camera == null ? 0 : camera.getPosition().z, player.getZ());
    }
}
