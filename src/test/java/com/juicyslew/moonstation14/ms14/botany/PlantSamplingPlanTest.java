package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlantSamplingPlanTest {
    private static PlantInstance plant(float health, float age, boolean ready, boolean dead) {
        return plant(health, age, ready, dead, 3, 12);
    }
    private static PlantInstance plant(float health, float age, boolean ready, boolean dead, int stages, int maturation) {
        var profile = new SeedProfile(ResourceLocation.withDefaultNamespace("wheat_seeds"),
                ResourceLocation.withDefaultNamespace("wheat"), "wheat",
                new SeedProfile.Biology(100,1,1,1,1,1,1,1,1,1,1),
                new SeedProfile.Lifecycle(stages,20,maturation,22,23,24,SeedProfile.Harvest.NO_REPEAT,false,true),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.List.of(),
                SeedProfile.Scream.DISABLED, "test-sampling-plan");
        var plant = new PlantInstance(0, 0, 0);
        plant.plant(ResourceLocation.withDefaultNamespace("wheat"), profile, 0);
        plant.state(age, health, 0, 0, ready, dead);
        return plant;
    }
    @Test void readinessControlsRawPacketHealthAndPlanDoesNotMutateSource() {
        var growing = plant(25, 8, false, false);
        var plan = PlantSamplingPlan.prepare(growing, 4);
        assertNotNull(plan);
        assertEquals(40f, plan.healthLoss());
        assertEquals(-15f, plan.packetHealthOverride());
        assertEquals(0f, plan.postCommitHealth());
        assertEquals(25f, growing.health());
        assertNull(PlantSamplingPlan.prepare(plant(25, 8, true, false), 3).packetHealthOverride());
    }
    @Test void invalidPlantsAreRejectedBeforeDamagePlanning() {
        assertNull(PlantSamplingPlan.prepare(null, 3));
        assertNull(PlantSamplingPlan.prepare(plant(100, 8, false, true), 3));
        var sampled = plant(100, 8, false, false); sampled.sampled(true);
        assertNull(PlantSamplingPlan.prepare(sampled, 3));
        assertThrows(IllegalArgumentException.class, () -> PlantSamplingPlan.prepare(plant(100, 8, false, false), 5));
    }
    @Test void sourceRawGrowthStageIsUncappedAndNotTheAgeProxyOrVisualStage() {
        var slow = plant(100, 2, false, false, 3, 12);
        assertEquals(1, PlantMetabolismEvaluator.rawGrowthStage(slow, slow.effectiveProfile()));
        assertFalse(PlantSamplingPlan.isEligible(slow));
        slow.state(8, 100, 0, 0, false, false);
        assertEquals(2, PlantMetabolismEvaluator.rawGrowthStage(slow, slow.effectiveProfile()));
        assertTrue(PlantSamplingPlan.isEligible(slow));
        var oneStage = plant(100, 8, false, false, 1, 4);
        assertEquals(2, PlantMetabolismEvaluator.rawGrowthStage(oneStage, oneStage.effectiveProfile()));
        assertEquals(1, PlantMetabolismEvaluator.appearance(oneStage, oneStage.effectiveProfile()).stage());
        assertTrue(PlantSamplingPlan.isEligible(oneStage));
    }
    @Test void finalHealthClampIsUnconditionalWhenSampledRollIsFalseAndDoesNotKillImmediately() {
        var plant = plant(-15, 8, false, false);
        plant.clampCareHealth(100);
        assertEquals(0f, plant.health());
        assertFalse(plant.sampled());
        assertFalse(plant.dead());
    }
    @Test void sampledFlagHasLegacyDefaultAndResetsOnlyOnClearOrNewPlant() {
        var original = plant(100, 2, false, false);
        original.sampled(true);
        var encoded = PlantInstance.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, original).getOrThrow();
        var restored = PlantInstance.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.sampled());
        var legacy = encoded.deepCopy();
        var legacyObject = legacy.getAsJsonObject();
        if (legacyObject.has("mutation")) legacyObject.getAsJsonObject("mutation").remove("sampled");
        assertFalse(PlantInstance.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, legacy).getOrThrow().sampled());
        original.transitionSpecies(original.genetics());
        assertTrue(original.sampled());
        original.clearPlant();
        assertFalse(original.sampled());
    }
}
