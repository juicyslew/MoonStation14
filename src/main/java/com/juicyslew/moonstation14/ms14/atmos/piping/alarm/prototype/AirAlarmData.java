package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;

/** Resolved definition, not a live alarm or a functioning device network. */
public record AirAlarmData(WallMount wallMount, ApcPowerReceiver apcPowerReceiver,
                           ExtensionCableReceiver extensionCableReceiver, DeviceList deviceList) {
    public record WallMount(boolean anchored, boolean nonColliding, boolean allowVerticalMounts) {
        static final Codec<WallMount> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("anchored").forGetter(WallMount::anchored),
                Codec.BOOL.fieldOf("nonColliding").forGetter(WallMount::nonColliding),
                Codec.BOOL.fieldOf("allowVerticalMounts").forGetter(WallMount::allowVerticalMounts)
        ).apply(i, WallMount::new));
    }

    public record ApcPowerReceiver(double requestedWatts) {
        static final Codec<ApcPowerReceiver> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("requestedWatts").forGetter(ApcPowerReceiver::requestedWatts)
        ).apply(i, ApcPowerReceiver::new));
    }

    /** Local limits: reach in blocks and maximum locally tracked devices. */
    public record ExtensionCableReceiver(int localReachBlocks) {
        static final Codec<ExtensionCableReceiver> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("localReachBlocks").forGetter(ExtensionCableReceiver::localReachBlocks)
        ).apply(i, ExtensionCableReceiver::new));
    }

    public record DeviceList(int maxDevices) {
        static final Codec<DeviceList> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("maxDevices").forGetter(DeviceList::maxDevices)
        ).apply(i, DeviceList::new));
    }

    private static final Codec<AirAlarmData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            WallMount.CODEC.fieldOf("wallMount").forGetter(AirAlarmData::wallMount),
            ApcPowerReceiver.CODEC.fieldOf("apcPowerReceiver").forGetter(AirAlarmData::apcPowerReceiver),
            ExtensionCableReceiver.CODEC.fieldOf("extensionCableReceiver").forGetter(AirAlarmData::extensionCableReceiver),
            DeviceList.CODEC.fieldOf("deviceList").forGetter(AirAlarmData::deviceList)
    ).apply(i, AirAlarmData::new));

    public static final Codec<AirAlarmData> CODEC = Codec.of(STRUCTURAL, new Decoder<>() {
        @Override public <T> DataResult<Pair<AirAlarmData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = input instanceof JsonElement element ? element : ops.convertTo(JsonOps.INSTANCE, input);
                audit(json);
                return STRUCTURAL.decode(ops, input).flatMap(pair -> {
                    try {
                        pair.getFirst().validate();
                        return DataResult.success(pair);
                    } catch (IllegalArgumentException exception) {
                        return DataResult.error(exception::getMessage);
                    }
                });
            } catch (RuntimeException exception) {
                return DataResult.error(() -> "invalid air alarm: " + exception.getMessage());
            }
        }
    });

    public void validate() {
        if (wallMount == null || !wallMount.anchored() || !wallMount.nonColliding())
            throw new IllegalArgumentException("air alarm requires anchored non-colliding wallmount geometry");
        if (apcPowerReceiver == null || apcPowerReceiver.requestedWatts() != 100.0)
            throw new IllegalArgumentException("air alarm requires inherited APC demand of 100 W");
        if (extensionCableReceiver == null || extensionCableReceiver.localReachBlocks() != 3)
            throw new IllegalArgumentException("air alarm requires local extension reach of 3 blocks");
        if (deviceList == null || deviceList.maxDevices() != 32)
            throw new IllegalArgumentException("air alarm local device list is bounded at 32");
    }

    private static void audit(JsonElement json) {
        if (!json.isJsonObject()) throw new IllegalArgumentException("expected object");
        JsonObject object = json.getAsJsonObject();
        fields(object, Set.of("wallMount", "apcPowerReceiver", "extensionCableReceiver", "deviceList"));
        nested(object, "wallMount", Set.of("anchored", "nonColliding", "allowVerticalMounts"));
        nested(object, "apcPowerReceiver", Set.of("requestedWatts"));
        nested(object, "extensionCableReceiver", Set.of("localReachBlocks"));
        nested(object, "deviceList", Set.of("maxDevices"));
    }

    private static void nested(JsonObject object, String name, Set<String> allowed) {
        if (!object.has(name)) return;
        if (!object.get(name).isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        fields(object.getAsJsonObject(name), allowed);
    }

    private static void fields(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet())
            if (!allowed.contains(key) || object.get(key).isJsonNull())
                throw new IllegalArgumentException("unknown or null air alarm field: " + key);
    }
}
