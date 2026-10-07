package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.SoundType;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

public final class DisposalUnitBlock extends BaseEntityBlock {
    public enum VisualState implements StringRepresentable {
        UNPOWERED, READY, CHARGING, FLUSHED;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final MapCodec<DisposalUnitBlock> CODEC = MapCodec.unit(DisposalUnitBlock::new);
    public static final BooleanProperty ANCHORED = BooleanProperty.create("anchored");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final EnumProperty<VisualState> VISUAL_STATE = EnumProperty.create("visual_state", VisualState.class);
    public DisposalUnitBlock() {
        super(BlockBehaviour.Properties.of().strength(3f).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(ANCHORED, true).setValue(POWERED, false).setValue(VISUAL_STATE, VisualState.UNPOWERED));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ANCHORED, POWERED, VISUAL_STATE);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.DISPOSAL_UNIT.get().create(pos, state);
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.DISPOSAL_UNIT.get()) return null;
        return (tickLevel, pos, tickState, entity) -> DisposalUnitBlockEntity.serverTick((net.minecraft.server.level.ServerLevel) tickLevel, pos, tickState, (DisposalUnitBlockEntity) entity);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (state.getBlock() != next.getBlock()) {
            if (level.getBlockEntity(pos) instanceof DisposalUnitBlockEntity unit) {
                unit.handoffReservoirOnRemoval();
                unit.ejectAll(true);
            }
            if (level instanceof net.minecraft.server.level.ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.disposalUnitRemoved(server, pos);
            super.onRemove(state, level, pos, next, moved);
        }
    }
}
