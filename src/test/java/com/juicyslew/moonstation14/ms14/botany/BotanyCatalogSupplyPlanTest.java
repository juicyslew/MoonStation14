package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies;
import com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BotanyCatalogSupplyPlanTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void mainCatalogPlansEverySupportedSourceOnceWithinFixtureRooms() {
        var manager=new PrototypeManager(); manager.register(ModSeedProfiles.TYPE); manager.register(ModProduceProfiles.TYPE);
        var decoded=new LinkedHashMap<ResourceLocation,Map<ResourceLocation,JsonObject>>();
        manager.stage(Map.of(ModSeedProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("seed_profile"),
                ModProduceProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("produce_profile")), catalogs -> decoded.putAll(catalogs));
        var seeds=new LinkedHashMap<ResourceLocation,SeedProfile>();
        decoded.get(ModSeedProfiles.TYPE.typeId()).forEach((k,v)->seeds.put(k,SeedProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
        var produce=new LinkedHashMap<ResourceLocation,ProduceProfile>();
        decoded.get(ModProduceProfiles.TYPE.typeId()).forEach((k,v)->produce.put(k,ProduceProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
        var seedCatalog=new PrototypeCatalog<>(seeds); var produceCatalog=new PrototypeCatalog<>(produce);
        var availability=BotanySpeciesAvailability.policy(seedCatalog,produceCatalog,
                item->true, harvest->true);
        var expected=seeds.keySet().stream().filter(s->!s.equals(id("defaults"))).filter(availability)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(65,expected.size(),"the intended supported MAIN species set");
        var descriptors=BotanyFixtureSupplies.catalogDescriptors(seedCatalog,produceCatalog,availability);
        var seedDescriptors=descriptors.stream().filter(d->d.kind()==BotanyFixtureSupplies.Kind.SEED).toList();
        assertEquals(65,seedDescriptors.size());
        assertEquals(expected,seedDescriptors.stream().map(d->id(d.value())).collect(java.util.stream.Collectors.toSet()));
        assertEquals(65,seedDescriptors.stream().map(d->d.value()).distinct().count());
        assertEquals(65,seedDescriptors.stream().map(d->id(d.value())).distinct().count());
        assertFalse(seedDescriptors.stream().anyMatch(d->d.value().equals("defaults")));
        assertEquals(5,BotanyFixtureSupplies.descriptors().stream().filter(d->d.kind()==BotanyFixtureSupplies.Kind.SEED).count());
        assertTrue(BotanyFixtureSupplies.descriptors().stream().anyMatch(d->d.value().equals("wheat")));
        assertTrue(descriptors.stream().anyMatch(d->d.kind()==BotanyFixtureSupplies.Kind.TOOL));
        assertTrue(descriptors.stream().anyMatch(d->d.kind()==BotanyFixtureSupplies.Kind.REAGENT));
        assertEquals(seeds.get(id("pea")).packet(),seeds.get(id("world_pea")).packet());
        assertNotEquals(seeds.get(id("pea")).product(),seeds.get(id("world_pea")).product());
        assertTrue(expected.contains(id("holymelon")));
        assertEquals(id("watermelon"),produce.get(seeds.get(id("holymelon")).product()).seed(),
                "holymelon inherits the source product's watermelon fallback; carried genetics retain holymelon identity");

        var plan=BotanyFixtureSupplies.plan(BlockPos.ZERO,descriptors);
        assertEquals(descriptors.size(),plan.size());
        var positions=new HashSet<BlockPos>(); var slotCounts=new java.util.HashMap<String,Integer>();
        for(var p:plan) {
            assertTrue(positions.add(p.position()),"duplicate supply position "+p.position());
            assertTrue(p.slot()>=0&&p.slot()<6,"slot "+p.slot());
            assertTrue(slotCounts.merge(p.stationId(),1,Integer::sum)<=6,p.stationId());
            var station=BotanyFixtureStations.lookup(p.stationId()); assertNotNull(station);
            var room=SmallBotanyFixtureLayout.roomOrigins(BlockPos.ZERO).get(station.room());
            assertTrue(p.position().getX()>=room.getX()+1&&p.position().getX()<=room.getX()+SmallBotanyFixtureLayout.WIDTH-2);
            assertEquals(room.getY()+1,p.position().getY());
            assertTrue(p.position().getZ()>=room.getZ()+1&&p.position().getZ()<=room.getZ()+SmallBotanyFixtureLayout.DEPTH-2);
        }
        assertEquals(65,plan.stream().filter(p->expected.stream().anyMatch(s->s.getPath().equals(p.label()))).count());
    }
}
