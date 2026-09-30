package com.juicyslew.moonstation14.ms14.hands.quarantine;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Explicit, in-process carrier transaction. No event registration, crash journal, or gameplay authorization. */
public final class CreativeCarrierTransition {
    public enum Result { DENIED, PARKED, RESTORED, RECOVERY_REQUIRED }

    /** Only a failed read-only preflight proves DENIED happened without any transaction writes. */
    public record Attempt(Result result, boolean noWriteDenial, CreativeCarrierSnapshotProbe.Reason reason) { }

    public static Attempt parkAttempt(ServerPlayer player) {
        CreativeCarrierSnapshotProbe.Reason reason = CreativeCarrierSnapshotProbe.reason(player);
        if (reason != CreativeCarrierSnapshotProbe.Reason.READY)
            return new Attempt(Result.DENIED, true, reason);
        try {
            if (player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) != null)
                return new Attempt(Result.DENIED, false, CreativeCarrierSnapshotProbe.Reason.MARKER_PRESENT);
            Result result = park(player);
            return new Attempt(result, false, result == Result.PARKED
                    ? CreativeCarrierSnapshotProbe.Reason.READY
                    : CreativeCarrierSnapshotProbe.Reason.CHANGED_DURING_PARK);
        } catch (RuntimeException failure) {
            return new Attempt(Result.RECOVERY_REQUIRED, false, CreativeCarrierSnapshotProbe.Reason.CHANGED_DURING_PARK);
        }
    }

    private CreativeCarrierTransition() { }

    /** Package-local fixture injection only; production entrypoints always use the no-op writer. */
    @FunctionalInterface
    interface FixtureWriteHook {
        void beforeWrite(ServerPlayer player, int slot, ItemStack stack);
    }

    private static final FixtureWriteHook NO_HOOK = (player, slot, stack) -> { };

    static Result parkTrustedFixture(ServerPlayer player, CreativeInventorySnapshot verified,
                                     FixtureWriteHook hook) {
        if (!fixtureContext(player) || verified == null || hook == null) return Result.DENIED;
        return parkCore(player, verified, hook);
    }

    static Result restoreTrustedFixture(ServerPlayer player, FixtureWriteHook hook) {
        if (!fixtureContext(player) || hook == null) return Result.DENIED;
        return restoreCore(player, hook);
    }

    private static boolean fixtureContext(ServerPlayer player) {
        return player != null && !(player instanceof FakePlayer) && player.level() instanceof ServerLevel level
                && level.getServer() != null && level.getServer().isSameThread()
                && !player.isRemoved() && player.isAlive();
    }

    /** Selection is left unchanged while parked; restore installs the saved hotbar selection. */
    public static Result park(ServerPlayer player) {
        try {
            if (!authorized(player) || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE)
                return Result.DENIED;
            Optional<CreativeInventorySnapshot> captured = CreativeCarrierSnapshotProbe.capture(player);
            return captured.map(snapshot -> parkCore(player, snapshot, NO_HOOK)).orElse(Result.DENIED);
        } catch (RuntimeException failure) {
            return Result.RECOVERY_REQUIRED;
        }
    }

    private static Result parkCore(ServerPlayer player, CreativeInventorySnapshot snapshot, FixtureWriteHook hook) {
        List<ItemStack> originals;
        int selected;
        try {
            if (player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) != null
                    || !cleanExtras(player)) return Result.DENIED;
            originals = current(player);
            selected = player.getInventory().selected;
            if (originals.size() != CreativeInventorySnapshot.SLOT_COUNT
                    || selected != snapshot.selectedHotbarIndex() || !matchesSnapshot(originals, snapshot))
                return Result.DENIED;
        } catch (RuntimeException failure) {
            return Result.DENIED; // No writes have occurred.
        }
        CreativeParkedInventory park = new CreativeParkedInventory(player.getUUID(), snapshot);
        try {
            player.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(), park);
            if (player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) != park)
                return Result.RECOVERY_REQUIRED;
            for (int i = 0; i < CreativeInventorySnapshot.SLOT_COUNT; i++) write(player, i, ItemStack.EMPTY, hook);
            if (empty(player) && player.getInventory().selected == selected
                    && player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) == park)
                return Result.PARKED;
        } catch (RuntimeException failure) {
            // Only the exact originals or our own canonical clears can be safely rolled back.
        }
        try {
            if (!ownedParkState(player, park, originals, selected)
                    || !matchesSnapshot(originals, snapshot)) return Result.RECOVERY_REQUIRED;
            for (int i = 0; i < originals.size(); i++) write(player, i, originals.get(i), hook);
            if (!sameReferences(current(player), originals) || !matchesSnapshot(current(player), snapshot)
                    || player.getInventory().selected != selected || !cleanExtras(player))
                return Result.RECOVERY_REQUIRED;
            player.removeData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
            return player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) == null
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        } catch (RuntimeException failure) {
            return Result.RECOVERY_REQUIRED;
        }
    }

    /** Never writes over occupied carrier, crafting, result, cursor, or Ender compartments. */
    public static Result restore(ServerPlayer player) {
        try {
            if (!authorized(player) || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE)
                return Result.DENIED;
            return restoreCore(player, NO_HOOK);
        } catch (RuntimeException failure) {
            return Result.RECOVERY_REQUIRED;
        }
    }

    private static Result restoreCore(ServerPlayer player, FixtureWriteHook hook) {
        CreativeParkedInventory park;
        int previousSelection;
        try {
            park = player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
            if (park == null || !park.account().equals(player.getUUID())
                    || player.containerMenu != player.inventoryMenu || !empty(player)) return Result.DENIED;
            previousSelection = player.getInventory().selected;
        } catch (RuntimeException failure) {
            return Result.DENIED; // No writes have occurred.
        }
        CreativeInventorySnapshot snapshot = park.snapshot();
        List<ItemStack> copies = new ArrayList<>(CreativeInventorySnapshot.SLOT_COUNT);
        try {
            for (int i = 0; i < CreativeInventorySnapshot.SLOT_COUNT; i++) {
                ItemStack copy = snapshot.stackCopy(i);
                copies.add(copy.isEmpty() ? ItemStack.EMPTY : copy);
            }
        } catch (RuntimeException failure) {
            return Result.RECOVERY_REQUIRED;
        }
        try {
            for (int i = 0; i < copies.size(); i++) write(player, i, copies.get(i), hook);
            player.getInventory().selected = snapshot.selectedHotbarIndex();
            if (sameReferences(current(player), copies) && matchesSnapshot(current(player), snapshot)
                    && player.getInventory().selected == snapshot.selectedHotbarIndex() && cleanExtras(player)
                    && player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) == park) {
                player.removeData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
                if (player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) == null)
                    return Result.RESTORED;
            }
        } catch (RuntimeException failure) {
            // Marker remains until verification and removal; rollback below if still owned.
        }
        try {
            if (player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) != park
                    || !ownedRestoreState(player, copies, snapshot)) return Result.RECOVERY_REQUIRED;
            for (int i = 0; i < copies.size(); i++) write(player, i, ItemStack.EMPTY, hook);
            player.getInventory().selected = previousSelection;
            return Result.RECOVERY_REQUIRED; // Marker stays, even after a complete rollback.
        } catch (RuntimeException failure) {
            return Result.RECOVERY_REQUIRED;
        }
    }

    private static boolean authorized(ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || !(player.level() instanceof ServerLevel level)
                || player.isRemoved() || !player.isAlive()) return false;
        var server = level.getServer();
        return server != null && server.isSameThread()
                && server.getPlayerList().getPlayer(player.getUUID()) == player
                && level.getEntity(player.getUUID()) == player;
    }

    private static List<ItemStack> current(ServerPlayer player) {
        List<ItemStack> slots = new ArrayList<>(CreativeInventorySnapshot.SLOT_COUNT);
        slots.addAll(player.getInventory().items);
        slots.addAll(player.getInventory().armor);
        slots.addAll(player.getInventory().offhand);
        slots.add(player.inventoryMenu.getCarried());
        return slots;
    }

    private static void write(ServerPlayer player, int slot, ItemStack stack, FixtureWriteHook hook) {
        hook.beforeWrite(player, slot, stack);
        if (slot < CreativeInventorySnapshot.MAIN) player.getInventory().items.set(slot, stack);
        else if (slot < CreativeInventorySnapshot.MAIN + CreativeInventorySnapshot.ARMOR)
            player.getInventory().armor.set(slot - CreativeInventorySnapshot.MAIN, stack);
        else if (slot == CreativeInventorySnapshot.MAIN + CreativeInventorySnapshot.ARMOR)
            player.getInventory().offhand.set(0, stack);
        else player.inventoryMenu.setCarried(stack);
    }

    private static boolean sameReferences(List<ItemStack> actual, List<ItemStack> expected) {
        if (actual.size() != expected.size()) return false;
        for (int i = 0; i < actual.size(); i++) if (actual.get(i) != expected.get(i)) return false;
        return true;
    }

    private static boolean matchesSnapshot(List<ItemStack> slots, CreativeInventorySnapshot snapshot) {
        if (slots.size() != CreativeInventorySnapshot.SLOT_COUNT) return false;
        for (int i = 0; i < slots.size(); i++)
            if (!ItemStack.matches(slots.get(i), snapshot.stackCopy(i))) return false;
        return true;
    }

    private static boolean ownedParkState(ServerPlayer player, CreativeParkedInventory park,
                                          List<ItemStack> originals, int selected) {
        if (player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) != park
                || player.getInventory().selected != selected || !cleanExtras(player)) return false;
        List<ItemStack> now = current(player);
        if (now.size() != originals.size()) return false;
        for (int i = 0; i < now.size(); i++)
            if (now.get(i) != originals.get(i) && now.get(i) != ItemStack.EMPTY) return false;
        return true;
    }

    private static boolean ownedRestoreState(ServerPlayer player, List<ItemStack> copies,
                                             CreativeInventorySnapshot snapshot) {
        if (!cleanExtras(player)) return false;
        List<ItemStack> now = current(player);
        if (now.size() != copies.size()) return false;
        for (int i = 0; i < now.size(); i++) {
            if (now.get(i) != copies.get(i) && now.get(i) != ItemStack.EMPTY) return false;
            if (now.get(i) != ItemStack.EMPTY && !ItemStack.matches(now.get(i), snapshot.stackCopy(i)))
                return false;
        }
        return true;
    }

    private static boolean empty(ServerPlayer player) {
        if (!cleanExtras(player)) return false;
        List<ItemStack> slots = current(player);
        if (slots.size() != CreativeInventorySnapshot.SLOT_COUNT) return false;
        for (ItemStack stack : slots) if (stack != ItemStack.EMPTY || stack.getCount() != 0) return false;
        return true;
    }

    private static boolean cleanExtras(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu || player.inventoryMenu == null
                || player.inventoryMenu.getCraftSlots() == null
                || player.inventoryMenu.getCraftSlots().getContainerSize() != 4
                || player.inventoryMenu.slots.size() != 46
                || player.getEnderChestInventory().getContainerSize() != 27) return false;
        for (int i = 0; i < 4; i++)
            if (player.inventoryMenu.getCraftSlots().getItem(i) != ItemStack.EMPTY
                    || player.inventoryMenu.slots.get(i + 1).getItem() != ItemStack.EMPTY) return false;
        if (player.inventoryMenu.slots.get(0).getItem() != ItemStack.EMPTY) return false;
        for (int i = 0; i < 27; i++)
            if (player.getEnderChestInventory().getItem(i) != ItemStack.EMPTY) return false;
        return true;
    }
}
