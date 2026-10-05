package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;


/** Persisted configuration prototype only: no ticking, gas ownership, or gas transfer. */
public final class VentilationDeviceBlockEntity extends BlockEntity {
    private final VentilationDeviceConfig config;
    private boolean settingsValid = true;
    private UUID deviceId;

    public VentilationDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENTILATION_DEVICE.get(), pos, state);
        config = new VentilationDeviceConfig(kind(), null);
    }

    public VentilationDeviceConfig.Kind kind() {
        if (!(getBlockState().getBlock() instanceof VentilationDeviceBlock block))
            throw new IllegalStateException("Ventilation entity has incompatible block");
        return block.kind();
    }

    /** Server-owned identity of this exact live block entity, retained across chunk save/reload. */
    public UUID deviceId() {
        if (deviceId == null && level instanceof ServerLevel server && !isRemoved()
                && server.getServer().isSameThread()) {
            // No chunk request, and do not generate for an unattached, pending-NBT or replaced entity.
            var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
            if (chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                    && chunk.getBlockEntities().get(worldPosition) == this
                    && chunk.getBlockState(worldPosition).getBlock() instanceof VentilationDeviceBlock) {
                deviceId = UUID.randomUUID();
                setChanged();
            }
        }
        return deviceId;
    }

    @Override public void onLoad() {
        super.onLoad();
        deviceId();
    }

    /** Null until the matching concrete prototype is published and valid. */
    public VentilationDeviceData prototype() {
        if (level == null || !(getBlockState().getBlock() instanceof VentilationDeviceBlock block)
                || block.kind() != config.kind()) return null;
        VentilationDeviceData data = (level.isClientSide ? PrototypeRuntime.clientVentilationDevices()
                : PrototypeRuntime.serverVentilationDevices()).get(block.kind() == VentilationDeviceConfig.Kind.VENT
                ? ModVentilationDevices.VENT : ModVentilationDevices.SCRUBBER);
        if (data == null || data.kind() != VentilationDeviceData.Kind.valueOf(block.kind().name())) return null;
        try {
            data.validate();
            if (data.apcPowerReceiver().requestedWatts() != Ss14VentilationDefaults.APC_REQUESTED_POWER_WATTS)
                return null;
        } catch (RuntimeException invalid) { return null; }
        return data;
    }

    public VentilationDeviceConfig config() {
        config.bindPrototype(prototype());
        return config;
    }
    public VentilationDeviceConfig.Layer selectedLayer() { return config().settings().selectedLayer(); }
    /** Applies a revisioned configuration update only on the authoritative server. */
    public boolean compareAndSetSettings(long expectedRevision, VentilationDeviceConfig.Settings proposed) {
        if (level != null && level.isClientSide) return false;
        if (prototype() == null || !settingsValid()) return false;
        boolean changed = config().compareAndSet(expectedRevision, proposed);
        if (changed) setChanged();
        return changed;
    }
    public boolean settingsValid() { return settingsValid && prototype() != null && config().settingsValid(); }

    /** Sole gas-network attachment, one block behind the device front. */
    public BlockPos pipePortPos() { return worldPosition.relative(getBlockState().getValue(VentilationDeviceBlock.FACING).getOpposite()); }
    /** Room-side orientation; exposure/loaded checks belong to the eventual controlled runtime. */
    public Direction roomFacePos() { return getBlockState().getValue(VentilationDeviceBlock.FACING); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("VentilationSettings", config().save());
        if (deviceId != null) tag.putUUID("VentilationDeviceId", deviceId);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deviceId = tag.hasUUID("VentilationDeviceId") ? tag.getUUID("VentilationDeviceId") : null;
        // A missing or malformed identity receives a fresh server identity on load; old links cannot match.
        if (!tag.contains("VentilationSettings", CompoundTag.TAG_COMPOUND)) {
            config.bindPrototype(prototype());
            settingsValid = true;
            config.restore(new CompoundTag());
            return;
        }
        config.bindPrototype(prototype());
        settingsValid = config.restore(tag.getCompound("VentilationSettings"));
        if (!settingsValid) System.err.println("Invalid persisted ventilation settings at " + worldPosition + "; device disabled");
    }
}
