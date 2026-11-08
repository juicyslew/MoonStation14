package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

/** Static Minecraft tray geometry; it does not claim to reproduce SS14 fixtures or rendering. */
public final class HydroponicsTrayBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 5, 15);
    private final String profile;
    public HydroponicsTrayBlock(String profile, Properties properties) { super(properties); this.profile = profile; }
    public String profile() { return profile; }
    @Override public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return ModBlockEntities.HYDROPONICS_TRAY.get().create(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.HYDROPONICS_TRAY.get()) return null;
        return (world, pos, blockState, entity) -> { if (entity instanceof HydroponicsTrayBlockEntity tray) HydroponicsTrayBlockEntity.tick(world, pos, blockState, tray); };
    }
}
