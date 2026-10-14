package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class RadiationCollectorBlock extends BaseEntityBlock {
    public RadiationCollectorBlock() { super(BlockBehaviour.Properties.of().strength(3f).noOcclusion()); }
    @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(RadiationCollectorBlock::new);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        var drops = super.getDrops(state, params);
        BlockEntity blockEntity = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof RadiationCollectorBlockEntity collector) for (var drop : drops)
            if (drop.is(this.asItem())) drop.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                    net.minecraft.world.item.component.CustomData.of(collector.saveForItem(params.getLevel().registryAccess())));
        return drops;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RadiationCollectorBlockEntity collector)
            collector.permanentlyRemove();
        super.onRemove(state, level, pos, newState, moving);
    }
    @Override public net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RadiationCollectorBlockEntity collector) {
            collector.toggleEnabled();
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return ModBlockEntities.RADIATION_COLLECTOR.get().create(pos, state); }
    @Nullable @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlockEntities.RADIATION_COLLECTOR.get() ? null : (l, p, s, be) -> {
            if (l instanceof ServerLevel server && be instanceof RadiationCollectorBlockEntity collector)
                RadiationCollectorBlockEntity.serverTick(server, p, s, collector);
        };
    }
}

