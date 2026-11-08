package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.character.components.DamageOnInteractProtectionComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageModifierData;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContactDamageProtectionTest {
    private static ResolvedWornEquipment.Entry entry(List<InventoryTemplateData.SlotFlag> actual, Map<String,Float> flat) {
        var p = new ContactDamageProtectionData(new DamageModifierData(Map.of(), flat), List.of(InventoryTemplateData.SlotFlag.GLOVES));
        var eq = new EquipmentItemData(new EquipmentItemData.Item("Small"), Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(p));
        return new ResolvedWornEquipment.Entry("slot", actual, net.minecraft.world.item.ItemStack.EMPTY, eq);
    }
    private static Map<String,Float> apply(Map<String,Float> raw, List<ResolvedWornEquipment.Entry> entries, Optional<DamageOnInteractProtectionComponent> actor) { return ContactDamageProtectionSystem.apply(raw, entries, actor); }
    private static DamageOnInteractProtectionComponent actor(Map<String,Float> flat) { return new DamageOnInteractProtectionComponent(new ContactDamageProtectionData(new DamageModifierData(Map.of(),flat), List.of(InventoryTemplateData.SlotFlag.GLOVES))); }
    @Test void actualSlotSelectionAndNoStacking() {
        var raw = Map.of("heat",4f,"caustic",4f);
        assertEquals(raw, apply(raw,List.of(),Optional.empty()));
        assertEquals(Map.of("caustic",4f), apply(raw,List.of(entry(List.of(InventoryTemplateData.SlotFlag.GLOVES),Map.of("heat",5f))),Optional.empty()));
        assertEquals(Map.of(), apply(raw,List.of(entry(List.of(InventoryTemplateData.SlotFlag.GLOVES),Map.of("heat",10f,"caustic",5f))),Optional.empty()));
        assertEquals(raw,apply(raw,List.of(entry(List.of(InventoryTemplateData.SlotFlag.POCKET),Map.of("heat",10f))),Optional.empty()));
        assertEquals(Map.of("heat",2f),apply(Map.of("heat",4f),List.of(entry(List.of(InventoryTemplateData.SlotFlag.GLOVES),Map.of("heat",2f)),entry(List.of(InventoryTemplateData.SlotFlag.GLOVES),Map.of("heat",4f))),Optional.empty()));
        assertEquals(Map.of("heat",1f),apply(Map.of("heat",4f),List.of(entry(List.of(InventoryTemplateData.SlotFlag.POCKET),Map.of("heat",4f))),Optional.of(actor(Map.of("heat",3f)))));
        assertEquals(raw,ContactDamageProtectionSystem.apply(raw,List.of(entry(List.of(InventoryTemplateData.SlotFlag.GLOVES),Map.of("heat",10f,"caustic",10f))),Optional.empty(),true));
    }
}
