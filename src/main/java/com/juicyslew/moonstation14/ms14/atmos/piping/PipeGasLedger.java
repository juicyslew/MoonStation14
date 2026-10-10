package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure arithmetic for splitting and recombining stored gas; it does not define ownership or persistence. */
public final class PipeGasLedger {
    public static final int MAX_RECIPIENTS = 1024;

    private PipeGasLedger() { }

    /** Splits gas proportionally to positive physical volumes. The last recipient receives rounding residuals. */
    public static List<GasMixture> split(GasMixture source, double[] volumes) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(volumes, "volumes");
        if (volumes.length == 0 || volumes.length > MAX_RECIPIENTS)
            throw new IllegalArgumentException("recipient count must be between 1 and " + MAX_RECIPIENTS);
        double totalVolume = 0.0;
        for (double volume : volumes) {
            if (!Double.isFinite(volume) || volume <= 0.0) throw new IllegalArgumentException("volumes must be positive and finite");
            totalVolume += volume;
        }
        if (!Double.isFinite(totalVolume)) throw new IllegalArgumentException("total volume overflow");

        List<EnumMap<GasType, Double>> amounts = new ArrayList<>(volumes.length);
        for (int i = 0; i < volumes.length; i++) amounts.add(new EnumMap<>(GasType.class));
        for (Map.Entry<GasType, Double> entry : source.gasMoles().entrySet()) {
            float total = entry.getValue().floatValue();
            for (int i = 0; i < volumes.length; i++) {
                float part = total * (float) (volumes[i] / totalVolume);
                if (!Float.isFinite(part)) throw new IllegalArgumentException("unrepresentable species split");
                if (part > 0.0f) amounts.get(i).put(entry.getKey(), (double) part);
            }
        }
        List<GasMixture> result = new ArrayList<>(volumes.length);
        for (int i = 0; i < amounts.size(); i++) {
            EnumMap<GasType, Double> moles = amounts.get(i);
            double recipientVolume = source.volumeCubicMeters() * (volumes[i] / totalVolume);
            if (!Double.isFinite(recipientVolume) || recipientVolume <= 0.0)
                throw new IllegalArgumentException("unrepresentable recipient volume");
            GasMixture part = new GasMixture(moles, source.temperatureKelvin(), recipientVolume);
            result.add(part);
        }
        double representedEnergy = result.stream().mapToDouble(GasMixture::thermalEnergy).sum();
        if (!conservedEnergy(source.thermalEnergy(), representedEnergy))
            throw new IllegalArgumentException("split energy cannot be represented conservatively");
        for (GasType gas : GasType.values()) {
            double represented = result.stream().mapToDouble(part -> part.moles(gas)).sum();
            if (!conservedSpecies(represented, source.moles(gas)))
                throw new IllegalArgumentException("split species cannot be represented conservatively");
        }
        return Collections.unmodifiableList(result);
    }

    private static double energyTolerance(double energy) {
        float stored = (float) energy;
        if (!Float.isFinite(stored)) return Double.NaN;
        return Math.max(Math.ulp(stored) * 8.0, Math.ulp(energy) * 8.0);
    }

    static boolean conservedSpecies(double before, double after) {
        float storedBefore = (float) before, storedAfter = (float) after;
        return Double.isFinite(before) && Double.isFinite(after) && Float.isFinite(storedBefore)
                && Float.isFinite(storedAfter)
                && Math.abs(before - after) <= Math.max(Math.ulp(storedBefore) * 8.0,
                Math.ulp(storedAfter) * 8.0);
    }

    static boolean conservedEnergy(double before, double after) {
        double tolerance = Math.max(energyTolerance(before), energyTolerance(after));
        return Double.isFinite(before) && Double.isFinite(after) && Double.isFinite(tolerance)
                && Math.abs(before - after) <= tolerance;
    }

    /** Combines species inventories and their actual thermal energies. */
    public static GasMixture merge(List<GasMixture> mixtures) {
        Objects.requireNonNull(mixtures, "mixtures");
        if (mixtures.isEmpty() || mixtures.size() > MAX_RECIPIENTS) throw new IllegalArgumentException("invalid mixture count");
        EnumMap<GasType, Double> total = new EnumMap<>(GasType.class);
        double energy = 0.0;
        double capacity = 0.0;
        for (GasMixture mixture : mixtures) {
            Objects.requireNonNull(mixture, "mixture");
            energy += mixture.thermalEnergy();
            capacity += mixture.heatCapacity();
            for (var entry : mixture.gasMoles().entrySet()) total.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
        if (!Double.isFinite(energy) || !Double.isFinite(capacity) || energy < 0.0 || capacity < 0.0
                || total.values().stream().anyMatch(value -> !Double.isFinite(value)))
            throw new IllegalArgumentException("merge overflow");
        if (capacity == 0.0) {
            if (energy != 0.0) throw new IllegalArgumentException("energy without heat capacity");
            return new GasMixture(total, 0.0);
        }
        double temperature = energy / capacity;
        if (!Double.isFinite(temperature) || temperature < GasMixture.MIN_TEMPERATURE_KELVIN
                || temperature > GasMixture.MAX_TEMPERATURE_KELVIN)
            throw new IllegalArgumentException("unrepresentable merged temperature");
        return new GasMixture(total, temperature);
    }
}
