package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances;
import com.juicyslew.moonstation14.ms14.power.device.PowerAppearanceData;
import com.juicyslew.moonstation14.ms14.power.ui.ApcMenuService;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/** Visible, static power device. This class only defines ports and APC interaction; no allocation. */
public class PowerDeviceBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty GLOW = BooleanProperty.create("glow");
    public static final IntegerProperty EMISSION = IntegerProperty.create("emission", 0, 15);
    public enum Display implements net.minecraft.util.StringRepresentable {
        OFF, ON, FIXED_ON, UNPOWERED, DEAD, CHARGING, FULL, LACK;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Display> DISPLAY = EnumProperty.create("display", Display.class);
    private final PowerDeviceKind kind;
    // Export bounds from Structures/Power/APC/model_spec.json converted from
    // ground-centered model coordinates (+8 X/Z): x=4..12, y=4.5..11.5,
    // z=12.7..16. Facing NORTH means the front is -Z and the mounting back is +Z.
    private static final VoxelShape APC_NORTH_SHAPE = box(4, 4.5, 12.7, 12, 11.5, 16);

    public PowerDeviceBlock(PowerDeviceKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        Display initial = switch (kind) {
            case HV_SOURCE -> Display.UNPOWERED;
            case HV_MV_SUBSTATION -> Display.DEAD;
            case APC -> Display.LACK;
            default -> Display.OFF;
        };
        BlockState initialState = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false)
                .setValue(GLOW, false).setValue(DISPLAY, initial);
        if (initialState.hasProperty(EMISSION)) initialState = initialState.setValue(EMISSION, 0);
        registerDefaultState(initialState);
    }

    public PowerDeviceKind kind() { return kind; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, GLOW, DISPLAY);
    }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = kind == PowerDeviceKind.APC ? context.getClickedFace()
                : context.getHorizontalDirection().getOpposite();
        if (kind == PowerDeviceKind.APC) {
            if (!facing.getAxis().isHorizontal()) return null;
            BlockPos supportPos = context.getClickedPos().relative(facing.getOpposite());
            BlockState support = context.getLevel().getBlockState(supportPos);
            if (!support.isFaceSturdy(context.getLevel(), supportPos, facing)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        if (kind != PowerDeviceKind.APC) return super.canSurvive(state, level, pos);
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (kind != PowerDeviceKind.APC) return super.getShape(state, level, pos, context);
        var entity = level.getBlockEntity(pos);
        if (entity != null && entity.getLevel() != null) {
            PowerAppearanceData profile = ModPowerAppearances.catalog(entity.getLevel()).get(ModPowerAppearances.APC);
            if (profile != null) return oriented(profile.shape(), state.getValue(FACING));
        }
        return switch (state.getValue(FACING)) {
            case NORTH -> APC_NORTH_SHAPE;
            case SOUTH -> box(4, 4.5, 0, 12, 11.5, 3.3);
            case EAST -> box(0, 4.5, 4, 3.3, 11.5, 12);
            case WEST -> box(12.7, 4.5, 4, 16, 11.5, 12);
            default -> APC_NORTH_SHAPE;
        };
    }

    private static VoxelShape oriented(PowerAppearanceData.Shape s, Direction facing) {
        return switch (facing) {
            case NORTH -> box(s.minX(), s.minY(), s.minZ(), s.maxX(), s.maxY(), s.maxZ());
            case SOUTH -> box(16-s.maxX(), s.minY(), 16-s.maxZ(), 16-s.minX(), s.maxY(), 16-s.minZ());
            case EAST -> box(16-s.maxZ(), s.minY(), s.minX(), 16-s.minZ(), s.maxY(), s.maxX());
            case WEST -> box(s.minZ(), s.minY(), 16-s.maxX(), s.maxZ(), s.maxY(), 16-s.minX());
            default -> APC_NORTH_SHAPE;
        };
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getShape(state, level, pos, CollisionContext.empty());
    }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                             net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (kind == PowerDeviceKind.APC && direction == state.getValue(FACING).getOpposite()
                && !neighborState.isFaceSturdy(level, neighborPos, state.getValue(FACING)))
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof PowerDeviceBlockEntity device)) return InteractionResult.FAIL;
        return ApcMenuService.open(serverPlayer, pos, device) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
}

