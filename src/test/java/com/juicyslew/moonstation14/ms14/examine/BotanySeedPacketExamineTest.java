package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.botany.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BotanySeedPacketExamineTest {
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("moonstation14", name); }

    @Test void registeredContributorShowsResolvedDefaultPacketFieldsButNotOnProduceOrSwab() {
        withCatalogs(() -> {
        BuiltinExamineContributors.register();
        ItemStack packetStack = new ItemStack(ModItems.WHEAT_SEEDS.get());
        var rows = examine(packetStack);
        var wheat = ModSeedProfiles.serverLookup(id("wheat"));
        assertEquals(List.of("examine.moonstation14.botany.seed_packet.yield", "examine.moonstation14.botany.seed_packet.potency"),
                rows.stream().map(BotanySeedPacketExamineTest::key).toList());
        assertEquals(wheat.lifecycle().yield(), arguments(rows.get(0))[0]);
        assertEquals(wheat.lifecycle().potency(), arguments(rows.get(1))[0]);

        var custom = new SeedProfile(wheat.packet(), wheat.product(), wheat.visualKey(), wheat.biology(),
                new SeedProfile.Lifecycle(6, 100, 10, 5, 7, 19, SeedProfile.Harvest.REPEAT, true, false),
                wheat.chemicals(), wheat.consumeGasses(), wheat.exudeGasses(), wheat.mutationTargets(), wheat.scream(), wheat.sourceName());
        var current = new GeneticValue(id("wheat"), custom);
        packetStack.set(ModDataComponents.PLANT_SEED_REFERENCE.get(), new PlantSeedReference(current, false));
        packetStack.set(ModDataComponents.GENETIC_VALUE.get(), new GeneticValue(id("wheat"), wheat));
        rows = examine(packetStack);
        assertEquals(7, arguments(rows.get(0))[0]);
        assertEquals(19f, arguments(rows.get(1))[0], "current reference wins over stale compatibility snapshot");

        for (ItemStack unrelated : List.of(new ItemStack(ModItems.WHEAT_BUSHEL.get()), new ItemStack(Items.PAPER))) {
            unrelated.set(ModDataComponents.GENETIC_VALUE.get(), current);
            assertTrue(examine(unrelated).isEmpty(), "genetics do not make produce or unrelated sample carriers a seed packet");
        }
        });
    }

    @Test void composedSeedItemAndCarriedProfileControlExamination() {
        withCatalogs(() -> {
        BuiltinExamineContributors.register();
        var wheat = ModSeedProfiles.serverLookup(id("wheat"));
        var custom = new SeedProfile(wheat.packet(), wheat.product(), wheat.visualKey(), wheat.biology(),
                new SeedProfile.Lifecycle(6, 100, 10, 5, 3, 12, SeedProfile.Harvest.REPEAT, true, false),
                wheat.chemicals(), wheat.consumeGasses(), wheat.exudeGasses(), wheat.mutationTargets(), wheat.scream(), wheat.sourceName());
        var packet = new ItemStack(ModItems.WHEAT_SEEDS.get());
        packet.set(ModDataComponents.GENETIC_VALUE.get(), new GeneticValue(id("wheat"), custom));
        assertEquals(3, arguments(examine(packet).get(0))[0]);

        var mismatched = new SeedProfile(id("potato_seeds"), wheat.product(), wheat.visualKey(), wheat.biology(),
                custom.lifecycle(), wheat.chemicals(), wheat.consumeGasses(), wheat.exudeGasses(), custom.mutationTargets(), custom.scream(), "test-fixture-source-name");
        packet.set(ModDataComponents.GENETIC_VALUE.get(), new GeneticValue(id("wheat"), mismatched));
        assertEquals(3, arguments(examine(packet).get(0))[0], "item composition admits; carried profile supplies output values");
        var unprofiled = new ItemStack(Items.PAPER);
        unprofiled.set(ModDataComponents.GENETIC_VALUE.get(), new GeneticValue(id("wheat"), mismatched));
        assertTrue(examine(unprofiled).isEmpty());
        var withoutWheatSeeds = com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResources(
                "seed_item_profile", "aloe_seeds", "poppy_seeds", "potato_seeds", "tomato_seeds");
        com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                .reload(ModSeedItemProfiles.TYPE, withoutWheatSeeds);
        var uncomposed = new ItemStack(ModItems.WHEAT_SEEDS.get());
        uncomposed.set(ModDataComponents.GENETIC_VALUE.get(), new GeneticValue(id("wheat"), custom));
        assertTrue(examine(uncomposed).isEmpty());
        });
    }

    private static void withCatalogs(Runnable test) {
        var manager = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        var seeds = manager.snapshot(ModSeedProfiles.TYPE);
        var items = manager.snapshot(ModSeedItemProfiles.TYPE);
        try {
            manager.reload(ModSeedProfiles.TYPE, com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResources(
                    "seed_profile", "defaults", "wheat", "meatwheat", "tomato", "blue_tomato", "blood_tomato", "killer_tomato", "potato", "poppy", "lily", "spacemans_trumpet", "aloe"));
            manager.reload(ModSeedItemProfiles.TYPE, com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResources(
                    "seed_item_profile", "aloe_seeds", "poppy_seeds", "potato_seeds", "tomato_seeds", "wheat_seeds"));
            test.run();
        } finally {
            manager.reload(ModSeedProfiles.TYPE, encode(seeds.asMap(), ModSeedProfiles.TYPE.codec()));
            manager.reload(ModSeedItemProfiles.TYPE, encode(items.asMap(), ModSeedItemProfiles.TYPE.codec()));
        }
    }
    private static <T> java.util.Map<ResourceLocation, com.google.gson.JsonObject> encode(java.util.Map<ResourceLocation,T> values, com.mojang.serialization.Codec<T> codec) {
        var raw = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        values.forEach((key, value) -> raw.put(key, codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject()));
        return raw;
    }

    private static List<ExamineEntry> examine(ItemStack stack) {
        ItemEntity entity = new ItemEntity(EntityType.ITEM, null);
        entity.setItem(stack);
        var context = new ExamineContext(null, null, null, entity, null, null, null, true);
        var collector = new ExamineCollector(true, Component.empty());
        for (var contributor : ExamineContributorRegistry.contributors()) contributor.contribute(context, collector);
        return collector.sortedEntries().stream().filter(entry -> entry.group().equals("botany-seed-packet")).toList();
    }
    private static String key(ExamineEntry entry) { return ((TranslatableContents) entry.text().getContents()).getKey(); }
    private static Object[] arguments(ExamineEntry entry) { return ((TranslatableContents) entry.text().getContents()).getArgs(); }
}
