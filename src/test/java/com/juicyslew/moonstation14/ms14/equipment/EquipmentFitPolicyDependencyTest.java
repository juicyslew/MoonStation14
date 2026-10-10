package com.juicyslew.moonstation14.ms14.equipment;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentFitPolicyDependencyTest {
    private static InventoryTemplateData.Slot suitSlot(boolean allowed) {
        return new InventoryTemplateData.Slot("suitstorage", "Suit storage",
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("air"), "Default", 0, 0,
                List.of(InventoryTemplateData.SlotFlag.SUITSTORAGE), List.of("outerClothing"), List.of(), allowed);
    }

    @Test void suitStorageRequiresOccupiedOuterAndExplicitAllowPolicy() {
        var slot=suitSlot(true);
        assertFalse(EquipmentFitPolicy.dependenciesSatisfied(slot, Set.of(), true));
        assertFalse(EquipmentFitPolicy.dependenciesSatisfied(slot, Set.of("outerClothing"), false));
        assertTrue(EquipmentFitPolicy.dependenciesSatisfied(slot, Set.of("outerClothing"), true));
        assertFalse(EquipmentFitPolicy.dependenciesSatisfied(suitSlot(false), Set.of("outerClothing"), true));
    }

    @Test void resolvedSimpleCoatDoesNotPermitSuitStorage() throws Exception {
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(ModEquipmentItems.TYPE);
        var items=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
        for(String name:List.of("base_item","base_clothing","simple_coat")) {
            try(var stream=getClass().getResourceAsStream("/data/moonstation14/moonstation14/equipment_item/"+name+".json")) {
                assertNotNull(stream,name);
                items.put(ModInventoryTemplates.id(name),com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        manager.reload(ModEquipmentItems.TYPE,items);
        var coat=manager.snapshot(ModEquipmentItems.TYPE).get(ModInventoryTemplates.id("simple_coat"));
        assertTrue(coat.allowSuitStorage().isEmpty());
        assertFalse(EquipmentFitPolicy.dependenciesSatisfied(suitSlot(true),Set.of("outerClothing"),coat.allowSuitStorage().isPresent()));
        var qualifyingOuter=suitSlot(true);
        assertTrue(EquipmentFitPolicy.dependenciesSatisfied(qualifyingOuter,Set.of("outerClothing"),true));
    }

    @Test void nonSuitDependenciesDoNotRequireOuterSuitPermission() {
        var slot=new InventoryTemplateData.Slot("id", "ID", net.minecraft.resources.ResourceLocation.withDefaultNamespace("air"),
                "Default", 0, 0, List.of(InventoryTemplateData.SlotFlag.IDCARD), List.of("jumpsuit"), List.of(), false);
        assertTrue(EquipmentFitPolicy.dependenciesSatisfied(slot, Set.of("jumpsuit"), false));
        assertFalse(EquipmentFitPolicy.dependenciesSatisfied(slot, Set.of(), false));
    }
}
