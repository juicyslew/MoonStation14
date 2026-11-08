package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.nbt.CompoundTag;
import java.util.Optional;

/** Typed, server-owned setting overrides for the resolved atmos-machine component composition. */
public final class AtmosMachineSettings {
    private static final int VERSION = 1;
    private AtmosMachineSettings() { }

    public enum Operation {
        SET_ENABLED, SET_TARGET_PRESSURE_KPA, SET_TRANSFER_RATE_M3_PER_SECOND,
        SET_MIXER_FRACTION_A, SET_FILTER_GAS, CLEAR_FILTER_GAS,
        SET_THERMOSTAT_SETPOINT_K, SET_VALVE_OPEN
    }
    public sealed interface Value permits BooleanValue, NumericValue, GasValue, ClearGasValue { }
    public record BooleanValue(boolean value) implements Value { }
    public record NumericValue(double value) implements Value { }
    public record GasValue(GasType value) implements Value {
        public GasValue { if (value == null) throw new IllegalArgumentException("gas required"); }
    }
    public enum ClearGasValue implements Value { INSTANCE }

    public record Values(long revision, Optional<Boolean> enabled, Optional<Double> targetPressureKpa,
                        Optional<Double> transferRateM3PerSecond, Optional<Double> mixerFractionA,
                        Optional<GasType> filterGas, Optional<Double> thermostatSetpointK,
                        Optional<Boolean> valveOpen) {
        public Values {
            if (revision < 0) throw new IllegalArgumentException("revision must be nonnegative");
            if (enabled == null || targetPressureKpa == null || transferRateM3PerSecond == null
                    || mixerFractionA == null || filterGas == null || thermostatSetpointK == null || valveOpen == null)
                throw new IllegalArgumentException("settings optionals must not be null");
        }
        public double mixerFractionB() { return mixerFractionA.map(value -> 1d - value).orElse(Double.NaN); }
    }

    /** Defaults are always taken from the fully resolved profile, including its inherited component defaults. */
    public static Values defaults(AtmosMachineProfile profile) {
        if (profile == null) throw new IllegalArgumentException("resolved profile required");
        Optional<Boolean> enabled = Optional.empty();
        if (profile.pump() != null) enabled = Optional.of(profile.pump().enabled());
        else if (profile.mixer() != null) enabled = Optional.of(profile.mixer().enabled());
        else if (profile.filter() != null) enabled = Optional.of(profile.filter().enabled());
        else if (profile.thermostat() != null) enabled = Optional.of(profile.thermostat().enabled());
        else if (profile.teg() != null) enabled = Optional.of(true);

        Optional<Double> pressure = Optional.empty();
        Optional<Double> rate = Optional.empty();
        Optional<Double> ratio = Optional.empty();
        Optional<GasType> gas = Optional.empty();
        Optional<Double> setpoint = Optional.empty();
        Optional<Boolean> valveOpen = Optional.empty();
        if (profile.pump() != null && profile.pump().mode() == AtmosMachineProfile.PumpMode.PRESSURE)
            pressure = profile.pump().targetPressureKpa();
        else if (profile.mixer() != null) {
            pressure = Optional.of(profile.mixer().targetPressureKpa());
            ratio = Optional.of(profile.mixer().ratios().getFirst());
        }
        if (profile.pump() != null && profile.pump().mode() == AtmosMachineProfile.PumpMode.VOLUME)
            rate = profile.pump().transferRate().map(AtmosMachineSettings::litresPerSecondToCubicMetresPerSecond);
        else if (profile.filter() != null) {
            rate = Optional.of(litresPerSecondToCubicMetresPerSecond(profile.filter().transferRate()));
            gas = profile.filter().gas();
        }
        if (profile.thermostat() != null) setpoint = Optional.of(profile.thermostat().targetK());
        if (profile.valve() != null) valveOpen = Optional.of(profile.valve().open());
        Values result = new Values(0, enabled, pressure, rate, ratio, gas, setpoint, valveOpen);
        validate(profile, result);
        return result;
    }

