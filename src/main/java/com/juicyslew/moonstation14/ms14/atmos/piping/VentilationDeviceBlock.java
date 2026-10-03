package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;

import javax.annotation.Nullable;

/** Static, inactive SS14 ventilation-device prototype. Deliberately has no BlockItem. */
public final class VentilationDeviceBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
    private final VentilationDeviceConfig.Kind kind;

    public VentilationDeviceBlock(VentilationDeviceConfig.Kind kind, Properties properties) {
        super(properties);
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public VentilationDeviceConfig.Kind kind() { return kind; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Level world;
        if (level instanceof Level direct) world = direct;
        else if (level.getBlockEntity(pos) instanceof VentilationDeviceBlockEntity entity && entity.getLevel() != null)
            world = entity.getLevel();
        else return Shapes.empty();
        var id = kind == VentilationDeviceConfig.Kind.VENT ? ModVentilationDevices.VENT : ModVentilationDevices.SCRUBBER;
        var definition = ModVentilationDevices.catalog(world).get(id);
        if (definition == null) return Shapes.empty();
        var geometry = definition.fixtureGeometry();
        double inset = (1.0 - geometry.width()) / 2.0;
        double h0 = (1.0 - geometry.height()) / 2.0;
        double h1 = h0 + geometry.height();
        double d = geometry.depth();
        return switch (state.getValue(FACING)) {
            case NORTH -> fixtureBox(inset, h0, 1 - d, 1 - inset, h1, 1);
            case SOUTH -> fixtureBox(inset, h0, 0, 1 - inset, h1, d);
            case EAST -> fixtureBox(0, h0, inset, d, h1, 1 - inset);
            case WEST -> fixtureBox(1 - d, h0, inset, 1, h1, 1 - inset);
            case UP -> fixtureBox(inset, 0, h0, 1 - inset, d, h1);
            case DOWN -> fixtureBox(inset, 1 - d, h0, 1 - inset, 1, h1);
        };
    }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var id = kind == VentilationDeviceConfig.Kind.VENT ? ModVentilationDevices.VENT : ModVentilationDevices.SCRUBBER;
        Level world = level instanceof Level direct ? direct
                : level.getBlockEntity(pos) instanceof VentilationDeviceBlockEntity entity ? entity.getLevel() : null;
        if (world == null) return Shapes.empty();
        var definition = ModVentilationDevices.catalog(world).get(id);
        return definition != null && definition.fixtureGeometry().nonColliding() ? Shapes.empty()
                : getShape(state, level, pos, context);
    }
    private static VoxelShape fixtureBox(double x0, double y0, double z0, double x1, double y1, double z1) {
        return net.minecraft.world.phys.shapes.Shapes.box(x0, y0, z0, x1, y1, z1);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.VENTILATION_DEVICE.get().create(pos, state);
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server)
            PowerRuntime.ventilationDeviceChanged(server, pos);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server)
            PowerRuntime.ventilationDeviceRemoved(server, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
