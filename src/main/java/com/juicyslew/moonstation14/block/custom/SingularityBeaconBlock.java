package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.block_entity.SingularityBeaconBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Static machine placement, matching BaseMachine's anchored composition. */
public final class SingularityBeaconBlock extends Block implements EntityBlock {
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.box(.1, 0, .1, .9, 1, .9);
    public SingularityBeaconBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter world,
                                                                         BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,
            net.minecraft.world.level.BlockGetter world, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.SINGULARITY_BEACON.get().create(pos, state);
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof net.minecraft.server.level.ServerLevel && type == ModBlockEntities.SINGULARITY_BEACON.get()
                ? (world, pos, blockState, entity) -> SingularityBeaconBlockEntity.serverTick(
                        (net.minecraft.server.level.ServerLevel) world, pos, blockState, (SingularityBeaconBlockEntity) entity) : null;
    }
}
