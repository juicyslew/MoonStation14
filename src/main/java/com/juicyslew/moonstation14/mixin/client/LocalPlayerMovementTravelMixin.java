package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.movement.client.MovementClientController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps vanilla travel from applying a second local-player movement step after server commit. */
@Mixin(LivingEntity.class)
public abstract class LocalPlayerMovementTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$cancelOwnedLocalTravel(Vec3 input, CallbackInfo callback) {
        if (Minecraft.getInstance().player == (Object) this
                && MovementClientController.owns((LocalPlayer) (Object) this)) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            MovementClientController.onTravel(player, input);
            callback.cancel();
        }
    }
}
