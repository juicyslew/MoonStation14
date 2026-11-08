package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereBlockPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import javax.annotation.Nullable;

/** Registered machine shell. The explicit profile identity selects resolved composition, never block-name inference. */
public final class AtmosMachineBlock extends Block implements EntityBlock, AtmosphereBlockPolicy {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Compact resource selector: TEG unpowered/powered-level values then machine-specific states. */
    public static final IntegerProperty DISPLAY = IntegerProperty.create("display", 0, 29);
    /** Derived visual lane, never read as the selected gas inventory lane. */
    public static final IntegerProperty PIPE_LAYER = IntegerProperty.create("pipe_layer", 0, 2);
    private final String profileIdentity;

    public AtmosMachineBlock(String profileIdentity, Properties properties) {
        super(properties);
        if (profileIdentity == null || profileIdentity.isBlank()) throw new IllegalArgumentException("profile identity required");
        this.profileIdentity = profileIdentity;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DISPLAY, 0).setValue(PIPE_LAYER, 0));
    }

    public String profileIdentity() { return profileIdentity; }

    /** Outline and collision share geometry resolved from the model currently bound to this state. */
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state, level, pos);
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state, level, pos);
    }

    private VoxelShape shapeFor(BlockState state, BlockGetter level, BlockPos pos) {
        return AtmosMachineGeometry.shape(profileIdentity, state, level, pos);
    }

    /** SS14 atmos machine prototypes do not compose Airtight; full collision remains a Minecraft adaptation. */
    @Override public boolean atmospherePassable() { return true; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, DISPLAY, PIPE_LAYER); }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.ATMOS_MACHINE.get().create(pos, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                       BlockEntityType<T> type) {
        if (type != ModBlockEntities.ATMOS_MACHINE.get()) return null;
        if (level.isClientSide) return (world, pos, blockState, entity) -> {
            if (entity instanceof AtmosMachineBlockEntity machine)
                com.juicyslew.moonstation14.ms14.atmos.machine.client.AtmosMachineAmbientClient.observe(machine);
        };
        return (world, pos, blockState, entity) -> {
            if (world instanceof ServerLevel server && entity instanceof AtmosMachineBlockEntity machine)
                AtmosMachineBlockEntity.tick(server, machine);
        };
    }

    @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && (!oldState.is(state.getBlock())
                || oldState.getValue(FACING) != state.getValue(FACING)
                || oldState.getValue(PIPE_LAYER) != state.getValue(PIPE_LAYER))) {
            if (oldState.is(state.getBlock()) && oldState.getValue(FACING) != state.getValue(FACING))
                com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeNetworks.invalidateTopology(server, pos);
            com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.refreshLoadedAdjacent(server, pos);
        }
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine) {
            com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeNetworks.invalidateTopology(server, pos);
            if (machine.resolvedProfile() != null && machine.resolvedProfile().kind() == AtmosMachineProfile.Kind.TEG_CENTER) {
                Direction facing = state.getValue(FACING);
                machine.clearTegCirculatorPresentation(server, pos.relative(facing));
                machine.clearTegCirculatorPresentation(server, pos.relative(facing.getOpposite()));
            }
            machine.retireRemovedPorts(server, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
