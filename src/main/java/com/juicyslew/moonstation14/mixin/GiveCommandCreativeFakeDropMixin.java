package com.juicyslew.moonstation14.mixin;

import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prevents GiveCommand's inventory-success fake visual drop from duplicating converted items. */
@Mixin(GiveCommand.class)
public abstract class GiveCommandCreativeFakeDropMixin {
    @Redirect(method = "giveItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"),
            require = 1)
    private static boolean moonstation14$giveToControlledBody(Inventory inventory, ItemStack stack)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (inventory.player instanceof ServerPlayer player) {
            BodyGiveService.CommandResult result = BodyGiveService.giveCommand(player, stack);
            if (result == BodyGiveService.CommandResult.HANDLED) return true;
            if (result == BodyGiveService.CommandResult.FAILED)
                throw com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS
                        .dispatcherUnknownArgument().create();
        }
        return inventory.add(stack);
    }

    @Redirect(method = "giveItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
                    ordinal = 0), require = 1)
    private static ItemEntity moonstation14$skipCreativeFakeDrop(ServerPlayer player, ItemStack stack,
                                                                  boolean dropAround) {
        if (BodyActionAuthority.resolve(player).isPresent()) return null;
        if (player.isCreative() && isMoonstationItem(stack)) return null;
        return player.drop(stack, dropAround);
    }

    private static boolean isMoonstationItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().builtInRegistryHolder().unwrapKey()
                .map(key -> "moonstation14".equals(key.location().getNamespace())).orElse(false);
    }
}