    /** Applies one capability-checked request; numeric inputs outside SS14-derived bounds are rejected. */
    public static Values apply(AtmosMachineProfile profile, Values current, Operation operation, Value value) {
        if (profile == null || current == null || operation == null) throw new IllegalArgumentException("profile, settings and operation required");
        if (current.revision() == Long.MAX_VALUE) throw new IllegalStateException("settings revision exhausted");
        Optional<Boolean> enabled = current.enabled();
        Optional<Double> pressure = current.targetPressureKpa();
        Optional<Double> rate = current.transferRateM3PerSecond();
        Optional<Double> ratio = current.mixerFractionA();
        Optional<GasType> gas = current.filterGas();
        Optional<Double> setpoint = current.thermostatSetpointK();
        Optional<Boolean> valve = current.valveOpen();
        switch (operation) {
            case SET_ENABLED -> enabled = Optional.of(require(value, BooleanValue.class).value());
            case SET_TARGET_PRESSURE_KPA -> pressure = Optional.of(require(value, NumericValue.class).value());
            case SET_TRANSFER_RATE_M3_PER_SECOND -> rate = Optional.of(require(value, NumericValue.class).value());
            case SET_MIXER_FRACTION_A -> ratio = Optional.of(require(value, NumericValue.class).value());
            case SET_FILTER_GAS -> gas = Optional.of(require(value, GasValue.class).value());
            case CLEAR_FILTER_GAS -> {
                if (profile.filter() == null) throw new IllegalArgumentException("filter gas unsupported by profile");
                require(value, ClearGasValue.class);
                gas = Optional.empty();
            }
            case SET_THERMOSTAT_SETPOINT_K -> setpoint = Optional.of(require(value, NumericValue.class).value());
            case SET_VALVE_OPEN -> valve = Optional.of(require(value, BooleanValue.class).value());
        }
        Values proposed = new Values(current.revision() + 1, enabled, pressure, rate, ratio, gas, setpoint, valve);
        validate(profile, proposed);
        return proposed;
    }

