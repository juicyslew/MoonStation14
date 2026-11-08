package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponProfile;
import com.juicyslew.moonstation14.ms14.combat.ModMeleeWeaponProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ThrownRosterCompositionTest {
    private static final String ROOT = "/data/moonstation14/moonstation14/thrown_item_profile/";

    @Test void productionRosterResolvesInheritedCombatAndInjectorDefaults() {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (String name : new String[]{"base_item_sounds", "bola", "dart", "hypo_dart", "throwing_knife"})
            raw.put(id(name), read(name));
        var resolved = PrototypeResolver.resolve(raw, ModThrownItemProfiles.TYPE.mergeStrategy().orElseThrow());
        assertEquals(ThrownItemProfile.CODEC, ModThrownItemProfiles.TYPE.codec());
        assertFalse(resolved.contains(id("kitchen_knife")));
        var knife = decode(resolved.get(id("throwing_knife")));
        assertTrue(knife.alignToVelocity());
        assertEquals(Map.of("slash", 10f, "piercing", 15f), knife.damageOnHit());
        assertTrue(knife.ignoreResistances());
        assertEquals(-.15f, knife.embedding().orElseThrow().offsetX());
        assertEquals(0f, knife.embedding().orElseThrow().offsetY());
        assertEquals(45f, knife.embedding().orElseThrow().spriteForwardDegrees());
        var dart = decode(resolved.get(id("dart")));
        assertTrue(dart.alignToVelocity());
        assertFalse(dart.ignoreResistances());
        assertEquals(315f, dart.embedding().orElseThrow().spriteForwardDegrees());
        assertTrue(dart.embedding().orElseThrow().hitSound().isEmpty());
        assertEquals(2f, dart.landSoundVolumeDb());
        assertEquals(3f, dart.collisionSound().orElseThrow().minimumVelocity());
        assertEquals("moonstation14:ss14/effects/weak_hit", dart.collisionSound().orElseThrow().cue().event().toString());
        assertInjector(dart, 200, SetHolder.OUTER);
        var hypo = decode(resolved.get(id("hypo_dart")));
        assertTrue(hypo.alignToVelocity());
        assertEquals(4f, hypo.damageOnHit().get("piercing"));
        assertEquals(.5f, hypo.embedding().orElseThrow().removeSeconds());
        assertInjector(hypo, 1000, SetHolder.EMPTY);
        var bola = decode(resolved.get(id("bola")));
        assertFalse(bola.alignToVelocity());
        assertEquals(2f, bola.ensnaring().orElseThrow().freeSeconds());
        assertEquals("moonstation14:ss14/effects/snap", bola.ensnaring().orElseThrow().ensnareSound().orElseThrow().toString());
        assertEquals(0f, bola.landSoundVolumeDb());
    }

    @Test void productionMeleeFamilyResolvesThrownWeaponProfilesAndInjectorComposition() {
        var resolved = loadFamily("melee_weapon_profile", new String[]{"base_melee", "dart", "hypo_dart", "throwing_knife", "kitchen_knife"}, ModMeleeWeaponProfiles.TYPE.mergeStrategy().orElseThrow());
        assertEquals(MeleeWeaponProfile.CODEC, ModMeleeWeaponProfiles.TYPE.codec());
        var dart = melee(resolved, "dart");
        assertEquals(2f, dart.weapon().damage().get("piercing"));
        assertEquals(-45f, dart.weapon().wideAnimationRotation());
        assertEquals("moonstation14:ss14/weapons/pierce", dart.weapon().audio().hit().event().toString());
        assertEquals(new MeleeWeaponProfile.MeleeChemicalInjectorData("melee", 100, true), dart.chemicalInjector().orElseThrow());
        var hypo = melee(resolved, "hypo_dart");
        assertEquals(dart, hypo);
        var knife = melee(resolved, "throwing_knife");
        assertEquals(5f, knife.weapon().damage().get("slash"));
        assertEquals(2f, knife.weapon().attackRate());
        assertEquals(-135f, knife.weapon().wideAnimationRotation());
        assertEquals("moonstation14:ss14/weapons/blade_slice", knife.weapon().audio().hit().event().toString());
        assertEquals(10f, melee(resolved, "kitchen_knife").weapon().damage().get("slash"));
    }
    private static MeleeWeaponProfile melee(PrototypeCatalog<JsonObject> catalog, String name) {
        return MeleeWeaponProfile.CODEC.parse(JsonOps.INSTANCE, catalog.get(id(name))).getOrThrow();
    }

    @Test void productionFoodSolutionFamilyDefinesDartAndHypoDoseAndRefillPolicy() {
        var resolved = loadFamily("item_food_solution_profile", new String[]{"dart", "hypo_dart"}, ModItemFoodSolutionProfiles.TYPE.mergeStrategy().orElse(null));
        var dart = ItemFoodSolutionProfile.CODEC.parse(JsonOps.INSTANCE, resolved.get(id("dart"))).getOrThrow();
        var hypo = ItemFoodSolutionProfile.CODEC.parse(JsonOps.INSTANCE, resolved.get(id("hypo_dart"))).getOrThrow();
        assertSolution(dart, 200); assertSolution(hypo, 1000);
    }

    @Test void productionEquipmentCompositionResolvesArmorFromClothingParent() {
        var resolved = loadFamily("equipment_item", new String[]{"base_item", "base_clothing", "clothing_head_base", "armor_vest", "basic_helmet"}, ModEquipmentItems.TYPE.mergeStrategy().orElseThrow());
        for (String name : new String[]{"armor_vest", "basic_helmet"}) {
            EquipmentItemData data = EquipmentItemData.CODEC.parse(JsonOps.INSTANCE, resolved.get(id(name))).getOrThrow();
            assertTrue(data.armor().isPresent(), name);
        }
        assertEquals(.7f, resolvedArmor(resolved, "armor_vest", "blunt"));
        assertEquals(.8f, resolvedArmor(resolved, "armor_vest", "heat"));
        assertEquals(.9f, resolvedArmor(resolved, "basic_helmet", "blunt"));
    }

    private static float resolvedArmor(PrototypeCatalog<JsonObject> catalog, String item, String damage) {
        var typed = EquipmentItemData.CODEC.parse(JsonOps.INSTANCE, catalog.get(id(item))).getOrThrow();
        return typed.armor().orElseThrow().coefficients().get(damage);
    }
    private static void assertSolution(ItemFoodSolutionProfile profile, int capacity) {
        assertEquals(capacity, profile.capacityCents()); assertEquals("melee", profile.solutionName());
        assertTrue(profile.contentsCents().isEmpty()); assertTrue(profile.refillable());
    }
    private static PrototypeCatalog<JsonObject> loadFamily(String family, String[] names, com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy strategy) {
        Map<ResourceLocation, JsonObject> values = new LinkedHashMap<>();
        for (String name : names) values.put(id(name), read("/data/moonstation14/moonstation14/" + family + "/", name));
        return strategy == null ? PrototypeResolver.resolve(values) : PrototypeResolver.resolve(values, strategy);
    }

    private static void assertInjector(ThrownItemProfile profile, int cents, java.util.Set<com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag> slots) {
        var injector = profile.injectOnEmbed().orElseThrow();
        assertEquals("melee", injector.solutionName()); assertEquals(cents, injector.transferCents());
        assertTrue(injector.pierceArmor()); assertEquals(slots, injector.blockSlots()); assertEquals(1f, injector.transferEfficiency());
    }
    private static final class SetHolder {
        static final java.util.Set<com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag> EMPTY = java.util.Set.of();
        static final java.util.Set<com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag> OUTER = java.util.Set.of(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.OUTERCLOTHING);
    }
    private static ThrownItemProfile decode(JsonObject json) { return ThrownItemProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(); }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("moonstation14", name); }
    private static JsonObject read(String name) {
        return read(ROOT, name);
    }
    private static JsonObject read(String root, String name) {
        try (var stream = ThrownRosterCompositionTest.class.getResourceAsStream(root + name + ".json")) {
            assertNotNull(stream, name); return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
