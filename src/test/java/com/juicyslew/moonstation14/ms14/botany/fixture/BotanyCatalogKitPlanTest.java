package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.botany.*;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BotanyCatalogKitPlanTest {
    @Test void sevenRegisteredHeadwearInspectionItemsUseUniquePrimaryRoomBenchPositions() {
        var descriptors=BotanyFixtureSupplies.galleryDescriptors();
        assertEquals(List.of("ambrosia_deus","ambrosia_vulgaris","lily","clothing_head_hat_watermelon",
                "clothing_head_hat_holy_watermelon","trash_banana_peel","trash_mimana_peel"),descriptors.stream().map(BotanyFixtureSupplies.Descriptor::value).toList());
        assertTrue(descriptors.stream().allMatch(d->d.kind()==BotanyFixtureSupplies.Kind.ITEM));
        assertEquals(List.of("ambrosia_deus","ambrosia_vulgaris","lily","clothing_head_hat_watermelon",
                "clothing_head_hat_holy_watermelon","trash_banana_peel","trash_mimana_peel").stream()
                .map(id->ResourceLocation.fromNamespaceAndPath("moonstation14",id)).toList(),descriptors.stream()
                .map(d->ResourceLocation.fromNamespaceAndPath(com.juicyslew.moonstation14.MoonStation14.MOD_ID,d.value())).toList());
        var manager=new PrototypeManager(); manager.register(ModSeedProfiles.TYPE); manager.register(ModProduceProfiles.TYPE);
        var resolved=new LinkedHashMap<ResourceLocation,Map<ResourceLocation,JsonObject>>();
        manager.stage(Map.of(ModSeedProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("seed_profile"),ModProduceProfiles.TYPE,BotanyCatalogCandidateTest.mainResourcesAll("produce_profile")),cats->resolved.putAll(cats));
        var seeds=new LinkedHashMap<ResourceLocation,SeedProfile>(); resolved.get(ModSeedProfiles.TYPE.typeId()).forEach((k,v)->seeds.put(k,SeedProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
        var produce=new LinkedHashMap<ResourceLocation,ProduceProfile>(); resolved.get(ModProduceProfiles.TYPE.typeId()).forEach((k,v)->produce.put(k,ProduceProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
        var plan=BotanyCatalogGalleryPlan.create(new BlockPos(17,64,-31),new PrototypeCatalog<>(seeds),new PrototypeCatalog<>(produce),ignored->true);
        var positions=BotanyFixtureCommands.galleryKitPositions(plan);
        assertEquals(7,positions.size()); assertEquals(7,new HashSet<>(positions).size());
        for(var pos:positions) {
            assertTrue(SmallBotanyFixtureLayout.roomFootprint(plan.origin(),plan.width(),plan.depth(),plan.height()).contains(pos));
            assertEquals(plan.origin().getY()+2,pos.getY());
            assertEquals(plan.origin().getY()+1,pos.below().getY());
            assertFalse(pos.distToCenterSqr(plan.entryPosition().x,plan.entryPosition().y,plan.entryPosition().z)<1.0);
            assertFalse(plan.entries().stream().anyMatch(e->e.trayPosition().equals(pos)||e.labelPosition().equals(pos)));
        }
        assertFalse(descriptors.stream().anyMatch(d->d.kind()==BotanyFixtureSupplies.Kind.SEED));
    }
}
