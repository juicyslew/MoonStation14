package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualServerHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Objects;

/** Installs a visible steel finish on the existing station-floor block. */
public class StationFloorTileItem extends Item {
    private final StationFloorBlock.TileFinish finish;

    public StationFloorTileItem(Properties properties) {
        this(properties, StationFloorBlock.TileFinish.STEEL);
    }

    public StationFloorTileItem(Properties properties, StationFloorBlock.TileFinish finish) {
        super(properties);
        if (finish == StationFloorBlock.TileFinish.NONE) {
            throw new IllegalArgumentException("A tile item must have a visible finish");
        }
        this.finish = Objects.requireNonNull(finish);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
        }
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (!state.is(ModBlocks.STATION_FLOOR.get()) || state.getValue(StationFloorBlock.TILE_FINISH) != StationFloorBlock.TileFinish.NONE) {
            return InteractionResult.FAIL;
        }

        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (context.getPlayer() == null
                || !context.getLevel().mayInteract(context.getPlayer(), context.getClickedPos())
                || !context.getPlayer().mayUseItemAt(context.getClickedPos(), context.getClickedFace(),
                context.getItemInHand())) {
            return InteractionResult.FAIL;
        }

        if (!context.getLevel().setBlock(context.getClickedPos(),
                state.setValue(StationFloorBlock.TILE_FINISH, finish), 3)) {
            return InteractionResult.FAIL;
        }
        if (context.getLevel() instanceof ServerLevel serverLevel)
            CableVisualServerHooks.noteChanged(serverLevel, new net.minecraft.world.level.ChunkPos(context.getClickedPos()));
        ItemStack held = context.getItemInHand();
        if (!context.getPlayer().getAbilities().instabuild) {
            held.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
