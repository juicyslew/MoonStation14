package com.juicyslew.moonstation14.ms14.atmos.piping.prototype;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolved component definition only; it does not configure a live device. */
public record VentilationDeviceData(Kind kind, PipeLane pipeLane, ApcPowerReceiver apcPowerReceiver,
                                    ExtensionCableReceiver extensionCableReceiver, GasVentPump gasVentPump,
                                    GasVentScrubber gasVentScrubber, FixtureGeometry fixtureGeometry) {
    public enum Kind { VENT, SCRUBBER }
    public enum PipeLane { PRIMARY, SECONDARY, TERTIARY }

    public record ApcPowerReceiver(double requestedWatts) {
        public static final Codec<ApcPowerReceiver> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("requestedWatts").forGetter(ApcPowerReceiver::requestedWatts)
        ).apply(i, ApcPowerReceiver::new));
    }

    /** Local extension reach in blocks, not an assertion about SS14's cable radius. */
    public record ExtensionCableReceiver(int localReachBlocks) {
        public static final Codec<ExtensionCableReceiver> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("localReachBlocks").forGetter(ExtensionCableReceiver::localReachBlocks)
        ).apply(i, ExtensionCableReceiver::new));
    }

    public record GasVentPump(double targetPressureChangeKPaPerSecond, double externalPressureBoundKPa) {
        public static final Codec<GasVentPump> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("targetPressureChangeKPaPerSecond").forGetter(GasVentPump::targetPressureChangeKPaPerSecond),
                Codec.DOUBLE.fieldOf("externalPressureBoundKPa").forGetter(GasVentPump::externalPressureBoundKPa)
        ).apply(i, GasVentPump::new));
    }

    public record GasVentScrubber(double volumeRateM3PerSecond, List<GasType> filteredGases) {
        public static final Codec<GasVentScrubber> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("volumeRateM3PerSecond").forGetter(GasVentScrubber::volumeRateM3PerSecond),
                GAS.listOf().fieldOf("filteredGases").forGetter(GasVentScrubber::filteredGases)
        ).apply(i, GasVentScrubber::new));
    }

    /** Minecraft-only thin surface fixture; deliberately not an SS14 component claim. */
    public record FixtureGeometry(double width, double height, double depth, boolean nonColliding, boolean allowVerticalMounts) {
        public static final Codec<FixtureGeometry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("width").forGetter(FixtureGeometry::width),
                Codec.DOUBLE.fieldOf("height").forGetter(FixtureGeometry::height),
                Codec.DOUBLE.fieldOf("depth").forGetter(FixtureGeometry::depth),
                Codec.BOOL.fieldOf("nonColliding").forGetter(FixtureGeometry::nonColliding),
                Codec.BOOL.fieldOf("allowVerticalMounts").forGetter(FixtureGeometry::allowVerticalMounts)
        ).apply(i, FixtureGeometry::new));
    }

    private static final Codec<GasType> GAS = Codec.STRING.comapFlatMap(id -> {
        for (GasType gas : GasType.values()) if (gas.id().equals(id)) return DataResult.success(gas);
        return DataResult.error(() -> "unknown scrubber gas: " + id);
    }, GasType::id);

    private static final Codec<VentilationDeviceData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(VentilationDeviceData::kind),
            Codec.STRING.xmap(PipeLane::valueOf, PipeLane::name).fieldOf("pipeLane").forGetter(VentilationDeviceData::pipeLane),
            ApcPowerReceiver.CODEC.fieldOf("apcPowerReceiver").forGetter(VentilationDeviceData::apcPowerReceiver),
            ExtensionCableReceiver.CODEC.fieldOf("extensionCableReceiver").forGetter(VentilationDeviceData::extensionCableReceiver),
            GasVentPump.CODEC.optionalFieldOf("gasVentPump").forGetter(d -> java.util.Optional.ofNullable(d.gasVentPump())),
            GasVentScrubber.CODEC.optionalFieldOf("gasVentScrubber").forGetter(d -> java.util.Optional.ofNullable(d.gasVentScrubber()))
            , FixtureGeometry.CODEC.fieldOf("fixtureGeometry").forGetter(VentilationDeviceData::fixtureGeometry)
    ).apply(i, (kind, lane, apc, extension, vent, scrubber, geometry) ->
            new VentilationDeviceData(kind, lane, apc, extension, vent.orElse(null), scrubber.orElse(null), geometry)));

    public static final Codec<VentilationDeviceData> CODEC = Codec.of(STRUCTURAL, new Decoder<>() {
        @Override public <T> DataResult<Pair<VentilationDeviceData, T>> decode(DynamicOps<T> ops, T input) {
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
                return DataResult.error(() -> "invalid ventilation device: " + exception.getMessage());
            }
        }
    });

    public void validate() {
        if (kind == null || pipeLane == null || apcPowerReceiver == null || extensionCableReceiver == null)
            throw new IllegalArgumentException("missing shared ventilation components");
        if (fixtureGeometry == null || fixtureGeometry.width() <= 0 || fixtureGeometry.width() > 1
                || fixtureGeometry.height() <= 0 || fixtureGeometry.height() > 1
                || fixtureGeometry.depth() <= 0 || fixtureGeometry.depth() > 0.5)
            throw new IllegalArgumentException("invalid thin fixture geometry");
        positive(apcPowerReceiver.requestedWatts(), "APC receiver requested watts");
        if (extensionCableReceiver.localReachBlocks() < 1 || extensionCableReceiver.localReachBlocks() > 16)
            throw new IllegalArgumentException("local extension receiver reach must be between 1 and 16 blocks");
        if (kind == Kind.VENT) {
            if (gasVentPump == null || gasVentScrubber != null)
                throw new IllegalArgumentException("vent requires only GasVentPump");
            positive(gasVentPump.targetPressureChangeKPaPerSecond(), "vent pressure change");
            positive(gasVentPump.externalPressureBoundKPa(), "vent external pressure bound");
            if (gasVentPump.targetPressureChangeKPaPerSecond() > 1_000_000
                    || gasVentPump.externalPressureBoundKPa() > Ss14VentilationDefaults.MAXIMUM_TARGET_PRESSURE_KPA)
                throw new IllegalArgumentException("vent defaults exceed live settings bounds");
        } else {
            if (gasVentScrubber == null || gasVentPump != null)
                throw new IllegalArgumentException("scrubber requires only GasVentScrubber");
            positive(gasVentScrubber.volumeRateM3PerSecond(), "scrubber volume rate");
            if (gasVentScrubber.volumeRateM3PerSecond() > 1_000_000)
                throw new IllegalArgumentException("scrubber defaults exceed live settings bounds");
            if (gasVentScrubber.filteredGases() == null || gasVentScrubber.filteredGases().isEmpty()
                    || new HashSet<>(gasVentScrubber.filteredGases()).size() != gasVentScrubber.filteredGases().size())
                throw new IllegalArgumentException("scrubber needs distinct filtered gases");
        }
    }

    private static void positive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(field + " must be finite and positive");
    }

    private static void audit(JsonElement json) {
        if (!json.isJsonObject()) throw new IllegalArgumentException("expected object");
        JsonObject object = json.getAsJsonObject();
        fields(object, Set.of("kind", "pipeLane", "apcPowerReceiver", "extensionCableReceiver", "gasVentPump", "gasVentScrubber", "fixtureGeometry"));
        for (String name : List.of("apcPowerReceiver", "extensionCableReceiver"))
            if (object.has(name)) nested(object, name, switch (name) {
                case "apcPowerReceiver" -> Set.of("requestedWatts");
                default -> Set.of("localReachBlocks");
            });
        if (object.has("gasVentPump")) nested(object, "gasVentPump", Set.of("targetPressureChangeKPaPerSecond", "externalPressureBoundKPa"));
        if (object.has("gasVentScrubber")) nested(object, "gasVentScrubber", Set.of("volumeRateM3PerSecond", "filteredGases"));
        if (object.has("fixtureGeometry")) nested(object, "fixtureGeometry", Set.of("width", "height", "depth", "nonColliding", "allowVerticalMounts"));
    }

    private static void nested(JsonObject object, String name, Set<String> allowed) {
        if (!object.get(name).isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        fields(object.getAsJsonObject(name), allowed);
    }

    private static void fields(JsonObject object, Set<String> allowed) {
        for (String name : object.keySet()) {
            if (!allowed.contains(name) || object.get(name).isJsonNull())
                throw new IllegalArgumentException("unknown or null ventilation field: " + name);
        }
    }
}
