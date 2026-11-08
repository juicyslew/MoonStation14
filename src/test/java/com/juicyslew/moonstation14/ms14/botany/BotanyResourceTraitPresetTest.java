package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class BotanyResourceTraitPresetTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14",path); }

    @Test void independentWheatResourcePresetsChangeOnlyTheirNamedBiologyValue() {
        var base=new GeneticValue(id("wheat"),resolvedWheat()); var original=base.profile(); var biology=original.biology();
        assertEquals(.5f,biology.waterConsumption()); assertEquals(.4f,biology.nutrientConsumption());
        var presets=BotanyFixtureScenarios.wheatTraitPresets(new GeneticValue(id("wheat"),original));
        var water=presets.water(); var nutrient=presets.nutrient();
        assertEquals(.9f,water.profile().biology().waterConsumption());
        assertEquals(.4f,water.profile().biology().nutrientConsumption());
        assertEquals(.5f,nutrient.profile().biology().waterConsumption());
        assertEquals(1.2f,nutrient.profile().biology().nutrientConsumption());
        assertEquals(new SeedProfile.Biology(biology.endurance(),biology.nutrientConsumption(),.9f,biology.idealHeat(),biology.heatTolerance(),
                biology.lowPressure(),biology.highPressure(),biology.weedTolerance(),biology.weedHighLevel(),biology.toxinTolerance(),biology.pestTolerance()),
                water.profile().biology());
        assertEquals(new SeedProfile.Biology(biology.endurance(),1.2f,biology.waterConsumption(),biology.idealHeat(),biology.heatTolerance(),
                biology.lowPressure(),biology.highPressure(),biology.weedTolerance(),biology.weedHighLevel(),biology.toxinTolerance(),biology.pestTolerance()),
                nutrient.profile().biology());
        assertEquals(original,base.profile());
        for(var changed:java.util.List.of(water.profile(),nutrient.profile())) {
            assertEquals(original.packet(),changed.packet()); assertEquals(original.product(),changed.product());
            assertEquals(original.visualKey(),changed.visualKey()); assertEquals(original.chemicals(),changed.chemicals());
            assertEquals(original.consumeGasses(),changed.consumeGasses()); assertEquals(original.exudeGasses(),changed.exudeGasses());
            assertEquals(original.mutationTargets(),changed.mutationTargets()); assertEquals(original.scream(),changed.scream());
            assertEquals(original.lifecycle(),changed.lifecycle()); assertEquals(original.biology().endurance(),changed.biology().endurance());
            assertEquals(original.biology().idealHeat(),changed.biology().idealHeat());
        }
        var basePlant=planted(base); var waterPlant=planted(water); var nutrientPlant=planted(nutrient);
        assertNotSame(basePlant.seedReference(),waterPlant.seedReference());
        assertNotSame(waterPlant.seedReference(),nutrientPlant.seedReference());
        assertNotSame(basePlant.effectiveProfile(),waterPlant.effectiveProfile());
    }

    @Test void ordinaryCycleConsumesResolvedWaterAndNutritionRatesIndependently() {
        var base=new GeneticValue(id("wheat"),resolvedWheat());
        var presets=BotanyFixtureScenarios.wheatTraitPresets(base);
        var baseline=runCycle(planted(base));
        var water=runCycle(planted(presets.water()));
        var nutrient=runCycle(planted(presets.nutrient()));
        assertEquals(39.6f,baseline.nutrition(),1e-5); assertEquals(39f,baseline.water(),1e-5);
        assertEquals(39.6f,water.nutrition(),1e-5); assertEquals(38.2f,water.water(),1e-5);
        assertEquals(38.8f,nutrient.nutrition(),1e-5); assertEquals(39f,nutrient.water(),1e-5);
    }

    @Test void tolerancePairsUseMatchedResolvedProfilesAndOrdinaryPollUsesThresholdWithoutKillingHealthyPlants() {
        var base=new GeneticValue(id("wheat"),resolvedWheat());
        var values=BotanyFixtureScenarios.tolerancePresets(base);
        assertEquals(8,values.size());
        var b=base.profile().biology();
        for(int pair=0;pair<4;pair++) {
            var low=values.get(pair*2).profile(); var high=values.get(pair*2+1).profile();
            var lb=low.biology(); var hb=high.biology();
            assertEquals(low.packet(),high.packet()); assertEquals(low.lifecycle(),high.lifecycle());
            assertEquals(low.chemicals(),high.chemicals()); assertEquals(low.consumeGasses(),high.consumeGasses());
            assertEquals(low.exudeGasses(),high.exudeGasses()); assertEquals(low.mutationTargets(),high.mutationTargets());
            assertEquals(lb.endurance(),hb.endurance()); assertEquals(lb.nutrientConsumption(),hb.nutrientConsumption());
            assertEquals(lb.waterConsumption(),hb.waterConsumption()); assertEquals(lb.idealHeat(),hb.idealHeat());
            if(pair!=3) assertEquals(lb.heatTolerance(),hb.heatTolerance()); assertEquals(lb.lowPressure(),hb.lowPressure());
            assertEquals(lb.highPressure(),hb.highPressure()); assertEquals(lb.weedHighLevel(),hb.weedHighLevel());
            if(pair==0) { assertEquals(4,lb.toxinTolerance()); assertEquals(10,hb.toxinTolerance()); assertEquals(lb.toxinTolerance(),b.toxinTolerance()); }
            if(pair==1) { assertEquals(5,lb.pestTolerance()); assertEquals(15,hb.pestTolerance()); }
            if(pair==2) { assertEquals(5,lb.weedTolerance()); assertEquals(15,hb.weedTolerance()); }
            if(pair==3) { assertEquals(275,lb.idealHeat()); assertEquals(275,hb.idealHeat()); assertEquals(10,lb.heatTolerance()); assertEquals(25,hb.heatTolerance()); }
            for(int n=0;n<3;n++) if(pair!=n && pair<3) assertEquals(switch(n) {case 0->lb.toxinTolerance();case 1->lb.pestTolerance();default->lb.weedTolerance();},
                    switch(n) {case 0->hb.toxinTolerance();case 1->hb.pestTolerance();default->hb.weedTolerance();});
            if(pair==3) { assertEquals(b.toxinTolerance(),lb.toxinTolerance()); assertEquals(b.pestTolerance(),lb.pestTolerance()); assertEquals(b.weedTolerance(),lb.weedTolerance()); }
            var baseline=healthyExposedPlant(values.get(pair*2),pair*2); var tolerant=healthyExposedPlant(values.get(pair*2+1),pair*2+1);
            assertEquals(baseline.age(),tolerant.age()); assertEquals(100,baseline.health()); assertEquals(baseline.health(),tolerant.health());
            assertEquals(0,baseline.skipAging()); assertEquals(100,baseline.water()); assertEquals(100,baseline.nutrition());
            assertEquals(baseline.age(),tolerant.age()); assertEquals(baseline.skipAging(),tolerant.skipAging());
            assertEquals(baseline.water(),tolerant.water()); assertEquals(baseline.nutrition(),tolerant.nutrition());
            assertEquals(0,baseline.mutationLevel()); assertEquals(baseline.mutationLevel(),tolerant.mutationLevel());
            PlantMetabolismEvaluator.cycle(baseline,new TrayProfile(60,300,100,100,true,20000,5000),baseline.effectiveProfile(),300,
                    new ControlledExposureRandom(),Optional.of(new PlantMetabolismEvaluator.Environment(100,293.15f)));
            PlantMetabolismEvaluator.cycle(tolerant,new TrayProfile(60,300,100,100,true,20000,5000),tolerant.effectiveProfile(),300,
                    new ControlledExposureRandom(),Optional.of(new PlantMetabolismEvaluator.Environment(100,293.15f)));
            assertFalse(baseline.dead(),"susceptible baseline remains alive pair "+pair); assertFalse(tolerant.dead(),"tolerant pair "+pair);
            assertTrue(baseline.health()<100,"susceptible profile receives exposure damage pair "+pair);
            assertEquals(100,tolerant.health(),"tolerant profile avoids exposure damage pair "+pair);
            assertEquals(0,baseline.skipAging()); assertEquals(0,tolerant.skipAging());
        }
    }

    private static PlantInstance healthyExposedPlant(GeneticValue value,int pair) {
        var p=new PlantInstance(0,0,0); p.plant(value,0);
        BotanyFixtureScenarios.prepareToleranceHolder(p,value,pair);
        assertEquals(100,p.health()); assertEquals(100,p.water()); assertEquals(100,p.nutrition()); assertEquals(0,p.skipAging());
        return p;
    }
    private static final class ControlledExposureRandom implements RandomGenerator {
        @Override public float nextFloat(){return .99f;}
        @Override public int nextInt(int origin,int bound){return Math.min(1,bound-1);}
        @Override public long nextLong(){return 0;}
    }

    private static PlantInstance planted(GeneticValue genetics) {
        PlantInstance plant=new PlantInstance(40,40,0); plant.plant(genetics,0);
        plant.state(2,genetics.profile().biology().endurance(),0,0,false,false); return plant;
    }
    private static PlantInstance runCycle(PlantInstance plant) {
        PlantMetabolismEvaluator.cycle(plant,new TrayProfile(60,300,100,100,true,20000,5000),plant.effectiveProfile(),
                300,new CycleRandom(),Optional.of(new PlantMetabolismEvaluator.Environment(100,293)));
        return plant;
    }
    private static SeedProfile resolvedWheat() {
        return BotanyTestSeedFixtures.resolved(id("wheat"));
    }
    private static final class CycleRandom implements RandomGenerator {
        private final float[] values={.99f,.99f,.99f,0,0,.99f,.99f}; private int index;
        @Override public float nextFloat(){ return values[Math.min(index++,values.length-1)]; }
        @Override public long nextLong(){ return 0; }
        @Override public int nextInt(int origin,int bound){ return origin; }
    }
}
