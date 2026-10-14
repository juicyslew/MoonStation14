package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import com.juicyslew.moonstation14.ms14.singularity.containment.ModFieldGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class FieldGeneratorBlock extends Block implements EntityBlock {
    // Accepted model's metal foot is x/z 3..13 and y 0..2 model units.
    private static final VoxelShape METAL_FOOT = Block.box(3, 0, 3, 13, 2, 13);
    private static final VoxelShape METAL_BODY = Shapes.or(METAL_FOOT, Block.box(6, 2, 6, 10, 20, 10));
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 3);
    public static final IntegerProperty FIELD = IntegerProperty.create("field", 0, 3);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public FieldGeneratorBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(POWER,0).setValue(FIELD,0).setValue(ACTIVE,false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(POWER,FIELD,ACTIVE); }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof FieldGeneratorBlockEntity entity)) return InteractionResult.PASS;
        boolean changed=entity.toggleEnabled();
        if (!changed) player.displayClientMessage(entity.toggleFeedback(false), true);
        return InteractionResult.SUCCESS;
    }

    // Selection outline follows the visible assembly (including the upper active lobes);
    // entity/projectile collision remains limited to the accepted metal foot and column.
    private static final VoxelShape INTERACTION_OUTLINE = Block.box(0, 0, 0, 16, 20, 16);
    @Override public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                         BlockPos pos, CollisionContext context) { return INTERACTION_OUTLINE; }
    @Override public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                   BlockPos pos, CollisionContext context) { return METAL_BODY; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.FIELD_GENERATOR.get().create(pos, state);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.FIELD_GENERATOR.get()) return null;
        return (world, pos, blockState, entity) -> {
            if (world instanceof ServerLevel server && entity instanceof FieldGeneratorBlockEntity generator)
                FieldGeneratorBlockEntity.tick(server, generator);
        };
    }
}

