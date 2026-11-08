package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Timed adapter for authenticated body-owned equipment changes. */
final class EquipmentTimedInteraction {
    private static final String KIND = "body_equipment";
    private EquipmentTimedInteraction() { }

    static int delay(ItemStack stack, boolean equipping) {
        if (stack == null || stack.isEmpty()) return 0;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var data = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.serverCatalog().get(id);
        return delay(data, equipping);
    }

    static int delay(EquipmentItemData data, boolean equipping) {
        return data == null ? 0 : data.clothing().map(c -> equipping ? c.equipDelay() : c.unequipDelay()).orElse(0);
    }

    static BodyHandActionResult.Reason start(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, String hand, String slot, String token, long revision, long sequence,
            boolean equipping, int ticks) {
        return start(actor, authority, hands, hand, slot, token, revision, sequence, equipping, ticks, false);
    }

    static BodyHandActionResult.Reason startQuick(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, String hand, String token, long revision, long sequence, int ticks) {
        return start(actor, authority, hands, hand, null, token, revision, sequence, true, ticks, true);
    }

    private static BodyHandActionResult.Reason start(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, String hand, String slot, String token, long revision, long sequence,
            boolean equipping, int ticks, boolean quick) {
        if (ticks <= 0) return BodyHandActionResult.Reason.DENIED;
        var body = authority.body();
        var capturedEquipment = LiveHands.equipmentSnapshot(body).orElse(null);
        if (capturedEquipment == null) return BodyHandActionResult.Reason.DENIED;
        var sourceItem = equipping ? hands.stackCopy(hand).orElse(null)
                : capturedEquipment.slots().stream().filter(s -> s.id().equals(slot))
                    .findFirst().flatMap(LiveHands.EquipmentSlot::stack).orElse(null);
        if (sourceItem == null || !(quick ? quickFits(actor, body, hands, capturedEquipment, hand, token, revision)
                : fits(actor, body, hands, capturedEquipment, hand, slot, token, revision, equipping)))
            return BodyHandActionResult.Reason.DENIED;
        var key = (quick ? "quick" : slot) + ":" + token;
        var start = TimedInteractionSystem.start(body, actor, sequence, KIND, key, ticks, false, true,
                () -> valid(actor, authority, hands, capturedEquipment, hand, slot, token, revision, equipping, quick),
                () -> {
                    if (!valid(actor, authority, hands, capturedEquipment, hand, slot, token, revision, equipping, quick))
                        return TimedInteractionSystem.Completion.DENIED;
                    var outcome = equipping ? quick ? BodyEquipmentTransfer.quickEquip(actor, hand, token, revision)
                            : BodyEquipmentTransfer.equip(actor, hand, slot, token, revision)
                            : BodyEquipmentTransfer.unequip(actor, slot, hand, token, revision);
                    if (outcome == BodyEquipmentTransfer.Result.SUCCESS) {
                        BodyHandStateService.requireFreshQuery(actor);
                        return TimedInteractionSystem.Completion.STOP;
                    }
                    return outcome == BodyEquipmentTransfer.Result.RECOVERY_REQUIRED
                            ? TimedInteractionSystem.Completion.RECOVERY_REQUIRED : TimedInteractionSystem.Completion.DENIED;
                });
        return start == TimedInteractionEngine.StartResult.STARTED || start == TimedInteractionEngine.StartResult.DUPLICATE_CANCELLED
                ? BodyHandActionResult.Reason.OK : BodyHandActionResult.Reason.DENIED;
    }

    private static boolean valid(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands captured,
            LiveHands.EquipmentSnapshot equipment, String hand, String slot, String token, long revision, boolean equipping,
            boolean quick) {
        var body = authority.body();
        LiveHands now = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (!HandActorAuthority.revalidate(actor, authority) || now == null || !now.compatible(body)
                || now != captured && !now.continuesAcrossPassiveTankDebit(body, captured)
                || now.revision() != revision) return false;
        var current = LiveHands.equipmentSnapshot(body).orElse(null);
        if (current == null || !current.persisted() || !equipment.persisted()
                || !equipment.slots().stream().map(LiveHands.EquipmentSlot::id).toList()
                    .equals(current.slots().stream().map(LiveHands.EquipmentSlot::id).toList())
                || !sameSlotTokens(equipment, current)) return false;
        if (quick) return now.token(hand).filter(token::equals).isPresent()
                && now.stackCopy(hand).isPresent() && quickFits(actor, body, now, current, hand, token, revision);
        var slotNow = current.slots().stream().filter(s -> s.id().equals(slot)).findFirst().orElse(null);
        return slotNow != null && fits(actor, body, now, current, hand, slot, token, revision, equipping) && (equipping
                ? slotNow.stack().isEmpty() && slotNow.token().isEmpty() && now.token(hand).filter(token::equals).isPresent()
                : slotNow.token().filter(token::equals).isPresent() && now.stackCopy(hand).isEmpty());
    }

    private static boolean sameSlotTokens(LiveHands.EquipmentSnapshot before, LiveHands.EquipmentSnapshot now) {
        for (var slot : before.slots()) {
            var current = now.slots().stream().filter(value -> value.id().equals(slot.id())).findFirst().orElse(null);
            if (current == null || !slot.token().equals(current.token())) return false;
        }
        return true;
    }

    private static boolean quickFits(ServerPlayer actor, net.minecraft.world.entity.LivingEntity body, LiveHands hands,
            LiveHands.EquipmentSnapshot equipment, String hand, String token, long revision) {
        var candidate = hands.stackCopy(hand).orElse(null);
        var template = BodyEquipmentTransfer.templateForTransfer(body);
        if (candidate == null || template == null || hands.revision() != revision
                || hands.token(hand).filter(token::equals).isEmpty()
                || !com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.quickEquip(actor.level(), candidate)) return false;
        var catalog = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.catalog(body.level());
        var occupied = equipment.slots().stream().filter(s -> s.stack().isPresent())
                .map(LiveHands.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
        for (String slot : com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.quickEquipSlots(
                actor.level(), template, candidate, occupied, null)) {
            var target = equipment.slots().stream().filter(s -> s.id().equals(slot)).findFirst().orElse(null);
            if (target == null) continue;
            var fitOccupied = new java.util.HashSet<>(occupied);
            if (target.stack().isPresent()) {
                var old = target.stack().orElseThrow();
                var oldData = catalog.get(BuiltInRegistries.ITEM.getKey(old.getItem()));
                if (oldData != null && oldData.clothing().isPresent()
                        && !oldData.clothing().orElseThrow().quickEquip()) continue;
                fitOccupied.remove(slot);
            }
            if (BodyEquipmentTransfer.fitsForTransfer((ServerLevel) body.level(), template, equipment, slot, candidate, fitOccupied))
                return true;
        }
        return false;
    }

    private static boolean fits(ServerPlayer actor, net.minecraft.world.entity.LivingEntity body, LiveHands hands,
            LiveHands.EquipmentSnapshot equipment, String hand, String slot, String token, long revision, boolean equipping) {
        if (!equipping) return true;
        var template = BodyEquipmentTransfer.templateForTransfer(body);
        var candidate = hands.stackCopy(hand).orElse(null);
        var occupied = equipment.slots().stream().filter(s -> s.stack().isPresent()).map(LiveHands.EquipmentSlot::id)
                .collect(java.util.stream.Collectors.toSet());
        return template != null && candidate != null && hands.revision() == revision
                && hands.token(hand).filter(token::equals).isPresent()
                && BodyEquipmentTransfer.fitsForTransfer((ServerLevel) body.level(), template,
                    equipment, slot, candidate, occupied);
    }
}
