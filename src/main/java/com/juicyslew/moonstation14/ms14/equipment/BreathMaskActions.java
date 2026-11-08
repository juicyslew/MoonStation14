package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;

/** Exact-provider mask action; changes only the owned stack's replicated component. */
public final class BreathMaskActions {
    private BreathMaskActions() { }

    public static boolean isLowered(LivingEntity body, String providerKey) {
        if (body == null || providerKey == null || !providerKey.startsWith("equipment:")) return false;
        int split = providerKey.indexOf(':', "equipment:".length());
        if (split < 0) return false;
        String slot = providerKey.substring("equipment:".length(), split);
        String token = providerKey.substring(split + 1);
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)) return false;
        var inventory = LiveHands.equipmentSnapshot(body, hands).orElse(null);
        var selected = inventory == null ? null : inventory.slots().stream().filter(value -> value.id().equals(slot)).findFirst().orElse(null);
        var stack = selected == null ? null : selected.stack().orElse(null);
        if (stack == null || !selected.token().filter(token::equals).isPresent()) return false;
        var data = ModEquipmentItems.catalog(body.level()).get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return data != null && data.breathMask().isPresent() && BreathToolItemState.isLowered(stack);
    }

    public static boolean toggleMask(LivingEntity body, String providerKey) {
        if (body == null || providerKey == null || !providerKey.startsWith("equipment:")) return false;
        int split = providerKey.indexOf(':', "equipment:".length());
        if (split < 0) return false;
        String slot = providerKey.substring("equipment:".length(), split);
        String token = providerKey.substring(split + 1);
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)) return false;
        long revision = hands.revision();
        var snapshot = LiveOwnedItemAccess.read(body, new LiveOwnedItemAccess.Binding(
                LiveOwnedItemAccess.Location.EQUIPMENT, slot, revision, token)).orElse(null);
        if (snapshot == null) return false;
        var stack = snapshot.stack();
        var data = ModEquipmentItems.catalog(body.level()).get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (data == null || data.breathMask().isEmpty() || data.supportedActionGrant().isEmpty()
                || !data.supportedActionGrant().orElseThrow().itemActionGrant().activeIfWorn()
                || !data.supportedActionGrant().orElseThrow().actions().contains(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "toggle_mask"))) return false;
        var template = com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.templateForTransfer(body);
        var slotData = template == null ? null : template.slot(slot).orElse(null);
        if (slotData == null || slotData.flags().contains(InventoryTemplateData.SlotFlag.POCKET)
                || !data.breathMask().orElseThrow().allowedSlots().stream().anyMatch(slotData.flags()::contains)) return false;
        stack = BreathToolItemState.toggledCopy(stack);
        // The action was authenticated against this exact equipment binding by BodyActionService.
        // commitComponents revalidates the equipment slot, token, owner, and revision itself;
        // LiveHands.token only accepts hand IDs, not equipment slot IDs.
        var committed = LiveOwnedItemAccess.commitComponents(snapshot, stack, () -> true).isPresent();
        if (!committed) return false;
        com.juicyslew.moonstation14.ms14.lung.InternalsSystem.isWorking(body);
        return true;
    }
}
