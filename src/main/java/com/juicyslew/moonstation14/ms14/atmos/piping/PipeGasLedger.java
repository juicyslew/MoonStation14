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
            double residual = entry.getValue();
            for (int i = 0; i < volumes.length - 1; i++) {
                double part = entry.getValue() * (volumes[i] / totalVolume);
                if (!Double.isFinite(part) || (entry.getValue() > 0.0 && part == 0.0)) throw new IllegalArgumentException("unrepresentable species split");
                if (part > 0.0) amounts.get(i).put(entry.getKey(), part);
                residual -= part;
                if (residual < 0.0) throw new IllegalArgumentException("rounding produced negative residual");
            }
            if (!Double.isFinite(residual) || residual < 0.0 || (entry.getValue() > 0.0 && residual == 0.0))
                throw new IllegalArgumentException("unrepresentable residual species split");
            amounts.get(volumes.length - 1).put(entry.getKey(), residual);
        }

        // GasMixture stores temperature rather than energy. Temperatures therefore quantize the
        // represented energy; compensate the final recipient and reject if the residual cannot
        // be represented within a tight floating-point tolerance.
        double remainingEnergy = source.thermalEnergy();
        List<GasMixture> result = new ArrayList<>(volumes.length);
        for (int i = 0; i < amounts.size(); i++) {
            EnumMap<GasType, Double> moles = amounts.get(i);
            double capacity = 0.0;
            for (var entry : moles.entrySet()) capacity += entry.getValue() * entry.getKey().molarHeatCapacity();
            if (!Double.isFinite(capacity) || capacity < 0.0) throw new IllegalArgumentException("unrepresentable split heat capacity");
            double energy = 0.0;
            if (source.heatCapacity() > 0.0) {
                energy = i == amounts.size() - 1
                        ? remainingEnergy
                        : source.thermalEnergy() * (capacity / source.heatCapacity());
            }
            if (!Double.isFinite(energy) || energy < 0.0 || (source.thermalEnergy() > 0.0 && capacity > 0.0 && energy == 0.0))
                throw new IllegalArgumentException("unrepresentable energy split");
            double temperature = capacity == 0.0 ? source.temperatureKelvin() : energy / capacity;
            if (!Double.isFinite(temperature) || temperature < 0.0) throw new IllegalArgumentException("unrepresentable split temperature");
            GasMixture part = new GasMixture(moles, temperature);
            if (capacity > 0.0) {
                double represented = part.thermalEnergy();
                if (!Double.isFinite(represented) || represented < 0.0) throw new IllegalArgumentException("unrepresentable split energy");
                remainingEnergy -= represented;
                if (remainingEnergy < 0.0 && remainingEnergy >= -energyTolerance(source.thermalEnergy())) remainingEnergy = 0.0;
                if (remainingEnergy < 0.0) throw new IllegalArgumentException("split energy exceeds source energy");
            }
            result.add(part);
        }
        double representedEnergy = result.stream().mapToDouble(GasMixture::thermalEnergy).sum();
        if (!Double.isFinite(representedEnergy) || Math.abs(representedEnergy - source.thermalEnergy()) > energyTolerance(source.thermalEnergy()))
            throw new IllegalArgumentException("split energy cannot be represented conservatively");
        return Collections.unmodifiableList(result);
    }

    private static double energyTolerance(double energy) {
        return Math.max(Math.ulp(energy) * 8.0, Math.abs(energy) * 1e-14);
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
        if (!Double.isFinite(temperature)) throw new IllegalArgumentException("unrepresentable merged temperature");
        return new GasMixture(total, temperature);
    }
}
