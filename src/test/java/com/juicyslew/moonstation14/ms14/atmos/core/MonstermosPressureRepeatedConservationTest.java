package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Operation-local IEEE-754 audit for the repeated split/merge packet lifecycle. */
class MonstermosPressureRepeatedConservationTest {
    private static final double CLEANUP_MOLES = GasMixture.GAS_MIN_MOLES;

    @Test
    void repeatedPacketSplitAndMergeDriftFitsCountedFloatOperationBound() {
        GasMixture original = mixture(37.1234567, 91.7654321, 293.15);
        GasMixture remainder = original;
        double signedOxygen = 0, absoluteOxygen = 0, signedEnergy = 0, absoluteEnergy = 0;
        double oxygenBound = 0, energyBound = 0;
        int faces = 0, cleanupOpportunities = 0;
        for (int i = 0; i < 160; i++) {
            GasMixture before = remainder;
            GasMixture.Removal split = before.splitRatio((float) (0.013 + (i % 11) * 0.001));
            GasMixture after = split.remaining().mergedWith(split.removed());
            double speciesDelta = after.moles(GasType.OXYGEN) - before.moles(GasType.OXYGEN);
            double energyDelta = after.thermalEnergy() - before.thermalEnergy();
            signedOxygen += speciesDelta;
            absoluteOxygen += Math.abs(speciesDelta);
            signedEnergy += energyDelta;
            absoluteEnergy += Math.abs(energyDelta);
            // A face does packet scaling, source multiplication/subtraction, receiver addition,
            // and storage casts: <=8 half-ulp errors; cleanup independently drops packet and
            // remainder at the explicit 5e-8 species cutoff.
            oxygenBound += 4 * Math.ulp((float) Math.max(before.moles(GasType.OXYGEN),
                    after.moles(GasType.OXYGEN))) + 2 * CLEANUP_MOLES;
            // Thermal merge has at most 9 Cp products + 9-term sum, weighted-T products/add/divide,
            // and Cp*T observation. 32 half-ulps bounds those float roundings at the largest local E.
            energyBound += 16 * Math.ulp((float) Math.max(before.thermalEnergy(), after.thermalEnergy()));
            cleanupOpportunities += 2;
            faces++;
            remainder = after;
        }
        assertTrue(Double.isFinite(signedOxygen) && Double.isFinite(signedEnergy));
        assertTrue(Math.abs(signedOxygen) <= oxygenBound,
                "signed O2 drift=" + signedOxygen + " abs-stage drift=" + absoluteOxygen
                        + " bound=" + oxygenBound + " faces=" + faces + " cleanup opportunities=" + cleanupOpportunities);
        assertTrue(absoluteOxygen <= oxygenBound, "absolute stage O2 drift=" + absoluteOxygen + " bound=" + oxygenBound);
        assertTrue(Math.abs(signedEnergy) <= energyBound,
                "signed energy drift=" + signedEnergy + " abs-stage drift=" + absoluteEnergy
                        + " bound=" + energyBound + " faces=" + faces);
        assertTrue(absoluteEnergy <= energyBound, "absolute stage energy drift=" + absoluteEnergy + " bound=" + energyBound);
    }

    @Test
    void repeatedPressureAndLindaStagesConserveAgainstActualKernelOperations() {
        // A bounded six-face graph repeatedly exercises pressure routes and LINDA faces.
        List<BlockPos> ring = new ArrayList<>();
        for (int i = 0; i < 20; i++) ring.add(new BlockPos(i, 0, 0));
        Map<BlockPos, Set<BlockPos>> adjacency = new LinkedHashMap<>();
        for (int i = 0; i < ring.size(); i++) {
            Set<BlockPos> neighbors = new LinkedHashSet<>();
            if (i > 0) neighbors.add(ring.get(i - 1));
            if (i + 1 < ring.size()) neighbors.add(ring.get(i + 1));
            adjacency.put(ring.get(i), neighbors);
        }
        Map<BlockPos, GasMixture> state = new LinkedHashMap<>();
        for (int i = 0; i < ring.size(); i++)
            state.put(ring.get(i), mixture(15 + (i % 5) * 3.713, 30 + (i % 7) * 2.179, 280 + (i % 4) * 17.31));
        Audit audit = new Audit(state);
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        for (int step = 0; step < 160; step++) {
            var pressure = MonstermosPressureEqualization.equalizeDiscovered(state, adjacency, ring.get(step % ring.size()), Set.of());
            state = new LinkedHashMap<>(pressure.transport().states());
            audit.record("pressure", state, pressure.transport().work().transferOperations());
            var linda = LindaGasSharing.processPressureAware(state, state, adjacency, Set.copyOf(ring), Set.of(), Set.of(), ring.size());
            state = new LinkedHashMap<>(linda.mixtures());
            audit.record("LINDA", state, linda.sharedPairs().size());
            for (LindaGasSharing.Pair pair : linda.sharedPairs())
                groups.onShare(pair, linda.absoluteMovedMolesByPair().getOrDefault(pair, 0.0), state);
            if (step % 15 == 14) {
                var grouped = groups.advanceFullCycle(state, ring.size());
                state.putAll(grouped.replacements());
                int memberOperations = grouped.replacements().size();
                audit.record("group", state, memberOperations);
            }
        }
        assertTrue(audit.groupMembers > 0, "fixture must exercise finite excited-group averaging");
        audit.assertWithinBound();
    }

