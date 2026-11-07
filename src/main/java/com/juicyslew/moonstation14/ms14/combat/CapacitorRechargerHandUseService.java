package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import net.minecraft.server.level.ServerPlayer;

/** Normal body USE inserts into an empty charger or takes its actual item with an empty hand. */
public final class CapacitorRechargerHandUseService {
    private CapacitorRechargerHandUseService() { }
    public static BodyBlockUseResult.Outcome use(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission, WeaponCapacitorRechargerBlockEntity machine,
            net.minecraft.world.item.ItemStack held) {
        if (!(admission.authority().body().level() instanceof net.minecraft.server.level.ServerLevel level)
                || !machine.isLoadedValid()) return BodyBlockUseResult.Outcome.DENIED;
        var slot = machine.slot(); if (slot == null) return BodyBlockUseResult.Outcome.DENIED;
        var ledger = LevelItemLocationLedgers.forLevel(level);
        BodyMachineItemSlotTransfer.Result result;
        if (held == null) {
            var profile = ModChargerProfiles.serverCatalog().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_capacitor_recharger"));
            if (profile == null || !profile.ejectOnInteract()) return BodyBlockUseResult.Outcome.DENIED;
            result = BodyMachineItemSlotTransfer.take(admission.authority(), request.handId(), request.expectedRevision(),
                    slot, slot.snapshot().revision(), ledger.snapshot().revision(), ledger, machine::isLoadedValid);
            if (result == BodyMachineItemSlotTransfer.Result.SUCCESS) machine.slotChanged();
            return result == BodyMachineItemSlotTransfer.Result.SUCCESS ? BodyBlockUseResult.Outcome.HAND_CHANGED
                    : result == BodyMachineItemSlotTransfer.Result.RECOVERY_REQUIRED ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
        }
        var token = admission.hands().token(request.handId()).orElse(null);
        result = token == null ? BodyMachineItemSlotTransfer.Result.DENIED
                : BodyMachineItemSlotTransfer.insert(admission.authority(), request.handId(), token,
                    admission.hands().revision(), slot, slot.snapshot().revision(), ledger.snapshot().revision(), ledger,
                    machine::isLoadedValid);
        if (result == BodyMachineItemSlotTransfer.Result.SUCCESS) {
            if (!machine.prepareInsertedCharge()) return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            return BodyBlockUseResult.Outcome.PLACED;
        }
        return result == BodyMachineItemSlotTransfer.Result.RECOVERY_REQUIRED ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
    }
}
