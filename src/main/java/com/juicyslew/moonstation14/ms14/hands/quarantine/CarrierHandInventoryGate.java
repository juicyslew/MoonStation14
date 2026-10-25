package com.juicyslew.moonstation14.ms14.hands.quarantine;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only prerequisite for body hand actions, not proof that an unmarked account was ever parked. */
public final class CarrierHandInventoryGate {
    private CarrierHandInventoryGate() { }

    /** Values are inspected, never copied or repaired; callers must recheck immediately before committing. */
    public record Fixture(UUID account, boolean markerPresent, CreativeParkedInventory existingPark,
                          List<ItemStack> main, List<ItemStack> armor, List<ItemStack> offhand,
                          ItemStack cursor, List<ItemStack> crafting, ItemStack result,
                          List<ItemStack> ender, List<ItemStack> menuSlots, int selected) { }

    public static boolean allows(ServerPlayer player) {
        return player != null && allowsMenuIdentity(player.containerMenu, player.inventoryMenu, player.inventoryMenu, false)
                && inspect(player, player.inventoryMenu).map(CarrierHandInventoryGate::allows).orElse(false);
    }

    /** Persistent body-menu gate: only the exact server-owned current menu supplied by its owner is admitted. */
    public static boolean allows(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu expectedMenu) {
        if (player == null || !allowsMenuIdentity(player.containerMenu, expectedMenu, player.inventoryMenu, true)) return false;
        return inspect(player, expectedMenu).map(CarrierHandInventoryGate::allows).orElse(false);
    }

    /** Shared identity policy: ordinary admission requires the inventory menu; persistent admission its exact other menu. */
    static boolean allowsMenuIdentity(Object currentMenu, Object expectedMenu, Object inventoryMenu, boolean persistent) {
        return allowsMenuIdentity(currentMenu, expectedMenu, inventoryMenu, persistent, true, true);
    }

    static boolean allowsMenuIdentity(Object currentMenu, Object expectedMenu, Object inventoryMenu, boolean persistent,
                                      boolean expectedSlotsEmpty, boolean expectedCursorEmpty) {
        return currentMenu != null && expectedMenu != null && currentMenu == expectedMenu && inventoryMenu != null
                && (persistent ? expectedMenu != inventoryMenu && expectedSlotsEmpty && expectedCursorEmpty
                        : expectedMenu == inventoryMenu);
    }

    /** A connected, exact carrier's physical inventory and marker, without materializing attachments. */
    public static Optional<Fixture> inspect(ServerPlayer player) {
        return inspect(player, player == null ? null : player.inventoryMenu);
    }

    private static Optional<Fixture> inspect(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu expectedMenu) {
        if (player == null || player instanceof FakePlayer || !(player.level() instanceof ServerLevel level)
                || level.isClientSide || player.isRemoved() || !player.isAlive()) return Optional.empty();
        var server = level.getServer();
        if (server == null || !server.isSameThread()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || level.getEntity(player.getUUID()) != player) return Optional.empty();
        try {
            if (expectedMenu == null || player.containerMenu != expectedMenu
                    || !(player.inventoryMenu instanceof InventoryMenu menu)
                    || menu.getResultSlotIndex() != InventoryMenu.RESULT_SLOT
                    || menu.slots.size() != 46 || menu.getCraftSlots() == null
                    || menu.getCraftSlots().getContainerSize() != InventoryMenu.CRAFT_SLOT_COUNT
                     || player.getInventory() == null || player.getEnderChestInventory() == null) return Optional.empty();
            if (!allowsMenuIdentity(player.containerMenu, expectedMenu, menu, expectedMenu != menu,
                    expectedMenu.slots.isEmpty(), canonicalEmpty(expectedMenu.getCarried()))) return Optional.empty();
            for (int i = 0; i < InventoryMenu.CRAFT_SLOT_COUNT; i++) {
                var slot = menu.slots.get(InventoryMenu.CRAFT_SLOT_START + i);
                if (slot == null || slot.container != menu.getCraftSlots()
                        || slot.getItem() != menu.getCraftSlots().getItem(i)) return Optional.empty();
            }
            if (menu.slots.get(InventoryMenu.RESULT_SLOT) == null) return Optional.empty();
            var slots = new java.util.ArrayList<ItemStack>(menu.slots.size());
            for (var slot : menu.slots) {
                if (slot == null) return Optional.empty();
                slots.add(slot.getItem());
            }
            var craft = new java.util.ArrayList<ItemStack>(InventoryMenu.CRAFT_SLOT_COUNT);
            for (int i = 0; i < InventoryMenu.CRAFT_SLOT_COUNT; i++)
                craft.add(menu.getCraftSlots().getItem(i));
            var ender = player.getEnderChestInventory();
            var enderSlots = new java.util.ArrayList<ItemStack>(ender.getContainerSize());
            for (int i = 0; i < ender.getContainerSize(); i++) enderSlots.add(ender.getItem(i));
            var inventory = player.getInventory();
            return Optional.of(new Fixture(player.getUUID(),
                    player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                    player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                    inventory.items, inventory.armor, inventory.offhand, menu.getCarried(), craft,
                    menu.slots.get(InventoryMenu.RESULT_SLOT).getItem(), enderSlots, slots, inventory.selected));
        } catch (RuntimeException unreadableCarrier) {
            return Optional.empty();
        }
    }

    /** Pure value-level seam; no synthetic actor can satisfy the connected-player check above. */
    public static boolean allows(Fixture fixture) {
        if (fixture == null || fixture.account() == null
                || fixture.markerPresent() != (fixture.existingPark() != null)
                || fixture.markerPresent() && !fixture.account().equals(fixture.existingPark().account())
                || fixture.selected() < 0 || fixture.selected() > 8
                || !empty(fixture.main(), CreativeInventorySnapshot.MAIN)
                || !empty(fixture.armor(), CreativeInventorySnapshot.ARMOR)
                || !empty(fixture.offhand(), CreativeInventorySnapshot.OFFHAND)
                || !canonicalEmpty(fixture.cursor())
                || !empty(fixture.crafting(), InventoryMenu.CRAFT_SLOT_COUNT)
                || !canonicalEmpty(fixture.result())
                || !empty(fixture.ender(), 27)
                || !empty(fixture.menuSlots(), 46)) return false;
        return true;
    }

    private static boolean empty(List<ItemStack> slots, int expected) {
        if (slots == null || slots.size() != expected) return false;
        for (ItemStack stack : slots) if (!canonicalEmpty(stack)) return false;
        return true;
    }

    private static boolean canonicalEmpty(ItemStack stack) {
        return stack == ItemStack.EMPTY && stack.getCount() == 0;
    }
}

