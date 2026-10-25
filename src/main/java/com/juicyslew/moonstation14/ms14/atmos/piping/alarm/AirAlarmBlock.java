package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** Non-colliding wall shell: FACING points into the room; the host is behind it. */
public final class AirAlarmBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
    private static final VoxelShape NORTH = Block.box(4, 4, 13, 12, 12, 16);
    private static final VoxelShape SOUTH = Block.box(4, 4, 0, 12, 12, 3);
    private static final VoxelShape EAST = Block.box(0, 4, 4, 3, 12, 12);
    private static final VoxelShape WEST = Block.box(13, 4, 4, 16, 12, 12);
    private static final VoxelShape UP = Block.box(4, 0, 4, 12, 3, 12);
    private static final VoxelShape DOWN = Block.box(4, 13, 4, 12, 16, 12);

    public AirAlarmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState state = defaultBlockState().setValue(FACING, clicked);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos host = pos.relative(facing.getOpposite());
        return level.getBlockState(host).isFaceSturdy(level, host, facing);
    }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.AIR_ALARM.get().create(pos, state);
    }
}

