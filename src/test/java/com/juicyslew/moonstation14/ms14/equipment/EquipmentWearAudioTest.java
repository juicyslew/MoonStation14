package com.juicyslew.moonstation14.ms14.equipment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentWearAudioTest {
    private static final List<InventoryTemplateData.SlotFlag> HEAD = List.of(InventoryTemplateData.SlotFlag.HEAD);
    private static final EquipmentItemData PROFILE = new EquipmentItemData(new EquipmentItemData.Item("Small"),
            Optional.of(new EquipmentItemData.Clothing(HEAD, EquipmentItemData.AppearanceRegion.HEAD, 0, 0,
                    Optional.of("moonstation14:test_equip"), Optional.of("moonstation14:test_remove"))),
            Optional.empty(), List.of());
    private static ResolvedWornEquipment.Entry gear(String slot, List<InventoryTemplateData.SlotFlag> flags) {
        return new ResolvedWornEquipment.Entry(slot, flags, net.minecraft.world.item.ItemStack.EMPTY, PROFILE);
    }
    private static EquipmentWearAudio.WearEntry worn(String token, String slot, List<InventoryTemplateData.SlotFlag> flags) {
        return new EquipmentWearAudio.WearEntry(slot, token, gear(slot, flags));
    }

    @Test void committedWornTransitionsUseTokenIdentityAndOrderedReplacementCues() {
        var old = worn("old", "head", HEAD);
        assertEquals(List.of(new EquipmentWearAudio.Cue("moonstation14:test_equip", true)),
                EquipmentWearAudio.cues(List.of(), List.of(old)));
        assertEquals(List.of(new EquipmentWearAudio.Cue("moonstation14:test_remove", false)),
                EquipmentWearAudio.cues(List.of(old), List.of()));
        assertEquals(List.of(new EquipmentWearAudio.Cue("moonstation14:test_remove", false),
                        new EquipmentWearAudio.Cue("moonstation14:test_equip", true)),
                EquipmentWearAudio.cues(List.of(old), List.of(worn("new", "head", HEAD))));
        assertTrue(EquipmentWearAudio.cues(List.of(old), List.of(worn("old", "head", HEAD))).isEmpty());
    }

    @Test void nullSoundsAndNonWornSlotsAreSilent() {
        var pocket = worn("x", "pocket", List.of(InventoryTemplateData.SlotFlag.POCKET));
        assertTrue(EquipmentWearAudio.cues(List.of(), List.of(pocket)).isEmpty());
        var silentProfile = new EquipmentItemData(new EquipmentItemData.Item("Small"),
                Optional.of(new EquipmentItemData.Clothing(HEAD, EquipmentItemData.AppearanceRegion.HEAD, 0, 0,
                        Optional.empty(), Optional.empty())), Optional.empty(), List.of());
        var silent = new EquipmentWearAudio.WearEntry("head", "s", new ResolvedWornEquipment.Entry(
                "head", HEAD, net.minecraft.world.item.ItemStack.EMPTY, silentProfile));
        assertTrue(EquipmentWearAudio.cues(List.of(), List.of(silent)).isEmpty());
    }
}
