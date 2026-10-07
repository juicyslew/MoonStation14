package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange;

import java.util.Optional;
import java.util.function.Predicate;

/** Explicit finite, contained air inventory; exhaustion is represented as real vacuum. */
public final class FiniteExposureAir implements ExposureAirSource {
    private final double volumeCubicMeters;
    private GasMixture inventory;

    public FiniteExposureAir(GasMixture inventory, double volumeCubicMeters) {
        if (inventory == null || !Double.isFinite(volumeCubicMeters) || volumeCubicMeters <= 0)
            throw new IllegalArgumentException("invalid finite air inventory");
        this.inventory = inventory;
        this.volumeCubicMeters = volumeCubicMeters;
    }

    @Override public synchronized Optional<Sample> sample() {
        return Optional.of(new Sample(inventory, volumeCubicMeters));
    }

    /** Compare-and-replace seam for conserved gas transactions involving this finite inventory. */
    public synchronized boolean replaceInventory(GasMixture expected, GasMixture next) {
        if (expected == null || next == null || inventory != expected) return false;
        inventory = next;
        return true;
    }

    @Override public synchronized Optional<BreathExchange> exchange(double requestedMoles,
            GasMixture exhaled, Predicate<GasMixture> validateInhaled) {
        if (exhaled == null || validateInhaled == null) return Optional.empty();
        Optional<BreathExchange> candidate = BreathExchange.calculate(inventory, requestedMoles, exhaled);
        if (candidate.isEmpty()) return Optional.empty();
        try {
            if (!validateInhaled.test(candidate.get().inhaled())) return Optional.empty();
        } catch (RuntimeException invalid) { return Optional.empty(); }
        inventory = candidate.get().roomAfter();
        return candidate;
    }

    @Override public synchronized boolean exchangeThermalEnergy(double joules) {
        if (!Double.isFinite(joules)) return false;
        try {
            inventory = inventory.withEnergyDelta(joules);
            return true;
        } catch (RuntimeException invalid) { return false; }
    }
}
