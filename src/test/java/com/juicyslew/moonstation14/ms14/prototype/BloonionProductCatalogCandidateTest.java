package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only composition; small footprint and physical dimensions are local adaptations, not native parity. */
class BloonionProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void bloonionProfilesResolveTogetherWithoutPublication() throws java.io.IOException {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE); manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.register(ModItemFootprints.TYPE); manager.register(ModExplosions.EXPLOSION_TYPE);
        var before = manager.snapshot();
        var loose = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        loose.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "bloonion"));
        var explosion = BotanyCatalogCandidateTest.mainResources("explosion", "default");
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "bloonion"),
                ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.resources("item_food_solution_profile", "bloonion"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "bloonion"),
                ModLooseObjectProfiles.PROFILE_TYPE, loose,
                ModItemFootprints.TYPE, new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("item_footprint", "small")) {{ putAll(BotanyCatalogCandidateTest.resources("item_footprint", "bloonion")); }},
                ModExplosions.EXPLOSION_TYPE, explosion
        ), catalogs -> {
            var produce = ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModProduceProfiles.TYPE.typeId()).get(id("bloonion"))).getOrThrow();
            assertEquals(id("bloonion"), produce.seed());
            assertNotNull(produce.potencyVisuals());
            assertEquals(1f, produce.potencyVisuals().minimumScale());
            assertEquals(2f, produce.potencyVisuals().maximumScale());
            var food = ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).get(id("bloonion"))).getOrThrow();
            assertEquals(700, food.capacityCents());
            assertEquals(Map.of(id("potassium"),100L,id("phosphorus"),100L,id("sugar"),100L,id("allicin"),400L), food.contentsCents());
            assertFalse(food.canReact());
            var extractable = ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModExtractableProfiles.TYPE.typeId()).get(id("bloonion"))).getOrThrow();
            assertEquals("food", extractable.grindableSolutionName()); assertNull(extractable.juiceSolution());
            var damage = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModLooseObjectProfiles.PROFILE_TYPE.typeId()).get(id("bloonion"))).getOrThrow().itemDamage();
            assertEquals(Set.of("brute", "burn", "toxin", "airloss", "genetic"), damage.allowedGroups());
            assertEquals(1f, damage.destructionThreshold()); assertTrue(damage.highSpeedImpact().isEmpty());
            assertTrue(damage.throwLandDamage().isEmpty()); assertTrue(damage.spillCurrentReagents());
            assertEquals(.1, damage.destructionExplosion().totalIntensity());
            assertEquals(1, damage.destructionExplosion().intensitySlope());
            assertEquals(.001, damage.destructionExplosion().maxIntensity());
            assertEquals(id("default"), damage.destructionExplosion().explosionType());
            assertTrue(damage.destructionSound() == null); assertTrue(damage.destructionOutputs().isEmpty());
            var footprint = ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModItemFootprints.TYPE.typeId()).get(id("bloonion"))).getOrThrow();
            assertEquals("Small", footprint.itemSize().orElseThrow().name());
        });
        assertSame(before, manager.snapshot());
        for (String family : new String[]{"ingestion_profile", "seed_item_profile"}) {
            try (var stream = getClass().getClassLoader().getResourceAsStream("botany-catalog-candidate/" + family + "/bloonion.json")) { assertNull(stream); }
        }
        assertMainPresent("produce_profile");
    }

    private static void assertMainPresent(String family) {
        assertNotNull(BloonionProductCatalogCandidateTest.class.getClassLoader().getResource("data/moonstation14/moonstation14/" + family + "/bloonion.json"));
    }
}
