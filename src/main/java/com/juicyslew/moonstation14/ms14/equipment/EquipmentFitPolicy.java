package com.juicyslew.moonstation14.ms14.equipment;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Slot admission from the body's resolved template and the candidate's resolved composition. */
public final class EquipmentFitPolicy {
    private EquipmentFitPolicy() { }

    public static boolean fits(Level level, InventoryTemplateData template, String slotId, ItemStack stack,
                               java.util.Set<String> occupiedSlots) {
        if (level == null || template == null || slotId == null || stack == null || stack.isEmpty()) return false;
        var slot = template.slot(slotId).orElse(null);
        if (slot == null || slot.flags().contains(InventoryTemplateData.SlotFlag.PREVENTEQUIP)) return false;
        for (String dependency : slot.dependsOn()) if (!occupiedSlots.contains(dependency)) return false;
        var data = ModEquipmentItems.catalog(level).get(stack.getItem().builtInRegistryHolder().key().location());
        boolean pocket = slot.flags().contains(InventoryTemplateData.SlotFlag.POCKET);
        if (data == null) {
            var medical = com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalogForSide(level.isClientSide())
                    .get(stack.getItem().builtInRegistryHolder().key().location());
            if (medical != null && pocket)
                return fitsPocket(template, slotId, medical.item().size(), stack.getCount(),
                        medical.stack().map(com.juicyslew.moonstation14.ms14.medical.MedicalItemData.Stack::maxCount)
                                .orElse(stack.getMaxStackSize()), occupiedSlots);
            if (pocket) {
                var itemId = stack.getItem().builtInRegistryHolder().key().location();
                var profiles = level.isClientSide()
                        ? com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints.clientCatalog()
                        : com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints.serverCatalog();
                var profile = com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints.get(profiles, itemId);
                return fitsPocket(template, slotId, profile, stack.getCount(), stack.getMaxStackSize(), occupiedSlots);
            }
        }
        if (data != null && pocket)
            return fitsPocket(template, slotId, data.item().size(), stack.getCount(),
                    stack.getMaxStackSize(), occupiedSlots);
        if (data != null && data.clothing().isPresent() && stack.getCount() != 1
                && !pocket) return false;
        return data != null && fits(template, slotId, data, occupiedSlots, false);
    }

    /** Clothing-only generic use dispatch; non-clothing use behavior remains unchanged. */
    public static boolean quickEquip(Level level, ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return false;
        var data = ModEquipmentItems.catalog(level).get(stack.getItem().builtInRegistryHolder().key().location());
        return data != null && data.clothing().map(EquipmentItemData.Clothing::quickEquip).orElse(false);
    }

    /** First declared eligible slot, retaining template order and excluding duplicate candidate slots. */
    public static java.util.Optional<String> firstQuickEquipSlot(Level level, InventoryTemplateData template,
            ItemStack stack, java.util.Set<String> occupied, java.util.Set<String> attempted) {
        return quickEquipSlots(level, template, stack, occupied, attempted).stream().findFirst();
    }

    /** Eligible slots in preferred-flag order, then the body's unchanged resolved template order. */
    public static java.util.List<String> quickEquipSlots(Level level, InventoryTemplateData template,
            ItemStack stack, java.util.Set<String> occupied, java.util.Set<String> attempted) {
        if (!quickEquip(level, stack) || template == null) return java.util.List.of();
        var data = ModEquipmentItems.catalog(level).get(stack.getItem().builtInRegistryHolder().key().location());
        var preferred = data.clothing().orElseThrow().quickEquipPreferredSlotFlags();
        return template.slots().stream().map(InventoryTemplateData.Slot::id)
                .filter(id -> attempted == null || !attempted.contains(id))
                .filter(id -> fits(level, template, id, stack, occupied))
                .sorted(java.util.Comparator.comparingInt(id -> {
                    var slot = template.slot(id).orElseThrow();
                    for (int i = 0; i < preferred.size(); i++) if (slot.flags().contains(preferred.get(i))) return i;
                    return preferred.size();
                })).toList();
    }

    public static boolean fits(InventoryTemplateData template, String slotId, EquipmentItemData data,
                               java.util.Set<String> occupiedSlots, boolean outerAllowsSuitStorage) {
        if (template == null || slotId == null || data == null || occupiedSlots == null) return false;
        var slot = template.slot(slotId).orElse(null);
        if (slot == null || slot.flags().contains(InventoryTemplateData.SlotFlag.PREVENTEQUIP)) return false;
        if (!dependenciesSatisfied(slot, occupiedSlots, outerAllowsSuitStorage)) return false;
        if (slot.flags().contains(InventoryTemplateData.SlotFlag.POCKET)) return fitsPocket(template, slotId, data.item().size(), 1, 1, occupiedSlots);
        var clothing = data.clothing().orElse(null);
        if (clothing == null) return false;
        if (slot.blacklist().stream().anyMatch(data.tags()::contains)) return false;
        if (java.util.Collections.disjoint(clothing.slotFlags(), slot.flags())) return false;
        return true;
    }

    /** Shared resolved-slot dependency gate used by fitting and the equipment HUD. */
    public static boolean dependenciesSatisfied(InventoryTemplateData.Slot slot,
                                                java.util.Set<String> occupiedSlots,
                                                boolean outerAllowsSuitStorage) {
        return slot != null && occupiedSlots != null
                && slot.dependsOn().stream().allMatch(occupiedSlots::contains)
                && (!slot.flags().contains(InventoryTemplateData.SlotFlag.SUITSTORAGE)
                    || (outerAllowsSuitStorage && slot.allowSuitStorage()));
    }

    public static boolean fitsPocket(InventoryTemplateData template, String slotId,
                                     com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile profile,
                                     int count, int maxCount, java.util.Set<String> occupiedSlots) {
        String size = profile == null ? null : profile.itemSize().map(
                com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile.ItemSize::name).orElse(null);
        return size != null && fitsPocket(template, slotId, size, count, maxCount, occupiedSlots);
    }

    private static boolean fitsPocket(InventoryTemplateData template, String slotId, String size,
                                      java.util.Set<String> occupiedSlots) {
        return fitsPocket(template, slotId, size, 1, 1, occupiedSlots);
    }

    public static boolean fitsPocket(InventoryTemplateData template, String slotId, String size, int count,
                                     int maxCount, java.util.Set<String> occupiedSlots) {
        var slot = template == null ? null : template.slot(slotId).orElse(null);
        return slot != null && !slot.flags().contains(InventoryTemplateData.SlotFlag.PREVENTEQUIP)
                && slot.flags().contains(InventoryTemplateData.SlotFlag.POCKET)
                && slot.dependsOn().stream().allMatch(occupiedSlots::contains)
                && count > 0 && maxCount >= count
                && ("Tiny".equals(size) || "Small".equals(size));
    }
}

