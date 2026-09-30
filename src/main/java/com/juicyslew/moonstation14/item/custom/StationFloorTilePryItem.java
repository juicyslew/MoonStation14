package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import com.juicyslew.moonstation14.ms14.power.floor.TilePrySelection;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualServerHooks;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Removes a steel or white finish from the existing station-floor block. */
public class StationFloorTilePryItem extends Item {
    public StationFloorTilePryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
        }

        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!state.is(ModBlocks.STATION_FLOOR.get())) {
            return InteractionResult.FAIL;
        }
        StationFloorBlock.TileFinish finish = state.getValue(StationFloorBlock.TILE_FINISH);
        var choice = TilePrySelection.select(context.getClickedFace(), finish);
        if (choice == TilePrySelection.Result.DENIED) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        var player = context.getPlayer();
        if (player == null || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) {
            return InteractionResult.FAIL;
        }

        if (!level.setBlock(pos, state.setValue(StationFloorBlock.TILE_FINISH, StationFloorBlock.TileFinish.NONE), 3)) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel)
            CableVisualServerHooks.noteChanged(serverLevel, new net.minecraft.world.level.ChunkPos(pos));

        var tile = choice == TilePrySelection.Result.STEEL_TILE
                ? ModItems.STATION_FLOOR_TILE.get()
                : ModItems.STATION_FLOOR_TILE_WHITE.get();
        Block.popResource(level, pos, new ItemStack(tile));
        context.getItemInHand().hurtAndBreak(1, (ServerLevel) level, player,
                item -> player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
        level.playSound(null, pos, ModSounds.CROWBAR_USE.get(), SoundSource.BLOCKS);
        return InteractionResult.CONSUME;
    }
}
