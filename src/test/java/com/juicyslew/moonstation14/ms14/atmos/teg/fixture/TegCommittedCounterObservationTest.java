package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TegCommittedCounterObservationTest {
    @Test void deltaKeepsIdentityAndSubtractsOnlyImmutableCapturedCounters() {
        Object identity = new Object();
        var baseline = new TegCommittedCounterObservation(Map.of("injector",
                 new TegCommittedCounterObservation.OwnerCounter(BlockPos.ZERO, identity, 12, 4,
                        Map.of(GasType.PLASMA, 2d))));
        var end = new TegCommittedCounterObservation(Map.of("injector",
                 new TegCommittedCounterObservation.OwnerCounter(BlockPos.ZERO, identity, 20, 9,
                        Map.of(GasType.PLASMA, 5d, GasType.OXYGEN, 1d))));
        var change = TegCommittedCounterObservation.delta(baseline, end).owners().get("injector");
        assertSame(identity, change.identity());
        assertEquals(8, change.thermalJoules());
        assertEquals(5, change.movedMoles());
        assertEquals(3, change.speciesMoles().get(GasType.PLASMA));
        assertEquals(1, change.speciesMoles().get(GasType.OXYGEN));
        assertThrows(UnsupportedOperationException.class, () -> change.speciesMoles().put(GasType.NITROGEN, 4d));
        assertThrows(IllegalStateException.class, () -> TegCommittedCounterObservation.delta(baseline,
                new TegCommittedCounterObservation(Map.of("injector", new TegCommittedCounterObservation.OwnerCounter(
                         BlockPos.ZERO, new Object(), 20, 9, Map.of())))));
    }

    @Test void rejectsMovedMolesReset() {
        Object identity = new Object();
        var before = new TegCommittedCounterObservation(Map.of("pump", new TegCommittedCounterObservation.OwnerCounter(BlockPos.ZERO, identity, 0, 2, Map.of())));
        var after = new TegCommittedCounterObservation(Map.of("pump", new TegCommittedCounterObservation.OwnerCounter(BlockPos.ZERO, identity, 0, 1, Map.of())));
        assertThrows(IllegalStateException.class, () -> TegCommittedCounterObservation.delta(before, after));
    }
}
