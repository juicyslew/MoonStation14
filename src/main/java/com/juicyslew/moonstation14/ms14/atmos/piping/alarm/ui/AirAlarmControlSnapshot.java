package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Server-owned bounded view of linked devices, reports, and controller state. */
public record AirAlarmControlSnapshot(int containerId, UUID session, long requestId, boolean accepted,
        boolean powered, AtmosAlarmSeverity aggregateSeverity, AirAlarmMode currentMode, boolean autoMode,
        long controllerStateRevision, List<Device> devices, List<MonitorReading> readings) implements CustomPacketPayload {
    public record Device(UUID id, VentilationDeviceConfig.Kind kind, BlockPos pos, boolean linked,
            boolean online, boolean enabled, int mode, int filterBits, long revision,
            double externalPressureBoundKPa, double volumeRateM3PerSecond) {
        public Device(UUID id, VentilationDeviceConfig.Kind kind, BlockPos pos, boolean linked, boolean online,
                      boolean enabled, int mode, int filterBits, long revision) {
            this(id, kind, pos, linked, online, enabled, mode, filterBits, revision, 0, 0);
        }
        public Device { pos = pos.immutable(); }
    }
    /** A source reading is linked by canonical source UUID; valid=false means unavailable, never safe. */
    public record MonitorReading(UUID sourceId, String canonicalAddress, boolean online, boolean valid, boolean stale,
            double pressureKpa, double temperatureKelvin, double totalMoles,
            List<Double> gasMoles) {
        public MonitorReading {
            Objects.requireNonNull(sourceId); Objects.requireNonNull(canonicalAddress);
            if (stale && (online || !valid)) throw new IllegalArgumentException("Stale cache must be a valid offline reading");
            gasMoles = List.copyOf(gasMoles);
            if (canonicalAddress.length() > 64 || gasMoles.size() != GasType.values().length)
                throw new IllegalArgumentException("Air alarm reading");
            if (valid) {
                if (!Double.isFinite(pressureKpa) || pressureKpa < 0 || !Double.isFinite(temperatureKelvin)
                        || !Double.isFinite(totalMoles) || totalMoles < 0) throw new IllegalArgumentException("Air alarm reading");
                for (double n : gasMoles) if (!Double.isFinite(n) || n < 0) throw new IllegalArgumentException("Air alarm gas amount");
            } else if (!Double.isNaN(pressureKpa) || !Double.isNaN(temperatureKelvin) || !Double.isNaN(totalMoles)
                    || gasMoles.stream().anyMatch(n -> !Double.isNaN(n))) throw new IllegalArgumentException("Unavailable reading must be explicitly unknown");
        }
    }
    public static final Type<AirAlarmControlSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "air_alarm_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirAlarmControlSnapshot> STREAM_CODEC = StreamCodec.of(
        (b,v)->{
            b.writeVarInt(v.containerId); b.writeUUID(v.session); b.writeVarLong(v.requestId); b.writeBoolean(v.accepted); b.writeBoolean(v.powered);
            b.writeEnum(v.aggregateSeverity); b.writeEnum(v.currentMode); b.writeBoolean(v.autoMode); b.writeVarLong(v.controllerStateRevision);
            b.writeVarInt(v.devices.size()); for(Device d:v.devices){b.writeUUID(d.id);b.writeEnum(d.kind);b.writeBlockPos(d.pos);b.writeBoolean(d.linked);b.writeBoolean(d.online);b.writeBoolean(d.enabled);b.writeVarInt(d.mode);b.writeVarInt(d.filterBits);b.writeVarLong(d.revision);b.writeDouble(d.externalPressureBoundKPa);b.writeDouble(d.volumeRateM3PerSecond);}
            b.writeVarInt(v.readings.size()); for(MonitorReading r:v.readings){b.writeUUID(r.sourceId);b.writeUtf(r.canonicalAddress,64);b.writeBoolean(r.online);b.writeBoolean(r.valid);b.writeBoolean(r.stale);b.writeDouble(r.pressureKpa);b.writeDouble(r.temperatureKelvin);b.writeDouble(r.totalMoles);for(double n:r.gasMoles)b.writeDouble(n);}
        }, b->{
            int c=b.readVarInt(); UUID s=b.readUUID(); long id=b.readVarLong(); boolean accepted=b.readBoolean(), powered=b.readBoolean();
            var severity=b.readEnum(AtmosAlarmSeverity.class); var mode=b.readEnum(AirAlarmMode.class); boolean auto=b.readBoolean(); long rev=b.readVarLong();
            int n=b.readVarInt(); if(n<0||n>32)throw new IllegalArgumentException("Air alarm device count"); var ds=new ArrayList<Device>(n);
            for(int i=0;i<n;i++){UUID uuid=b.readUUID();var kind=b.readEnum(VentilationDeviceConfig.Kind.class);var pos=b.readBlockPos();boolean linked=b.readBoolean(),online=b.readBoolean(),enabled=b.readBoolean();int m=b.readVarInt(),bits=b.readVarInt();long dr=b.readVarLong();double p=b.readDouble(),rate=b.readDouble();if(m<0||m>=VentilationDeviceConfig.ScrubberMode.values().length||bits<0||(bits>>>GasType.values().length)!=0||dr<0||!Double.isFinite(p)||p<0||p>4500||!Double.isFinite(rate)||rate<0||rate>0.2)throw new IllegalArgumentException("Air alarm device state");ds.add(new Device(uuid,kind,pos,linked,online,enabled,m,bits,dr,p,rate));}
            int rn=b.readVarInt();if(rn<0||rn>32)throw new IllegalArgumentException("Air alarm reading count");var rs=new ArrayList<MonitorReading>(rn);
            for(int i=0;i<rn;i++){UUID uuid=b.readUUID();String address=b.readUtf(64);boolean online=b.readBoolean(),valid=b.readBoolean(),stale=b.readBoolean();double p=b.readDouble(),t=b.readDouble(),total=b.readDouble();var gases=new ArrayList<Double>(GasType.values().length);for(int g=0;g<GasType.values().length;g++)gases.add(b.readDouble());rs.add(new MonitorReading(uuid,address,online,valid,stale,p,t,total,gases));}
            return new AirAlarmControlSnapshot(c,s,id,accepted,powered,severity,mode,auto,rev,ds,rs);
        });
    public AirAlarmControlSnapshot(int containerId, UUID session, long requestId, boolean accepted, boolean powered, List<Device> devices) {
        this(containerId,session,requestId,accepted,powered,AtmosAlarmSeverity.NORMAL,AirAlarmMode.FILTERING,true,0,devices,List.of());
    }
    public AirAlarmControlSnapshot {
        if(containerId<0||session==null||requestId<0||controllerStateRevision<0||aggregateSeverity==null||currentMode==null||devices==null||devices.size()>32||readings==null||readings.size()>32)throw new IllegalArgumentException("Invalid air alarm snapshot");
        devices=List.copyOf(devices);readings=List.copyOf(readings);
        var ids=new HashSet<UUID>(); for(var d:devices) if(!ids.add(d.id()))throw new IllegalArgumentException("Duplicate linked device");
        ids.clear(); for(var r:readings) if(!ids.add(r.sourceId()))throw new IllegalArgumentException("Duplicate monitor source");
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
