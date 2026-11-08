package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved SS14 GasMiner composition: explicit resource-generation profile, not a pipe port. */
public record GasMinerProfile(GasType gas, double spawnAmount, double spawnTemperatureKelvin,
                              double maxExternalPressureKpa, double maxTotalMoles) {
    private static final Codec<GasType> GAS_CODEC = Codec.STRING.comapFlatMap(id -> {
        try { return DataResult.success(GasType.fromId(id)); }
        catch (IllegalArgumentException ex) { return DataResult.error(ex::getMessage); }
    }, GasType::id);
    public static final Codec<GasMinerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            GAS_CODEC.fieldOf("gas").forGetter(GasMinerProfile::gas),
            Codec.DOUBLE.optionalFieldOf("spawnAmount", 400.0).forGetter(GasMinerProfile::spawnAmount),
            Codec.DOUBLE.optionalFieldOf("spawnTemperatureKelvin", 293.15).forGetter(GasMinerProfile::spawnTemperatureKelvin),
            Codec.DOUBLE.optionalFieldOf("maxExternalPressureKpa", 300.0).forGetter(GasMinerProfile::maxExternalPressureKpa),
            Codec.DOUBLE.optionalFieldOf("maxTotalMoles", Double.POSITIVE_INFINITY).forGetter(GasMinerProfile::maxTotalMoles)
    ).apply(i, GasMinerProfile::new));
    public GasMinerProfile {
        if (gas == null || !positive(spawnAmount) || !positive(spawnTemperatureKelvin)
                || !positive(maxExternalPressureKpa) || Double.isNaN(maxTotalMoles) || maxTotalMoles < 0)
            throw new IllegalArgumentException("invalid gas miner composition");
    }
    public double molesForElapsedSeconds(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return 0;
        double amount = spawnAmount * seconds;
        return Double.isFinite(amount) ? amount : 0;
    }
    /** Energy-conserving mixture after one bounded source step; null means no valid addition. */
    public static GasMixture boundedAddition(GasMixture current, GasType gas, double requestedMoles,
                                             double temperatureKelvin, double maxPressureKpa,
                                             double maxTotalMoles) {
        if (current == null || gas == null || !Double.isFinite(requestedMoles) || requestedMoles <= 0
                || !Double.isFinite(temperatureKelvin) || temperatureKelvin < GasMixture.MIN_TEMPERATURE_KELVIN
                || !Double.isFinite(maxPressureKpa) || maxPressureKpa <= 0 || Double.isNaN(maxTotalMoles)
                || maxTotalMoles < 0 || current.pressureKpa() >= maxPressureKpa || current.totalMoles() >= maxTotalMoles) return null;
        double high = Math.min(requestedMoles, maxTotalMoles - current.totalMoles());
        double low = 0;
        try {
            if (current.withGasDelta(gas, high, temperatureKelvin).pressureKpa() <= maxPressureKpa) low = high;
            else for (int i = 0; i < 32; i++) {
                double mid = (low + high) * 0.5;
                if (current.withGasDelta(gas, mid, temperatureKelvin).pressureKpa() <= maxPressureKpa) low = mid;
                else high = mid;
            }
            return low > 0 ? current.withGasDelta(gas, low, temperatureKelvin) : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
}
