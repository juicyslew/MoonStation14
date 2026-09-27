package com.juicyslew.moonstation14.ms14.atmos.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

/** Pure equalization kernel for a complete, finite connected set of atmosphere cells. */
public final class BoundedGasEqualizer {
    public static final int MAX_EQUALIZE_CELLS = 800;
    /** Discovery is an adapter-side budget; reaching it does not imply that a region is exterior. */
    public static final int MAX_DISCOVERY_CELLS = 8000;

    private static final Comparator<GasMixture> CANONICAL_ORDER = (left, right) -> {
        int comparison = Double.compare(left.totalMoles(), right.totalMoles());
        if (comparison != 0) return comparison;
        for (GasType type : GasType.values()) {
            comparison = Double.compare(left.moles(type), right.moles(type));
            if (comparison != 0) return comparison;
        }
        return Double.compare(left.temperatureKelvin(), right.temperatureKelvin());
    };

    private BoundedGasEqualizer() { }

    /**
     * Equalizes total moles to the region average while transporting donor composition and
     * enthalpy. Donors and receivers are processed in canonical state order (moles, species in
     * enum order, then temperature), making results independent of the caller's list order except
     * for indistinguishable cells. Results retain the caller's original cell ordering.
     *
     * @throws IllegalArgumentException when the input is null, empty, oversized, contains null,
     *                                   or has aggregate values that cannot be represented finitely
     */
    public static List<GasMixture> equalize(List<GasMixture> cells) {
        if (cells == null || cells.isEmpty() || cells.size() > MAX_EQUALIZE_CELLS) {
            throw new IllegalArgumentException("Cell count must be between 1 and " + MAX_EQUALIZE_CELLS);
        }

        List<Cell> sorted = new ArrayList<>(cells.size());
        double totalMoles = 0.0;
        for (int index = 0; index < cells.size(); index++) {
            GasMixture mixture = cells.get(index);
            if (mixture == null) throw new IllegalArgumentException("Cells cannot contain null");
            totalMoles += mixture.totalMoles();
            if (!Double.isFinite(totalMoles)) {
                throw new IllegalArgumentException("Region aggregate moles must be finite");
            }
            sorted.add(new Cell(index, mixture));
        }
        sorted.sort(Comparator.comparing(Cell::mixture, CANONICAL_ORDER));

        double target = totalMoles / cells.size();
        List<Cell> donors = new ArrayList<>();
        List<Cell> receivers = new ArrayList<>();
        for (Cell cell : sorted) {
            double difference = cell.mixture.totalMoles() - target;
            if (difference > 0.0) {
                donors.add(cell.withTransfer(difference));
            } else if (difference < 0.0) {
                receivers.add(cell.withTransfer(-difference));
            }
        }
        if (donors.isEmpty()) return List.copyOf(cells);

        List<EnumMap<GasType, Double>> incoming = new ArrayList<>(receivers.size());
        double[] incomingEnergy = new double[receivers.size()];
        for (int i = 0; i < receivers.size(); i++) incoming.add(new EnumMap<>(GasType.class));
        List<GasMixture> output = new ArrayList<>(java.util.Collections.nCopies(cells.size(), null));

        int receiverIndex = 0;
        double receiverRemaining = receivers.isEmpty() ? 0.0 : receivers.get(0).transfer;
        for (Cell donor : donors) {
            double removed = donor.transfer;
            GasMixture remnant = donor.mixture.withScaledMoles((donor.mixture.totalMoles() - removed)
                    / donor.mixture.totalMoles());
            output.set(donor.originalIndex, remnant);
            double fractionRemaining = 1.0;
            while (fractionRemaining > 0.0 && receiverIndex < receivers.size()) {
                double portion = Math.min(removed * fractionRemaining, receiverRemaining);
                double fraction = portion / donor.mixture.totalMoles();
                for (GasType type : GasType.values()) {
                    double amount = donor.mixture.moles(type) * fraction;
                    if (amount > 0.0) {
                        EnumMap<GasType, Double> composition = incoming.get(receiverIndex);
                        composition.merge(type, amount, Double::sum);
                    }
                }
                incomingEnergy[receiverIndex] += donor.mixture.thermalEnergy()
                        * (portion / donor.mixture.totalMoles());
                fractionRemaining -= portion / removed;
                receiverRemaining -= portion;
                if (receiverRemaining <= Math.ulp(receivers.get(receiverIndex).transfer) * 4.0) {
                    receiverIndex++;
                    if (receiverIndex < receivers.size()) receiverRemaining = receivers.get(receiverIndex).transfer;
                }
            }
        }

        for (Cell cell : sorted) {
            if (output.get(cell.originalIndex) == null) output.set(cell.originalIndex, cell.mixture);
        }
        for (int i = 0; i < receivers.size(); i++) {
            Cell receiver = receivers.get(i);
            EnumMap<GasType, Double> composition = new EnumMap<>(GasType.class);
            composition.putAll(receiver.mixture.gasMoles());
            incoming.get(i).forEach((type, amount) -> composition.merge(type, amount, Double::sum));
            double energy = receiver.mixture.thermalEnergy() + incomingEnergy[i];
            double capacity = 0.0;
            for (var entry : composition.entrySet()) capacity += entry.getValue() * entry.getKey().molarHeatCapacity();
            GasMixture result = capacity == 0.0
                    ? new GasMixture(composition, receiver.mixture.temperatureKelvin())
                    : new GasMixture(composition, energy / capacity);
            output.set(receiver.originalIndex, result);
        }
        return List.copyOf(output);
    }

    private static final class Cell {
        private final int originalIndex;
        private final GasMixture mixture;
        private final double transfer;
        private Cell(int originalIndex, GasMixture mixture) {
            this(originalIndex, mixture, 0.0);
        }

        private Cell(int originalIndex, GasMixture mixture, double transfer) {
            this.originalIndex = originalIndex;
            this.mixture = mixture;
            this.transfer = transfer;
        }

        private Cell withTransfer(double amount) {
            return new Cell(originalIndex, mixture, amount);
        }

        private GasMixture mixture() { return mixture; }
    }
}
