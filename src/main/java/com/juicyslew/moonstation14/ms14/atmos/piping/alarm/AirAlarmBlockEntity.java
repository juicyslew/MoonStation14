package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.AirAlarmData;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Resolved wall composition and indexed APC receiver; no device-network simulation. */
public final class AirAlarmBlockEntity extends BlockEntity {
    private final AirAlarmDeviceLinks deviceLinks = new AirAlarmDeviceLinks();
    public AirAlarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_ALARM.get(), pos, state);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) PowerRuntime.airAlarmChanged(server, worldPosition);
    }

    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) PowerRuntime.airAlarmRemoved(server, worldPosition);
        super.setRemoved();
    }

    public java.util.List<AirAlarmDeviceLinks.Link> deviceLinks() { return deviceLinks.snapshot(); }
    public boolean deviceLinksValid() { return deviceLinks.valid(); }

    /** Server-only explicit association; admission requires the resolved concrete DeviceList limit. */
    public boolean addDeviceLink(VentilationDeviceBlockEntity device) {
        AirAlarmData data = prototype();
        if (!(level instanceof ServerLevel server) || data == null || isRemoved()
                || !server.getServer().isSameThread()
                || server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4) == null
                || server.getBlockEntity(worldPosition) != this
                || !deviceLinks.add(server, device, data.deviceList().maxDevices())) return false;
        setChanged();
        return true;
    }

    public boolean removeDeviceLink(java.util.UUID deviceId) {
        if (!(level instanceof ServerLevel server) || prototype() == null || isRemoved()
                || !server.getServer().isSameThread() || !deviceLinks.valid()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                || chunk.getBlockEntity(worldPosition) != this) return false;
        if (!deviceLinks.remove(deviceId)) return false;
        setChanged();
        return true;
    }

    public VentilationDeviceBlockEntity resolveDeviceLink(AirAlarmDeviceLinks.Link link) {
        if (!(level instanceof ServerLevel server) || prototype() == null || !deviceLinks.valid()
                || !deviceLinks.snapshot().contains(link)) return null;
        return AirAlarmDeviceLinks.resolve(server, link);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        deviceLinks.save(tag);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // The concrete prototype validates 32; refuse association if resolution later fails.
        deviceLinks.load(tag, 32);
    }

    /** Fail closed until the matching concrete alarm definition has been published. */
    public AirAlarmData prototype() {
        if (level == null || !(getBlockState().getBlock() instanceof AirAlarmBlock)) return null;
        AirAlarmData data = (level.isClientSide ? PrototypeRuntime.clientAirAlarms()
                : PrototypeRuntime.serverAirAlarms()).get(ModAirAlarms.AIR_ALARM);
        if (data == null) return null;
        try {
            data.validate(); // Includes inherited anchored/non-colliding wallmount and 100 W APC demand.
            return data;
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
