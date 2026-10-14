package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.random.RandomGenerator;
import static org.junit.jupiter.api.Assertions.*;

class TrayRulesTest {
    @Test void trustedSoilMutationTouchAdvancesPlantRevision() {
        var plant = new PlantInstance(50, 50, 0);
        long before = plant.revision();
        plant.soil(plant.water(), plant.nutrition(), 0, plant.soilToxins());
        plant.touch();
        assertEquals(before + 1, plant.revision());
    }

    private static ResourceLocation id(String s) { return ResourceLocation.fromNamespaceAndPath("moonstation14",s); }
    private static TrayProfile tray() { return new TrayProfile(60,300,100,100,true,20000,5000); }
    private static SeedProfile seed(float nutrient,float water,float production,float maturation,int yield) {
        return new SeedProfile(id("packet"),id("produce"),"plant",
                new SeedProfile.Biology(30,nutrient,water,293,10,81,121,5,10,4),
                new SeedProfile.Lifecycle(5,25,maturation,production,yield,1,SeedProfile.Harvest.REPEAT),Map.of());
    }
    private static PlantInstance planted(SeedProfile s,float water,float nutrition) {
        PlantInstance p=new PlantInstance(water,nutrition,0); p.plant(id("test"),s,0); return p;
    }
    private static final class Script implements RandomGenerator {
        private final float[] floats; private int cursor; private final int integer;
        Script(int integer,float... floats) { this.integer=integer; this.floats=floats; }
        @Override public long nextLong() { return 0; }
        @Override public float nextFloat() { return cursor<floats.length ? floats[cursor++] : 1f; }
        @Override public int nextInt(int origin,int bound) { return integer; }
    }
    private static Optional<PlantMetabolismEvaluator.Environment> goodEnv() { return Optional.of(new PlantMetabolismEvaluator.Environment(100,293)); }

    @Test void resolvedTrayInheritanceHasNormalAndEmptyDefaults() {
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        Map<ResourceLocation,JsonObject> raw=new LinkedHashMap<>();
        for(String name:List.of("defaults","hydroponics_tray","empty_hydroponics_tray")) {
            String path="data/moonstation14/moonstation14/tray_profile/"+name+".json";
            try(var stream=getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream,path); raw.put(id(name),JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
            } catch(java.io.IOException e) { throw new AssertionError(e); }
        }
        manager.register(ModTrayProfiles.TYPE);
        manager.reload(ModTrayProfiles.TYPE,raw);
        var catalog=manager.snapshot(ModTrayProfiles.TYPE); var normal=catalog.get(id("hydroponics_tray")); var empty=catalog.get(id("empty_hydroponics_tray"));
        assertTrue(catalog.get(id("defaults")).wateringSound().isEmpty(), "shared tray defaults are silent");
        assertEquals(100,normal.initialWater()); assertEquals(100,normal.initialNutrition()); assertTrue(normal.drawWarnings());
        assertEquals(20000,normal.soilCapacityCents()); assertEquals(5000,normal.refillMaximumCents());
        assertEquals(60,normal.pollDelayTicks()); assertEquals(300,normal.cycleDelayTicks());
        assertEquals(0,empty.initialWater()); assertEquals(0,empty.initialNutrition()); assertEquals(20000,empty.soilCapacityCents());
        assertTrue(normal.wateringSound().isPresent());
        assertEquals(normal.wateringSound(), empty.wateringSound(), "empty child inherits the normal tray cue through its parent");
        assertEquals(id("chemistry/chemmaster/dump"), normal.wateringSound().orElseThrow().soundResourceId());
        assertEquals(-6f, normal.wateringSound().orElseThrow().volumeDb());
        assertEquals(.2f, normal.wateringSound().orElseThrow().pitchVariation());
    }

    @Test void starvationAndThirstHaveIndependentHealingRolls() {
        SeedProfile s=seed(0,0,50,10,2); PlantInstance p=planted(s,50,50); p.state(2,10,0,0,false,false);
        PlantMetabolismEvaluator.cycle(p,tray(),s,300,new Script(1,1f,0f,0f,0f,0f,0f),goodEnv());
        // age roll succeeds, nutrition and water each independently heal; no resource deficits here
        assertEquals(12,p.health());
        PlantInstance dry=planted(s,0,10); dry.state(2,10,0,0,false,false);
        PlantMetabolismEvaluator.cycle(dry,tray(),s,300,new Script(1,0f,0f,0f),goodEnv());
        assertEquals(10,dry.health());
    }

    @Test void atmospherePressureAndHeatAreIndependentAndToxinsDecayBelowTolerance() {
        SeedProfile s=seed(0,0,50,10,2); PlantInstance p=planted(s,50,50); p.soil(50,50,0,3); p.state(2,20,0,0,false,false);
        PlantMetabolismEvaluator.cycle(p,tray(),s,300,new Script(1,1f),Optional.of(new PlantMetabolismEvaluator.Environment(1,400)));
        assertEquals(18,p.health()); assertEquals(2,p.toxins());
    }

