package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;
import javax.annotation.Nullable;

/** Static, anchored local adaptation of WeaponCapacitorRecharger. */
public final class WeaponCapacitorRechargerBlock extends Block implements EntityBlock {
    public enum Visual implements StringRepresentable { OFF_EMPTY, OFF_FULL, EMPTY, CHARGING, CHARGED;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Visual> VISUAL = EnumProperty.create("visual", Visual.class);
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 8, 14);
    public static Visual projectVisual(boolean powered, boolean occupied, float charge, float capacity) {
        if (!powered) return occupied ? Visual.OFF_FULL : Visual.OFF_EMPTY;
        if (!occupied) return Visual.EMPTY;
        return charge >= capacity ? Visual.CHARGED : Visual.CHARGING;
    }
    public WeaponCapacitorRechargerBlock(Properties properties) { super(properties.sound(SoundType.METAL)); registerDefaultState(stateDefinition.any().setValue(VISUAL, Visual.OFF_EMPTY)); }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) { builder.add(VISUAL); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.WEAPON_CAPACITOR_RECHARGER.get().create(pos, state);
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.WEAPON_CAPACITOR_RECHARGER.get()
                ? (world, pos, blockState, entity) -> { if (entity instanceof WeaponCapacitorRechargerBlockEntity charger) charger.tick(); }
                : null;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean movedByPiston) {
        if (state.getBlock() != next.getBlock()) {
            if (level.getBlockEntity(pos) instanceof WeaponCapacitorRechargerBlockEntity charger) charger.dropInstalledItems();
            if (level instanceof net.minecraft.server.level.ServerLevel server)
                com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineRemoved(server, pos);
        }
        super.onRemove(state, level, pos, next, movedByPiston);
    }
}
