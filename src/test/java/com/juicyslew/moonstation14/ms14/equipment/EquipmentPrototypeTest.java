package com.juicyslew.moonstation14.ms14.equipment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentPrototypeTest {
    private static JsonObject resource(String family,String name) throws Exception {
        try(var stream=EquipmentPrototypeTest.class.getResourceAsStream("/data/moonstation14/moonstation14/"+family+"/"+name+".json")) {
            assertNotNull(stream,name); return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void humanoidTemplateAndComposedStarterItemsResolveInheritedDefaults() throws Exception {
        var manager=new PrototypeManager();manager.register(ModInventoryTemplates.TYPE);manager.register(ModEquipmentItems.TYPE);
        var templates=new LinkedHashMap<ResourceLocation,JsonObject>();templates.put(ModInventoryTemplates.HUMAN_ID,resource("inventory_template","human"));
        manager.reload(ModInventoryTemplates.TYPE,templates);
        var items=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String name:java.util.List.of("base_item","base_clothing","grey_cap","grey_jumpsuit","simple_coat","black_gloves","black_shoes","glasses","red_tie","black_bandana","earpieces","blank_id_card","utility_case","bag","belt"))
            items.put(ModInventoryTemplates.id(name),resource("equipment_item",name));
        manager.reload(ModEquipmentItems.TYPE,items);
        var template=manager.snapshot(ModInventoryTemplates.TYPE).get(ModInventoryTemplates.HUMAN_ID);
        assertEquals(15,template.slots().size());assertEquals(EquipmentSlots.SLOT_ORDER,template.slots().stream().map(InventoryTemplateData.Slot::id).toList());
        assertEquals("Default",template.slot("shoes").orElseThrow().group());
        assertEquals(java.util.List.of(InventoryTemplateData.SlotFlag.FEET),template.slot("shoes").orElseThrow().flags());
        for(var slot:template.slots()) {
            assertTrue(slot.icon().getPath().startsWith("textures/gui/equipment/"));
            try(var icon=EquipmentPrototypeTest.class.getResourceAsStream("/assets/"+slot.icon().getNamespace()+"/"+slot.icon().getPath())) {
                assertNotNull(icon,slot.icon().toString());
            } catch(java.io.IOException e) { throw new AssertionError(e); }
        }
        assertEquals(java.util.List.of("jumpsuit"),template.slot("pocket1").orElseThrow().dependsOn());
        assertEquals(java.util.List.of("jumpsuit"),template.slot("id").orElseThrow().dependsOn());
        assertTrue(template.slot("suitstorage").orElseThrow().allowSuitStorage());assertEquals(java.util.List.of("outerClothing"),template.slot("suitstorage").orElseThrow().dependsOn());
        assertEquals(java.util.List.of(EquipmentTag.PET_ONLY),template.slot("back").orElseThrow().blacklist());
        var resolved=manager.snapshot(ModEquipmentItems.TYPE);
        assertEquals("Normal",resolved.get(ModInventoryTemplates.id("grey_cap")).item().size());
        assertEquals(java.util.List.of(InventoryTemplateData.SlotFlag.HEAD),resolved.get(ModInventoryTemplates.id("grey_cap")).clothing().orElseThrow().slotFlags());
        for(String id:java.util.List.of("grey_cap","grey_jumpsuit","simple_coat","black_shoes","belt"))
            assertEquals("Normal",resolved.get(ModInventoryTemplates.id(id)).item().size(),id);
        for(String id:java.util.List.of("black_gloves","glasses","red_tie","black_bandana","earpieces","blank_id_card"))
            assertEquals("Small",resolved.get(ModInventoryTemplates.id(id)).item().size(),id);
        assertEquals("Ginormous",resolved.get(ModInventoryTemplates.id("utility_case")).item().size());
        assertThrows(IllegalArgumentException.class, () -> new EquipmentItemData.Item("Unrecognized"));
        assertEquals("Huge",resolved.get(ModInventoryTemplates.id("bag")).item().size());
        assertEquals(0,resolved.get(ModInventoryTemplates.id("grey_cap")).clothing().orElseThrow().equipDelay());
        assertEquals(java.util.List.of(InventoryTemplateData.SlotFlag.IDCARD),
                resolved.get(ModInventoryTemplates.id("blank_id_card")).clothing().orElseThrow().quickEquipPreferredSlotFlags());
        assertTrue(resolved.get(ModInventoryTemplates.id("grey_cap")).clothing().orElseThrow().quickEquipPreferredSlotFlags().isEmpty());
        assertTrue(resolved.get(ModInventoryTemplates.id("grey_cap")).clothing().orElseThrow().quickEquip());
        assertTrue(resolved.get(ModInventoryTemplates.id("grey_cap")).clothing().orElseThrow().equipSound().isEmpty());
        assertTrue(resolved.get(ModInventoryTemplates.id("simple_coat")).allowSuitStorage().isEmpty());
        assertTrue(resolved.get(ModInventoryTemplates.id("utility_case")).allowSuitStorage().isEmpty());
    }

    @Test void quickEquipDefaultAndInheritedFalseSurviveChildOverrides() throws Exception {
        var manager=new PrototypeManager();manager.register(ModEquipmentItems.TYPE);
        var items=new LinkedHashMap<ResourceLocation,JsonObject>();
        items.put(ModInventoryTemplates.id("base_item"),resource("equipment_item","base_item"));
        items.put(ModInventoryTemplates.id("base_clothing"),resource("equipment_item","base_clothing"));
        items.put(ModInventoryTemplates.id("grey_cap"),resource("equipment_item","grey_cap"));
        var parent=resource("equipment_item","grey_cap");
        parent.getAsJsonObject("clothing").addProperty("quick_equip",false);
        items.put(ModInventoryTemplates.id("no_quick_parent"),parent);
        var child=JsonParser.parseString("{\"parent\":\"no_quick_parent\",\"item\":{\"size\":\"Small\"},\"clothing\":{\"color\":-1}}").getAsJsonObject();
        items.put(ModInventoryTemplates.id("child_override"),child);
        manager.reload(ModEquipmentItems.TYPE,items);
        var resolved=manager.snapshot(ModEquipmentItems.TYPE);
        assertTrue(resolved.get(ModInventoryTemplates.id("grey_cap")).clothing().orElseThrow().quickEquip());
        assertFalse(resolved.get(ModInventoryTemplates.id("child_override")).clothing().orElseThrow().quickEquip());
        assertEquals("Small",resolved.get(ModInventoryTemplates.id("child_override")).item().size());
        assertEquals(0xffffffff,resolved.get(ModInventoryTemplates.id("child_override")).clothing().orElseThrow().color());
        var preferredParent=resource("equipment_item","blank_id_card");
        items.put(ModInventoryTemplates.id("preferred_parent"),preferredParent);
        var inherited=JsonParser.parseString("{\"parent\":\"preferred_parent\",\"clothing\":{\"color\":-1}}").getAsJsonObject();
        items.put(ModInventoryTemplates.id("preferred_inherited"),inherited);
        var cleared=JsonParser.parseString("{\"parent\":\"preferred_parent\",\"clothing\":{\"quick_equip_preferred_slot_flags\":[]}}").getAsJsonObject();
        items.put(ModInventoryTemplates.id("preferred_cleared"),cleared);
        manager.reload(ModEquipmentItems.TYPE,items);
        var composed=manager.snapshot(ModEquipmentItems.TYPE);
        assertEquals(java.util.List.of(InventoryTemplateData.SlotFlag.IDCARD),composed.get(ModInventoryTemplates.id("preferred_inherited")).clothing().orElseThrow().quickEquipPreferredSlotFlags());
        assertTrue(composed.get(ModInventoryTemplates.id("preferred_cleared")).clothing().orElseThrow().quickEquipPreferredSlotFlags().isEmpty());
    }

    @Test void templatesPermitSubsetAndApplyOnlyOmittedSlotFlagDefaults() {
        var decoded=InventoryTemplateData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"slots\":[{\"id\":\"belt\",\"display_name\":\"Belt\",\"icon\":\"moonstation14:textures/gui/equipment/belt.png\",\"x\":0,\"y\":0}]}").getAsJsonObject()).getOrThrow();
        assertEquals(1,decoded.slots().size());
        assertEquals(java.util.List.of(InventoryTemplateData.SlotFlag.PREVENTEQUIP),decoded.slot("belt").orElseThrow().flags());
        assertTrue(InventoryTemplateData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{}").getAsJsonObject()).getOrThrow().slots().isEmpty());
        var icon=net.minecraft.resources.ResourceLocation.parse("moonstation14:textures/gui/equipment/belt.png");
        var first=new InventoryTemplateData.Slot("belt","Belt",icon,"Default",0,0,java.util.List.of(),java.util.List.of("back"),java.util.List.of(),false);
        var second=new InventoryTemplateData.Slot("back","Back",icon,"Default",0,0,java.util.List.of(),java.util.List.of("belt"),java.util.List.of(),false);
        var cycleError=assertThrows(IllegalArgumentException.class,()->new InventoryTemplateData(java.util.List.of(first,second)));
        assertTrue(cycleError.getMessage().contains("cyclic equipment slot dependency"),cycleError.getMessage());
        assertTrue(InventoryTemplateData.SLOT_FLAG_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("\"NOT_A_SLOT_FLAG\"")).error().isPresent());
    }

    @Test void resolvedCompositionControlsFitAndDependencies() throws Exception {
        var manager=new PrototypeManager();manager.register(ModInventoryTemplates.TYPE);manager.register(ModEquipmentItems.TYPE);
        var templates=new LinkedHashMap<ResourceLocation,JsonObject>();templates.put(ModInventoryTemplates.HUMAN_ID,resource("inventory_template","human"));
        manager.reload(ModInventoryTemplates.TYPE,templates);
        var items=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String name:java.util.List.of("base_item","base_clothing","grey_cap","grey_jumpsuit","simple_coat","black_gloves","black_shoes","glasses","red_tie","black_bandana","earpieces","blank_id_card","utility_case","bag","belt"))
            items.put(ModInventoryTemplates.id(name),resource("equipment_item",name));
        manager.reload(ModEquipmentItems.TYPE,items);
        var template=manager.snapshot(ModInventoryTemplates.TYPE).get(ModInventoryTemplates.HUMAN_ID);
        var catalog=manager.snapshot(ModEquipmentItems.TYPE);
        manager.register(com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.TYPE);
        var medicalRaw=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String name:java.util.List.of("base_item","base_healing_item","brutepack"))
            medicalRaw.put(ModInventoryTemplates.id(name),resource("medical_item",name));
        manager.reload(com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.TYPE,medicalRaw);
        var cap=catalog.get(ModInventoryTemplates.id("grey_cap"));
        assertTrue(EquipmentFitPolicy.fits(template,"head",cap,java.util.Set.of(),false));
        var neckHead = template.slots().stream().map(slot -> slot.id().equals("head")
                ? new InventoryTemplateData.Slot(slot.id(), slot.displayName(), slot.icon(), slot.group(), slot.x(), slot.y(),
                java.util.List.of(InventoryTemplateData.SlotFlag.NECK), slot.dependsOn(), slot.blacklist(), slot.allowSuitStorage())
                : slot).toList();
        var neckTemplate = new InventoryTemplateData(neckHead);
        var neckClothing = new EquipmentItemData(cap.item(), java.util.Optional.of(new EquipmentItemData.Clothing(
                java.util.List.of(InventoryTemplateData.SlotFlag.NECK), EquipmentItemData.AppearanceRegion.HEAD,
                0xffffffff, 0, java.util.Optional.empty(), java.util.Optional.empty())), cap.allowSuitStorage(), cap.tags());
        assertFalse(EquipmentFitPolicy.fits(neckTemplate,"head",cap,java.util.Set.of(),false));
        assertTrue(EquipmentFitPolicy.fits(neckTemplate,"head",neckClothing,java.util.Set.of(),false));
        assertFalse(EquipmentFitPolicy.fits(template,"shoes",cap,java.util.Set.of(),false));
        var small=catalog.get(ModInventoryTemplates.id("black_gloves"));
        assertTrue(EquipmentFitPolicy.fits(template,"pocket1",small,java.util.Set.of("jumpsuit"),false));
        var medical=manager.snapshot(com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.TYPE).get(ModInventoryTemplates.id("brutepack"));
        assertEquals(10,medical.stack().orElseThrow().maxCount());
        assertTrue(EquipmentFitPolicy.fitsPocket(template,"pocket1",medical.item().size(),10,10,java.util.Set.of("jumpsuit")));
        assertFalse(EquipmentFitPolicy.fitsPocket(template,"pocket1",medical.item().size(),11,10,java.util.Set.of("jumpsuit")));
        assertFalse(EquipmentFitPolicy.fits(template,"pocket1",cap,java.util.Set.of("jumpsuit"),false)); // Normal size is too large
        assertFalse(EquipmentFitPolicy.fits(template,"pocket1",small,java.util.Set.of(),false));
        assertFalse(EquipmentFitPolicy.fits(template,"id",catalog.get(ModInventoryTemplates.id("blank_id_card")),java.util.Set.of(),false));
        assertTrue(EquipmentFitPolicy.fits(template,"id",catalog.get(ModInventoryTemplates.id("blank_id_card")),java.util.Set.of("jumpsuit"),false));
        var suitStorage=catalog.get(ModInventoryTemplates.id("utility_case"));
        assertFalse(EquipmentFitPolicy.fits(template,"pocket1",suitStorage,java.util.Set.of("jumpsuit"),false));
        assertFalse(EquipmentFitPolicy.fits(template,"suitstorage",suitStorage,java.util.Set.of(),true));
        assertFalse(EquipmentFitPolicy.fits(template,"suitstorage",suitStorage,java.util.Set.of("outerClothing"),false));
        assertFalse(EquipmentFitPolicy.fits(template,"suitstorage",small,java.util.Set.of("outerClothing"),true));
        var coat=catalog.get(ModInventoryTemplates.id("simple_coat"));
        assertFalse(EquipmentFitPolicy.fits(template,"suitstorage",suitStorage,java.util.Set.of("outerClothing"),coat.allowSuitStorage().isPresent()));
        // Suit-storage support remains compositional for future qualifying outer garments.
        var qualifyingOuter = new EquipmentItemData(coat.item(), coat.clothing(),
                java.util.Optional.of(new EquipmentItemData.AllowSuitStorage(true)), coat.tags());
        assertTrue(EquipmentFitPolicy.fits(template,"suitstorage",suitStorage,java.util.Set.of("outerClothing"),qualifyingOuter.allowSuitStorage().isPresent()));
        var backItem=catalog.get(ModInventoryTemplates.id("bag"));
        var petOnly=new EquipmentItemData(backItem.item(),backItem.clothing(),backItem.allowSuitStorage(),java.util.List.of(EquipmentTag.PET_ONLY));
        assertFalse(EquipmentFitPolicy.fits(template,"back",petOnly,java.util.Set.of(),false));
    }
}
