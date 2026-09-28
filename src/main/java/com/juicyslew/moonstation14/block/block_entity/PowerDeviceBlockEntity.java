package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;

import java.util.List;

/** Device-owned APC battery and breaker. Energy is uniquely BE-owned, so vanilla BE NBT is authoritative. */
public class PowerDeviceBlockEntity extends BlockEntity {
    private double energyJoules = PowerDeviceRules.APC_INITIAL_JOULES;
    private boolean breakerClosed = true;

    public PowerDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_DEVICE.get(), pos, state);
    }

    public List<DevicePort> ports() {
        if (!(getBlockState().getBlock() instanceof PowerDeviceBlock block)) return List.of();
        return block.kind().ports(worldPosition, getBlockState().getValue(PowerDeviceBlock.FACING));
    }

    public double energyJoules() { return energyJoules; }
    public boolean breakerClosed() { return breakerClosed; }

    /** Server-only interaction; player permissions and range are checked before changing state. */
    public boolean toggleBreaker(Player player, double distanceSquared) {
        if (level == null || level.isClientSide || !isApc()
                || !PowerDeviceRules.canToggle(true, player != null && player.mayBuild(), distanceSquared)) return false;
        breakerClosed = !breakerClosed;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    public void setEnergyJoules(double joules) {
        if (!isApc()) return;
        energyJoules = PowerDeviceRules.validatedEnergy(joules);
        setChanged();
    }

    private boolean isApc() {
        return getBlockState().getBlock() instanceof PowerDeviceBlock block && block.kind() == PowerDeviceKind.APC;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (isApc()) {
            tag.merge(PowerDeviceRules.saveApc(energyJoules, breakerClosed));
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (isApc()) {
            var data = PowerDeviceRules.loadApc(tag);
            energyJoules = data.energyJoules();
            breakerClosed = data.breakerClosed();
        }
    }
}
