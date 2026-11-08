package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.character.components.AbsorbentCleaningComponent;
import com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent;
import com.juicyslew.moonstation14.ms14.mobs.NpcTaskPlanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;

class AbsorbentCleaningComponentTest {
    @Test void moproachResolvedProfileAndDeadlinesRoundTrip() {
        var profile = new AbsorbentCleaningComponent(200, 5000, 10, 1000, 1000, 20);
        assertEquals(profile, AbsorbentCleaningComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"type\":\"AbsorbentCleaning\",\"pickupCents\":200,\"capacityCents\":5000,\"cooldownTicks\":10,\"regenerationCents\":1000,\"purgeCents\":1000,\"cycleTicks\":20}"))
                .result().orElseThrow());
        assertEquals(AbsorbentCleaningAttachment.EMPTY, AbsorbentCleaningAttachment.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{}"))
                .result().orElseThrow());
        assertEquals(new AbsorbentCleaningAttachment(80, 120), AbsorbentCleaningAttachment.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"nextUseTick\":80,\"nextCycleTick\":120}"))
                .result().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new AbsorbentCleaningComponent(201, 200, 10, 1000, 1000, 20));
    }

    @Test void cleanTaskSelectsOnlyAdmittedVisibleCandidate() {
        var policy = new NpcBehaviorComponent(List.of(NpcBehaviorComponent.Task.CLEAN,
                NpcBehaviorComponent.Task.IDLE), 10, 1, 7, 9, 40, 140);
        var candidate = new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.CLEAN, 2, 0,
                false, true, true);
        var choice = NpcTaskPlanner.select(policy, 0, 0, 10, 10, List.of(candidate)).orElseThrow();
        assertEquals(NpcBehaviorComponent.Task.CLEAN, choice.task());
        assertEquals(NpcBehaviorComponent.Task.IDLE, NpcTaskPlanner.select(policy, 0, 0, 10, 10,
                List.of(new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.CLEAN, 2, 0, false, false, true)))
                .orElseThrow().task());
    }

    @Test void exchangeConservesEachReagentAndPeriodicPurgePreservesRegeneratedWater() {
        var water = ModReagents.createKey("water");
        var contaminant = ModReagents.createKey("cleaning_test_contaminant");
        var profile = new AbsorbentCleaningComponent(200, 5000, 10, 100, 100, 20);
        var puddle = new ReagentAttachment(Map.of(water, 10f, contaminant, 3f));
        var cleaner = new ReagentAttachment(Map.of(water, 2f));
        var transfer = AbsorbentCleaningPolicy.prepare(puddle, cleaner, profile, water, 100_000);
        assertNotNull(transfer);
        assertEquals(200, transfer.transferredCents());
        assertEquals(puddle.totalUnits() + cleaner.totalUnits(),
                transfer.puddleAfter().totalUnits() + transfer.cleanerAfter().totalUnits());
        assertEquals(puddle.snapshotUnits().getOrDefault(water, 0L) + cleaner.snapshotUnits().getOrDefault(water, 0L),
                transfer.puddleAfter().snapshotUnits().getOrDefault(water, 0L) + transfer.cleanerAfter().snapshotUnits().getOrDefault(water, 0L));
        assertEquals(200, transfer.cleanerAfter().snapshotUnits().get(contaminant));

        var loaded = new ReagentAttachment(Map.of(contaminant, 2f));
        var cycled = AbsorbentCleaningPolicy.cycle(loaded, profile, water);
        assertEquals(100, cycled.snapshotUnits().get(water));
        assertEquals(100, cycled.snapshotUnits().get(contaminant));
        assertEquals(loaded.totalUnits(), cycled.totalUnits());
    }
}
