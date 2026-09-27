package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.block_entity.AtmosphereTestDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.device.AtmosphereDeviceRules;
import com.juicyslew.moonstation14.ms14.atmos.device.AtmosphereSampleFormatter;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

public class AtmosphereTestDeviceBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private final AtmosphereDeviceRules.Device device;
    private final GasType pureGas;

    public AtmosphereTestDeviceBlock(AtmosphereDeviceRules.Device device, Properties properties) {
        this(device, null, properties);
    }

    public AtmosphereTestDeviceBlock(AtmosphereDeviceRules.Device device, @Nullable GasType pureGas, Properties properties) {
        super(properties);
        this.device = device;
        this.pureGas = pureGas;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public AtmosphereDeviceRules.Device device() { return device; }
    @Nullable public GasType pureGas() { return pureGas; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        if (!AtmosphereService.INSTANCE.isEnabled()) {
            player.sendSystemMessage(Component.literal("Atmospherics disabled"));
            return InteractionResult.SUCCESS;
        }

        BlockPos target = pos.relative(state.getValue(FACING));
        var sample = AtmosphereService.INSTANCE.readAtmosphere(serverLevel, target);
        if (sample.isEmpty()) {
            player.sendSystemMessage(Component.literal("No gas cell on the clicked side"));
        } else {
            player.sendSystemMessage(Component.literal(AtmosphereSampleFormatter.format(sample.get())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.ATMOSPHERE_TEST_DEVICE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.ATMOSPHERE_TEST_DEVICE.get()) return null;
        return (world, pos, blockState, blockEntity) -> {
            if (world instanceof ServerLevel server && blockEntity instanceof AtmosphereTestDeviceBlockEntity deviceEntity)
                AtmosphereTestDeviceBlockEntity.tick(server, pos, blockState, deviceEntity);
        };
    }
}
