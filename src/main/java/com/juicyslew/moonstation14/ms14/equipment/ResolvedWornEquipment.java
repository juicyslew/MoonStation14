package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;

/** Shared resolved view of occupied, declared equipment slots and their composed item profiles. */
public final class ResolvedWornEquipment {
    private ResolvedWornEquipment() { }

    public static List<Entry> entries(Entity body) {
        var layout = HandCapability.resolveEquipmentTemplate(body);
        var snapshot = LiveHands.equipmentSnapshot(body);
        if (layout.isEmpty() || snapshot.isEmpty()) return List.of();
        return entries(snapshot.orElseThrow(), layout.orElseThrow(), ModEquipmentItems.catalog(body.level()));
    }

    static List<Entry> entries(LiveHands.EquipmentSnapshot snapshot, InventoryTemplateData layout,
                               PrototypeCatalog<EquipmentItemData> catalog) {
        var result = new java.util.ArrayList<Entry>();
        for (var slot : snapshot.slots()) {
            var declared = layout.slot(slot.id()).orElse(null);
            Optional<ItemStack> stack = slot.stack();
            if (declared == null || stack.isEmpty() || stack.orElseThrow().isEmpty()) continue;
            var profile = catalog.get(BuiltInRegistries.ITEM.getKey(stack.orElseThrow().getItem()));
            if (profile != null) result.add(new Entry(slot.id(), declared.flags(), stack.orElseThrow(), profile));
        }
        return List.copyOf(result);
    }

    public record Entry(String slotId, List<InventoryTemplateData.SlotFlag> slotFlags,
                        ItemStack stack, EquipmentItemData profile) {
        public Entry {
            slotFlags = List.copyOf(slotFlags);
            stack = stack.copy();
        }
        @Override public ItemStack stack() { return stack.copy(); }
    }
}
