package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static org.junit.jupiter.api.Assertions.*;

class EquippedArmorTest {
    @Test void armorReducesFlatThenCoefficientAndLeavesOtherTypesAndHealingAlone() {
        var damage = new LinkedHashMap<>(Map.of(DamageKeys.RADIATION, 10f, DamageKeys.HEAT, 4f));
        EquippedArmor.apply(damage, new EquipmentItemData.Armor(Map.of(
                DamageKeys.RADIATION, new EquipmentItemData.ArmorModifier(2f, .5f))));
        assertEquals(4f, damage.get(DamageKeys.RADIATION));
        assertEquals(4f, damage.get(DamageKeys.HEAT));
        var healing = new LinkedHashMap<>(Map.of(DamageKeys.RADIATION, -10f));
        EquippedArmor.apply(healing, new EquipmentItemData.Armor(Map.of(
                DamageKeys.RADIATION, new EquipmentItemData.ArmorModifier(2f, .01f))));
        assertEquals(-10f, healing.get(DamageKeys.RADIATION));
        var healingAcrossArmor = Map.of(DamageKeys.RADIATION, -10f);
        var first = ArmorModifiers.apply(healingAcrossArmor, List.of(new EquipmentItemData.Armor(Map.of(
                DamageKeys.RADIATION, new EquipmentItemData.ArmorModifier(2f, .5f)))));
        assertEquals(-10f, ArmorModifiers.apply(first, List.of(new EquipmentItemData.Armor(Map.of(
                DamageKeys.RADIATION, new EquipmentItemData.ArmorModifier(1f, 0f))))).get(DamageKeys.RADIATION));
    }

    @Test void armorCodecRejectsUnknownDamageAndInvalidValues() {
        assertTrue(EquipmentItemData.ARMOR_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"modifiers\":{\"not_damage\":{}}}")).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new EquipmentItemData.Armor(Map.of(
                DamageKeys.RADIATION, new EquipmentItemData.ArmorModifier(-1f, 1f))));
        var vulnerability = new EquipmentItemData.Armor(Map.of(
                DamageKeys.RADIATION, new EquipmentItemData.ArmorModifier(0f, 1.5f)));
        assertEquals(15f, ArmorModifiers.apply(Map.of(DamageKeys.RADIATION, 10f), List.of(vulnerability))
                .get(DamageKeys.RADIATION));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentItemData.Armor(Map.of(
                DamageKeys.RADIATION, new EquipmentItemData.ArmorModifier(Float.NaN, 1f))));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentItemData.Armor(Map.of(
                DamageKeys.RADIATION, new EquipmentItemData.ArmorModifier(0f, Float.POSITIVE_INFINITY))));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentItemData.Armor(Map.of(
                DamageKeys.RADIATION, new EquipmentItemData.ArmorModifier(0f, Float.NEGATIVE_INFINITY))));
    }

    @Test void resolvedCatalogConsumerUsesWornSlotFlagsAndExcludesPocketsAndSuitStorage() throws Exception {
        var manager = new PrototypeManager();
        manager.register(ModEquipmentItems.TYPE);
        var raw = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("minecraft", "chainmail_helmet"), JsonParser.parseString(
                "{\"item\":{\"size\":\"Normal\"},\"armor\":{\"modifiers\":{\"radiation\":{\"coefficient\":0.65}}}}").getAsJsonObject());
        raw.put(ResourceLocation.fromNamespaceAndPath("minecraft", "chainmail_chestplate"), JsonParser.parseString(
                "{\"item\":{\"size\":\"Normal\"},\"armor\":{\"modifiers\":{\"radiation\":{\"coefficient\":0.01}}}}").getAsJsonObject());
        raw.put(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_helmet"), JsonParser.parseString(
                "{\"item\":{\"size\":\"Normal\"},\"armor\":{\"modifiers\":{\"radiation\":{\"coefficient\":0}}}}").getAsJsonObject());
        manager.reload(ModEquipmentItems.TYPE, raw);
        var catalog = manager.snapshot(ModEquipmentItems.TYPE);
        var templateJson = JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream(
                "/data/moonstation14/moonstation14/inventory_template/human.json")));
        var template = InventoryTemplateData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, templateJson).getOrThrow();
        var snapshot = new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSnapshot(true, List.of(
                new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSlot("outerClothing", Optional.empty(), Optional.of(new ItemStack(Items.CHAINMAIL_CHESTPLATE))),
                new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSlot("head", Optional.empty(), Optional.of(new ItemStack(Items.CHAINMAIL_HELMET))),
                new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSlot("pocket1", Optional.empty(), Optional.of(new ItemStack(Items.IRON_HELMET))),
                new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSlot("suitstorage", Optional.empty(), Optional.of(new ItemStack(Items.IRON_HELMET)))));
        var result = EquippedArmor.modify(snapshot, template, catalog, Map.of(DamageKeys.RADIATION, 10f));
        // SS14 FixedPoint2 truncates to two decimals after each worn item: .10 then .06.
        assertEquals(.06f, result.get(DamageKeys.RADIATION), .000001f);
    }

    @Test void equippedOuterSuitAndSeparatelyWornHoodMultiplyRadiationProtection() throws Exception {
        var manager = new PrototypeManager(); manager.register(ModEquipmentItems.TYPE);
        var raw = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("minecraft", "chainmail_chestplate"), JsonParser.parseString(
                "{\"item\":{\"size\":\"Huge\"},\"armor\":{\"modifiers\":{\"radiation\":{\"coefficient\":0.01}}}}").getAsJsonObject());
        raw.put(ResourceLocation.fromNamespaceAndPath("minecraft", "chainmail_helmet"), JsonParser.parseString(
                "{\"item\":{\"size\":\"Small\"},\"armor\":{\"modifiers\":{\"radiation\":{\"coefficient\":0.65}}}}").getAsJsonObject());
        manager.reload(ModEquipmentItems.TYPE, raw);
        var templateJson = JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream(
                "/data/moonstation14/moonstation14/inventory_template/human.json")));
        var template = InventoryTemplateData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, templateJson).getOrThrow();
        var snapshot = new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSnapshot(true, List.of(
                new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSlot("outerClothing", Optional.empty(), Optional.of(new ItemStack(Items.CHAINMAIL_CHESTPLATE))),
                new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSlot("head", Optional.empty(), Optional.of(new ItemStack(Items.CHAINMAIL_HELMET)))));
        var catalog = manager.snapshot(ModEquipmentItems.TYPE);
        // SS14 FixedPoint2 truncates to two decimals after each worn item: .10 then .06.
        assertEquals(.06f, EquippedArmor.modify(snapshot, template, catalog, Map.of(DamageKeys.RADIATION, 10f)).get(DamageKeys.RADIATION), .000001f);
        var suitOnly = new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSnapshot(true, List.of(
                new com.juicyslew.moonstation14.ms14.hands.live.LiveHands.EquipmentSlot("outerClothing", Optional.empty(), Optional.of(new ItemStack(Items.CHAINMAIL_CHESTPLATE)))));
        assertEquals(.1f, EquippedArmor.modify(suitOnly, template, catalog, Map.of(DamageKeys.RADIATION, 10f)).get(DamageKeys.RADIATION), .000001f);
    }
}
