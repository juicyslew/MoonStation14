package com.juicyslew.moonstation14.ms14.stripping.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.equipment.*;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import com.juicyslew.moonstation14.ms14.stripping.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StripSlotAvailabilityTest {
    private static JsonObject data(String path)throws Exception {
        try(var stream=StripSlotAvailabilityTest.class.getResourceAsStream(path)){
            assertNotNull(stream,path);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static InventoryTemplateData human()throws Exception {
        var manager=new PrototypeManager();manager.register(ModInventoryTemplates.TYPE);
        manager.reload(ModInventoryTemplates.TYPE,new LinkedHashMap<>(Map.of(ModInventoryTemplates.HUMAN_ID,
                data("/data/moonstation14/moonstation14/inventory_template/human.json"))));
        return manager.snapshot(ModInventoryTemplates.TYPE).get(ModInventoryTemplates.HUMAN_ID);
    }
    private static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<EquipmentItemData> equipment(boolean qualifying)throws Exception {
        var manager=new PrototypeManager();manager.register(ModEquipmentItems.TYPE);
        var base=data("/data/moonstation14/moonstation14/equipment_item/base_item.json");
        var clothing=data("/data/moonstation14/moonstation14/equipment_item/base_clothing.json");
        var coat=data("/data/moonstation14/moonstation14/equipment_item/simple_coat.json");
        if(qualifying)coat.addProperty("allow_suit_storage",true);
        var values=new LinkedHashMap<ResourceLocation,JsonObject>();
        values.put(ModInventoryTemplates.id("base_item"),base);values.put(ModInventoryTemplates.id("base_clothing"),clothing);
        values.put(ModInventoryTemplates.id("simple_coat"),coat);
        manager.reload(ModEquipmentItems.TYPE,values);return manager.snapshot(ModEquipmentItems.TYPE);
    }
    private static StripViewSnapshot view(Set<String> occupied,boolean includeCoat)throws Exception {
        var slots=new ArrayList<StripViewSnapshot.Slot>();
        for(String id:List.of("pocket1","id","jumpsuit","outerClothing","suitstorage")) {
            boolean isOccupied=occupied.contains(id);
            ItemPresentation item=id.equals("outerClothing")&&isOccupied&&includeCoat
                    ?ItemPresentation.fromStack(new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.SIMPLE_COAT.get())):null;
            slots.add(new StripViewSnapshot.Slot(StripSlotRef.Kind.EQUIPMENT,id,isOccupied,false,item));
        }
        return new StripViewSnapshot(2,3,UUID.randomUUID(),ResourceLocation.withDefaultNamespace("overworld"),12,7,
                "moonstation14:human",slots,StripViewSnapshot.Status.AVAILABLE,true);
    }
    @Test void resolvedHumanDependenciesGateEmptySlotsButPreserveOccupiedRemoval()throws Exception {
        var template=human();var catalog=equipment(false);
        var none=view(Set.of(),false);
        assertFalse(StripSlotAvailability.equipmentEnabled(none,template,catalog,"pocket1"));
        assertFalse(StripSlotAvailability.equipmentEnabled(none,template,catalog,"id"));
        assertFalse(StripSlotAvailability.equipmentEnabled(none,template,catalog,"suitstorage"));
        var pocket=none.slots().stream().filter(s->s.id().equals("pocket1")).findFirst().orElseThrow();
        assertFalse(StripSlotAvailability.actionEnabled(none,pocket,template,catalog));
        var jumpsuit=view(Set.of("jumpsuit"),false);
        assertTrue(StripSlotAvailability.equipmentEnabled(jumpsuit,template,catalog,"pocket1"));
        assertTrue(StripSlotAvailability.equipmentEnabled(jumpsuit,template,catalog,"id"));
        var pocketEnabled=jumpsuit.slots().stream().filter(s->s.id().equals("pocket1")).findFirst().orElseThrow();
        assertTrue(StripSlotAvailability.actionEnabled(jumpsuit,pocketEnabled,template,catalog));
        var coat=view(Set.of("outerClothing"),true);
        assertFalse(StripSlotAvailability.equipmentEnabled(coat,template,catalog,"suitstorage"));
        var occupiedSuit=view(Set.of("suitstorage"),false);
        assertTrue(StripSlotAvailability.equipmentEnabled(occupiedSuit,template,catalog,"suitstorage"));
    }
    @Test void resolvedAllowSuitStorageCompositionEnablesOnlyQualifiedOuterwear()throws Exception {
        var template=human();var qualifying=equipment(true);var coat=view(Set.of("outerClothing"),true);
        assertTrue(StripSlotAvailability.equipmentEnabled(coat,template,qualifying,"suitstorage"));
    }
}
