package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.entity.player.Player;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ExposedPipeVisualBlock extends Block implements EntityBlock {
    public static final BooleanProperty PRIMARY_CONNECTED = BooleanProperty.create("primary");
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final VoxelShape CENTER = box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape[] ARMS = {
            box(6, 6, 0, 10, 10, 6), box(6, 6, 10, 10, 10, 16),
            box(10, 6, 6, 16, 10, 10), box(0, 6, 6, 6, 10, 10),
            box(6, 10, 6, 10, 16, 10), box(6, 0, 6, 10, 6, 10)
    };
    private static final BooleanProperty[] PROPERTIES = {NORTH, SOUTH, EAST, WEST, UP, DOWN};

    public ExposedPipeVisualBlock(Properties properties) {
        super(properties.noOcclusion().isViewBlocking((state, level, pos) -> false));
        registerDefaultState(stateDefinition.any().setValue(PRIMARY_CONNECTED, true).setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VisualPipeBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PRIMARY_CONNECTED);
        builder.add(PROPERTIES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            BlockState connected = connections(level, pos, state);
            if (connected != state) level.setBlock(pos, connected, Block.UPDATE_ALL);
            if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof VisualPipeBlockEntity entity)
                entity.refreshConcealedJoins();
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Minecraft invokes this before replacing the block, while lane ownership and
        // its block entity are still valid. Only a proven finite room can receive gas.
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual) {
            for (int lane : new int[]{VisualPipeBlockEntity.PRIMARY,
                    VisualPipeBlockEntity.SECONDARY, VisualPipeBlockEntity.TERTIARY}) {
                var pipe = PipeGasInventoryEndpoint.create(server, pos, lane).orElse(null);
                if (pipe == null) continue;
                var mixture = pipe.snapshot();
                if (mixture == null || mixture.gasMoles().isEmpty()) continue;
                var room = AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(server, pos).orElse(null);
                if (room == null) continue;
                double moles = mixture.gasMoles().values().stream().mapToDouble(Double::doubleValue).sum();
                var plan = PipeGasTransaction.plan(pipe, room, Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3,
                        1.0, moles, Double.MAX_VALUE, EnumSet.allOf(GasType.class)).orElse(null);
                if (plan != null) PipeGasTransaction.commit(pipe, room, plan);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // A state update on the same visual is not destruction. Retire each lane while
        // its block entity identity is still available; chunk unloads do not call this.
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof VisualPipeBlockEntity entity) {
            for (int lane : new int[]{VisualPipeBlockEntity.PRIMARY, VisualPipeBlockEntity.SECONDARY,
                    VisualPipeBlockEntity.TERTIARY}) {
                if (entity.hasLayer(lane)) LoadedPipeGasOwner.retireRemovedLane(server, pos, lane,
                        entity.functionalIdentity(lane).orElse(null));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof VisualPipeBlockEntity entity)
            entity.refreshConcealedJoins();
        boolean connected = state.getValue(PRIMARY_CONNECTED) && neighborState.is(this)
                && neighborState.getValue(PRIMARY_CONNECTED);
        if (!connected && state.getValue(PRIMARY_CONNECTED) && level != null
                && neighborState.is(ModBlocks.STEEL_WALL_BLOCK.get())
                && level.getBlockEntity(pos) instanceof VisualPipeBlockEntity entity
                && entity.hasLayerArm(VisualPipeBlockEntity.PRIMARY, direction)) connected = true;
        return state.setValue(property(direction), connected);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Selection only: keep the pipe's rendered/collision geometry thin while giving
        // body-eye outline raycasts a reliable voxel-sized target.
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return layerShape(state, level, pos);
    }

    private static VoxelShape layerShape(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = state.getValue(PRIMARY_CONNECTED) ? shape(state) : Shapes.empty();
        if (level == null || pos == null || !(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity entity)) return shape;
        if (entity.hasLayer(VisualPipeBlockEntity.PRIMARY)) {
            for (Direction direction : Direction.values())
                if (entity.hasLayerArm(VisualPipeBlockEntity.PRIMARY, direction))
                    shape = Shapes.or(shape, primaryArm(direction));
        }
        int mask = entity.getLayerMask();
        if ((mask & VisualPipeBlockEntity.SECONDARY) != 0) shape = Shapes.or(shape, extraLayer(level, pos, VisualPipeBlockEntity.SECONDARY));
        if ((mask & VisualPipeBlockEntity.TERTIARY) != 0) shape = Shapes.or(shape, extraLayer(level, pos, VisualPipeBlockEntity.TERTIARY));
        return shape;
    }

    private static VoxelShape extraLayer(BlockGetter level, BlockPos pos, int layer) {
        int low = layer == VisualPipeBlockEntity.TERTIARY ? 12 : 2;
        int high = low + 2;
        VoxelShape shape = box(low, low, low, high, high, high);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level instanceof LevelReader reader && reader.hasChunkAt(neighbor)
                    && level.getBlockEntity(pos) instanceof VisualPipeBlockEntity entity
                    && entity.hasLayerArm(layer, direction)) {
                shape = Shapes.or(shape, extraArm(direction, low, high));
            }
        }
        return shape;
    }

    private static VoxelShape extraArm(Direction direction, int low, int high) {
        return switch (direction) {
            case NORTH -> box(low, low, 0, high, high, low);
            case SOUTH -> box(low, low, high, high, high, 16);
            case EAST -> box(high, low, low, 16, high, high);
            case WEST -> box(0, low, low, low, high, high);
            case UP -> box(low, high, low, high, 16, high);
            case DOWN -> box(low, 0, low, high, low, high);
        };
    }

    private static VoxelShape primaryArm(Direction direction) {
        return switch (direction) {
            case NORTH -> box(6, 6, 0, 10, 10, 6);
            case SOUTH -> box(6, 6, 10, 10, 10, 16);
            case EAST -> box(10, 6, 6, 16, 10, 10);
            case WEST -> box(0, 6, 6, 6, 10, 10);
            case UP -> box(6, 10, 6, 10, 16, 10);
            case DOWN -> box(6, 0, 6, 10, 6, 10);
        };
    }

    private static VoxelShape shape(BlockState state) {
        VoxelShape shape = CENTER;
        for (int i = 0; i < PROPERTIES.length; i++) {
            if (state.getValue(PROPERTIES[i])) shape = Shapes.or(shape, ARMS[i]);
        }
        return shape;
    }

    private static BlockState connections(LevelReader level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            boolean connected = level.hasChunkAt(neighbor) && level.getBlockState(neighbor).is(state.getBlock())
                    && state.getValue(PRIMARY_CONNECTED)
                    && level.getBlockState(neighbor).getValue(PRIMARY_CONNECTED);
            state = state.setValue(property(direction), connected);
        }
        return state;
    }

    static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }
}

