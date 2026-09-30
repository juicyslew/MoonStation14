package com.juicyslew.moonstation14.ms14.hands.quarantine;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Narrow guard for known direct vanilla/project mutation routes, not an inventory capability. */
public final class ParkedInventoryDirectPolicy {
    private ParkedInventoryDirectPolicy() { }

    public static boolean deny(Inventory inventory) {
        return inventory != null && denyHolder(inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    public static boolean denyHolder(ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || !(player.level() instanceof ServerLevel level)
                || level.isClientSide || player.isRemoved() || !player.isAlive()) return false;
        var server = level.getServer();
        if (server == null || !server.isSameThread()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || level.getEntity(player.getUUID()) != player) return false;
        // Presence is enough: a mismatched or unreadable account park must never authorize writes.
        try {
            return player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
        } catch (RuntimeException unreadableMarker) {
            return true;
        }
    }

    /** Detects bypass writes before resolving or revalidating a body hand action; never repairs them. */
    public static boolean denyDirtyHandAction(ServerPlayer player) {
        if (!denyHolder(player)) return false;
        try {
            var inventory = player.getInventory();
            for (ItemStack stack : inventory.items) if (!canonicalEmpty(stack)) return true;
            for (ItemStack stack : inventory.armor) if (!canonicalEmpty(stack)) return true;
            for (ItemStack stack : inventory.offhand) if (!canonicalEmpty(stack)) return true;
            if (!canonicalEmpty(player.inventoryMenu.getCarried())) return true;
            for (int i = 0; i < player.inventoryMenu.getCraftSlots().getContainerSize(); i++)
                if (!canonicalEmpty(player.inventoryMenu.getCraftSlots().getItem(i))) return true;
            if (!canonicalEmpty(player.inventoryMenu.slots.get(0).getItem())) return true;
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++)
                if (!canonicalEmpty(player.getEnderChestInventory().getItem(i))) return true;
            return false;
        } catch (RuntimeException unreadableCarrier) {
            return true;
        }
    }

    private static boolean canonicalEmpty(ItemStack stack) {
        return stack == ItemStack.EMPTY && stack.getCount() == 0;
    }
}