    @Test void emptyTrayWeedsCycleAndUnknownAtmosphereDoesNotConsumePlantCycle() {
        PlantInstance empty=new PlantInstance(20,20,0);
        var result=PlantMetabolismEvaluator.cycle(empty,tray(),null,300,new Script(1,0f),goodEnv());
        assertEquals(2,empty.weeds()); assertEquals(PlantInstance.Status.EMPTY,result.status());
        SeedProfile s=seed(0,0,50,10,2); PlantInstance p=planted(s,50,50);
        PlantMetabolismEvaluator.cycle(p,tray(),s,300,new Script(1,1f),Optional.empty()); assertEquals(0,p.lastCycle());
    }

    @Test void unknownExposurePausesActivePlantBeforeWeedRollAndAllCycleMutation() {
        SeedProfile s=seed(0,0,50,10,2); PlantInstance p=planted(s,50,50);
        long revision=p.revision(); float weeds=p.weeds();
        for(long now: new long[]{300,360,420}) {
            var result=PlantMetabolismEvaluator.cycle(p,tray(),s,now,new Script(1,0f),Optional.empty());
            assertFalse(result.changed()); assertEquals(weeds,p.weeds());
            assertEquals(0,p.lastCycle()); assertEquals(revision,p.revision());
        }
    }

    @Test void pollRunsSoilBeforeCycleAndDoesNotCatchUp() {
        PlantInstance p=new PlantInstance(10,10,0); int[] calls={0};
        assertTrue(PlantMetabolismEvaluator.poll(p,tray(),null,0,x->calls[0]++,new Script(1),goodEnv()).isPresent());
        assertEquals(1,calls[0]); assertEquals(60,p.nextUpdate());
        assertTrue(PlantMetabolismEvaluator.poll(p,tray(),null,1000,x->calls[0]++,new Script(1),goodEnv()).isPresent());
        assertEquals(2,calls[0]); assertEquals(1060,p.nextUpdate()); assertEquals(1000,p.lastCycle());
    }

    @Test void readinessTimestampIsStableAndHarvestRepeatNoRepeatAreDomainTransactions() {
        SeedProfile repeat=seed(0,0,2,10,0); PlantInstance p=planted(repeat,50,50); p.state(5,20,2,0,true,false);
        PlantMetabolismEvaluator.cycle(p,tray(),repeat,300,new Script(1,1f),goodEnv()); assertEquals(2,p.lastProduceAge());
        var harvest=PlantMetabolismEvaluator.harvest(p,repeat,1); assertEquals(1,harvest.plannedYield()); assertEquals(PlantInstance.Status.GROWING,p.status());
        long publishedRevision = p.revision();
        p.touch(); // same revision bump performed by the tray commit before publishing its update tag
        var appearance = PlantMetabolismEvaluator.appearance(p, repeat);
        assertTrue(p.revision() > publishedRevision);
        assertEquals(PlantInstance.Status.GROWING, appearance.status());
        assertEquals(2, appearance.stage());
        SeedProfile noRepeat=new SeedProfile(repeat.packet(),repeat.product(),repeat.visualKey(),repeat.biology(),
                new SeedProfile.Lifecycle(5,25,10,2,1,1,SeedProfile.Harvest.NO_REPEAT),Map.of());
        PlantInstance q=planted(noRepeat,50,50); q.state(5,20,2,0,true,false);
        assertEquals(1,PlantMetabolismEvaluator.harvest(q,noRepeat,1).plannedYield()); assertNull(q.seed()); assertEquals(50,q.water());
    }

    @Test void invalidMaturationIsRejectedWithoutMutationAndInstanceCodecRoundTrips() {
        SeedProfile invalid=new SeedProfile(id("p"),id("x"),"x",seed(0,0,0,1,1).biology(),
                new SeedProfile.Lifecycle(1,1,0,0,1,1,SeedProfile.Harvest.REPEAT),Map.of());
        PlantInstance p=planted(invalid,50,50); var result=PlantMetabolismEvaluator.cycle(p,tray(),invalid,300,new Script(1),goodEnv());
        assertFalse(result.changed()); assertEquals(0,p.lastCycle());
        PlantInstance okay=planted(seed(0,0,5,10,1),22,33);
        var json=PlantInstance.CODEC.encodeStart(JsonOps.INSTANCE,okay).getOrThrow();
        PlantInstance decoded=PlantInstance.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        assertEquals(okay.seed(),decoded.seed()); assertEquals(okay.water(),decoded.water()); assertEquals(okay.revision(),decoded.revision());
    }
}
