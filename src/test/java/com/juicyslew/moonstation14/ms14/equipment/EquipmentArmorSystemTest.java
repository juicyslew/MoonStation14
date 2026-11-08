package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentArmorSystemTest {
    private static InventoryTemplateData.Slot slot(String id, InventoryTemplateData.SlotFlag... flags) {
        return new InventoryTemplateData.Slot(id, id, ResourceLocation.parse("moonstation14:textures/gui/equipment/head.png"),
                "Default", 0, 0, List.of(flags), List.of(), List.of(), false);
    }
    @Test void collectsResolvedProfilesOnlyFromOccupiedNonPocketSlotsInLayoutOrder() {
        var layout = new InventoryTemplateData(List.of(slot("head", InventoryTemplateData.SlotFlag.HEAD),
                slot("pocket1", InventoryTemplateData.SlotFlag.POCKET), slot("belt", InventoryTemplateData.SlotFlag.BELT),
                slot("id", InventoryTemplateData.SlotFlag.HEAD, InventoryTemplateData.SlotFlag.POCKET)));
        var head = ResourceLocation.parse("minecraft:leather_helmet");
        var belt = ResourceLocation.parse("minecraft:iron_ingot");
        var blunt = new EquipmentItemData.Armor(Map.of("blunt", new EquipmentItemData.ArmorModifier(2f, .5f)));
        var caustic = EquipmentItemData.Armor.fromCoefficients(Map.of("caustic", .95f));
        var holy = EquipmentItemData.Armor.fromCoefficients(Map.of("holy", .8f));
        var catalog = new PrototypeCatalog<>(Map.of(head, item(blunt), belt, item(caustic),
                ResourceLocation.parse("minecraft:stone"), item(holy)));
        var snapshot = new LiveHands.EquipmentSnapshot(true, List.of(
                new LiveHands.EquipmentSlot("head", Optional.of("h"), Optional.of(new ItemStack(Items.LEATHER_HELMET))),
                new LiveHands.EquipmentSlot("pocket1", Optional.of("p"), Optional.of(new ItemStack(Items.LEATHER_HELMET))),
                new LiveHands.EquipmentSlot("belt", Optional.of("b"), Optional.of(new ItemStack(Items.IRON_INGOT))),
                new LiveHands.EquipmentSlot("id", Optional.of("m"), Optional.of(new ItemStack(Items.STONE))),
                new LiveHands.EquipmentSlot("unknown", Optional.of("u"), Optional.of(new ItemStack(Items.LEATHER_HELMET)))));
        var worn = ResolvedWornEquipment.entries(snapshot, layout, catalog);
        assertEquals(List.of(blunt, caustic), EquipmentArmorSystem.collect(worn));
        assertEquals(Map.of("blunt", 1.5f, "caustic", 4.75f, "holy", 5f),
                EquipmentArmorSystem.apply(snapshot, layout, catalog, Map.of("blunt", 5f, "caustic", 5f, "holy", 5f)));
    }

    @Test void emptyUnresolvedAndHeldOnlySlotsAreNeutral() {
        var layout = new InventoryTemplateData(List.of(slot("head", InventoryTemplateData.SlotFlag.HEAD)));
        var catalog = new PrototypeCatalog<EquipmentItemData>(Map.of());
        var empty = new LiveHands.EquipmentSnapshot(true, List.of(new LiveHands.EquipmentSlot("head", Optional.empty(), Optional.empty())));
        assertTrue(EquipmentArmorSystem.collect(ResolvedWornEquipment.entries(empty, layout, catalog)).isEmpty());
        var unresolved = new LiveHands.EquipmentSnapshot(true, List.of(new LiveHands.EquipmentSlot("pocket1", Optional.of("x"), Optional.of(new ItemStack(Items.LEATHER_HELMET)))));
        assertTrue(EquipmentArmorSystem.collect(ResolvedWornEquipment.entries(unresolved, layout, catalog)).isEmpty());
    }
    @Test void noSlipRequiresResolvedProfileInOccupiedFeetFlaggedEquipmentSlot() {
        var layout = new InventoryTemplateData(List.of(slot("shoes", InventoryTemplateData.SlotFlag.FEET),
                slot("head", InventoryTemplateData.SlotFlag.HEAD, InventoryTemplateData.SlotFlag.FEET),
                slot("eyes", InventoryTemplateData.SlotFlag.HEAD),
                slot("pocket1", InventoryTemplateData.SlotFlag.FEET, InventoryTemplateData.SlotFlag.POCKET)));
        var marker = new EquipmentItemData.NoSlip();
        var profile = new EquipmentItemData(new EquipmentItemData.Item("Small"), Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.of(marker));
        var catalog = new PrototypeCatalog<>(Map.of(ResourceLocation.parse("minecraft:leather_helmet"), profile));
        assertFalse(query(layout, catalog, new LiveHands.EquipmentSlot("eyes", Optional.of("h"), Optional.of(new ItemStack(Items.LEATHER_HELMET)))));
        assertFalse(query(layout, catalog, new LiveHands.EquipmentSlot("shoes", Optional.empty(), Optional.empty())));
        assertTrue(query(layout, catalog, new LiveHands.EquipmentSlot("pocket1", Optional.of("p"), Optional.of(new ItemStack(Items.LEATHER_HELMET)))));
        var pocketOnly = new InventoryTemplateData(List.of(slot("pocket1", InventoryTemplateData.SlotFlag.POCKET)));
        assertFalse(query(pocketOnly, catalog, new LiveHands.EquipmentSlot("pocket1", Optional.of("p"), Optional.of(new ItemStack(Items.LEATHER_HELMET)))));
        assertTrue(query(layout, catalog, new LiveHands.EquipmentSlot("head", Optional.of("m"), Optional.of(new ItemStack(Items.LEATHER_HELMET)))));
    }
    private static boolean query(InventoryTemplateData layout, PrototypeCatalog<EquipmentItemData> catalog,
                                LiveHands.EquipmentSlot slot) {
        return EquipmentNoSlipPolicy.hasWornFeetNoSlip(ResolvedWornEquipment.entries(
                new LiveHands.EquipmentSnapshot(true, List.of(slot)), layout, catalog));
    }
    private static EquipmentItemData item(EquipmentItemData.Armor armor) {
        return new EquipmentItemData(new EquipmentItemData.Item("Small"), Optional.empty(), Optional.empty(), List.of(), Optional.of(armor));
    }
}
