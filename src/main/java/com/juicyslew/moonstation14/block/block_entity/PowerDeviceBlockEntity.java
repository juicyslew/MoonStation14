package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;

import java.util.List;

/** Device-owned APC battery and breaker. Energy is uniquely BE-owned, so vanilla BE NBT is authoritative. */
public class PowerDeviceBlockEntity extends BlockEntity {
    private double energyJoules = PowerDeviceRules.APC_INITIAL_JOULES;
    private boolean breakerClosed = true;
    private long breakerRevision;
    private boolean tripLatched;
    private PowerDeviceRules.ApcProtectionState protectionState = PowerDeviceRules.ApcProtectionState.initial();

    public PowerDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_DEVICE.get(), pos, state);
    }

    public List<DevicePort> ports() {
        if (!(getBlockState().getBlock() instanceof PowerDeviceBlock block)) return List.of();
        return block.kind().ports(worldPosition, getBlockState().getValue(PowerDeviceBlock.FACING));
    }

    public double energyJoules() { return energyJoules; }
    public boolean breakerClosed() { return breakerClosed; }
    public long breakerRevision() { return breakerRevision; }
    public boolean tripLatched() { return tripLatched; }

    /** Server-only interaction; player permissions and range are checked before changing state. */
    public boolean toggleBreaker(Player player, double distanceSquared) {
        if (level == null || level.isClientSide || !isApc()
                || !PowerDeviceRules.canToggle(true, player != null && player.mayBuild(), distanceSquared)) return false;
        if (breakerRevision == Long.MAX_VALUE) return false;
        breakerClosed = !breakerClosed;
        if (breakerClosed) tripLatched = false;
        protectionState = PowerDeviceRules.resetApcProtection(protectionState);
        breakerRevision++;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        playBreakerSwitch();
        return true;
    }

    /**
     * Server-only protection input for the runtime's actual delivered-output meter.
     * The runtime should call once per loaded server tick, passing its latest solve's
     * meter/sample tick; samples expire after the 20-tick solve interval.
     */
    public void observeActualApcOutput(long serverTick, long sampleTick, boolean sampleKnown,
                                       double actualOutputWatts) {
        if (level == null || level.isClientSide || !isApc()) return;
        protectionState = PowerDeviceRules.observeApcOutput(protectionState, serverTick, sampleTick,
                sampleKnown, actualOutputWatts, breakerClosed, PowerDeviceRules.DEFAULT_APC_PROTECTION);
        if (protectionState.tripped() && breakerClosed && !tripLatched) {
            breakerClosed = false;
            tripLatched = true;
            protectionState = PowerDeviceRules.resetApcProtection(protectionState);
            incrementBreakerRevision();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            playBreakerSwitch();
        }
    }

    /** Called only by authoritative breaker transitions, never by load/NBT or energy solves. */
    private void playBreakerSwitch() {
        // null excludes nobody: the clicking player hears the same positional sound as nearby viewers.
        level.playSound(null, worldPosition, ModSounds.APC_SWITCH.get(), SoundSource.BLOCKS, 0.7943282F, 1.0F);
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
            tag.merge(PowerDeviceRules.saveApc(energyJoules, breakerClosed, tripLatched));
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (isApc()) {
            var data = PowerDeviceRules.loadApc(tag);
            energyJoules = data.energyJoules();
            breakerClosed = data.breakerClosed();
            tripLatched = data.tripLatched();
            protectionState = PowerDeviceRules.ApcProtectionState.initial();
        }
    }

    @Override public void onLoad() {
        super.onLoad();
        protectionState = PowerDeviceRules.ApcProtectionState.initial();
    }

    @Override public void setRemoved() {
        protectionState = PowerDeviceRules.ApcProtectionState.initial();
        super.setRemoved();
    }

    private void incrementBreakerRevision() {
        // Keep the UI convergence token changing even in the practically unreachable
        // overflow case; normal manual toggles retain their existing max-value guard.
        breakerRevision = breakerRevision == Long.MAX_VALUE ? 0 : breakerRevision + 1;
    }
}
