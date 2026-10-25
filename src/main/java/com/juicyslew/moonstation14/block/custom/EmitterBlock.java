package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class EmitterBlock extends Block implements EntityBlock, com.juicyslew.moonstation14.ms14.singularity.SingularityConsumable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public enum Visual implements StringRepresentable { OFF("default"), ON("on"), UNDERPOWERED("underpowered"); private final String id; Visual(String id){this.id=id;} @Override public String getSerializedName(){return id;} }
    public static final EnumProperty<Visual> VISUAL=EnumProperty.create("visual",Visual.class);
    public EmitterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(VISUAL,Visual.OFF));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING,VISUAL);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        if (!state.getValue(FACING).getAxis().isHorizontal()) return state;
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        if (!state.getValue(FACING).getAxis().isHorizontal()) return state;
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.EMITTER.get().create(pos, state);
    }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level instanceof net.minecraft.server.level.ServerLevel && type == ModBlockEntities.EMITTER.get()
                ? (tickLevel, pos, blockState, entity) -> com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity
                        .serverTick((net.minecraft.server.level.ServerLevel) tickLevel, pos, blockState,
                                (com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity) entity) : null;
    }
    @Override protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity emitter) {
            emitter.toggleRequestedOn();
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity emitter) {
            emitter.toggleRequestedOn();
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return net.minecraft.world.InteractionResult.PASS;
    }
}