    public static CompoundTag save(AtmosMachineProfile profile, Values values) {
        validate(profile, values);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION); tag.putLong("Revision", values.revision());
        values.enabled().ifPresent(v -> tag.putBoolean("Enabled", v));
        values.targetPressureKpa().ifPresent(v -> tag.putDouble("TargetPressureKpa", v));
        values.transferRateM3PerSecond().ifPresent(v -> tag.putDouble("TransferRateM3PerSecond", v));
        values.mixerFractionA().ifPresent(v -> tag.putDouble("MixerFractionA", v));
        if (profile.filter() != null) {
            tag.putBoolean("HasFilterGas", values.filterGas().isPresent());
            values.filterGas().ifPresent(v -> tag.putString("FilterGas", v.id()));
        }
        values.thermostatSetpointK().ifPresent(v -> tag.putDouble("ThermostatSetpointK", v));
        values.valveOpen().ifPresent(v -> tag.putBoolean("ValveOpen", v));
        return tag;
    }

    public static Values load(AtmosMachineProfile profile, CompoundTag tag) {
        if (profile == null || tag == null || !tag.contains("Version", CompoundTag.TAG_INT)
                || tag.getInt("Version") != VERSION || !tag.contains("Revision", CompoundTag.TAG_LONG))
            throw new IllegalArgumentException("missing or unsupported atmos-machine settings version");
        Values defaults = defaults(profile);
        Optional<Boolean> enabled = readBoolean(tag, "Enabled", defaults.enabled());
        Optional<Double> pressure = readDouble(tag, "TargetPressureKpa", defaults.targetPressureKpa());
        Optional<Double> rate = readDouble(tag, "TransferRateM3PerSecond", defaults.transferRateM3PerSecond());
        Optional<Double> ratio = readDouble(tag, "MixerFractionA", defaults.mixerFractionA());
        Optional<GasType> gas = defaults.filterGas();
        if (profile.filter() != null) {
            if (!tag.contains("HasFilterGas", CompoundTag.TAG_BYTE)) throw new IllegalArgumentException("missing filter setting");
            gas = tag.getBoolean("HasFilterGas") ? Optional.of(GasType.fromId(tag.getString("FilterGas"))) : Optional.empty();
        }
        Optional<Double> setpoint = readDouble(tag, "ThermostatSetpointK", defaults.thermostatSetpointK());
        Optional<Boolean> valve = readBoolean(tag, "ValveOpen", defaults.valveOpen());
        Values result = new Values(tag.getLong("Revision"), enabled, pressure, rate, ratio, gas, setpoint, valve);
        validate(profile, result);
        return result;
    }

    public static void validate(AtmosMachineProfile profile, Values values) {
        if (profile == null || values == null) throw new IllegalArgumentException("profile and settings required");
        Values defaults = profile == null ? null : capabilityShape(profile);
        sameCapability(values.enabled(), defaults.enabled(), "enabled");
        sameCapability(values.targetPressureKpa(), defaults.targetPressureKpa(), "pressure");
        sameCapability(values.transferRateM3PerSecond(), defaults.transferRateM3PerSecond(), "transfer rate");
        sameCapability(values.mixerFractionA(), defaults.mixerFractionA(), "mixer ratio");
        if (profile.filter() == null && values.filterGas().isPresent())
            throw new IllegalArgumentException("filter gas unsupported by profile");
        sameCapability(values.thermostatSetpointK(), defaults.thermostatSetpointK(), "thermostat");
        sameCapability(values.valveOpen(), defaults.valveOpen(), "valve");
        if (values.targetPressureKpa().isPresent()) {
            double max = profile.pump() != null ? profile.pump().maxPressureKpa().orElse(4500d) : profile.mixer().maxPressureKpa();
            bounded(values.targetPressureKpa().get(), 0, max, "target pressure");
        }
        if (values.transferRateM3PerSecond().isPresent()) {
            double maxLitres = profile.pump() != null ? profile.pump().maxTransferRate().orElseThrow()
                    : profile.filter().maxTransferRate();
            bounded(values.transferRateM3PerSecond().get(), 0, litresPerSecondToCubicMetresPerSecond(maxLitres), "transfer rate");
        }
        values.mixerFractionA().ifPresent(value -> bounded(value, 0, 1, "mixer fraction"));
        if (values.thermostatSetpointK().isPresent())
            bounded(values.thermostatSetpointK().get(), profile.thermostat().minimumTemperatureK(),
                    profile.thermostat().maximumTemperatureK(), "thermostat setpoint");
    }

    private static Values capabilityShape(AtmosMachineProfile p) {
        Optional<Boolean> enabled = p.pump() != null || p.mixer() != null || p.filter() != null || p.thermostat() != null || p.teg() != null ? Optional.of(false) : Optional.empty();
        Optional<Double> pressure = p.pump() != null && p.pump().mode() == AtmosMachineProfile.PumpMode.PRESSURE
                || p.mixer() != null ? Optional.of(0d) : Optional.empty();
        Optional<Double> rate = p.pump() != null && p.pump().mode() == AtmosMachineProfile.PumpMode.VOLUME || p.filter() != null
                ? Optional.of(0d) : Optional.empty();
        Optional<Double> ratio = p.mixer() != null ? Optional.of(0d) : Optional.empty();
        Optional<GasType> gas = p.filter() != null ? Optional.of(GasType.OXYGEN) : Optional.empty();
        Optional<Double> thermostat = p.thermostat() != null ? Optional.of(0d) : Optional.empty();
        Optional<Boolean> valve = p.valve() != null ? Optional.of(false) : Optional.empty();
        return new Values(0, enabled, pressure, rate, ratio, gas, thermostat, valve);
    }

    private static void sameCapability(Optional<?> actual, Optional<?> shape, String field) {
        if (actual.isPresent() != shape.isPresent()) throw new IllegalArgumentException("unsupported or missing " + field + " setting");
    }
    private static void bounded(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(name + " outside permitted range");
    }
    private static <T> T require(Object value, Class<T> type) {
        if (!type.isInstance(value)) throw new IllegalArgumentException("expected " + type.getSimpleName());
        return type.cast(value);
    }
    private static Optional<Boolean> readBoolean(CompoundTag tag, String key, Optional<Boolean> fallback) {
        if (!fallback.isPresent()) { if (tag.contains(key)) throw new IllegalArgumentException("unsupported " + key); return fallback; }
        if (!tag.contains(key, CompoundTag.TAG_BYTE)) throw new IllegalArgumentException("missing " + key);
        return Optional.of(tag.getBoolean(key));
    }
    private static Optional<Double> readDouble(CompoundTag tag, String key, Optional<Double> fallback) {
        if (!fallback.isPresent()) { if (tag.contains(key)) throw new IllegalArgumentException("unsupported " + key); return fallback; }
        if (!tag.contains(key, CompoundTag.TAG_DOUBLE)) throw new IllegalArgumentException("missing " + key);
        return Optional.of(tag.getDouble(key));
    }
    private static double litresPerSecondToCubicMetresPerSecond(double litresPerSecond) { return litresPerSecond / 1000d; }
}
