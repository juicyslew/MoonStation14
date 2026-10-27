package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.combat.client.GunCameraRecoilClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds recoil only to the camera eye position after vanilla camera setup. */
@Mixin(Camera.class)
public abstract class GunCameraRecoilMixin {
    @Shadow @Mutable private Vec3 position;
    @Inject(method = "setup", at = @At("TAIL"), require = 1)
    private void moonstation14$cameraRecoil(BlockGetter level, Entity entity, boolean detached, boolean thirdPerson, float partialTick, CallbackInfo ci) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
        if (mc.level == null || body == null || entity != body) { GunCameraRecoilClient.clear(); return; }
        var offset = GunCameraRecoilClient.renderOffset(body.getUUID(), epoch, mc.level.dimension().location(), System.nanoTime());
        position = position.add(offset);
    }
}
