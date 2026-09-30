package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.custom.AtmosphereTestDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.device.AtmosphereDeviceRules;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AtmosphereTestDeviceBlockEntity extends BlockEntity {
    public AtmosphereTestDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ATMOSPHERE_TEST_DEVICE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AtmosphereTestDeviceBlockEntity entity) {
        if (!(level instanceof ServerLevel server) || !AtmosphereService.INSTANCE.isEnabled()) return;
        if (!(state.getBlock() instanceof AtmosphereTestDeviceBlock block)) return;
        if (entity.isRemoved() || entity.getLevel() != server || !entity.getBlockPos().equals(pos)) return;
        long gameTime = server.getGameTime();
        if (!AtmosphereDeviceRules.isDue(gameTime)) return;
        var target = AtmosphereDeviceRules.target(pos, state.getValue(AtmosphereTestDeviceBlock.FACING));
        AtmosphereService service = AtmosphereService.INSTANCE;
        AtmosphereDeviceRules.Receiver receiver = new AtmosphereDeviceRules.Receiver() {
            @Override public java.util.Optional<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> sample(BlockPos targetPos) { return service.sample(server, targetPos); }
            @Override public boolean addBreathableAir(BlockPos targetPos, double moles, double temperature) { return service.addBreathableAir(server, targetPos, moles, temperature); }
            @Override public boolean addGas(BlockPos targetPos, com.juicyslew.moonstation14.ms14.atmos.core.GasType gas, double moles, double temperature) { return service.addGas(server, targetPos, gas, moles, temperature); }
            @Override public double removeGas(BlockPos targetPos, double moles) { return service.removeGasUpTo(server, targetPos, moles); }
            @Override public boolean addEnergy(BlockPos targetPos, double joules) { return service.addEnergy(server, targetPos, joules); }
            @Override public boolean addHeaterEnergy(BlockPos targetPos, double joules) { return service.addHeaterEnergy(server, targetPos, joules); }
        };
        AtmosphereDeviceRules.tick(true, gameTime, target, block.device(), block.pureGas(), receiver);
    }
}
