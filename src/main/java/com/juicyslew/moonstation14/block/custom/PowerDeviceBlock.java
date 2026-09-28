package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/** Visible, static power device. This class only defines ports and APC interaction; no allocation. */
public class PowerDeviceBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private final PowerDeviceKind kind;

    public PowerDeviceBlock(PowerDeviceKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public PowerDeviceKind kind() { return kind; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.POWER_DEVICE.get().create(pos, state);
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide || !(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (state.getBlock() != oldState.getBlock()) {
            PowerRuntime.deviceChanged(server, pos);
        } else if (!state.getValue(FACING).equals(oldState.getValue(FACING))) {
            // A same-block state update keeps the existing device entity and index entry, but its
            // directional ports may have changed the graph topology for this chunk.
            PowerGraphService.noteChanged(server, new ChunkPos(pos));
        }
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()
                && !level.isClientSide && level instanceof net.minecraft.server.level.ServerLevel server)
            PowerRuntime.deviceRemoved(server, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (kind != PowerDeviceKind.APC) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(level.getBlockEntity(pos) instanceof PowerDeviceBlockEntity device)) return InteractionResult.PASS;
        double distanceSquared = player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (!device.toggleBreaker(player, distanceSquared)) return InteractionResult.FAIL;
        return InteractionResult.SUCCESS;
    }
}
