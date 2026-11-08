package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfileMergeStrategy;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponProfile;
import com.juicyslew.moonstation14.ms14.combat.ModMeleeWeaponProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only glasstle/shard composition; unsupported sharp/melee behavior is intentionally omitted. */
class GlasstleProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void glasstleAndShardResolveInheritedDefaultsWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE);
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE);
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModItemFootprints.TYPE);
        manager.register(ModMeleeWeaponProfiles.TYPE);
        var before = manager.snapshot();
        var loose = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        loose.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "glasstle", "shard_glass"));
        var looseResolved = PrototypeResolver.resolve(loose, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow()).asMap();
        var fruit = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, looseResolved.get(id("glasstle"))).getOrThrow();
        var damage = fruit.itemDamage();
        assertEquals(5f, damage.highSpeedImpact().get("blunt")); assertEquals(20f, damage.minimumImpactSpeed());
        assertEquals(.5f, damage.impactFactor()); assertEquals(40, damage.impactCooldownTicks());
        assertEquals(10f, damage.destructionThreshold()); assertFalse(damage.spillCurrentReagents());
        assertEquals(id("ss14/effects/glass_break"), damage.destructionSound()); assertEquals(-4f, damage.destructionSoundVolumeDb());
        assertEquals(1, damage.destructionOutputs().size());
        assertEquals(new com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput(id("shard_glass"), 1), damage.destructionOutputs().getFirst());
        var shard = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, looseResolved.get(id("shard_glass"))).getOrThrow();
        assertEquals(.2, shard.stepTrigger().intersectionRatio(), .001); assertEquals(3.5f, shard.stepTrigger().requiredSpeed());
        assertTrue(shard.preventableStepTrigger()); assertEquals(4.5f, shard.stepDamage().damage().get("piercing"));
        assertTrue(shard.stepDamage().deleteSource()); assertEquals(0f, shard.itemDamage().damageModifiers().coefficients().get("shock"));
        assertEquals(5f, shard.itemDamage().damageModifiers().flatReductions().get("slash"));
        assertTrue(shard.itemDamage().throwLandDamage().isEmpty()); assertEquals(5f, shard.itemDamage().highSpeedImpact().get("blunt"));
        assertEquals(id("ss14/effects/glass_step"), shard.slippery().soundEvent()); assertEquals(0d, shard.slippery().launchVelocityMultiplier());

        var food = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "glasstle", "shard_glass"));
        assertEquals(2500, food.get(id("glasstle")).capacityCents());
        assertEquals(Map.of(id("razorium"),1500L,id("desoxyephedrine"),1000L), food.get(id("glasstle")).contentsCents());
        assertTrue(food.get(id("glasstle")).refillable());
        assertEquals("shardglass", food.get(id("shard_glass")).solutionName());
        assertEquals(Map.of(id("silicon"),500L), food.get(id("shard_glass")).contentsCents());
        assertEquals(500, food.get(id("shard_glass")).capacityCents()); assertFalse(food.get(id("shard_glass")).refillable());
        var extract = PrototypeResolver.resolve(BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "glasstle", "shard_glass"), null).asMap();
        assertEquals("food", ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, extract.get(id("glasstle"))).getOrThrow().grindableSolutionName());
        assertEquals("shardglass", ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, extract.get(id("shard_glass"))).getOrThrow().grindableSolutionName());
        var ingestionRaw = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("ingestion_profile", "edible_defaults", "food_base"));
        ingestionRaw.putAll(BotanyCatalogCandidateTest.resources("ingestion_profile", "glasstle"));
        var ingestion = PrototypeResolver.resolve(ingestionRaw, new IngestionProfileMergeStrategy()).asMap();
        assertEquals(IngestionProfile.Kind.FOOD, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, ingestion.get(id("glasstle"))).getOrThrow().edible().kind());
        assertFalse(ingestion.containsKey(id("shard_glass")), "glass shard is not edible");
        var produce = PrototypeResolver.resolve(BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "glasstle"), null).asMap();
        assertEquals(id("glasstle"), ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, produce.get(id("glasstle"))).getOrThrow().seed());
        var footprintRows = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("item_footprint", "small", "tiny"));
        footprintRows.putAll(BotanyCatalogCandidateTest.resources("item_footprint", "glasstle", "shard_glass"));
        var footprint = PrototypeResolver.resolve(footprintRows, null).asMap();
        assertEquals("Small", ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, footprint.get(id("glasstle"))).getOrThrow().itemSize().orElseThrow().name());
        assertEquals("Tiny", ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, footprint.get(id("shard_glass"))).getOrThrow().itemSize().orElseThrow().name());
        var melee = BotanyCatalogCandidateTest.resources("melee_weapon_profile", "glasstle", "shard_glass");
        var meleeResolved = PrototypeResolver.resolve(melee, ModMeleeWeaponProfiles.TYPE.mergeStrategy().orElseThrow()).asMap();
        var glasstleMelee = MeleeWeaponProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, meleeResolved.get(id("glasstle"))).getOrThrow();
        assertEquals(Map.of("slash", 15f), glasstleMelee.weapon().damage());
        assertEquals(Map.of("blunt", 10f), glasstleMelee.damageOnHit());
        assertEquals(id("ss14/weapons/blade_slice"), glasstleMelee.weapon().audio().hit().event());
        assertEquals(60f, glasstleMelee.weapon().wideAnimationRotation());
        var shardMelee = MeleeWeaponProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, meleeResolved.get(id("shard_glass"))).getOrThrow();
        assertEquals(Map.of("slash", 3.5f), shardMelee.weapon().damage());
        assertNull(shardMelee.weapon().audio().hit());
        assertEquals(id("ss14/effects/weak_hit"), shardMelee.weapon().audio().noDamage().event());
        manager.stage(Map.of(ModProduceProfiles.TYPE, produce), ignored -> {});
        assertSame(before, manager.snapshot(), "candidate staging must not publish profiles");
    }
}
