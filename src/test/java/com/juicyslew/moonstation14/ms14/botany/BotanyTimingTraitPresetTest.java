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

class BotanyTimingTraitPresetTest {
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("moonstation14",path);}

    @Test void samplingFixtureEnablesOnlyResolvedWheatScreamCapability() {
        var wheat = new GeneticValue(id("wheat"), resolved("wheat"));
        var preset = BotanyFixtureScenarios.wheatCanScreamPreset(wheat);
        assertFalse(wheat.profile().scream().enabled());
        assertTrue(preset.profile().scream().enabled());
        assertEquals(wheat.profile().scream().sound(), preset.profile().scream().sound());
        assertEquals(wheat.profile().scream().volumeDb(), preset.profile().scream().volumeDb());
        assertEquals(wheat.profile().withScream(preset.profile().scream()), preset.profile());

        var plant = stagePlant(wheat);
        BotanyFixtureScenarios.prepareStageHolder(plant, preset);
        assertSame(preset, plant.genetics());
        assertEquals(2, plant.age());
        assertEquals(2, PlantMetabolismEvaluator.rawGrowthStage(plant, plant.effectiveProfile()));
        assertFalse(plant.ready());
        assertFalse(plant.dead());
    }

    @Test void fastStageWheatUsesSameVSetupAndDoesNotChangeReadinessTiming() {
        var wheat=new GeneticValue(id("wheat"),resolved("wheat"));
        var fast=BotanyFixtureScenarios.wheatTraitPresets(wheat).fastStages();
        assertEquals(6,wheat.profile().lifecycle().maturation());
        assertEquals(3,fast.profile().lifecycle().maturation());
        assertEquals(wheat.profile().biology(),fast.profile().biology());
        var original=wheat.profile().lifecycle(); var changed=fast.profile().lifecycle();
        assertEquals(original.growthStages(),changed.growthStages()); assertEquals(original.lifespan(),changed.lifespan());
        assertEquals(original.production(),changed.production()); assertEquals(original.yield(),changed.yield());
        assertEquals(original.potency(),changed.potency()); assertEquals(original.harvest(),changed.harvest());
        assertEquals(original.viable(),changed.viable()); assertEquals(original.seedless(),changed.seedless());

        var v=stagePlant(wheat); var aa=stagePlant(wheat);
        BotanyFixtureScenarios.prepareStageHolder(v,wheat);
        BotanyFixtureScenarios.prepareStageHolder(aa,fast);
        assertEquals(v.age(),aa.age()); assertEquals(v.health(),aa.health());
        assertEquals(v.water(),aa.water()); assertEquals(v.nutrition(),aa.nutrition());
        assertFalse(v.ready()); assertFalse(aa.ready());
        assertTrue(PlantMetabolismEvaluator.rawGrowthStage(aa,aa.effectiveProfile())>
                PlantMetabolismEvaluator.rawGrowthStage(v,v.effectiveProfile()));
        // Same age and production threshold mean maturation changes stage projection, not readiness.
        v.state(4,v.health(),0,0,false,false); aa.state(4,aa.health(),0,0,false,false);
        cycle(v); cycle(aa);
        assertTrue(v.ready()); assertTrue(aa.ready());
    }

    @Test void earlyFruitTomatoRetainsRepeatAndNormalCycleUsesItsProductionGene() {
        var tomato=new GeneticValue(id("tomato"),resolved("tomato"));
        var early=BotanyFixtureScenarios.tomatoEarlyFruit(tomato).earlyFruit();
        assertEquals(6,tomato.profile().lifecycle().production());
        assertEquals(1,early.profile().lifecycle().production());
        assertEquals(SeedProfile.Harvest.REPEAT,early.profile().lifecycle().harvest());
        assertEquals(tomato.profile().biology(),early.profile().biology());
        var baseLife=tomato.profile().lifecycle(); var life=early.profile().lifecycle();
        assertEquals(baseLife.growthStages(),life.growthStages()); assertEquals(baseLife.lifespan(),life.lifespan());
        assertEquals(baseLife.maturation(),life.maturation()); assertEquals(baseLife.yield(),life.yield());
        assertEquals(baseLife.potency(),life.potency()); assertEquals(baseLife.viable(),life.viable());
        assertEquals(baseLife.seedless(),life.seedless());

        var e=readyRepeatTomato(tomato); var ab=readyRepeatTomato(early);
        assertTrue(PlantMetabolismEvaluator.harvest(e,e.effectiveProfile(),1).changed());
        assertTrue(PlantMetabolismEvaluator.harvest(ab,ab.effectiveProfile(),1).changed());
        assertNotNull(e.seed()); assertNotNull(ab.seed());
        assertFalse(e.ready()); assertFalse(ab.ready());
        cycle(ab); cycle(e);
        assertFalse(ab.ready()); assertFalse(e.ready(),"strict age > production requires a second successful age advance for AB");
        cycle(ab); cycle(e);
        assertTrue(ab.ready()); assertFalse(e.ready());
        for(int i=0;i<5;i++) cycle(e);
        assertTrue(e.ready(),"baseline tomato needs seven successful age advances after harvest at production 6");
    }

    @Test void allFixturePresetsPreserveAnInheritedLigneousGene() {
        var wheat=valueWithLigneous("wheat");
        var presets=BotanyFixtureScenarios.wheatTraitPresets(wheat);
        for(var preset:java.util.List.of(presets.yield(),presets.potency(),presets.water(),presets.nutrient(),presets.fastStages(),presets.endurance()))
            assertTrue(preset.profile().lifecycle().ligneous());
        var tomato=valueWithLigneous("tomato");
        assertTrue(BotanyFixtureScenarios.tomatoEarlyFruit(tomato).earlyFruit().profile().lifecycle().ligneous());
    }

    private static PlantInstance stagePlant(GeneticValue genetics){PlantInstance p=new PlantInstance(0,0,0);p.plant(genetics,0);return p;}
    private static PlantInstance readyRepeatTomato(GeneticValue genetics){
        PlantInstance p=new PlantInstance(40,40,0);p.plant(genetics,0);
        p.state(8,genetics.profile().biology().endurance(),0,0,true,false);return p;
    }
    private static void cycle(PlantInstance p){
        PlantMetabolismEvaluator.cycle(p,new TrayProfile(60,300,100,100,true,20000,5000),p.effectiveProfile(),
                p.lastCycle()+300,new SuccessfulAgeRoll(),Optional.of(new PlantMetabolismEvaluator.Environment(100,293)));
    }
    private static SeedProfile resolved(String leaf){
        return BotanyTestSeedFixtures.resolved(id(leaf));
    }
    private static GeneticValue valueWithLigneous(String leaf){
        var profile=resolved(leaf); var life=profile.lifecycle();
        return new GeneticValue(id(leaf),new SeedProfile(profile.packet(),profile.product(),profile.visualKey(),profile.biology(),
                new SeedProfile.Lifecycle(life.growthStages(),life.lifespan(),life.maturation(),life.production(),life.yield(),
                        life.potency(),life.harvest(),life.viable(),life.seedless(),true),profile.chemicals(),profile.consumeGasses(),
                profile.exudeGasses(),profile.mutationTargets(),profile.scream(), profile.sourceName()));
    }
    private static final class SuccessfulAgeRoll implements RandomGenerator {
        private final float[] values={1,1,0,1,1,1,1};private int i;
        @Override public float nextFloat(){return values[Math.min(i++,values.length-1)];}
        @Override public long nextLong(){return 0;}
        @Override public int nextInt(int origin,int bound){return origin;}
    }
}
