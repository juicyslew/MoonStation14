package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Server-owned bounded view of discovered devices and persistent offline links. */
public record AirAlarmControlSnapshot(int containerId, UUID session, long requestId, boolean accepted,
                                      boolean powered, List<Device> devices) implements CustomPacketPayload {
    public record Device(UUID id, VentilationDeviceConfig.Kind kind, BlockPos pos, boolean linked,
                         boolean online, boolean enabled, int mode, int filterBits, long revision,
                         double externalPressureBoundKPa, double volumeRateM3PerSecond) {
        public Device(UUID id, VentilationDeviceConfig.Kind kind, BlockPos pos, boolean linked, boolean online,
                      boolean enabled, int mode, int filterBits, long revision) {
            this(id, kind, pos, linked, online, enabled, mode, filterBits, revision, 0, 0);
        }
        public Device { pos = pos.immutable(); }
    }
    public static final Type<AirAlarmControlSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "air_alarm_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirAlarmControlSnapshot> STREAM_CODEC = StreamCodec.of(
            (b, v) -> {
                b.writeVarInt(v.containerId); b.writeUUID(v.session); b.writeVarLong(v.requestId);
                b.writeBoolean(v.accepted); b.writeBoolean(v.powered); b.writeVarInt(v.devices.size());
                for (Device d : v.devices) {
                    b.writeUUID(d.id); b.writeEnum(d.kind); b.writeBlockPos(d.pos); b.writeBoolean(d.linked);
                    b.writeBoolean(d.online); b.writeBoolean(d.enabled); b.writeVarInt(d.mode);
                    b.writeVarInt(d.filterBits); b.writeVarLong(d.revision); b.writeDouble(d.externalPressureBoundKPa);
                    b.writeDouble(d.volumeRateM3PerSecond);
                }
            }, b -> {
                int container = b.readVarInt(); UUID session = b.readUUID(); long id = b.readVarLong();
                boolean accepted = b.readBoolean(), powered = b.readBoolean();
                int size = b.readVarInt();
                if (size < 0 || size > 32) throw new IllegalArgumentException("Air alarm candidate count");
                var list = new java.util.ArrayList<Device>(size);
                for (int i = 0; i < size; i++) {
                    UUID device = b.readUUID(); var kind = b.readEnum(VentilationDeviceConfig.Kind.class);
                    BlockPos pos = b.readBlockPos(); boolean linked = b.readBoolean(), online = b.readBoolean();
                    boolean enabled = b.readBoolean(); int mode = b.readVarInt(), filters = b.readVarInt();
                    long revision = b.readVarLong(); double pressure = b.readDouble(), rate = b.readDouble();
                    if (mode < 0 || mode >= VentilationDeviceConfig.ScrubberMode.values().length
                            || filters < 0 || (filters >>> GasType.values().length) != 0 || revision < 0
                            || !Double.isFinite(pressure) || pressure < 0 || pressure > 4500
                            || !Double.isFinite(rate) || rate < 0 || rate > 0.2)
                        throw new IllegalArgumentException("Air alarm device state");
                    list.add(new Device(device, kind, pos, linked, online, enabled, mode, filters, revision, pressure, rate));
                }
                return new AirAlarmControlSnapshot(container, session, id, accepted, powered, list);
            });
    public AirAlarmControlSnapshot {
        if (containerId < 0 || session == null || requestId < 0 || devices == null || devices.size() > 32)
            throw new IllegalArgumentException("Invalid air alarm snapshot");
        devices = List.copyOf(devices);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
