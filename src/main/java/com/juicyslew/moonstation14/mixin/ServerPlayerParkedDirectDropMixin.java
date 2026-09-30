package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.hands.quarantine.ParkedInventoryDirectPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The two-argument Player.drop delegates virtually to this ServerPlayer override. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerParkedDirectDropMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyDirectDrop(ItemStack stack, boolean throwRandomly, boolean traceItem,
                                               CallbackInfoReturnable<ItemEntity> callback) {
        if (ParkedInventoryDirectPolicy.denyHolder((ServerPlayer) (Object) this)) callback.setReturnValue(null);
    }
}
