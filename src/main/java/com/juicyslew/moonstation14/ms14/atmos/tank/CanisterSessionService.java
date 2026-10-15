package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.entities.GasCanisterEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Server-only canister session/control backend. No client packet dispatch is registered in this slice. */
public final class CanisterSessionService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private record Session(UUID id, ServerPlayer player, UUID machine, UUID body, long epoch,
                           net.minecraft.resources.ResourceLocation dimension, long lastSequence) {
        Session sequence(long value) { return new Session(id, player, machine, body, epoch, dimension, value); }
    }
    private CanisterSessionService() { }

    public static Optional<CanisterSessionSnapshot> openForActor(ServerPlayer player, GasCanisterEntity entity,
                                                                    HandActorAuthority.Snapshot authority) {
        if (player == null || entity == null || authority == null || !(player.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || authority.actor() != player
                || !HandActorAuthority.revalidate(player, authority) || !validWorld(entity, player.level())
                || !inRange(authority.body(), entity)
                || !com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.loadedReachableEntity(
                    level, authority.body(), entity, 5.0)) return Optional.empty();
        var state = entity.canisterState(); if (state == null || !state.owner().equals(entity.getUUID())) return Optional.empty();
        SESSIONS.values().removeIf(s -> s.player() == player && s.machine().equals(entity.getUUID()));
        var s = new Session(UUID.randomUUID(), player, entity.getUUID(), authority.body().getUUID(), authority.epoch(),
                level.dimension().location(), -1L);
        SESSIONS.put(s.id(), s);
        return Optional.of(snapshot(s, entity, true));
    }

    /** Invoke from an authenticated server packet handler; rejected/stale requests return current authoritative state. */
    public static Optional<CanisterSessionSnapshot> handle(ServerPlayer player, CanisterControlRequest request) {
        if (request == null || request.sessionId() == null || request.operation() == null || player == null
                || !(player.level() instanceof ServerLevel server) || !server.getServer().isSameThread()) return Optional.empty();
        Session s = SESSIONS.get(request.sessionId());
        if (s == null || s.player() != player) return Optional.empty();
        if (!s.machine().equals(request.machineId()) || !s.body().equals(request.bodyId())
                || s.epoch() != request.authorityEpoch() || !s.dimension().equals(request.dimension())) {
            SESSIONS.remove(request.sessionId()); return Optional.empty();
        }
        if (!(player.level() instanceof ServerLevel level) || !level.dimension().location().equals(s.dimension())
                ) { SESSIONS.remove(s.id()); return Optional.empty(); }
        var entity = level.getEntity(s.machine());
        if (!(entity instanceof GasCanisterEntity canister) || !validWorld(canister, player.level())) return Optional.empty();
        var authority = HandActorAuthority.resolve(player).orElse(null);
        if (authority == null || !authority.body().getUUID().equals(s.body()) || authority.epoch() != s.epoch()
                || !HandActorAuthority.revalidate(player, authority) || !inRange(authority.body(), canister)) {
            SESSIONS.remove(s.id()); return Optional.empty();
        }
        if (request.sequence() <= s.lastSequence()) return Optional.of(snapshot(s, canister, false));
        SESSIONS.put(s.id(), s = s.sequence(request.sequence()));
        var state = canister.canisterState();
        boolean applied = request.operation() == CanisterControlRequest.Operation.QUERY;
        boolean playSlotInsert = false;
        boolean playSlotEject = false;
        boolean playValve = false;
        if (state != null && request.operation() != CanisterControlRequest.Operation.QUERY
                && request.stateRevision() == state.revision() && request.slotRevision() == state.slot().snapshot().revision()) {
            var hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var ledger = LevelItemLocationLedgers.forLevel(level);
            switch (request.operation()) {
                 case SET_VALVE -> {
                     boolean changed = state.valveClosed() != request.valveClosed();
                     applied = state.setValveClosed(request.stateRevision(), request.valveClosed());
                     playValve = applied && changed;
                 }
                case SET_PRESSURE -> applied = state.setReleasePressure(request.stateRevision(), request.pressureKpa());
                case INSERT_ACTIVE -> {
                    String hand = request.handId();
                    boolean observed = hands != null && request.handRevision() == hands.revision()
                            && hand != null && hand.equals(hands.activeHand()) && hand.equals(request.handId())
                            && Objects.equals(request.handToken(), hands.token(hand).orElse(null));
                    if (observed) applied = BodyMachineItemSlotTransfer.insert(authority, hand, request.handToken(), request.handRevision(),
                            state.slot(), request.slotRevision(), request.ledgerRevision(), ledger,
                            () -> validWorld(canister, player.level()) && HandActorAuthority.revalidate(player, authority)) == BodyMachineItemSlotTransfer.Result.SUCCESS;
                    playSlotInsert = applied;
                }
                case EJECT -> { if (hands != null && request.handRevision() == hands.revision()
                        && Objects.equals(request.handId(), hands.activeHand())
                        && Objects.equals(request.handToken(), hands.token(request.handId()).orElse(null)))
                    applied = BodyMachineItemSlotTransfer.ejectToHandsOrWorld(authority, state.slot(),
                        request.handRevision(), request.slotRevision(), request.ledgerRevision(), ledger,
                        () -> validWorld(canister, player.level()) && HandActorAuthority.revalidate(player, authority)) == BodyMachineItemSlotTransfer.Result.SUCCESS;
                    playSlotEject = applied; }
                default -> { }
            }
        }
        if (playSlotInsert || playSlotEject || playValve) {
            var profile = ModGasCanisters.catalog(level).get(state.profile());
            if (profile != null) {
                if (playValve) com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.emit(level, profile.valveSound(),
                        canister.getX(), canister.getY(), canister.getZ(),
                        com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.decibels(profile.valveVolumeDb()));
                else {
                    var transfer = playSlotInsert
                            ? com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.Transfer.INSERT
                            : com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.Transfer.EJECT;
                    for (var event : com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.committedCues(
                            transfer, BodyMachineItemSlotTransfer.Result.SUCCESS, profile.slotInsertSound(), profile.slotEjectSound()))
                        com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.emit(level, event,
                                canister.getX(), canister.getY(), canister.getZ(), 1f);
                }
            }
        }
        return Optional.of(snapshot(s, canister, applied));
    }

    public static void close(ServerPlayer player, UUID sessionId) {
        Session s = SESSIONS.get(sessionId);
        if (s != null && s.player() == player) SESSIONS.remove(sessionId);
    }

    private static boolean validWorld(GasCanisterEntity e, net.minecraft.world.level.Level l) {
        return e.level() == l && l instanceof ServerLevel sl && sl.hasChunkAt(e.blockPosition())
                && sl.getEntity(e.getUUID()) == e && !e.isRemoved();
    }
    private static boolean inRange(net.minecraft.world.entity.LivingEntity body, GasCanisterEntity e) {
        return body.level() == e.level() && body.distanceToSqr(e.position()) <= 64.0;
    }
    private static CanisterSessionSnapshot snapshot(Session s, GasCanisterEntity entity, boolean accepted) {
        var c = entity.canisterState(); var profile = ModGasCanisters.catalog(entity.level()).get(c.profile());
        var gas = c.gas(); var slot = c.slot().snapshot();
        return new CanisterSessionSnapshot(s.id(),s.machine(),s.body(),s.epoch(),s.dimension(),entity.getX(),entity.getY(),entity.getZ(),
                c.revision(),slot.revision(),LevelItemLocationLedgers.forLevel((ServerLevel)entity.level()).snapshot().revision(),
                c.releasePressure(),profile.minReleasePressureKpa(),profile.maxReleasePressureKpa(),c.valveClosed(),
                com.juicyslew.moonstation14.ms14.atmos.tank.GasCompositionCodec.encode(gas.gasMoles()),gas.temperatureKelvin(),
                slot.stack(),accepted);
    }
}


