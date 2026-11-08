package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import com.mojang.serialization.MapCodec;

/** Placeholder PA hardware. Its placed facing is authoritative; assembly/profile logic lives elsewhere. */
public final class ParticleAcceleratorPartBlock extends HorizontalDirectionalBlock implements EntityBlock, com.juicyslew.moonstation14.ms14.singularity.SingularityConsumable {
    public static final MapCodec<ParticleAcceleratorPartBlock> CODEC = MapCodec.unit(() -> new ParticleAcceleratorPartBlock("control", "particle_accelerator_control", true, Properties.of()));
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public enum Visual implements StringRepresentable {
        DEFAULT("default"), COMPLETED("completed"), UNLITP("unlitp"), UNLITP0("unlitp0"), UNLITP1("unlitp1"), UNLITP2("unlitp2"), UNLITP3("unlitp3");
        private final String name; Visual(String name) { this.name=name; }
        @Override public String getSerializedName() { return name; }
    }
    public static final EnumProperty<Visual> VISUAL = EnumProperty.create("visual", Visual.class);
    private final String role;
    private final String prototype;
    private final boolean control;

    public ParticleAcceleratorPartBlock(String role, String prototype, boolean control, Properties properties) {
        super(properties);
        this.role = role;
        this.prototype = prototype;
        this.control = control;
        registerDefaultState(stateDefinition.any().setValue(FACING, control ? Direction.SOUTH : Direction.WEST).setValue(VISUAL, Visual.COMPLETED));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    public String role() { return role; }
    public String prototype() { return prototype; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(FACING, VISUAL); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, control ? Direction.SOUTH : Direction.WEST);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.PARTICLE_ACCELERATOR_PART.get().create(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.PARTICLE_ACCELERATOR_PART.get()) return null;
        return (world,pos,blockState,entity) -> { if (world instanceof ServerLevel server && entity instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity pa) com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity.serverTick(server,pos,blockState,pa); };
    }

    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("PA requires horizontal facing");
        };
    }
}

