package com.juicyslew.moonstation14.ms14.power.floor;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

/** One persistent station substrate whose small visual finish is ordinary vanilla blockstate. */
public class StationFloorBlock extends Block {
    public static final EnumProperty<TileFinish> TILE_FINISH = EnumProperty.create("tile_finish", TileFinish.class);

    public StationFloorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TILE_FINISH, TileFinish.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> builder) {
        builder.add(TILE_FINISH);
    }

    @Override
    public void onRemove(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos,
                         net.minecraft.world.level.block.state.BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CableStorage.removeHost(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public enum TileFinish implements StringRepresentable {
        NONE("none"),
        STEEL("steel"),
        WHITE("white");

        private final String name;

        TileFinish(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
