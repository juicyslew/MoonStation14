package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.StationFloorTileItem;
import com.juicyslew.moonstation14.item.custom.StationFloorTilePryItem;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualServerHooks;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import com.juicyslew.moonstation14.ms14.power.floor.TilePrySelection;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Body-owned adapter for the existing station floor tile install and pry operations. */
public final class BodyFloorToolUse {
    private BodyFloorToolUse() { }

    public static boolean supports(ItemStack stack) {
        return stack != null && supports(stack.getItem());
    }

    static boolean supports(net.minecraft.world.item.Item item) {
        return item != null && supportsType(item.getClass());
    }

    static boolean supportsType(Class<?> itemType) {
        return itemType != null && (StationFloorTileItem.class.isAssignableFrom(itemType)
                || StationFloorTilePryItem.class.isAssignableFrom(itemType));
    }

    public static BodyBlockUseResult.Outcome use(BodyUseItemContext context) {
        if (context == null || !supports(context.stack())
                || !(context.authority().body().level() instanceof ServerLevel level))
            return BodyBlockUseResult.Outcome.DENIED;
        BlockPos pos = context.pos();
        if (context.face() != net.minecraft.core.Direction.UP || !level.hasChunkAt(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(context.actor(), pos))
            return BodyBlockUseResult.Outcome.DENIED;
        BlockState beforeState = level.getBlockState(pos);
        ItemStack before = context.stack();
        ItemStack after = before.copy();
        boolean pry = before.getItem() instanceof StationFloorTilePryItem;
        BlockState afterState;
        ItemStack drop = ItemStack.EMPTY;
        ItemEntity dropped = null;
        if (pry) {
            if (!beforeState.is(ModBlocks.STATION_FLOOR.get())) return BodyBlockUseResult.Outcome.DENIED;
            var choice = TilePrySelection.select(context.face(), beforeState.getValue(StationFloorBlock.TILE_FINISH));
            if (choice == TilePrySelection.Result.DENIED) return BodyBlockUseResult.Outcome.DENIED;
            afterState = beforeState.setValue(StationFloorBlock.TILE_FINISH, StationFloorBlock.TileFinish.NONE);
            drop = new ItemStack(choice == TilePrySelection.Result.STEEL_TILE
                    ? ModItems.STATION_FLOOR_TILE.get() : ModItems.STATION_FLOOR_TILE_WHITE.get());
            after.hurtAndBreak(1, level, context.actor(), item ->
                    context.actor().onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
        } else {
            if (!beforeState.is(ModBlocks.STATION_FLOOR.get())
                    || beforeState.getValue(StationFloorBlock.TILE_FINISH) != StationFloorBlock.TileFinish.NONE)
                return BodyBlockUseResult.Outcome.DENIED;
            after.shrink(1);
            afterState = beforeState.setValue(StationFloorBlock.TILE_FINISH,
                    ((StationFloorTileItem) before.getItem()).bodyUseFinish());
        }
        if (BodyHeldItemMutation.preview(context.actor(), context.authority(), context.hands(), context.handId(),
                context.token(), context.revision(), before, after).isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        if (pry) {
            BlockPos bodyPos = context.authority().body().blockPosition();
            drop = drop.copy();
            dropped = new ItemEntity(level, bodyPos.getX() + .5, bodyPos.getY() + .5,
                    bodyPos.getZ() + .5, drop);
            if (!level.addFreshEntity(dropped)) return BodyBlockUseResult.Outcome.DENIED;
            if (level.getEntity(dropped.getUUID()) != dropped || !dropped.isAddedToLevel() || dropped.isRemoved())
                return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
        }
        if (!level.setBlock(pos, afterState, 3)) {
            if (pry) return removeOwnedDrop(level, dropped) ? BodyBlockUseResult.Outcome.DENIED
                    : BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            return BodyBlockUseResult.Outcome.DENIED;
        }
        var committed = BodyHeldItemMutation.commit(context.actor(), context.authority(), context.hands(),
                context.handId(), context.token(), context.revision(), before, after);
        if (committed != BodyHeldItemMutation.Result.SUCCESS) {
            BlockState currentState = level.getBlockState(pos);
            boolean restoreAttempted = currentState == afterState;
            boolean restoreAccepted = restoreAttempted && level.setBlock(pos, beforeState, 3);
            boolean floorRestored = compensationRestored(currentState == beforeState, currentState == afterState,
                    restoreAttempted, restoreAccepted, level.getBlockState(pos) == beforeState);
            boolean dropRemoved = !pry || removeOwnedDrop(level, dropped);
            if (floorRestored && dropRemoved)
                return committed == BodyHeldItemMutation.Result.DENIED
                        ? BodyBlockUseResult.Outcome.DENIED : BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
        }
        CableVisualServerHooks.noteChanged(level, new net.minecraft.world.level.ChunkPos(pos));
        if (pry) {
            level.playSound(null, pos, ModSounds.CROWBAR_USE.get(), SoundSource.BLOCKS);
        }
        return BodyBlockUseResult.Outcome.PLACED;
    }

    static BodyBlockUseResult.Outcome compensationOutcome(BodyHeldItemMutation.Result committed,
                                                           boolean floorRestored, boolean dropRemoved) {
        if (!floorRestored || !dropRemoved) return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
        return committed == BodyHeldItemMutation.Result.DENIED
                ? BodyBlockUseResult.Outcome.DENIED : BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
    }

    static boolean compensationRestored(boolean wasOriginal, boolean wasCreated, boolean restoreAttempted,
                                        boolean restoreAccepted, boolean verifiesOriginal) {
        if (wasOriginal) return true;
        return wasCreated && restoreAttempted && restoreAccepted && verifiesOriginal;
    }

    private static boolean removeOwnedDrop(ServerLevel level, ItemEntity dropped) {
        if (dropped == null || level.getEntity(dropped.getUUID()) != dropped) return false;
        dropped.discard();
        return dropped.isRemoved() && level.getEntity(dropped.getUUID()) != dropped;
    }
}
