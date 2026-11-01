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
import com.juicyslew.moonstation14.ms14.devicenet.DeviceComponents;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmableData;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode;

/** Resolved definition, not a live alarm or a functioning device network. */
public record AirAlarmData(WallMount wallMount, ApcPowerReceiver apcPowerReceiver,
                           ExtensionCableReceiver extensionCableReceiver, DeviceList deviceList,
                            DeviceComponents deviceComponents, AtmosAlarmableData atmosAlarmable, Controller controller) {
    public record Preset(boolean ventEnabled, double externalTargetKPa, boolean pressureLockoutOverride,
                         boolean scrubberEnabled, String scrubberMode, double scrubberRateM3PerSecond,
                         Set<String> filterGases, boolean wideNet) {
        static final Codec<Preset> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("ventEnabled").forGetter(Preset::ventEnabled), Codec.DOUBLE.fieldOf("externalTargetKPa").forGetter(Preset::externalTargetKPa),
                Codec.BOOL.fieldOf("pressureLockoutOverride").forGetter(Preset::pressureLockoutOverride), Codec.BOOL.fieldOf("scrubberEnabled").forGetter(Preset::scrubberEnabled),
                Codec.STRING.fieldOf("scrubberMode").forGetter(Preset::scrubberMode), Codec.DOUBLE.fieldOf("scrubberRateM3PerSecond").forGetter(Preset::scrubberRateM3PerSecond),
                Codec.STRING.listOf().xmap(java.util.Set::copyOf, java.util.List::copyOf).fieldOf("filterGases").forGetter(Preset::filterGases),
                Codec.BOOL.fieldOf("wideNet").forGetter(Preset::wideNet)).apply(i, Preset::new));
    }
    public record Controller(boolean automaticMode, AirAlarmMode initialMode, java.util.Map<AirAlarmMode, Preset> presets) {
        static final Codec<Controller> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("automaticMode").forGetter(Controller::automaticMode),
                Codec.STRING.xmap(AirAlarmMode::valueOf, AirAlarmMode::name).fieldOf("initialMode").forGetter(Controller::initialMode),
                Codec.unboundedMap(Codec.STRING.xmap(AirAlarmMode::valueOf, AirAlarmMode::name), Preset.CODEC).fieldOf("presets").forGetter(Controller::presets)
        ).apply(i, Controller::new));
    }
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
            DeviceList.CODEC.fieldOf("deviceList").forGetter(AirAlarmData::deviceList),
            DeviceComponents.CODEC.optionalFieldOf("deviceComponents", DeviceComponents.empty()).forGetter(AirAlarmData::deviceComponents),
            AtmosAlarmableData.CODEC.optionalFieldOf("atmosAlarmable").forGetter(d -> java.util.Optional.ofNullable(d.atmosAlarmable())),
            Controller.CODEC.fieldOf("controller").forGetter(AirAlarmData::controller)
    ).apply(i, (wall, power, extension, list, components, alarmable, controller) -> new AirAlarmData(wall, power, extension, list, components, alarmable.orElse(null), controller)));

    public AirAlarmData(WallMount wallMount, ApcPowerReceiver apcPowerReceiver, ExtensionCableReceiver extensionCableReceiver,
                        DeviceList deviceList, DeviceComponents deviceComponents) {
        this(wallMount, apcPowerReceiver, extensionCableReceiver, deviceList, deviceComponents, null, null);
    }

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
        if (atmosAlarmable == null || atmosAlarmable.sourceTags().isEmpty() || atmosAlarmable.syncWith().isEmpty())
            throw new IllegalArgumentException("air alarm requires resolved alarm tags and syncWith filter");
        if (wallMount == null || !wallMount.anchored() || !wallMount.nonColliding())
            throw new IllegalArgumentException("air alarm requires anchored non-colliding wallmount geometry");
        if (apcPowerReceiver == null || apcPowerReceiver.requestedWatts() != 100.0)
            throw new IllegalArgumentException("air alarm requires inherited APC demand of 100 W");
        if (extensionCableReceiver == null || extensionCableReceiver.localReachBlocks() != 3)
            throw new IllegalArgumentException("air alarm requires local extension reach of 3 blocks");
        if (deviceList == null || deviceList.maxDevices() != 32)
            throw new IllegalArgumentException("air alarm local device list is bounded at 32");
        if (controller == null || !controller.presets().keySet().equals(java.util.EnumSet.allOf(AirAlarmMode.class)))
            throw new IllegalArgumentException("controller requires all five source modes");
        controller.presets().values().forEach(p -> {
            if (p.externalTargetKPa() < 0 || p.scrubberRateM3PerSecond() < 0 || !Double.isFinite(p.externalTargetKPa()) || !Double.isFinite(p.scrubberRateM3PerSecond())
                    || !java.util.Set.of("FILTER", "SIPHON").contains(p.scrubberMode())) throw new IllegalArgumentException("invalid preset settings");
            p.filterGases().forEach(com.juicyslew.moonstation14.ms14.atmos.core.GasType::fromId);
        });
    }

    private static void audit(JsonElement json) {
        if (!json.isJsonObject()) throw new IllegalArgumentException("expected object");
        JsonObject object = json.getAsJsonObject();
        fields(object, Set.of("wallMount", "apcPowerReceiver", "extensionCableReceiver", "deviceList", "deviceComponents", "atmosAlarmable", "controller"));
        nested(object, "wallMount", Set.of("anchored", "nonColliding", "allowVerticalMounts"));
        nested(object, "apcPowerReceiver", Set.of("requestedWatts"));
        nested(object, "extensionCableReceiver", Set.of("localReachBlocks"));
        nested(object, "deviceList", Set.of("maxDevices"));
        if (object.has("deviceComponents")) {
            nested(object, "deviceComponents", Set.of("deviceNetwork", "deviceConnection", "deviceLinkSource", "deviceLinkSink", "deviceList"));
            JsonObject components = object.getAsJsonObject("deviceComponents");
            nested(components, "deviceNetwork", Set.of("network", "transmit", "receive", "transmitFrequency", "receiveFrequency", "addressPrefix", "autoConnect", "savableAddress", "customAddress", "receiveAll", "broadcastHook", "requiresPower"));
            nested(components, "deviceConnection", Set.of("wired", "wireless", "wirelessRange"));
            nested(components, "deviceLinkSource", Set.of("ports", "range", "lastSignals"));
            nested(components, "deviceLinkSink", Set.of("ports", "limit"));
            nested(components, "deviceList", Set.of("capacity", "allowList", "filterIncoming"));
        }
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
