package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Body-admitted interactions with a collector's privately owned gas tank. */
public final class RadiationCollectorInteractionService {
    private RadiationCollectorInteractionService() { }

    public static BodyBlockUseResult.Outcome use(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission, RadiationCollectorBlockEntity collector) {
        if (actor == null || request == null || admission == null || collector == null
                || request.intent() != BodyBlockUseRequest.Intent.USE) return BodyBlockUseResult.Outcome.DENIED;
        HandActorAuthority.Snapshot authority = admission.authority();
        if (!(authority.body().level() instanceof ServerLevel level) || collector.getLevel() != level
                || collector.isRemoved() || !collector.getBlockPos().equals(admission.hit().pos())
                || level.getBlockEntity(admission.hit().pos()) != collector) return BodyBlockUseResult.Outcome.DENIED;
        var hands = admission.hands();
        if (!HandActorAuthority.revalidate(actor, authority) || !hands.compatible(authority.body())
                || hands.revision() != request.expectedRevision() || !request.handId().equals(hands.activeHand())
                || request.expectedToken() == null || hands.token(request.handId()).filter(request.expectedToken()::equals).isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        var slot = collector.tankSlot();
        var profile = ModRadiationCollectors.catalog(level).get(collector.profileId());
        if (profile == null) return BodyBlockUseResult.Outcome.DENIED;
        var snapshot = slot.snapshot();
        var ledger = LevelItemLocationLedgers.forLevel(level);
        long ledgerRevision = ledger.snapshot().revision();
        ItemStack held = hands.stackCopy(request.handId()).orElse(ItemStack.EMPTY);
        if (held.isEmpty() || GasTankItem.contents(held, level).isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        var valid = (java.util.function.BooleanSupplier) () -> !collector.isRemoved()
                && collector.getLevel() == level && level.getBlockEntity(admission.hit().pos()) == collector
                && collector.identity() != null;
        BodyMachineItemSlotTransfer.Result result;
        if (snapshot.token() == null) {
            result = BodyMachineItemSlotTransfer.insert(authority, request.handId(), request.expectedToken(),
                    request.expectedRevision(), slot, snapshot.revision(), ledgerRevision, ledger, valid);
        } else {
            result = BodyMachineItemSlotTransfer.swapActiveHand(authority, request.expectedToken(),
                    request.expectedRevision(), slot, snapshot.revision(), ledgerRevision, ledger, valid);
        }
        return switch (result) {
            case SUCCESS -> {
                var transfer = snapshot.token() == null
                        ? com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.Transfer.INSERT
                        : com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.Transfer.SWAP;
                for (var event : com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.committedCues(
                        transfer, BodyMachineItemSlotTransfer.Result.SUCCESS, profile.slotInsertSound(), profile.slotEjectSound()))
                    com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.emit(level, event,
                            collector.getBlockPos().getX() + .5, collector.getBlockPos().getY() + .5,
                            collector.getBlockPos().getZ() + .5, 1f);
                yield BodyBlockUseResult.Outcome.HAND_CHANGED;
            }
            case RECOVERY_REQUIRED -> BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            case DENIED -> BodyBlockUseResult.Outcome.DENIED;
        };
    }

    /** Future context-verb contract; callers must pass the UUID and revisions from their admitted snapshot. */
    public static BodyBlockUseResult.Outcome eject(BodyHandRequestService.BlockUseAdmission admission,
            RadiationCollectorBlockEntity collector, java.util.UUID expectedPlacementId,
            long expectedSlotRevision, long expectedLedgerRevision) {
        if (admission == null || collector == null || expectedPlacementId == null) return BodyBlockUseResult.Outcome.DENIED;
        HandActorAuthority.Snapshot authority = admission.authority();
        if (!(authority.body().level() instanceof ServerLevel level) || collector.getLevel() != level
                || collector.isRemoved() || !collector.identity().equals(expectedPlacementId)
                || !collector.getBlockPos().equals(admission.hit().pos())
                || level.getBlockEntity(admission.hit().pos()) != collector
                || !HandActorAuthority.revalidate(authority.actor(), authority)
                || authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                        != admission.hands() || !admission.hands().compatible(authority.body()))
            return BodyBlockUseResult.Outcome.DENIED;
        var slot = collector.tankSlot();
        if (slot.snapshot().revision() != expectedSlotRevision || slot.snapshot().token() == null)
            return BodyBlockUseResult.Outcome.DENIED;
        var ledger = LevelItemLocationLedgers.forLevel(level);
        if (ledger.snapshot().revision() != expectedLedgerRevision) return BodyBlockUseResult.Outcome.DENIED;
        var valid = (java.util.function.BooleanSupplier) () -> !collector.isRemoved()
                && collector.getLevel() == level && collector.identity().equals(expectedPlacementId)
                && level.getBlockEntity(admission.hit().pos()) == collector;
        var result = BodyMachineItemSlotTransfer.ejectToHandsOrWorld(authority, slot,
                admission.hands().revision(), expectedSlotRevision, expectedLedgerRevision, ledger, valid);
        return switch (result) {
            case SUCCESS -> {
                var profile = ModRadiationCollectors.catalog(level).get(collector.profileId());
                if (profile != null) for (var event : com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.committedCues(
                        com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.Transfer.EJECT,
                        BodyMachineItemSlotTransfer.Result.SUCCESS, profile.slotInsertSound(), profile.slotEjectSound()))
                    com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.emit(level, event,
                            collector.getBlockPos().getX() + .5, collector.getBlockPos().getY() + .5,
                            collector.getBlockPos().getZ() + .5, 1f);
                yield BodyBlockUseResult.Outcome.HAND_CHANGED;
            }
            case RECOVERY_REQUIRED -> BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            case DENIED -> BodyBlockUseResult.Outcome.DENIED;
        };
    }
}

