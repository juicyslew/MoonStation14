package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Static placed Tesla coil/rod; per-placement state belongs to its block entity. */
public final class TeslaMachineBlock extends Block implements EntityBlock, com.juicyslew.moonstation14.ms14.singularity.SingularityConsumable {
    public static final BooleanProperty HIT = BooleanProperty.create("hit");
    private final ResourceLocation profileId;
    public TeslaMachineBlock(Properties properties, ResourceLocation profileId) {
        super(properties); this.profileId=profileId;
        registerDefaultState(stateDefinition.any().setValue(HIT,false));
    }
    public ResourceLocation profileId() { return profileId; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HIT); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        TeslaMachineBlockEntity be=(TeslaMachineBlockEntity)ModBlockEntities.TESLA_MACHINE.get().create(pos,state);
        if (be != null) be.setProfileId(profileId);
        return be;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.TESLA_MACHINE.get()) return null;
        return (world,pos,blockState,entity) -> TeslaMachineBlockEntity.tick(world,pos,blockState,(TeslaMachineBlockEntity)entity);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (state.getBlock()!=replacement.getBlock() && level instanceof ServerLevel server)
            com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.deviceRemoved(server,pos);
        super.onRemove(state,level,pos,replacement,moved);
    }
}
