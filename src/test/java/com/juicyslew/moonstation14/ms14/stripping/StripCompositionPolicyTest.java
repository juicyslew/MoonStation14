package com.juicyslew.moonstation14.ms14.stripping;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.*;
import com.juicyslew.moonstation14.ms14.equipment.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StripCompositionPolicyTest {
    @Test void humanResolvedCompositionSuppliesStripDefaultsAndSlotOverrides() {
        var json = JsonParser.parseString("""
                {"components":[{"type":"Hands","hands":["left","right"]},{"type":"Stripping"},{"type":"Strippable"}]}
                """).getAsJsonObject();
        CharacterData data = CharacterData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(4.0, data.component(StrippablePrototypeComponent.class).orElseThrow().handStripDelay());
        assertTrue(data.component(HandsPrototypeComponent.class).orElseThrow().canBeStripped());
        assertTrue(data.component(StrippingPrototypeComponent.class).isPresent());
        var humanTemplate = JsonParser.parseString("""
                {"slots":[{"id":"shoes","display_name":"Shoes","icon":"minecraft:item/iron_boots","x":0,"y":0,"flags":["FEET"],"strip_time":3,"strip_hidden":false},{"id":"pocket1","display_name":"Pocket","icon":"minecraft:item/air","x":0,"y":1,"flags":["POCKET"],"strip_time":3,"strip_hidden":true}]}
                """).getAsJsonObject();
        var template = InventoryTemplateData.CODEC.parse(JsonOps.INSTANCE, humanTemplate).getOrThrow();
        assertEquals(3.0, template.slot("shoes").orElseThrow().stripTime());
        assertEquals(3.0, template.slot("pocket1").orElseThrow().stripTime());
        assertTrue(template.slot("pocket1").orElseThrow().stripHidden());
        assertFalse(template.slot("shoes").orElseThrow().stripHidden());
    }

    @Test void inheritedCapabilityFieldsCanBeOverriddenAndRoundTripThroughSealedRegistry() {
        var manager = new PrototypeManager(); manager.register(ModCharacters.CHARACTER_TYPE);
        var parentId = ResourceLocation.parse("moonstation14:strip_parent");
        var childId = ResourceLocation.parse("moonstation14:strip_child");
        var parent = JsonParser.parseString("""
                {"abstract":true,"components":[{"type":"Stripping"},{"type":"Strippable","hand_strip_delay":5},{"type":"Hands","hands":["left"],"can_be_stripped":true}]}
                """).getAsJsonObject();
        var child = JsonParser.parseString("""
                {"parent":"strip_parent","components":[{"type":"Strippable","hand_strip_delay":2},{"type":"Hands","can_be_stripped":false}]}
                """).getAsJsonObject();
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(parentId,parent,childId,child));
        var resolved = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(childId);
        assertTrue(resolved.component(StrippingPrototypeComponent.class).isPresent());
        assertEquals(2.0,resolved.component(StrippablePrototypeComponent.class).orElseThrow().handStripDelay());
        assertFalse(resolved.component(HandsPrototypeComponent.class).orElseThrow().canBeStripped());
        assertEquals(resolved, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE,resolved).getOrThrow()).getOrThrow());
    }

    @Test void capabilityConsumerFailsClosedOnMissingActorMarkerTargetPolicyOrUnresolvedTemplate() {
        var actor = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"components":[{"type":"Stripping"}]}
                """).getAsJsonObject()).getOrThrow();
        var target = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"components":[{"type":"Strippable"},{"type":"Hands","hands":["left"]},{"type":"EquipmentSlots","template":"moonstation14:human"}]}
                """).getAsJsonObject()).getOrThrow();
        assertTrue(StripCapability.canActorOpen(actor));
        assertTrue(StripCapability.canTargetBeStripped(target, true));
        assertFalse(StripCapability.canStripInventory(target, false));
        assertTrue(StripCapability.canTargetBeStripped(target, false), "resolved strip-capable hands still permit a hand-only view");
        var noStripHands = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"components":[{"type":"Strippable"},{"type":"Hands","hands":["left"],"can_be_stripped":false},{"type":"EquipmentSlots","template":"moonstation14:human"}]}
                """).getAsJsonObject()).getOrThrow();
        assertTrue(StripCapability.canStripInventory(noStripHands, true));
        assertFalse(StripCapability.canStripHands(noStripHands));
        assertTrue(StripCapability.canTargetBeStripped(noStripHands, true));
        assertFalse(StripCapability.canActorOpen(target));
        var equippedNoHands = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"components":[{"type":"Strippable"},{"type":"Hands","hands":["left"],"can_be_stripped":false},{"type":"EquipmentSlots","template":"moonstation14:human"}]}
                """).getAsJsonObject()).getOrThrow();
        assertTrue(StripCapability.canStripInventory(equippedNoHands, true), "hand restriction must not deny inventory actions");
        assertFalse(StripCapability.canStripHands(equippedNoHands));
        assertTrue(StripCapability.canTargetBeStripped(equippedNoHands, true), "inventory capability permits viewing target");
        var handsOnly = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"components":[{"type":"Strippable"},{"type":"Hands","hands":["left"]}]}
                """).getAsJsonObject()).getOrThrow();
        assertTrue(StripCapability.canStripHands(handsOnly));
        assertTrue(StripCapability.canTargetBeStripped(handsOnly, false), "hands-only target does not need an inventory template");
        assertFalse(StripCapability.canStripInventory(handsOnly, false));
        var unsupported = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"components":[{"type":"Strippable"}]}
                """).getAsJsonObject()).getOrThrow();
        assertFalse(StripCapability.canTargetBeStripped(unsupported, false));
        var handsWithoutStrippable = CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"components":[{"type":"Hands","hands":["left"]}]}
                """).getAsJsonObject()).getOrThrow();
        assertFalse(StripCapability.canStripHands(handsWithoutStrippable), "hands alone do not grant target stripping capability");
    }

    @Test void clothingDelayIsInheritedAndTimingClampsThenQuantizes() {
        var manager = new PrototypeManager(); manager.register(ModEquipmentItems.TYPE);
        var parent = ResourceLocation.parse("moonstation14:strip_clothing_base");
        var child = ResourceLocation.parse("moonstation14:strip_clothing_child");
        manager.reload(ModEquipmentItems.TYPE, Map.of(parent, JsonParser.parseString("""
                {"item":{"size":"Normal"},"clothing":{"slot_flags":["FEET"],"region":"FEET","color":-1,"strip_delay":-1.0}}
                """).getAsJsonObject(), child, JsonParser.parseString("""
                {"parent":"strip_clothing_base","clothing":{"strip_delay":-2.0}}
                """).getAsJsonObject()));
        var clothing = manager.snapshot(ModEquipmentItems.TYPE).get(child).clothing().orElseThrow();
        assertEquals(-2.0,clothing.stripDelay());
        assertEquals(40,StripTimingPolicy.durationTicks(4,clothing.stripDelay(),MobStateComponent.State.ALIVE));
        assertEquals(1,StripTimingPolicy.durationTicks(4,-9,MobStateComponent.State.CRITICAL));
        assertEquals(27,StripTimingPolicy.durationTicks(4,0,MobStateComponent.State.DEAD));
        assertEquals(1,StripTimingPolicy.durationTicks(0,-1,MobStateComponent.State.ALIVE));
        assertEquals(2,StripTimingPolicy.durationTicks(0.051,0,MobStateComponent.State.ALIVE));
    }
}
