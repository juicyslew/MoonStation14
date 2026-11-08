package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable fixture-only snapshots of existing committed counters. */
record TegCommittedCounterObservation(Map<String, OwnerCounter> owners) {
    TegCommittedCounterObservation { owners = Map.copyOf(owners); }

    record OwnerCounter(BlockPos position, Object identity, double thermalJoules, double movedMoles,
                        Map<GasType, Double> speciesMoles) {
        OwnerCounter { position = position.immutable(); speciesMoles = Map.copyOf(speciesMoles); }
    }

    static TegCommittedCounterObservation delta(TegCommittedCounterObservation before,
                                                 TegCommittedCounterObservation after) {
        if (!before.owners.keySet().equals(after.owners.keySet()))
            throw new IllegalStateException("committed-counter observation roles changed");
        Map<String, OwnerCounter> result = new LinkedHashMap<>();
        before.owners.forEach((role, a) -> {
            OwnerCounter b = after.owners.get(role);
            if (!a.position().equals(b.position()) || !a.identity().equals(b.identity()))
                throw new IllegalStateException("committed-counter owner changed: " + role);
            if (!Double.isFinite(a.movedMoles()) || !Double.isFinite(b.movedMoles())
                    || a.movedMoles() < 0 || b.movedMoles() < a.movedMoles())
                throw new IllegalStateException("committed moved-moles counter reset or invalid: " + role);
            Map<GasType, Double> species = new java.util.EnumMap<>(GasType.class);
            for (GasType gas : GasType.values())
                species.put(gas, b.speciesMoles().getOrDefault(gas, 0d) - a.speciesMoles().getOrDefault(gas, 0d));
            result.put(role, new OwnerCounter(a.position(), a.identity(),
                    b.thermalJoules() - a.thermalJoules(), b.movedMoles() - a.movedMoles(), species));
        });
        return new TegCommittedCounterObservation(result);
    }
}
