package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class LigneousGeneticsTest {
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("moonstation14",path);}
    private static GeneticValue value(boolean ligneous){
        var profile=new SeedProfile(id("wheat_seeds"),id("wheat_bushel"),"wheat",
                new SeedProfile.Biology(100,1,1,293,10,81,121,5,10,4),
                new SeedProfile.Lifecycle(6,20,6,5,3,5,SeedProfile.Harvest.NO_REPEAT,true,false,ligneous),Map.of(), "test-wheat-name");
        return new GeneticValue(id("wheat"),profile);
    }

    @Test void lifecycleCompositionDefaultsFalseAndCodecsPreserveTrue() {
        var defaults=SeedProfile.Lifecycle.CODEC.parse(JsonOps.INSTANCE,com.google.gson.JsonParser.parseString(
                "{\"growth_stages\":6,\"lifespan\":20,\"maturation\":6,\"production\":5,\"yield\":3,\"potency\":5,\"harvest\":\"NO_REPEAT\"}")).getOrThrow();
        assertFalse(defaults.ligneous());
        var ligneous=value(true);
        var roundtrip=GeneticValue.CODEC.parse(JsonOps.INSTANCE,GeneticValue.CODEC.encodeStart(JsonOps.INSTANCE,ligneous).getOrThrow()).getOrThrow();
        assertEquals(ligneous,roundtrip);
        assertTrue(roundtrip.profile().lifecycle().ligneous());
        assertFalse(value(false).profile().lifecycle().ligneous());
    }

    @Test void resolvedSeedLifecycleInheritsAndAllowsExplicitLigneousOverride() {
        var raw=new java.util.LinkedHashMap<>(BotanyTestSeedFixtures.mainCatalog());
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();manager.register(ModSeedProfiles.TYPE);
        manager.reload(ModSeedProfiles.TYPE,raw);
        assertFalse(manager.snapshot(ModSeedProfiles.TYPE).get(id("wheat")).lifecycle().ligneous(),"baseline resolved wheat defaults false");
        raw.get(id("defaults")).getAsJsonObject("lifecycle").addProperty("ligneous",true);
        manager.reload(ModSeedProfiles.TYPE,raw);
        assertTrue(manager.snapshot(ModSeedProfiles.TYPE).get(id("wheat")).lifecycle().ligneous(),"leaf inherits composed parent value");
        raw.get(id("wheat")).getAsJsonObject("lifecycle").addProperty("ligneous",false);
        manager.reload(ModSeedProfiles.TYPE,raw);
        assertFalse(manager.snapshot(ModSeedProfiles.TYPE).get(id("wheat")).lifecycle().ligneous(),"explicit leaf value overrides inherited true");
    }

    @Test void sourceStatMutationTogglesThroughNormalOrderedMutationCycleWithoutExtraDraw() {
        PlantMutationTable source;
        try(var in=LigneousGeneticsTest.class.getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/plant_mutation_table/random_plant_mutations.json")){
            assertNotNull(in); var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
            source=PlantMutationTable.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        }catch(java.io.IOException e){throw new AssertionError(e);}
        var entry=source.mutations().get(20);
        assertEquals("Lignification",entry.name());
        assertEquals(PlantStatMutation.Target.LIGNEOUS,entry.stat().orElseThrow().target());
        var table=new PlantMutationTable(List.of(entry));
        var plant=new PlantInstance(80,80,0); plant.plant(value(false),0); plant.adjustMutationLevel(1);
        var rng=new CountingRandom();
        PlantMutationSystem.cycle(plant,table,rng,()->.5f,null,null);
        assertTrue(plant.genetics().profile().lifecycle().ligneous());
        assertEquals(1,rng.doubles); assertEquals(0,rng.floats);
        plant.adjustMutationLevel(1); PlantMutationSystem.cycle(plant,table,rng,()->.5f,null,null);
        assertFalse(plant.genetics().profile().lifecycle().ligneous());
        assertEquals(2,rng.doubles); assertEquals(0,rng.floats);
    }

    @Test void crossConsumesLigneousAtItsExistingDrawPositionAndRetainsDonorValue() {
        for (boolean donorLigneous : new boolean[]{true,false}) {
            var calls=new AtomicInteger();
            var crossed=PlantPollenCross.cross(value(donorLigneous),value(!donorLigneous),()->{
                int draw=calls.getAndIncrement(); return draw==18?.9:.1;
            });
            assertEquals(donorLigneous,crossed.profile().lifecycle().ligneous());
            assertEquals(21,calls.get());
        }
    }

    @Test void immutableCareSpeciesAndMutationCopiesRetainLigneous() {
        var original=value(true);
        assertTrue(original.adjustPotency(1).profile().lifecycle().ligneous());
        assertTrue(original.robustHarvest(100,1,100).profile().lifecycle().ligneous());
        assertTrue(original.diethylamine(true,true).profile().lifecycle().ligneous());
        assertTrue(original.viable().profile().lifecycle().ligneous());
        assertTrue(original.restoreSeeds().profile().lifecycle().ligneous());
        assertTrue(new PlantHarvestMutation().apply(original).profile().lifecycle().ligneous());
        assertTrue(PlantStatMutation.numeric(PlantStatMutation.Target.LIFESPAN,10,80,5)
                .apply(original,new CountingRandom()).profile().lifecycle().ligneous());
        var target=new SeedProfile(id("tomato_seeds"),id("tomato"),"tomato",original.profile().biology(),
                new SeedProfile.Lifecycle(6,30,6,5,3,5,SeedProfile.Harvest.REPEAT),Map.of(), "test-tomato-name");
        assertTrue(original.transformSpecies(id("tomato"),target).profile().lifecycle().ligneous());
        var reference=new PlantSeedReference(original,false);
        var unique=reference.ensureUnique();
        assertTrue(unique.current().profile().lifecycle().ligneous());
        var decoded=PlantSeedReference.CODEC.parse(JsonOps.INSTANCE,
                PlantSeedReference.CODEC.encodeStart(JsonOps.INSTANCE,unique).getOrThrow()).getOrThrow();
        assertTrue(decoded.current().profile().lifecycle().ligneous());
        assertFalse(reference.unique()); assertTrue(decoded.unique());
        var holder=new PlantInstance(80,80,4); holder.plant(unique,4);
        var restored=PlantInstance.CODEC.parse(JsonOps.INSTANCE,PlantInstance.CODEC.encodeStart(JsonOps.INSTANCE,holder).getOrThrow()).getOrThrow();
        assertTrue(restored.effectiveProfile().lifecycle().ligneous(),"tray persistence retains acquired ligneous gene");
    }

    @Test void fixturePresetChangesOnlyResolvedWheatLigneousAndHarvestCarrierKeepsIt() {
        var raw=new java.util.LinkedHashMap<>(BotanyTestSeedFixtures.mainCatalog());
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager(); manager.register(ModSeedProfiles.TYPE); manager.reload(ModSeedProfiles.TYPE,raw);
        var baseline=new GeneticValue(id("wheat"),manager.snapshot(ModSeedProfiles.TYPE).get(id("wheat")));
        assertFalse(baseline.profile().lifecycle().ligneous());
        var preset=BotanyFixtureScenarios.ligneousPreset(baseline);
        assertFalse(baseline.profile().lifecycle().ligneous(),"factory does not mutate canonical resolved baseline");
        var expectedLife=baseline.profile().lifecycle();
        var actualLife=preset.profile().lifecycle();
        assertEquals(new SeedProfile.Lifecycle(expectedLife.growthStages(),expectedLife.lifespan(),expectedLife.maturation(),expectedLife.production(),
                expectedLife.yield(),expectedLife.potency(),expectedLife.harvest(),expectedLife.viable(),expectedLife.seedless(),true),actualLife);
        assertEquals(baseline.profile().biology(),preset.profile().biology());
        assertEquals(baseline.profile().chemicals(),preset.profile().chemicals());
        assertEquals(baseline.profile().consumeGasses(),preset.profile().consumeGasses());
        assertEquals(baseline.profile().exudeGasses(),preset.profile().exudeGasses());
        assertEquals(baseline.profile().mutationTargets(),preset.profile().mutationTargets());
        assertEquals(baseline.profile().scream(),preset.profile().scream());
        var reference=new PlantSeedReference(preset,true);
        var packet=GeneticCarriers.createSeedPacket(reference,null);
        assertEquals(preset,GeneticCarriers.resolve(packet,()->null));
        assertTrue(GeneticCarriers.resolve(packet,()->null).profile().lifecycle().ligneous());
        assertSame(reference,packet.copy().get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get()));
    }

    private static final class CountingRandom implements RandomGenerator {
        int doubles,floats;
        @Override public long nextLong(){throw new AssertionError("unexpected nextLong");}
        @Override public double nextDouble(){doubles++;return 0;}
        @Override public float nextFloat(){floats++;return 0;}
    }
}
