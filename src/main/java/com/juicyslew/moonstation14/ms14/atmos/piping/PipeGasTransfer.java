package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure, finite-mixture planning for one directed pipe transfer. */
public final class PipeGasTransfer {
    private static final double GAS_CONSTANT = 8.31446261815324;
    private static final int SEARCH_STEPS = 80;

    private PipeGasTransfer() { }

    /** Detached value plan. perGasMoved is immutable and energyMoved is the packet's enthalpy. */
    public record Plan(GasMixture sourceAfter, GasMixture destinationAfter, GasMixture packet,
                       Map<GasType, Double> perGasMoved, double energyMoved) {
        public Plan {
            Objects.requireNonNull(sourceAfter, "sourceAfter");
            Objects.requireNonNull(destinationAfter, "destinationAfter");
            Objects.requireNonNull(packet, "packet");
            Objects.requireNonNull(perGasMoved, "perGasMoved");
            EnumMap<GasType, Double> copy = new EnumMap<>(GasType.class);
            perGasMoved.forEach((gas, amount) -> {
                Objects.requireNonNull(gas, "moved gas");
                Objects.requireNonNull(amount, "moved amount");
                if (!Double.isFinite(amount) || amount <= 0.0)
                    throw new IllegalArgumentException("moved amounts must be positive and finite");
                copy.put(gas, amount);
            });
            if (copy.isEmpty() || !Double.isFinite(energyMoved) || energyMoved < 0.0)
                throw new IllegalArgumentException("invalid transfer plan");
            perGasMoved = Map.copyOf(copy);
        }
    }

    /**
     * Plans extraction of selected gases at their common source fraction. Invalid arguments or
     * unrepresentable inputs throw IllegalArgumentException; an empty result means no selected
     * gas can be moved without violating a bound (including requests of zero).
     */
    public static Optional<Plan> plan(GasMixture source, GasMixture destination,
                                      double sourceVolumeM3, double destinationVolumeM3,
                                      double maximumMoles, double destinationMaxPressureKpa,
                                      Set<GasType> filterSet) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(filterSet, "filterSet");
        positiveFinite(sourceVolumeM3, "sourceVolumeM3");
        positiveFinite(destinationVolumeM3, "destinationVolumeM3");
        nonnegativeFinite(maximumMoles, "maximumMoles");
        nonnegativeFinite(destinationMaxPressureKpa, "destinationMaxPressureKpa");
        EnumMap<GasType, Double> selected = new EnumMap<>(GasType.class);
        for (GasType gas : filterSet) {
            Objects.requireNonNull(gas, "filter species");
            double amount = source.moles(gas);
            if (amount > 0.0) selected.put(gas, amount);
        }
        double total = selected.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total == 0.0 || maximumMoles == 0.0) return Optional.empty();
        double upper = Math.min(1.0, maximumMoles / total);
        if (!(upper > 0.0)) return Optional.empty();
        Plan candidate = create(source, destination, selected, upper);
        if (candidate != null && candidate.destinationAfter().pressureKpa(destinationVolumeM3) <= destinationMaxPressureKpa)
            return Optional.of(candidate);

        double low = 0.0, high = upper;
        Plan best = null;
        for (int i = 0; i < SEARCH_STEPS; i++) {
            double mid = low + (high - low) * 0.5;
            if (mid == low || mid == high) break;
            Plan attempt = create(source, destination, selected, mid);
            if (attempt != null && attempt.destinationAfter().pressureKpa(destinationVolumeM3) <= destinationMaxPressureKpa) {
                best = attempt;
                low = mid;
            } else high = mid;
        }
        return Optional.ofNullable(best);
    }

    private static Plan create(GasMixture source, GasMixture destination,
                               EnumMap<GasType, Double> selected, double fraction) {
        EnumMap<GasType, Double> packetMoles = new EnumMap<>(GasType.class);
        EnumMap<GasType, Double> remaining = new EnumMap<>(GasType.class);
        remaining.putAll(source.gasMoles());
        for (var entry : selected.entrySet()) {
            double moved = entry.getValue() * fraction;
            if (!(moved > 0.0) || moved > entry.getValue()) return null;
            double rest = entry.getValue() - moved;
            if (fraction < 1.0 && (rest <= 0.0 || rest == entry.getValue())) return null;
            if (rest == 0.0) remaining.remove(entry.getKey());
            else remaining.put(entry.getKey(), rest);
            packetMoles.put(entry.getKey(), moved);
        }
        if (packetMoles.isEmpty()) return null;
        try {
            GasMixture packet = new GasMixture(packetMoles, source.temperatureKelvin());
            GasMixture sourceAfter = new GasMixture(remaining, source.temperatureKelvin());
            GasMixture destinationAfter = PipeGasLedger.merge(List.of(destination, packet));
            if (!conserved(source, destination, sourceAfter, destinationAfter)) return null;
            EnumMap<GasType, Double> immutableMoved = new EnumMap<>(packetMoles);
            return new Plan(sourceAfter, destinationAfter, packet, Map.copyOf(immutableMoved), packet.thermalEnergy());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean conserved(GasMixture beforeSource, GasMixture beforeDestination,
                                     GasMixture afterSource, GasMixture afterDestination) {
        for (GasType gas : GasType.values())
            if (!close(beforeSource.moles(gas) + beforeDestination.moles(gas),
                    afterSource.moles(gas) + afterDestination.moles(gas))) return false;
        return close(beforeSource.thermalEnergy() + beforeDestination.thermalEnergy(),
                afterSource.thermalEnergy() + afterDestination.thermalEnergy());
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= Math.max(1e-12, Math.max(Math.abs(a), Math.abs(b)) * 1e-12);
    }
    private static void positiveFinite(double v, String name) {
        if (!Double.isFinite(v) || v <= 0.0) throw new IllegalArgumentException(name + " must be positive and finite");
    }
    private static void nonnegativeFinite(double v, String name) {
        if (!Double.isFinite(v) || v < 0.0) throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
}
