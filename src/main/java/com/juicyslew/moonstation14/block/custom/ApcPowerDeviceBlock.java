package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** APC-only native emission state; all placement, ports, and lifecycle behavior stays in PowerDeviceBlock. */
public final class ApcPowerDeviceBlock extends PowerDeviceBlock {
    public ApcPowerDeviceBlock(Properties properties) {
        super(PowerDeviceKind.APC, properties);
        registerDefaultState(defaultBlockState().setValue(EMISSION, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(EMISSION);
    }
}
