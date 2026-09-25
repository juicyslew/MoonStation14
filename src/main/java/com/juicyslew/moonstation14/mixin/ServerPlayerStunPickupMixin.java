package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks server-side world item collection for a stunned bound player. */
@Mixin(ItemEntity.class)
public abstract class ServerPlayerStunPickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyPickup(Player player, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer && !CharacterControlSystem.canAct(serverPlayer)) {
            callback.cancel();
        }
    }
}
