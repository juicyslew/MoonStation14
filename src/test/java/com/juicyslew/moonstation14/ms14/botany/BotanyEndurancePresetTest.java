package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BotanyEndurancePresetTest {
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("moonstation14",path);}

    @Test void oneRealSamplingPlanProducesDifferentExistingHealthWarningsForMatchedPresets() {
        var baseline=new GeneticValue(id("wheat"),resolvedWheat());
        var endurance=BotanyFixtureScenarios.wheatTraitPresets(baseline).endurance();
        var original=baseline.profile(); var changed=endurance.profile(); var b=original.biology();
        assertEquals(100f,b.endurance()); assertEquals(50f,changed.biology().endurance());
        assertEquals(new SeedProfile.Biology(50,b.nutrientConsumption(),b.waterConsumption(),b.idealHeat(),b.heatTolerance(),
                b.lowPressure(),b.highPressure(),b.weedTolerance(),b.weedHighLevel(),b.toxinTolerance(),b.pestTolerance()),changed.biology());
        assertEquals(original.lifecycle(),changed.lifecycle()); assertEquals(original.packet(),changed.packet());
        assertEquals(original.product(),changed.product()); assertEquals(original.visualKey(),changed.visualKey());
        assertEquals(original.chemicals(),changed.chemicals()); assertEquals(original.consumeGasses(),changed.consumeGasses());
        assertEquals(original.exudeGasses(),changed.exudeGasses()); assertEquals(original.mutationTargets(),changed.mutationTargets());
        assertEquals(original.scream(),changed.scream()); assertEquals(100f,baseline.profile().biology().endurance());

        var v=prepared(baseline); var ac=prepared(endurance);
        assertEquals(2,PlantMetabolismEvaluator.rawGrowthStage(v,v.effectiveProfile()));
        assertEquals(v.age(),ac.age()); assertEquals(100f,v.health()); assertEquals(50f,ac.health());
        assertEquals(v.water(),ac.water()); assertEquals(v.nutrition(),ac.nutrition());
        assertEquals(0,PlantMetabolismEvaluator.warningBits(v,v.effectiveProfile(),true,Optional.empty())&2);
        assertEquals(0,PlantMetabolismEvaluator.warningBits(ac,ac.effectiveProfile(),true,Optional.empty())&2);

        for(int damageRoll:new int[]{3,4}) {
            var vPlan=PlantSamplingPlan.prepare(v,damageRoll); var acPlan=PlantSamplingPlan.prepare(ac,damageRoll);
            assertNotNull(vPlan); assertNotNull(acPlan);
            assertEquals(damageRoll*10f,vPlan.healthLoss()); assertEquals(damageRoll*10f,acPlan.healthLoss());
            assertEquals(100f-damageRoll*10f,vPlan.packetHealthOverride());
            assertEquals(50f-damageRoll*10f,acPlan.packetHealthOverride());
            assertTrue(vPlan.postCommitHealth()>50f); assertTrue(acPlan.postCommitHealth()>0f);
            applySamplingPlan(v,vPlan); applySamplingPlan(ac,acPlan);
            assertEquals(0,PlantMetabolismEvaluator.warningBits(v,v.effectiveProfile(),true,Optional.empty())&2);
            assertNotEquals(0,PlantMetabolismEvaluator.warningBits(ac,ac.effectiveProfile(),true,Optional.empty())&2);
            assertFalse(v.dead()); assertFalse(ac.dead());
            // New, identically prepared holders model independent first-sample comparisons for the other roll.
            v=prepared(baseline); ac=prepared(endurance);
        }
    }
    private static void applySamplingPlan(PlantInstance plant,PlantSamplingPlan.Plan plan) {
        plant.samplingHealth(plan.postCommitHealth()); plant.clampCareHealth(plan.endurance());
    }
    private static PlantInstance prepared(GeneticValue genetics) {
        PlantInstance plant=new PlantInstance(0,0,0); plant.plant(genetics,0);
        BotanyFixtureScenarios.prepareStageHolder(plant,genetics); return plant;
    }
    private static SeedProfile resolvedWheat(){
        return BotanyTestSeedFixtures.resolved(id("wheat"));
    }
}