    @Test
    void perStageAuditRejectsLargeOppositeDriftsEvenWhenTheyWouldCancel() {
        BlockPos pos = new BlockPos(0, 0, 0);
        Map<BlockPos, GasMixture> start = Map.of(pos, mixture(10.0, 20.0, 293.15));
        Audit audit = new Audit(start);
        Map<BlockPos, GasMixture> drifted = Map.of(pos, mixture(9.0, 20.0, 293.15));
        assertThrows(AssertionError.class, () -> audit.record("synthetic unauthorized drift", drifted, 0));
        Map<BlockPos, GasMixture> restored = Map.of(pos, mixture(10.0, 20.0, 293.15));
        assertTrue(amount(restored, GasType.OXYGEN) == amount(start, GasType.OXYGEN),
                "the synthetic + and - stage changes would cancel at the endpoint, but the first stage is rejected");
    }

    private static final class Audit {
        private final Map<GasType, Double> baseline = new EnumMap<>(GasType.class);
        private final Map<GasType, Double> previous = new EnumMap<>(GasType.class);
        private final double baselineEnergy;
        private double previousEnergy;
        private double allowedEnergy, signedEnergy, absoluteStageEnergy;
        private final Map<GasType, Double> allowedSpecies = new EnumMap<>(GasType.class);
        private final Map<GasType, Double> signedSpecies = new EnumMap<>(GasType.class);
        private final Map<GasType, Double> absoluteStageSpecies = new EnumMap<>(GasType.class);
        private int operations;
        private int groupMembers;

        private Audit(Map<BlockPos, GasMixture> initial) {
            for (GasType gas : GasType.values()) {
                double value = amount(initial, gas);
                baseline.put(gas, value);
                previous.put(gas, value);
            }
            baselineEnergy = previousEnergy = energy(initial);
        }

        private void record(String stage, Map<BlockPos, GasMixture> states, int actualOperations) {
            if (stage.equals("group")) groupMembers += actualOperations;
            double currentEnergy = energy(states);
            double energyDelta = currentEnergy - previousEnergy;
            signedEnergy += energyDelta;
            absoluteStageEnergy += Math.abs(energyDelta);
            double magnitudeEnergy = Math.max(previousEnergy, currentEnergy);
            // Per operation: 9 products plus 9-term Cp sum (5 ulps), 2 weighted-energy
            // products + add + division (2 ulps), and Cp*T observer (0.5 ulp). 16 ulps
            // conservatively bounds the arithmetic at total closed-system E, >= any operand.
            double energyAllowance = actualOperations * 16 * Math.ulp((float) magnitudeEnergy);
            allowedEnergy += energyAllowance;
            assertTrue(Math.abs(energyDelta) <= energyAllowance, stage + " energy stage drift=" + energyDelta
                    + " bound=" + energyAllowance + " operations=" + actualOperations);
            for (GasType gas : GasType.values()) {
                double current = amount(states, gas);
                double delta = current - previous.get(gas);
                signedSpecies.merge(gas, delta, Double::sum);
                absoluteStageSpecies.merge(gas, Math.abs(delta), Double::sum);
                double magnitude = Math.max(previous.get(gas), current);
                // Scaling, source split and receiver merge use <=8 half-ulp roundings per face.
                // Independent packet/remainder cleanup contributes 2 * 5e-8 per face.
                double allowance = actualOperations * (4 * Math.ulp((float) magnitude)
                        + 2 * GasMixture.GAS_MIN_MOLES);
                allowedSpecies.merge(gas, allowance, Double::sum);
                assertTrue(Math.abs(delta) <= allowance, stage + " " + gas + " stage drift=" + delta
                        + " bound=" + allowance + " operations=" + actualOperations);
                previous.put(gas, current);
            }
            previousEnergy = currentEnergy;
            operations += actualOperations;
        }

        private void assertWithinBound() {
            for (GasType gas : GasType.values()) {
                assertTrue(Math.abs(signedSpecies.getOrDefault(gas, 0.0)) <= allowedSpecies.getOrDefault(gas, 0.0),
                        gas + " signed drift=" + signedSpecies.getOrDefault(gas, 0.0) + " abs-stage="
                                + absoluteStageSpecies.getOrDefault(gas, 0.0) + " allowance=" + allowedSpecies.getOrDefault(gas, 0.0)
                                + " operations=" + operations);
                assertTrue(absoluteStageSpecies.getOrDefault(gas, 0.0) <= allowedSpecies.getOrDefault(gas, 0.0),
                        gas + " cumulative absolute stage drift=" + absoluteStageSpecies.getOrDefault(gas, 0.0)
                                + " allowance=" + allowedSpecies.getOrDefault(gas, 0.0));
                assertTrue(Math.abs(signedSpecies.getOrDefault(gas, 0.0)
                                - (previous.get(gas) - baseline.get(gas))) <= 1e-9,
                        gas + " stage deltas do not telescope to final residual");
            }
            assertTrue(Math.abs(signedEnergy) <= allowedEnergy,
                    "signed raw energy drift=" + signedEnergy + " abs-stage=" + absoluteStageEnergy
                            + " allowance=" + allowedEnergy + " operations=" + operations);
            assertTrue(absoluteStageEnergy <= allowedEnergy, "cumulative absolute stage energy drift="
                    + absoluteStageEnergy + " allowance=" + allowedEnergy);
            assertTrue(Math.abs(signedEnergy - (previousEnergy - baselineEnergy)) <= 1e-7,
                    "energy stage deltas do not telescope to final residual");
        }
    }

    private static double amount(Map<BlockPos, GasMixture> states, GasType gas) {
        return states.values().stream().mapToDouble(m -> m.moles(gas)).sum();
    }

    private static double energy(Map<BlockPos, GasMixture> states) {
        return states.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
    }

    private static GasMixture mixture(double oxygen, double nitrogen, double temperature) {
        Map<GasType, Double> gases = new EnumMap<>(GasType.class);
        gases.put(GasType.OXYGEN, oxygen);
        gases.put(GasType.NITROGEN, nitrogen);
        return new GasMixture(gases, temperature);
    }
}
