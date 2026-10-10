package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.*;

/** Independent subscriptions. The map stores subscriptions/correlation only, never machine contents. */
public final class ChemMasterSessionService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> FRAMES = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private record Session(UUID id, ServerPlayer player, UUID machine, ResourceLocation dimension,
                           net.minecraft.core.BlockPos pos, UUID body, long epoch) { }
    private ChemMasterSessionService() { }

    public static boolean opensWorkspace(com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent intent,
            boolean emptyHand, boolean sneaking) {
        return emptyHand && !sneaking && (intent == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE
                || intent == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
    }

    public static boolean open(ServerPlayer player, ChemMasterBlockEntity machine) {
        var authority = BodyActionAuthority.resolve(player).orElse(null);
        if (authority == null || !BodyActionAuthority.eligibleForComplexInteraction(player, authority)
                || !validMachine(authority.body(), machine, true)) return false;
        for (Session old : List.copyOf(SESSIONS.values())) if (old.player() == player
                && old.machine().equals(machine.placementIdentity())
                && (!old.body().equals(authority.body().getUUID()) || old.epoch() != authority.identity().epoch()))
            invalidate(old, "body authority changed");
        Session existing = SESSIONS.values().stream().filter(s -> s.player() == player
                && s.machine().equals(machine.placementIdentity()) && s.body().equals(authority.body().getUUID())
                && s.epoch() == authority.identity().epoch()).findFirst().orElse(null);
        if (existing != null) { send(existing, machine, -1, Optional.empty(), true, Optional.empty()); return true; }
        var s = new Session(UUID.randomUUID(), player, machine.placementIdentity(),
                ((ServerLevel) machine.getLevel()).dimension().location(), machine.getBlockPos(),
                authority.body().getUUID(), authority.identity().epoch());
        SESSIONS.put(s.id(), s);
        send(s, machine, -1, Optional.empty(), true, Optional.empty());
        return true;
    }

    public static void handle(ServerPlayer player, ChemMasterControlRequest request) {
        Session session = SESSIONS.get(request.sessionId());
        if (session == null || session.player() != player || !session.machine().equals(request.machineId())
                || !session.dimension().equals(request.dimension()) || !session.pos().equals(request.position())) return;
        if (request.operation() == ChemMasterControlRequest.Operation.CLOSE) {
            SESSIONS.remove(session.id());
            LAST_REQUEST.remove(session.id());
            closeFrame(session, "dismissed");
            FRAMES.remove(session.id());
            return;
        }
        var authority = BodyActionAuthority.resolve(player).orElse(null);
        var machine = loadedMachine(player, session);
        if (!validSession(session, authority, machine,
                request.operation() == ChemMasterControlRequest.Operation.EJECT)) { invalidate(session, "interaction invalid"); return; }
        long last = LAST_REQUEST.getOrDefault(session.id(), -1L);
        if (request.requestId() <= last) return;
        LAST_REQUEST.put(session.id(), request.requestId());
        Optional<String> rejection = Optional.empty();
        boolean ejected = false;
        boolean powered = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.hasFullFreshChemMasterPower(
                (ServerLevel) machine.getLevel(), machine, machine.placementIdentity());
        if (!powered) {
            invalidate(session, "power lost"); return;
        }
        boolean changed = false;
        if (request.operation() != ChemMasterControlRequest.Operation.QUERY
                && (request.expectedStateRevision() != machine.stateRevision()
                || request.expectedSlotRevision() != machine.inputSlot().snapshot().revision())) {
            rejection = Optional.of("stale state");
        } else if (request.operation() == ChemMasterControlRequest.Operation.SET_MODE
                || request.operation() == ChemMasterControlRequest.Operation.SET_SORT) {
            var mode = request.operation() == ChemMasterControlRequest.Operation.SET_MODE ? request.mode() : machine.mode();
            var sort = request.operation() == ChemMasterControlRequest.Operation.SET_SORT ? request.sort() : machine.sort();
            changed = machine.configure(request.expectedStateRevision(), mode, sort);
            if (!changed) rejection = Optional.of("stale state");
        } else if (request.operation() == ChemMasterControlRequest.Operation.EJECT) {
            var handAuthority = HandActorAuthority.resolve(player).orElse(null);
            var body = handAuthority == null ? null : handAuthority.body();
            var hands = body == null ? null : body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var slot = machine.inputSlot();
            var ledger = LevelItemLocationLedgers.forLevel((ServerLevel) machine.getLevel());
            var result = handAuthority == null || hands == null || slot == null ? BodyMachineItemSlotTransfer.Result.DENIED
                    : BodyMachineItemSlotTransfer.ejectToHandsOrWorld(handAuthority, slot, hands.revision(),
                            slot.snapshot().revision(), ledger.snapshot().revision(), ledger,
                            () -> machine.isLoadedValid() && machine.placementIdentity().equals(session.machine()));
            if (result == BodyMachineItemSlotTransfer.Result.SUCCESS) {
                machine.slotChanged(); ejected = true;
            }
            else rejection = Optional.of("eject rejected");
        } else if (request.operation() == ChemMasterControlRequest.Operation.INPUT_REAGENT
                || request.operation() == ChemMasterControlRequest.Operation.BUFFER_REAGENT) {
            if (!process(machine, request)) {
                rejection = Optional.of("transfer rejected");
            } else changed = true;
        } else if (request.operation() != ChemMasterControlRequest.Operation.QUERY) {
            rejection = Optional.of("unsupported action");
        }
        if (rejection.isEmpty() && request.operation() != ChemMasterControlRequest.Operation.QUERY
                && request.operation() != ChemMasterControlRequest.Operation.CLOSE) {
            if (ejected) ChemMasterAudio.playEject((ServerLevel) machine.getLevel(), machine.getBlockPos(), machine.prototype());
            else ChemMasterAudio.playClick((ServerLevel) machine.getLevel(), machine.getBlockPos(), machine.prototype());
        }
        for (Session viewer : List.copyOf(SESSIONS.values())) if (viewer.machine().equals(session.machine()))
            send(viewer, machine, viewer.id().equals(session.id()) ? request.requestId() : -1,
                    viewer.id().equals(session.id()) ? rejection : Optional.empty(), true, Optional.empty());
    }

    /** Trusted in-process core used by the authenticated handler and same-package native fixtures.
     * This deliberately does not establish or bypass session/actor authorization. */
    static boolean process(ChemMasterBlockEntity be, ChemMasterControlRequest req) {
        var slot = be.inputSlot().snapshot();
        var token = slot.token();
        ItemStack before = slot.stack();
        boolean bufferOnlyDiscard = req.operation() == ChemMasterControlRequest.Operation.BUFFER_REAGENT
                && be.mode() == ChemMasterControlRequest.Mode.DISCARD;
        if (!before.isEmpty() && !(before.getItem() instanceof IReagentTrait)) return false;
        if (before.isEmpty() && !bufferOnlyDiscard) return false;
        IReagentTrait trait = before.isEmpty() ? null : (IReagentTrait) before.getItem();
        var stored = before.get(ModDataComponents.REAGENT.get());
        ReagentAttachment beaker = stored == null ? new ReagentAttachment() : stored.toAttachment();
        ChemMasterBuffer buffer = new ChemMasterBuffer(be.buffer().snapshot());
        var reagent = req.reagent().orElseThrow();
        var amount = amount(req.amountCents());
        if (amount == null) return false;
        if (before.isEmpty()) {
            if (!ChemMasterProcessingPolicy.discardBufferWithoutInput(false, req.operation(), be.mode(), buffer, reagent, amount)) return false;
        } else if (req.operation() == ChemMasterControlRequest.Operation.INPUT_REAGENT) {
            if (be.mode() == ChemMasterControlRequest.Mode.DISCARD) ChemMasterBuffer.discard(beaker, reagent, amount);
            else buffer.extractFrom(beaker, reagent, amount);
        } else if (be.mode() == ChemMasterControlRequest.Mode.DISCARD) buffer.discard(reagent, amount);
        else {
            long cap = ReagentUnits.fromFloat(IReagentTrait.resolveCapacity(trait, before, be.getLevel()));
            long admitted = buffer.returnTo(beaker, reagent, amount, cap);
            if (ChemMasterProcessingPolicy.shouldReactOnReturn(admitted))
                beaker.recursiveReaction(be.getLevel(), ReagentUnits.toFloat(cap));
        }
        ItemStack after = before.copy();
        if (beaker.isEmpty()) after.remove(ModDataComponents.REAGENT.get());
        else after.set(ModDataComponents.REAGENT.get(), beaker.toComponent());
        boolean committed = be.commitSolutions(be.stateRevision(), slot.revision(), token, after, buffer.snapshot());
        if (committed && be.getLevel() instanceof ServerLevel level)
            com.juicyslew.moonstation14.ms14.reagent.ReactionAudio.playCommitted(level, be.getBlockPos(), beaker);
        return committed;
    }
    private static ChemMasterBuffer.Amount amount(long cents) {
        if (cents == ChemMasterControlRequest.allAmountCents()) return ChemMasterBuffer.Amount.ALL;
        for (var a : ChemMasterBuffer.Amount.values()) if (a.cents() == cents) return a;
        return null;
    }

    public static void serverTick(ServerTickEvent.Post event) {
        for (Session s : List.copyOf(SESSIONS.values())) {
            if (!(s.player().level() instanceof ServerLevel level) || level.getServer() != event.getServer()) continue;
            var a = BodyActionAuthority.resolve(s.player()).orElse(null);
            var machine = loadedMachine(s.player(), s);
            if (!validSession(s, a, machine, false)
                    || !com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.hasFullFreshChemMasterPower(
                    level, machine, s.machine())) { invalidate(s, "interaction invalid"); continue; }
        }
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            for (Session s : List.copyOf(SESSIONS.values())) if (s.player() == player) {
                SESSIONS.remove(s.id()); LAST_REQUEST.remove(s.id()); FRAMES.remove(s.id());
            }
    }
    public static void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        SESSIONS.clear(); LAST_REQUEST.clear(); FRAMES.clear();
    }
    public static void machineChanged(ChemMasterBlockEntity machine) {
        if (machine == null || !machine.isLoadedValid()) return;
        for (Session s : List.copyOf(SESSIONS.values())) if (s.machine().equals(machine.placementIdentity()))
            send(s, machine, -1, Optional.empty(), true, Optional.empty());
    }

    private static boolean validSession(Session s, BodyActionAuthority.Snapshot a, ChemMasterBlockEntity be, boolean allowUnpowered) {
        return a != null && a.body().getUUID().equals(s.body()) && a.identity().epoch() == s.epoch()
                && BodyActionAuthority.revalidate(s.player(), a) && CharacterControlSystem.canAct(a.body())
                && validMachine(a.body(), be, false)
                && (allowUnpowered || com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.hasFullFreshChemMasterPower(
                (ServerLevel) be.getLevel(), be, s.machine()));
    }
    private static boolean validMachine(net.minecraft.world.entity.LivingEntity body, ChemMasterBlockEntity be, boolean power) {
        if (body == null || be == null || !be.isLoadedValid() || body.level() != be.getLevel()
                || be.prototype() == null || body.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))
                > (double) be.prototype().uiRange() * be.prototype().uiRange()) return false;
        return !power || com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.hasFullFreshChemMasterPower(
                (ServerLevel) be.getLevel(), be, be.placementIdentity());
    }
    private static ChemMasterBlockEntity loadedMachine(ServerPlayer p, Session s) {
        if (!(p.level() instanceof ServerLevel level) || !level.dimension().location().equals(s.dimension())
                || !level.hasChunkAt(s.pos())) return null;
        return level.getBlockEntity(s.pos()) instanceof ChemMasterBlockEntity be
                && be.placementIdentity().equals(s.machine()) ? be : null;
    }
    private static void invalidate(Session s, String reason) {
        if (SESSIONS.remove(s.id()) != null) {
            LAST_REQUEST.remove(s.id()); closeFrame(s, reason); FRAMES.remove(s.id());
        }
    }
    private static void closeFrame(Session s, String reason) {
        // Closed payload retains exact session identity, so it cannot close a replacement workspace.
        var pos = s.pos();
        ChemMasterNetworking.send(s.player(), new ChemMasterWindowState(FRAMES.merge(s.id(), 1L, Long::sum), 0, 0,
                -1, Optional.empty(), s.id(), s.machine(), s.body(), s.epoch(), s.dimension(), pos.getX(), pos.getY(), pos.getZ(),
                false, Optional.of(reason), Optional.empty(), 0, 0, List.of(), List.of(),
                ChemMasterControlRequest.Mode.TRANSFER, ChemMasterControlRequest.Sort.OLDEST_FIRST));
    }
    private static void send(Session s, ChemMasterBlockEntity be, long ack, Optional<String> rejection,
                             boolean open, Optional<String> closeReason) {
        // Every snapshot is authoritative for this session's request watermark. In
        // particular, unsolicited snapshots must not roll an acknowledged request back.
        long acknowledged = acknowledgedRequest(ack, LAST_REQUEST.getOrDefault(s.id(), -1L));
        var slot = be.inputSlot().snapshot();
        ItemStack item = slot.stack();
        var input = new ArrayList<ChemMasterWindowState.ReagentRow>(); long capacity = 0, volume = 0;
        if (!item.isEmpty() && item.getItem() instanceof IReagentTrait trait) {
            capacity = ReagentUnits.fromFloat(IReagentTrait.resolveCapacity(trait, item, be.getLevel()));
            var component = item.get(ModDataComponents.REAGENT.get());
            if (component != null) for (var entry : component.centContents().entrySet()) {
                input.add(new ChemMasterWindowState.ReagentRow(entry.getKey(), entry.getValue())); volume += entry.getValue();
            }
        }
        var b = be.buffer();
        var bufferSnapshot = b.snapshot();
        var quantities = bufferSnapshot.contents().centContents();
        var rows = bufferSnapshot.order().stream().filter(quantities::containsKey)
                .map(key -> new ChemMasterWindowState.ReagentRow(key, quantities.get(key))).toList();
        var dim = s.dimension(); var p = s.pos();
        var state = new ChemMasterWindowState(FRAMES.merge(s.id(), 1L, Long::sum), be.stateRevision(), slot.revision(), acknowledged,
                rejection, s.id(), s.machine(), s.body(), s.epoch(), dim, p.getX(), p.getY(), p.getZ(), open, closeReason,
                item.isEmpty() ? Optional.empty() : Optional.of(item.getHoverName()), capacity, volume, input, rows, be.mode(), be.sort());
        ChemMasterNetworking.send(s.player(), state);
    }

    static long acknowledgedRequest(long requestedAck, long sessionLastRequest) {
        return Math.max(requestedAck, sessionLastRequest);
    }
}
