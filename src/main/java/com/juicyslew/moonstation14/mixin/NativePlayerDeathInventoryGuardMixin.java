package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla death inventory projection from dropping/clearing medical corpse gear. */
@Mixin(Inventory.class)
public abstract class NativePlayerDeathInventoryGuardMixin {
    @Shadow @Final public Player player;

    @Inject(method = "dropAll()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$keepMedicalCorpseInventory(CallbackInfo callback) {
        if (player instanceof ServerPlayer && MobStateSystem.policy(player) != null
                && MobStateSystem.isDead(player)) {
            callback.cancel();
        }
    }
}
