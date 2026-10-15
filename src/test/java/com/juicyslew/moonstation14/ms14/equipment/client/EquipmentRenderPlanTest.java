package com.juicyslew.moonstation14.ms14.equipment.client;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates;
import com.juicyslew.moonstation14.ms14.hands.network.BodyAppearanceSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentRenderPlanTest {
    @Test void resolvedRadiationSuitFlowsIntoAuthoredGarmentPlanAndHoodStaysSeparate() throws Exception {
        var manager = new PrototypeManager();
        manager.register(ModEquipmentItems.TYPE);
        var items = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String name : List.of("base_item", "base_clothing", "suit_rad", "hood_rad")) {
            try (var stream = getClass().getResourceAsStream("/data/moonstation14/moonstation14/equipment_item/" + name + ".json")) {
                assertNotNull(stream, name);
                items.put(ModInventoryTemplates.id(name), com.google.gson.JsonParser.parseReader(
                        new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        manager.reload(ModEquipmentItems.TYPE, items);
        var catalog = manager.snapshot(ModEquipmentItems.TYPE);
        var suitData = ModEquipmentItems.require(catalog, ModInventoryTemplates.id("suit_rad"));
        var hoodData = ModEquipmentItems.require(catalog, ModInventoryTemplates.id("hood_rad"));
        var suitStack = new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.SUIT_RAD.get());
        var hoodStack = new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.HOOD_RAD.get());
        assertEquals("item.moonstation14.suit_rad", suitStack.getItem().getDescriptionId());
        assertEquals("item.moonstation14.hood_rad", hoodStack.getItem().getDescriptionId());
        var snapshot = new BodyAppearanceSnapshot(ResourceLocation.parse("minecraft:overworld"), 1,
                java.util.UUID.randomUUID(), 0, List.of(), List.of(
                new BodyAppearanceSnapshot.Entry("outerClothing", ItemPresentation.fromStack(suitStack)),
                new BodyAppearanceSnapshot.Entry("head", ItemPresentation.fromStack(hoodStack))));

        var plan = EquipmentRenderPlan.create(snapshot, catalog);
        var suit = plan.garments().stream().filter(g -> g.slot().equals("outerClothing")).findFirst().orElseThrow();
        var hood = plan.garments().stream().filter(g -> g.slot().equals("head")).findFirst().orElseThrow();
        var worn = suitData.clothing().orElseThrow().wornModel().orElseThrow();
        assertEquals(0.01f, suitData.armor().orElseThrow().modifiers().get("radiation").coefficient());
        assertEquals(0.65f, hoodData.armor().orElseThrow().modifiers().get("radiation").coefficient());
        assertEquals(worn.mesh(), suit.wornModel().orElseThrow().mesh());
        assertEquals(ResourceLocation.parse("moonstation14:wearables/meshes/outerclothing/suit_rad.json"), suit.wornModel().orElseThrow().mesh());
        assertEquals(EquipmentItemData.AppearanceRegion.TORSO, suit.region());
        assertEquals(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape.WIDE,
                suit.wornModel().orElseThrow().bodyShape());
        assertEquals(List.of(EquipmentRenderPlan.Anchor.TORSO, EquipmentRenderPlan.Anchor.LEFT_ARM,
                EquipmentRenderPlan.Anchor.RIGHT_ARM), suit.anchors());
        assertEquals("head", hood.slot());
        assertTrue(hood.wornModel().isEmpty());
        assertEquals(List.of(EquipmentRenderPlan.Anchor.HEAD), hood.anchors());
    }

    @Test void articulatedAnchorsCoverSupportedWornGeometryAndNeverPockets() {
        assertEquals(List.of(EquipmentRenderPlan.Anchor.HEAD), EquipmentRenderPlan.anchors("head", EquipmentItemData.AppearanceRegion.HEAD));
        assertEquals(List.of(EquipmentRenderPlan.Anchor.TORSO, EquipmentRenderPlan.Anchor.LEFT_ARM,
                EquipmentRenderPlan.Anchor.RIGHT_ARM, EquipmentRenderPlan.Anchor.LEFT_LEG,
                EquipmentRenderPlan.Anchor.RIGHT_LEG), EquipmentRenderPlan.anchors("jumpsuit", EquipmentItemData.AppearanceRegion.TORSO));
        assertEquals(List.of(EquipmentRenderPlan.Anchor.TORSO, EquipmentRenderPlan.Anchor.LEFT_ARM,
                EquipmentRenderPlan.Anchor.RIGHT_ARM), EquipmentRenderPlan.anchors("outerClothing", EquipmentItemData.AppearanceRegion.TORSO));
        assertEquals(List.of(EquipmentRenderPlan.Anchor.LEFT_ARM, EquipmentRenderPlan.Anchor.RIGHT_ARM),
                EquipmentRenderPlan.anchors("gloves", EquipmentItemData.AppearanceRegion.HANDS));
        assertEquals(List.of(EquipmentRenderPlan.Anchor.LEFT_LEG, EquipmentRenderPlan.Anchor.RIGHT_LEG),
                EquipmentRenderPlan.anchors("shoes", EquipmentItemData.AppearanceRegion.FEET));
        assertTrue(EquipmentRenderPlan.anchors("pocket1", EquipmentItemData.AppearanceRegion.TORSO).isEmpty());
        assertTrue(EquipmentRenderPlan.anchors("pocket2", EquipmentItemData.AppearanceRegion.TORSO).isEmpty());
    }

    @Test void outerwearAndHandwearAreOrderedOverUniform() {
        assertTrue(EquipmentRenderPlan.layerOrder("jumpsuit") < EquipmentRenderPlan.layerOrder("outerClothing"));
        assertTrue(EquipmentRenderPlan.layerOrder("gloves") < EquipmentRenderPlan.layerOrder("outerClothing"));
    }

    @Test void smallAccessoriesHaveDistinctSemanticAnchors() {
        assertEquals(EquipmentRenderPlan.Anchor.FACE, EquipmentRenderPlan.anchors("eyes", EquipmentItemData.AppearanceRegion.EYES).getFirst());
        assertEquals(EquipmentRenderPlan.Anchor.CHEST, EquipmentRenderPlan.anchors("neck", EquipmentItemData.AppearanceRegion.NECK).getFirst());
        assertEquals(EquipmentRenderPlan.Anchor.BACK, EquipmentRenderPlan.anchors("back", EquipmentItemData.AppearanceRegion.TORSO).getFirst());
        assertEquals(EquipmentRenderPlan.Anchor.WAIST, EquipmentRenderPlan.anchors("belt", EquipmentItemData.AppearanceRegion.TORSO).getFirst());
        assertEquals(EquipmentRenderPlan.Anchor.CHEST, EquipmentRenderPlan.anchors("id", EquipmentItemData.AppearanceRegion.TORSO).getFirst());
        assertEquals(EquipmentRenderPlan.Anchor.HIP, EquipmentRenderPlan.anchors("suitstorage", EquipmentItemData.AppearanceRegion.TORSO).getFirst());
    }

    @Test void garmentPlanRetainsResolvedRegionAndArgbWithoutItemSpecificRules() {
        var garment = new EquipmentRenderPlan.Garment("outerClothing",
                net.minecraft.resources.ResourceLocation.parse("moonstation14:simple_coat"),
                EquipmentItemData.AppearanceRegion.TORSO, 0x7FAABBCC,
                EquipmentRenderPlan.anchors("outerClothing", EquipmentItemData.AppearanceRegion.TORSO),
                EquipmentRenderPlan.layerOrder("outerClothing"));
        assertEquals(0x7FAABBCC, garment.color());
        assertEquals(EquipmentItemData.AppearanceRegion.TORSO, garment.region());
        assertEquals(3, garment.anchors().size());
        assertEquals("simple_coat", garment.item().getPath());
    }

    @Test void invalidWearRegionsAreRejectedBeforeTheyBecomeGarments() {
        assertTrue(EquipmentRenderPlan.anchors("jumpsuit", EquipmentItemData.AppearanceRegion.LEGS).isEmpty());
        assertTrue(EquipmentRenderPlan.anchors("outerClothing", EquipmentItemData.AppearanceRegion.HEAD).isEmpty());
        assertEquals(5, EquipmentRenderPlan.anchors("jumpsuit", EquipmentItemData.AppearanceRegion.TORSO).size());
        assertEquals(3, EquipmentRenderPlan.anchors("outerClothing", EquipmentItemData.AppearanceRegion.TORSO).size());
        assertFalse(EquipmentPlaceholderGeometry.regionFits("jumpsuit", EquipmentItemData.AppearanceRegion.LEGS));
        assertTrue(EquipmentPlaceholderGeometry.regionFits("outerClothing", EquipmentItemData.AppearanceRegion.TORSO));
    }

    @Test void garmentLayerOrderIsSemanticAndIndependentOfInputOrder() {
        var garments = List.of(garment("shoes", EquipmentItemData.AppearanceRegion.FEET),
                garment("id", EquipmentItemData.AppearanceRegion.TORSO),
                garment("gloves", EquipmentItemData.AppearanceRegion.HANDS),
                garment("outerClothing", EquipmentItemData.AppearanceRegion.TORSO),
                garment("jumpsuit", EquipmentItemData.AppearanceRegion.TORSO));
        var first = EquipmentRenderPlan.orderedGarments(garments);
        var reversed = new java.util.ArrayList<>(garments);
        java.util.Collections.reverse(reversed);
        assertEquals(first, EquipmentRenderPlan.orderedGarments(reversed));
        assertTrue(EquipmentRenderPlan.layerOrder("jumpsuit") < EquipmentRenderPlan.layerOrder("shoes"));
        assertTrue(EquipmentRenderPlan.layerOrder("outerClothing") < EquipmentRenderPlan.layerOrder("id"));
        assertTrue(EquipmentRenderPlan.layerOrder("gloves") < EquipmentRenderPlan.layerOrder("outerClothing"));
    }

    private static EquipmentRenderPlan.Garment garment(String slot, EquipmentItemData.AppearanceRegion region) {
        return new EquipmentRenderPlan.Garment(slot, net.minecraft.resources.ResourceLocation.parse("moonstation14:" + slot.toLowerCase(java.util.Locale.ROOT)),
                region, -1, EquipmentRenderPlan.anchors(slot, region), EquipmentRenderPlan.layerOrder(slot));
    }
}
