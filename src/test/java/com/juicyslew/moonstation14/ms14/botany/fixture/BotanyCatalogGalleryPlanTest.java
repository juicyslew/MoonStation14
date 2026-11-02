package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.botany.BotanySpeciesAvailability;
import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.PlantMetabolismEvaluator;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;
import com.juicyslew.moonstation14.ms14.botany.fixture.BotanyCatalogGalleryPlan;
import com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BotanyCatalogGalleryPlanTest {
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("moonstation14",path);}
    @Test void plansAllSupportedCatalogSpeciesInOneBoundedGalleryRoom(){
        var manager=new PrototypeManager(); manager.register(ModSeedProfiles.TYPE); manager.register(ModProduceProfiles.TYPE);
        var resolved=new LinkedHashMap<ResourceLocation,Map<ResourceLocation,JsonObject>>();
        manager.stage(Map.of(ModSeedProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("seed_profile"),ModProduceProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("produce_profile")),cats->resolved.putAll(cats));
        var seeds=new LinkedHashMap<ResourceLocation,SeedProfile>(); resolved.get(ModSeedProfiles.TYPE.typeId()).forEach((k,v)->seeds.put(k,SeedProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
        var produce=new LinkedHashMap<ResourceLocation,ProduceProfile>(); resolved.get(ModProduceProfiles.TYPE.typeId()).forEach((k,v)->produce.put(k,ProduceProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
        var sc=new PrototypeCatalog<>(seeds); var pc=new PrototypeCatalog<>(produce);
        var available=BotanySpeciesAvailability.policy(sc,pc,item->true,harvest->true);
        var excluded=Set.of("defaults","killer_tomato","anomaly_berry","gatfruit","fake_capfruit","real_capfruit","spacemans_trumpet");
        var expected=seeds.keySet().stream().filter(s->!excluded.contains(s.getPath())).sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        assertEquals(65,expected.size());
        var origin=new BlockPos(17,64,-31); var plan=BotanyCatalogGalleryPlan.create(origin,sc,pc,available);
        assertEquals(expected,plan.entries().stream().map(BotanyCatalogGalleryPlan.Entry::species).toList());
        assertEquals(new HashSet<>(expected),plan.entries().stream().map(BotanyCatalogGalleryPlan.Entry::species).collect(java.util.stream.Collectors.toSet()));
        assertEquals(65,plan.entries().stream().map(BotanyCatalogGalleryPlan.Entry::species).distinct().count());
        var labels=BotanyFixtureSigns.catalogLabels(plan);
        assertEquals(65,labels.size());
        for(int i=0;i<labels.size();i++) {
            var lines=labels.get(i).lines();
            assertEquals(String.format(Locale.ROOT,"SEED %02d",i+1),lines.getFirst());
            assertTrue(lines.size()<=4);
            assertTrue(lines.stream().allMatch(line->line.length()<=16));
        }
        var tea=plan.entries().stream().filter(e->e.species().equals(id("tea_plant"))).findFirst().orElseThrow();
        var teaLabel=labels.get(plan.entries().indexOf(tea)).lines();
        assertEquals("tea plant",String.join(" ",teaLabel.subList(1,teaLabel.size())));
        var worldPeaEntry=plan.entries().stream().filter(e->e.species().equals(id("world_pea"))).findFirst().orElseThrow();
        var worldPeaLabel=labels.get(plan.entries().indexOf(worldPeaEntry)).lines();
        assertEquals("world pea",String.join(" ",worldPeaLabel.subList(1,worldPeaLabel.size())));
        var pea=plan.entries().stream().filter(e->e.species().equals(id("pea"))).findFirst().orElseThrow();
        var worldPea=plan.entries().stream().filter(e->e.species().equals(id("world_pea"))).findFirst().orElseThrow();
        assertNotEquals(pea.species(),worldPea.species()); assertEquals(pea.profile().packet(),worldPea.profile().packet());
        assertEquals(23,plan.width());
        assertEquals(5,plan.height()); assertEquals(29,plan.depth()); assertEquals(SmallBotanyFixtureLayout.entry(origin),plan.entryPosition());
        assertEquals(origin.offset(2,1,5),plan.entries().getFirst().trayPosition());
        assertEquals(origin.offset(20,1,8),plan.entries().get(19).trayPosition());
        assertEquals(origin.offset(2,1,11),plan.entries().get(20).trayPosition());
        var footprint=plan.plannedFootprint(); assertTrue(footprint.containsAll(SmallBotanyFixtureLayout.roomFootprint(origin,23,29,5)));
        for(int x=5;x<=9;x++)for(int y=0;y<=3;y++)for(int z=-4;z<=-1;z++)assertTrue(footprint.contains(origin.offset(x,y,z)));
        JsonObject models;
        try(var input=getClass().getResourceAsStream("/assets/moonstation14/botany/plant-models.json")){assertNotNull(input);models=JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(java.io.IOException e){throw new AssertionError(e);}
        var bounds=models.getAsJsonObject("render_bounds");
        assertFalse(models.has("height_scales"));
        for(var e:plan.entries()){
            assertTrue(footprint.contains(e.trayPosition()));assertTrue(footprint.contains(e.labelPosition()));assertEquals(e.trayPosition().south(),e.labelPosition());
            assertTrue(e.labelPosition().getZ()<e.trayPosition().getZ()+3,"label/walk aisle clear");
            var box=bounds.has(e.profile().visualKey())?bounds.getAsJsonArray(e.profile().visualKey()):null;
            double minX=box==null?0:box.get(0).getAsDouble(), minY=box==null?0:box.get(1).getAsDouble(), minZ=box==null?0:box.get(2).getAsDouble();
            double maxX=box==null?1:box.get(3).getAsDouble(), maxY=box==null?2:box.get(4).getAsDouble(), maxZ=box==null?1:box.get(5).getAsDouble();
            assertTrue(e.trayPosition().getX()+minX>origin.getX()&&e.trayPosition().getX()+maxX<origin.getX()+plan.width()-1,"render bounds inside side walls: "+e.species());
            assertTrue(e.trayPosition().getZ()+minZ>origin.getZ()&&e.trayPosition().getZ()+maxZ<origin.getZ()+plan.depth()-1,"render bounds inside end walls: "+e.species());
            assertTrue(e.trayPosition().getY()+maxY<origin.getY()+plan.height()-1,"render bounds clear roof: "+e.species());
        }
        assertTrue(plan.lightingPositions().stream().allMatch(footprint::contains));
        assertTrue(plan.lightingPositions().stream().anyMatch(p->p.getY()==origin.getY()+plan.height()-1));
        for(var entry:plan.entries()) assertTrue(plan.lightingPositions().contains(entry.labelPosition().below()),"floor aisle light below each label/headspace");
        var old=SmallBotanyFixtureLayout.plannedFootprint(origin);
        for(var room:SmallBotanyFixtureLayout.roomOrigins(origin)) assertTrue(old.containsAll(SmallBotanyFixtureLayout.roomFootprint(room,23,29,5)));
        assertEquals(52,BotanyFixtureStations.all().size());
    }

    @Test void everyPlannedSpeciesIsCompatibleWithFreshFixtureAir(){
        var manager=new PrototypeManager(); manager.register(ModSeedProfiles.TYPE); manager.register(ModProduceProfiles.TYPE);
        var resolved=new LinkedHashMap<ResourceLocation,Map<ResourceLocation,JsonObject>>();
        manager.stage(Map.of(ModSeedProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("seed_profile"),ModProduceProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("produce_profile")),cats->resolved.putAll(cats));
        var seeds=new LinkedHashMap<ResourceLocation,SeedProfile>(); resolved.get(ModSeedProfiles.TYPE.typeId()).forEach((k,v)->seeds.put(k,SeedProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
        var produce=new LinkedHashMap<ResourceLocation,ProduceProfile>(); resolved.get(ModProduceProfiles.TYPE.typeId()).forEach((k,v)->produce.put(k,ProduceProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
        var seedCatalog=new PrototypeCatalog<>(seeds); var produceCatalog=new PrototypeCatalog<>(produce);
        var plan=BotanyCatalogGalleryPlan.create(BlockPos.ZERO,seedCatalog,produceCatalog,
                BotanySpeciesAvailability.policy(seedCatalog,produceCatalog,item->true,harvest->true));
        var air=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureCommands.freshFixtureAir();
        var environment=new PlantMetabolismEvaluator.Environment((float)air.pressureKpa(1.0),(float)air.temperatureKelvin());
        assertEquals(65,plan.entries().size());
        for(var entry:plan.entries()) {
            var warnings=PlantMetabolismEvaluator.improperEnvironment(entry.profile(),environment);
            assertFalse(warnings.pressure(),"fixture air pressure outside profile: "+entry.species());
            assertFalse(warnings.temperature(),"fixture air temperature outside profile: "+entry.species());
        }
    }
}
