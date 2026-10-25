package com.juicyslew.moonstation14.ms14.equipment;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class MeleeInjectionProtectionPolicyTest {
    private static ResolvedWornEquipment.Entry entry(String slot, EquipmentTag... tags) {
        var profile = new EquipmentItemData(new EquipmentItemData.Item("Small"), Optional.empty(), Optional.empty(),
                List.of(tags), Optional.empty());
        return new ResolvedWornEquipment.Entry(slot, List.of(), new ItemStack(Items.LEATHER_CHESTPLATE), profile);
    }

    @Test void onlyTaggedOuterClothingBlocksAndPiercingBypasses() {
        var hardsuit = List.of(entry(EquipmentSlots.OUTER_CLOTHING, EquipmentTag.HARDSUIT));
        assertTrue(MeleeInjectionProtectionPolicy.protects(hardsuit, false));
        assertFalse(MeleeInjectionProtectionPolicy.protects(List.of(entry(EquipmentSlots.OUTER_CLOTHING)), false));
        assertFalse(MeleeInjectionProtectionPolicy.protects(List.of(entry("pocket1", EquipmentTag.HARDSUIT)), false));
        assertFalse(MeleeInjectionProtectionPolicy.protects(List.of(entry("held", EquipmentTag.HARDSUIT)), false));
        assertFalse(MeleeInjectionProtectionPolicy.protects(List.of(entry(EquipmentSlots.GLOVES, EquipmentTag.HARDSUIT)), false));
        assertFalse(MeleeInjectionProtectionPolicy.protects(hardsuit, true));
    }

    @Test void hardsuitTagCodecUsesExactSourceSpellingAndStillRejectsUnknownTags() {
        assertEquals(EquipmentTag.HARDSUIT, EquipmentTag.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.mojang.serialization.JsonOps.INSTANCE.createString("Hardsuit")).result().orElseThrow());
        assertEquals("Hardsuit", EquipmentTag.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                EquipmentTag.HARDSUIT).result().orElseThrow().getAsString());
        assertTrue(EquipmentTag.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.mojang.serialization.JsonOps.INSTANCE.createString("hardsuit")).error().isPresent());
    }
}
