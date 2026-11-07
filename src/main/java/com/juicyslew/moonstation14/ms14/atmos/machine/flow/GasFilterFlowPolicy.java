package com.juicyslew.moonstation14.ms14.atmos.machine.flow;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransfer;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pure gas-filter tick policy; it deliberately has no power gate (SS14 GasFilterSystem has none). */
public final class GasFilterFlowPolicy {
    public static final double SS14_DEFAULT_TRANSFER_RATE_M3_PER_SECOND = 1.0; // 1000 L/s

    public record Plan(GasMixture inletAfter, GasMixture filterAfter, GasMixture outletAfter,
                       Map<GasType, Double> filteredByGas, Map<GasType, Double> passedByGas,
                       double energyFiltered, double energyPassed) {
        public Plan { filteredByGas = Map.copyOf(filteredByGas); passedByGas = Map.copyOf(passedByGas); }
    }

    private GasFilterFlowPolicy() { }

    public static Optional<Plan> plan(GasMixture inlet, GasMixture filter, GasMixture outlet,
            Optional<GasType> selectedGas, double inletVolumeM3, double filterVolumeM3,
            double outletVolumeM3, double rateM3PerSecond, double pumpSpeedup,
            double deltaSeconds, double maximumPressureKpa) {
        if (inlet == null || filter == null || outlet == null) throw new IllegalArgumentException("mixtures are required");
        selectedGas = selectedGas == null ? Optional.empty() : selectedGas;
        positive(inletVolumeM3, "inletVolumeM3"); positive(filterVolumeM3, "filterVolumeM3"); positive(outletVolumeM3, "outletVolumeM3");
        nonnegative(rateM3PerSecond, "rateM3PerSecond"); nonnegative(pumpSpeedup, "pumpSpeedup"); nonnegative(deltaSeconds, "deltaSeconds"); nonnegative(maximumPressureKpa, "maximumPressureKpa");
        double requestedVolume = rateM3PerSecond * pumpSpeedup * deltaSeconds;
        if (!Double.isFinite(requestedVolume)) throw new IllegalArgumentException("requested volume overflow");
        if (requestedVolume <= 0 || inlet.totalMoles() == 0
                || (outlet.pressureKpa(outletVolumeM3) >= maximumPressureKpa
                && filter.pressureKpa(filterVolumeM3) >= maximumPressureKpa)) return Optional.empty();
        double requestedMoles = inlet.totalMoles() * Math.min(1, requestedVolume / inletVolumeM3);
        GasMixture empty = new GasMixture(Map.of(), inlet.temperatureKelvin(), inletVolumeM3);
        var extraction = PipeGasTransfer.plan(inlet, empty, inletVolumeM3, inletVolumeM3,
                requestedMoles, Double.MAX_VALUE, EnumSet.allOf(GasType.class));
        if (extraction.isEmpty()) return Optional.empty();

        GasMixture packet = extraction.get().packet();
        Optional<PipeGasTransfer.Plan> filtered = Optional.empty();
        if (selectedGas.isPresent() && packet.moles(selectedGas.get()) > 0) {
            GasType gas = selectedGas.get();
            filtered = PipeGasTransfer.plan(packet, filter, inletVolumeM3, filterVolumeM3,
                    packet.moles(gas), maximumPressureKpa, Set.of(gas));
        }
        GasMixture remainingPacket = filtered.map(PipeGasTransfer.Plan::sourceAfter).orElse(packet);
        Set<GasType> passThroughGases = EnumSet.allOf(GasType.class);
        selectedGas.ifPresent(passThroughGases::remove);
        var passed = PipeGasTransfer.plan(remainingPacket, outlet, inletVolumeM3, outletVolumeM3,
                remainingPacket.totalMoles(), maximumPressureKpa, passThroughGases);
        GasMixture outletAfter = passed.map(PipeGasTransfer.Plan::destinationAfter).orElse(outlet);
        GasMixture returnedPacket = passed.map(PipeGasTransfer.Plan::sourceAfter).orElse(remainingPacket);
        GasMixture inletAfter = PipeGasLedger.merge(List.of(extraction.get().sourceAfter(), returnedPacket), inletVolumeM3);
        return Optional.of(new Plan(inletAfter, filtered.map(PipeGasTransfer.Plan::destinationAfter).orElse(filter),
                outletAfter, filtered.map(PipeGasTransfer.Plan::perGasMoved).orElse(Map.of()),
                passed.map(PipeGasTransfer.Plan::perGasMoved).orElse(Map.of()),
                filtered.map(PipeGasTransfer.Plan::energyMoved).orElse(0d),
                passed.map(PipeGasTransfer.Plan::energyMoved).orElse(0d)));
    }

    private static void positive(double value, String name) { if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be positive and finite"); }
    private static void nonnegative(double value, String name) { if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be finite and nonnegative"); }
}
