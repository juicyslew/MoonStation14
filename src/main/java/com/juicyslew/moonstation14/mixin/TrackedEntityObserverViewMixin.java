package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.medical_observer.ObserverTracking;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity visibility follows an observer's harness, while native player movement stays corpse-centered. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityObserverViewMixin {
    @Shadow @Final private net.minecraft.world.entity.Entity entity;
    @Shadow private void removePlayer(ServerPlayer player) { }

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void moonstation14$medicalGhostVisibility(ServerPlayer viewer, CallbackInfo ci) {
        if (entity instanceof ServerPlayer carrier && ObserverSystem.mustHideObserverCarrier(viewer, carrier)) {
            removePlayer(viewer);
            ci.cancel();
            return;
        }
        if (!ObserverSystem.isMedicalGhost(entity) || ObserverSystem.mayTrackMedicalGhost(viewer, entity)) return;
        removePlayer(viewer);
        ci.cancel();
    }

    @Redirect(method = "updatePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"),
            require = 1)
    private Vec3 moonstation14$observerTrackingPosition(ServerPlayer player) {
        return ObserverTracking.viewPosition(player);
    }
}
