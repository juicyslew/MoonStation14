package com.juicyslew.moonstation14.ms14.chemistry.output;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChemContainerPrototypeTest {
    @Test void upstreamCompositionDefaultsResolveForPillBottleAndCanister() {
        var bottle = resolved("base_item", "{\"abstract\":true}",
                "base_chemistry_empty_bottle", "{\"parent\":\"moonstation14:base_item\",\"solution\":{\"name\":\"drink\",\"capacity\":30,\"can_react\":true}}",
                "bottle", "{\"parent\":\"moonstation14:base_chemistry_empty_bottle\"}");
        assertEquals(30, bottle.get(id("bottle")).solution().orElseThrow().capacity());
        assertEquals(30f, com.juicyslew.moonstation14.item.custom.BottleItem.capacityFromResolved(bottle.get(id("bottle"))));
        assertEquals("drink", bottle.get(id("bottle")).solution().orElseThrow().name());
        assertTrue(bottle.get(id("bottle")).storage().isEmpty(), "chemistry bottle has no Storage component");

        var pill = resolved("base_item", "{\"abstract\":true}",
                "pill_base", "{\"parent\":\"moonstation14:base_item\",\"solution\":{\"name\":\"food\",\"capacity\":20,\"can_react\":false}}",
                "pill", "{\"parent\":\"moonstation14:pill_base\"}");
        assertEquals(20, pill.get(id("pill")).solution().orElseThrow().capacity());
        assertEquals(20f, com.juicyslew.moonstation14.item.custom.PillItem.capacityFromResolved(pill.get(id("pill"))));
        assertFalse(pill.get(id("pill")).solution().orElseThrow().canReact());
        assertTrue(pill.get(id("pill")).storage().isEmpty(), "pill has no Storage component");

        var canister = resolved("base_item", "{\"abstract\":true}",
                "base_storage_item", "{\"parent\":\"moonstation14:base_item\",\"storage\":{}}",
                "pill_canister", "{\"parent\":\"moonstation14:base_storage_item\",\"storage\":{\"width\":5,\"height\":2,\"whitelist_item\":\"moonstation14:pill\"}}");
        var inheritedStorage = canister.get(id("base_storage_item")).storage().orElseThrow();
        assertTrue(inheritedStorage.width().isEmpty() && inheritedStorage.height().isEmpty()
                && inheritedStorage.whitelistItem().isEmpty(), "BaseStorageItem carries Storage presence and real generic defaults only");
        var pillStorage = canister.get(id("pill_canister")).storage().orElseThrow();
        assertEquals(5, pillStorage.width().orElseThrow());
        assertEquals(2, pillStorage.height().orElseThrow());
        assertEquals("moonstation14:pill", pillStorage.whitelistItem().orElseThrow());
        assertTrue(canister.get(id("pill_canister")).solution().isEmpty(), "canister has no solution component");
    }

    @Test void pillTypePersistentCodecBoundsAndRoundTripsAllSpriteIndices() {
        for (int type = 0; type <= 19; type++) {
            var value = new PillData(type);
            var encoded = PillData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow();
            assertEquals(type, PillData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow().type());
        }
        assertThrows(IllegalArgumentException.class, () -> new PillData(20));
    }

    @Test void resolvedChemMasterOutputPolicyRequiresAllowedProfileAndActualComponentComposition() {
        var machine = com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterData.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"outputSlot\":{\"acceptedProfiles\":[\"moonstation14:bottle\",\"moonstation14:pill_canister\"]}}"))
                .getOrThrow();
        var bottle = resolved("base_item", "{\"abstract\":true}",
                "base_chemistry_empty_bottle", "{\"parent\":\"moonstation14:base_item\",\"solution\":{\"name\":\"drink\",\"capacity\":30}}",
                "bottle", "{\"parent\":\"moonstation14:base_chemistry_empty_bottle\"}");
        var pill = resolved("base_item", "{\"abstract\":true}",
                "pill_base", "{\"parent\":\"moonstation14:base_item\",\"solution\":{\"name\":\"food\",\"capacity\":20}}",
                "pill", "{\"parent\":\"moonstation14:pill_base\"}");
        var canister = resolved("base_item", "{\"abstract\":true}",
                "base_storage_item", "{\"parent\":\"moonstation14:base_item\",\"storage\":{}}",
                "pill_canister", "{\"parent\":\"moonstation14:base_storage_item\",\"storage\":{\"width\":5,\"height\":2,\"whitelist_item\":\"moonstation14:pill\"}}");
        var bottleId = id("bottle"); var canisterId = id("pill_canister"); var pillId = id("pill");
        assertTrue(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity.acceptsResolvedOutputPolicy(machine, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BOTTLE.get()), bottle.get(bottleId), bottleId));
        assertTrue(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity.acceptsResolvedOutputPolicy(machine, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.PILL_CANISTER.get()), canister.get(canisterId), canisterId));
        assertFalse(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity.acceptsResolvedOutputPolicy(machine, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.PILL.get()), pill.get(pillId), pillId));
        assertFalse(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity.acceptsResolvedOutputPolicy(machine, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.PILL.get()), bottle.get(bottleId), bottleId),
                "an allowlisted profile cannot authorize a different registered item");
        var excluded = com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterData.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertFalse(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity.acceptsResolvedOutputPolicy(excluded, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BOTTLE.get()), bottle.get(bottleId), bottleId));
    }

    @Test void pillProfileUsesResolvedSingleUseEdiblePolicyAndTiming() {
        var json = JsonParser.parseString("{\"edible\":{\"dose_cents\":2000,\"delay_ticks\":12,\"force_feed_delay_ticks\":40,\"destroy_on_empty\":false,\"consume_source\":true}}");
        var profile = IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(12, profile.delayTicks());
        assertEquals(40, profile.forceFeedDelayTicks());
        assertTrue(profile.consumeSource());
        assertFalse(profile.destroyOnEmpty());
    }

    private static PrototypeCatalog<ChemContainerData> resolved(String... pairs) {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String id = pairs[i];
            JsonObject object = JsonParser.parseString(pairs[i + 1]).getAsJsonObject();
            object.addProperty("id", "moonstation14:" + id);
            raw.put(id(id), object);
        }
        var merged = PrototypeResolver.resolve(raw, new ChemContainerMergeStrategy());
        Map<ResourceLocation, ChemContainerData> values = new LinkedHashMap<>();
        merged.asMap().forEach((key, value) -> values.put(key,
                ChemContainerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow()));
        return new PrototypeCatalog<>(values);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
}
