package com.juicyslew.moonstation14.ms14.blood;

import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BloodReducerTest {
    @Test void bothPrototypeBloodlossBranchesUseUpstreamFractionScalingWithoutFabricatedCap() throws Exception {
        for (String species : new String[]{"human", "pig"}) {
            String path = "data/moonstation14/moonstation14/character/" + species + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream);
                var policy = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject())
                        .getOrThrow().blood().orElseThrow();
                assertEquals(Map.of("bloodloss", 5f), BloodReducer.bloodloss(0, policy, false));
                assertEquals(0.5f / 0.999f, BloodReducer.bloodloss(.899, policy, false).get("bloodloss"), 1e-6);
                assertTrue(BloodReducer.bloodloss(.9, policy, false).isEmpty());
                assertEquals(-.9f, BloodReducer.bloodloss(.9, policy, true).get("bloodloss"), 1e-6);
                assertEquals(-2f, BloodReducer.bloodloss(2, policy, true).get("bloodloss"));
                assertEquals(.8, BloodSystem.typedDamageBleedRate(0, Map.of("blunt", 10f),
                        policy.damageBleedMultipliers(), policy.maxBleedRate()), 1e-6);
                var blood = ModReagents.createKey("blood");
                var reference = BloodReducer.reference(policy);
                var full = new ReagentAttachment(Map.of(blood, (float)policy.referenceSolution().get("moonstation14:blood").doubleValue() * 2));
                assertEquals(2, BloodReducer.usableFraction(full, reference, policy.maxVolumeModifier()));
            }
        }
    }

    @Test void typedDamageBleedRateClampsHeatOnlyAndMixedSignedCoefficients() {
        assertEquals(0, BloodSystem.typedDamageBleedRate(0, Map.of("heat", 4f),
                Map.of("heat", -0.5), 10));
        assertEquals(0, BloodSystem.typedDamageBleedRate(0.25, Map.of("heat", 4f, "blunt", 2f),
                Map.of("heat", -0.5, "blunt", 0.1), 10));
        assertEquals(0.2, BloodSystem.typedDamageBleedRate(0.25, Map.of("heat", 4f, "blunt", 2f),
                Map.of("heat", -0.5, "blunt", 0.975), 10), 0.000001);
    }

    @Test void usableFractionIsLimitedByMinimumReferenceConstituent() {
        var a = ModReagents.createKey("blood");
        var b = ModReagents.createKey("water");
        var mixture = new ReagentAttachment(Map.of(a, 9f, b, 2f, ModReagents.createKey("extra"), 100f));
        assertEquals(.2, BloodReducer.usableFraction(mixture, Map.of(a, 1000L, b, 1000L)), .00001);
        mixture.removeUnits(b, 200);
        assertEquals(0, BloodReducer.usableFraction(mixture, Map.of(a, 1000L, b, 1000L)));
    }

    @Test void effectScaleIsAppliedOnceAndRestorationIsProportionalCappedAndBoundedByReference() {
        var a = ModReagents.createKey("blood");
        var b = ModReagents.createKey("water");
        var reference = Map.of(a, 1_000L, b, 1_000L);
        assertEquals(300L, BloodReducer.scaledEffectUnits(1.5f, 2f));
        var mixture = new ReagentAttachment(Map.of(a, 2f, b, 2f));
        assertEquals(100L, BloodReducer.restoreTowardReference(mixture, reference, 100L, 10_000L));
        assertEquals(Map.of(a, 250L, b, 250L), mixture.snapshotUnits());
        assertEquals(100L, BloodReducer.restoreTowardReference(mixture, reference, 1_000L, 600L));
        assertEquals(600L, mixture.totalUnits(), "capacity limits total refresh");
        assertEquals(1_400L, BloodReducer.restoreTowardReference(mixture, reference, 5_000L, 10_000L));
        assertEquals(2_000L, mixture.totalUnits(), "refresh does not exceed reference composition");
    }

    @Test void negativeBloodLevelEffectRemovesActualCompositionProportionally() {
        var a = ModReagents.createKey("blood");
        var b = ModReagents.createKey("water");
        var mixture = new ReagentAttachment(Map.of(a, 3f, b, 1f));
        assertEquals(200L, ReagentUnits.total(
                mixture.splitUnits(BloodReducer.scaledEffectUnits(-1f, 2f)).values()));
        assertEquals(Map.of(a, 150L, b, 50L), mixture.snapshotUnits());
    }

    @Test void bleedRemovalAccountsForExactDiscardedCompositionWithoutCreatingOrRetainingMaterial() {
        var a = ModReagents.createKey("blood");
        var b = ModReagents.createKey("water");
        var c = ModReagents.createKey("extra");
        var mixture = new ReagentAttachment(Map.of(a, 1f, b, 2f, c, 3f));
        var starting = mixture.snapshotUnits();

        var discarded = BloodReducer.removeBleedUnits(mixture, 300L);

        assertEquals(Map.of(a, 50L, b, 100L, c, 150L), discarded);
        assertEquals(Map.of(a, 50L, b, 100L, c, 150L), mixture.snapshotUnits());
        assertEquals(300L, ReagentUnits.total(discarded.values()));
        assertEquals(ReagentUnits.total(starting.values()),
                ReagentUnits.total(discarded.values()) + mixture.totalUnits(),
                "the removed mixture plus final bloodstream must equal the starting solution");

        // A later update removes only what remains; discarded material is not stored or restored.
        var discardedAgain = BloodReducer.removeBleedUnits(mixture, 500L);
        assertEquals(Map.of(a, 50L, b, 100L, c, 150L), discardedAgain);
        assertTrue(mixture.isEmpty());
        assertEquals(600L, ReagentUnits.total(discarded.values()) + ReagentUnits.total(discardedAgain.values()));
    }

    @Test void temporaryBleedStagesExactMixedCentsWithoutBackpressure() {
        var a = ModReagents.createKey("blood");
        var b = ModReagents.createKey("water");
        var blood = new ReagentAttachment(Map.of(a, 3f, b, 1f));
        var pending = new ReagentAttachment(Map.of(a, .25f, b, .25f));
        var beforeTotal = blood.totalUnits() + pending.totalUnits();

        var stage = BloodReducer.stageBleed(blood, pending, 200L);

        assertEquals(200L, stage.removedUnits());
        assertEquals(Map.of(a, 175L, b, 75L), stage.pending().snapshotUnits());
        assertEquals(Map.of(a, 150L, b, 50L), stage.bloodstream().snapshotUnits());
        assertEquals(beforeTotal, stage.bloodstream().totalUnits() + stage.pending().totalUnits());

        var next = BloodReducer.stageBleed(stage.bloodstream(), stage.pending(), 100L);
        assertEquals(100L, next.removedUnits());
        assertEquals(Map.of(a, 250L, b, 100L), next.pending().snapshotUnits());
        assertEquals(Map.of(a, 75L, b, 25L), next.bloodstream().snapshotUnits());
        assertEquals(beforeTotal, next.pending().totalUnits() + next.bloodstream().totalUnits());
    }

    @Test void savedLargeTemporaryBatchIsNotAFlowLimit() {
        var a = ModReagents.createKey("blood");
        var blood = new ReagentAttachment(Map.of(a, 3f));
        var pending = new ReagentAttachment(Map.of(a, 2.5f));
        var stage = BloodReducer.stageBleed(blood, pending, 100L);
        assertEquals(100L, stage.removedUnits());
        assertEquals(350L, stage.pending().totalUnits());
        assertEquals(200L, stage.bloodstream().totalUnits());
    }
}
