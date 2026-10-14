package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Medical-transition adaptation for an entity's own native hands (not a carrier inventory). */
public final class NativeHeldItemTransfer {
    private NativeHeldItemTransfer() { }

    public static int occupiedHands(LivingEntity body) {
        if (body == null) return 0;
        int occupied = 0;
        if (!body.getMainHandItem().isEmpty()) occupied++;
        if (!body.getOffhandItem().isEmpty()) occupied++;
        return occupied;
    }

    /** Drops only native main/offhand slots; armor and all other inventory slots remain untouched. */
    public static void forceDrop(LivingEntity body) {
        if (!(body instanceof net.minecraft.world.entity.player.Player player)
                || !(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || !level.hasChunkAt(body.blockPosition())) return;
        Inventory inventory = player.getInventory();
        dropSlot(body, level, inventory, inventory.selected);
        dropSlot(body, level, inventory, 40);
    }

    private static void dropSlot(LivingEntity body, ServerLevel level, Inventory inventory, int slot) {
        ItemStack source = inventory.getItem(slot);
        if (source.isEmpty()) return;
        ItemStack original = source.copy();
        if (inventory.getItem(slot) != source) return;
        Vec3 pos = body.position();
        LooseItemEntity dropped = null;
        try {
            dropped = LooseItemEntity.create(level, pos, original);
            if (!preservesNativeStack(original, dropped.getItem())) return;
            // Extract first, publish second; restore only if publication did not admit the entity.
            if (inventory.getItem(slot) != source) return;
            inventory.setItem(slot, ItemStack.EMPTY);
            if (inventory.getItem(slot) != ItemStack.EMPTY && !inventory.getItem(slot).isEmpty()) return;
            boolean added = level.addFreshEntity(dropped);
            if (!added && level.getEntity(dropped.getUUID()) == null && !dropped.isAddedToLevel()
                    && inventory.getItem(slot).isEmpty()) inventory.setItem(slot, original);
        } catch (RuntimeException failure) {
            boolean unadmitted = dropped == null
                    || (level.getEntity(dropped.getUUID()) == null && !dropped.isAddedToLevel());
            if (unadmitted && level.getEntity(body.getUUID()) == body && body.isAddedToLevel()
                    && !body.isRemoved() && level.hasChunkAt(body.blockPosition())
                    && inventory.getItem(slot).isEmpty())
                inventory.setItem(slot, original);
        }
    }

    /** Loose-item materialization may append its runtime ownership marker; every source component must survive. */
    private static boolean preservesNativeStack(ItemStack source, ItemStack materialized) {
        if (!ItemStack.isSameItem(source, materialized) || source.getCount() != materialized.getCount()) return false;
        for (var type : source.getComponents().keySet())
            if (!java.util.Objects.equals(source.get(type), materialized.get(type))) return false;
        return true;
    }
    /** Native slots are mapped by the player's configured main arm, never by hand-list order. */
    public static int nativeSlotForHand(Player player, String handId) {
        if (player == null || handId == null) return -1;
        boolean main = (player.getMainArm() == HumanoidArm.RIGHT && handId.equals("right"))
                || (player.getMainArm() == HumanoidArm.LEFT && handId.equals("left"));
        boolean off = (player.getMainArm() == HumanoidArm.RIGHT && handId.equals("left"))
                || (player.getMainArm() == HumanoidArm.LEFT && handId.equals("right"));
        return main ? player.getInventory().selected : off ? 40 : -1;
    }

    public static boolean nativeOccupiedHand(Player player, String handId) {
        int slot = nativeSlotForHand(player, handId);
        return slot >= 0 && !player.getInventory().getItem(slot).isEmpty();
    }
}

