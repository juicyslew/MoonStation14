package com.juicyslew.moonstation14.ms14.hands.quarantine;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-only account Creative carrier observation; never an authorization to park or transfer items. */
public final class CreativeCarrierSnapshotProbe {
    private CreativeCarrierSnapshotProbe() { }

    /** Bounded, content-free observations. Never expose codec exception messages or stack data. */
    public enum Reason {
        READY, INVALID_OWNER_OR_MODE, UNSUPPORTED_MENU, CRAFTING_OR_RESULT_OCCUPIED,
        ENDER_OCCUPIED, INVALID_INVENTORY_SHAPE, NONCANONICAL_OR_INVALID_STACK, CODEC_REJECTED,
        MARKER_PRESENT, CHANGED_DURING_PARK
    }

    public static Reason reason(ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || !(player.level() instanceof ServerLevel level))
            return Reason.INVALID_OWNER_OR_MODE;
        try {
            var server = level.getServer();
            if (server == null || !server.isSameThread() || player.isRemoved() || !player.isAlive()
                    || server.getPlayerList().getPlayer(player.getUUID()) != player
                    || level.getEntity(player.getUUID()) != player
                    || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE)
                return Reason.INVALID_OWNER_OR_MODE;
            if (player.containerMenu != player.inventoryMenu
                    || !(player.inventoryMenu instanceof InventoryMenu menu)) return Reason.UNSUPPORTED_MENU;
            Container craft = menu.getCraftSlots();
            if (craft == null || craft.getContainerSize() != InventoryMenu.CRAFT_SLOT_COUNT
                    || menu.slots.size() != 46 || menu.getResultSlotIndex() != InventoryMenu.RESULT_SLOT
                    || menu.slots.get(InventoryMenu.RESULT_SLOT) == null) return Reason.UNSUPPORTED_MENU;
            if (!canonicalEmpty(menu.slots.get(InventoryMenu.RESULT_SLOT).getItem()))
                return Reason.CRAFTING_OR_RESULT_OCCUPIED;
            for (int i = 0; i < InventoryMenu.CRAFT_SLOT_COUNT; i++) {
                var slot = menu.slots.get(InventoryMenu.CRAFT_SLOT_START + i);
                if (slot == null || slot.container != craft) return Reason.UNSUPPORTED_MENU;
                if (!canonicalEmpty(craft.getItem(i)) || !canonicalEmpty(slot.getItem()))
                    return Reason.CRAFTING_OR_RESULT_OCCUPIED;
            }
            var ender = player.getEnderChestInventory();
            if (ender == null || ender.getContainerSize() != 27) return Reason.INVALID_INVENTORY_SHAPE;
            for (int i = 0; i < 27; i++)
                if (!canonicalEmpty(ender.getItem(i))) return Reason.ENDER_OCCUPIED;
            var inventory = player.getInventory();
            if (inventory == null || inventory.items.size() != CreativeInventorySnapshot.MAIN
                    || inventory.armor.size() != CreativeInventorySnapshot.ARMOR
                    || inventory.offhand.size() != CreativeInventorySnapshot.OFFHAND)
                return Reason.INVALID_INVENTORY_SHAPE;
            CreativeInventorySnapshot snapshot;
            try {
                snapshot = CreativeInventorySnapshot.capture(inventory.items, inventory.armor,
                        inventory.offhand, menu.getCarried(), inventory.selected, java.util.Collections.nCopies(27, ItemStack.EMPTY));
            } catch (RuntimeException invalidStack) {
                // A second, read-only observation is diagnostic only. Never log the exception,
                // stack data, or infer that a value remained invalid if it changed between reads.
                CreativeInventorySnapshot.ValidationIssue issue;
                try {
                    issue = CreativeInventorySnapshot.diagnose(
                            inventory.items, inventory.armor, inventory.offhand, menu.getCarried(), inventory.selected);
                } catch (RuntimeException unreadable) {
                    issue = new CreativeInventorySnapshot.ValidationIssue(
                            CreativeInventorySnapshot.ValidationCategory.OBSERVATION_FAILED, "unknown", -1);
                }
                if (issue == null) MoonStation14.LOGGER.warn(
                        "Creative carrier invalid-stack preflight diagnosis: inconclusive");
                else MoonStation14.LOGGER.warn(
                        "Creative carrier invalid-stack preflight diagnosis: category={}, compartment={}, slot={}",
                        issue.category(), issue.compartment(), issue.slot());
                return Reason.NONCANONICAL_OR_INVALID_STACK;
            }
            try {
                CreativeInventorySnapshot.decode(snapshot.encode(level.registryAccess()), level.registryAccess());
            } catch (RuntimeException codecFailure) { return Reason.CODEC_REJECTED; }
            return Reason.READY;
        } catch (RuntimeException unreadable) { return Reason.INVALID_INVENTORY_SHAPE; }
    }

    public static Optional<CreativeInventorySnapshot> capture(ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || !(player.level() instanceof ServerLevel level))
            return Optional.empty();
        var server = level.getServer();
        if (server == null || !server.isSameThread() || player.isRemoved() || !player.isAlive()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || level.getEntity(player.getUUID()) != player
                || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE
                || player.containerMenu != player.inventoryMenu
                || !(player.inventoryMenu instanceof InventoryMenu menu)) return Optional.empty();

        // In pinned 1.21.1 InventoryMenu, getCraftSlots() is the actual 2x2 container,
        // and slots[RESULT_SLOT] is the ResultSlot backed by the separate result container.
        // Inspect the underlying craft container as well as the result slot, not just visible stacks.
        Container craft = menu.getCraftSlots();
        if (craft == null || craft.getContainerSize() != InventoryMenu.CRAFT_SLOT_COUNT
                || menu.slots.size() != 46 || menu.getResultSlotIndex() != InventoryMenu.RESULT_SLOT
                || menu.slots.get(InventoryMenu.RESULT_SLOT) == null
                || !canonicalEmpty(menu.slots.get(InventoryMenu.RESULT_SLOT).getItem())) return Optional.empty();
        for (int i = 0; i < InventoryMenu.CRAFT_SLOT_COUNT; i++) {
            int slot = InventoryMenu.CRAFT_SLOT_START + i;
            if (menu.slots.get(slot) == null || menu.slots.get(slot).container != craft
                    || !canonicalEmpty(craft.getItem(i)) || !canonicalEmpty(menu.slots.get(slot).getItem()))
                return Optional.empty();
        }

        var ender = player.getEnderChestInventory();
        if (ender == null || ender.getContainerSize() != 27) return Optional.empty();
        List<ItemStack> extras = new ArrayList<>(ender.getContainerSize());
        for (int i = 0; i < ender.getContainerSize(); i++) {
            ItemStack stack = ender.getItem(i);
            if (!canonicalEmpty(stack)) return Optional.empty();
            extras.add(stack);
        }

        var inventory = player.getInventory();
        if (inventory == null || inventory.items.size() != CreativeInventorySnapshot.MAIN
                || inventory.armor.size() != CreativeInventorySnapshot.ARMOR
                || inventory.offhand.size() != CreativeInventorySnapshot.OFFHAND) return Optional.empty();
        try {
            CreativeInventorySnapshot snapshot = CreativeInventorySnapshot.capture(inventory.items, inventory.armor,
                    inventory.offhand, menu.getCarried(), inventory.selected, extras);
            // The value must also survive the supported, bounded codec without losing information.
            var registries = level.registryAccess();
            return Optional.of(CreativeInventorySnapshot.decode(snapshot.encode(registries), registries));
        } catch (IllegalArgumentException invalidSnapshot) {
            return Optional.empty();
        }
    }

    private static boolean canonicalEmpty(ItemStack stack) {
        return stack == ItemStack.EMPTY && stack.getCount() == 0;
    }
}
