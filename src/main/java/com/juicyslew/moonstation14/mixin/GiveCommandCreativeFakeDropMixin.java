package com.juicyslew.moonstation14.mixin;

import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prevents GiveCommand's inventory-success fake visual drop from duplicating converted items. */
@Mixin(GiveCommand.class)
public abstract class GiveCommandCreativeFakeDropMixin {
    @Redirect(method = "giveItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
                    ordinal = 0), require = 1)
    private static ItemEntity moonstation14$skipCreativeFakeDrop(ServerPlayer player, ItemStack stack,
                                                                  boolean dropAround) {
        if (player.isCreative() && isMoonstationItem(stack)) return null;
        return player.drop(stack, dropAround);
    }

    private static boolean isMoonstationItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().builtInRegistryHolder().unwrapKey()
                .map(key -> "moonstation14".equals(key.location().getNamespace())).orElse(false);
    }
}
