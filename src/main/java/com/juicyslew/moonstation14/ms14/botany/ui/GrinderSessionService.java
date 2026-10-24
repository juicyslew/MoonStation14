package com.juicyslew.moonstation14.ms14.botany.ui;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Session and snapshot core for the grinder UI; transport registration is intentionally owned by integration. */
public final class GrinderSessionService {
    private GrinderSessionService() { }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_POWER = new HashMap<>();
    private record Session(UUID id, ServerPlayer player, UUID placement, ResourceLocation dimension,
                           net.minecraft.core.BlockPos position, UUID body, long epoch,
                           java.util.function.Consumer<GrinderSnapshot> publish) { }

    /** Caller must invoke only after the shared authenticated block-use admission accepted the open. */
    public static UUID open(ServerPlayer player, ReagentGrinderBlockEntity machine,
                            java.util.function.Consumer<GrinderSnapshot> publish) {
        var authority = HandActorAuthority.resolve(player).orElse(null);
        if (authority == null || publish == null || !valid(authority, machine)) return null;
        var session = new Session(UUID.randomUUID(), player, machine.placementIdentity(),
                ((ServerLevel) machine.getLevel()).dimension().location(), machine.getBlockPos().immutable(),
                authority.body().getUUID(), authority.epoch(), publish);
        SESSIONS.put(session.id(), session); REVISIONS.put(session.id(), machine.stateRevision());
        send(session, machine, null); return session.id();
    }

    public static void control(ServerPlayer player, GrinderControl control) {
        if (control == null) return;
        Session s = SESSIONS.get(control.sessionId());
        if (s == null || s.player() != player || !s.placement().equals(control.placement())) return;
        if (control.operation() == GrinderControl.Operation.CLOSE) { remove(s); return; }
        var authority = HandActorAuthority.resolve(player).orElse(null);
        var machine = loaded(s);
        String rejection = null;
        if (!valid(authority, machine) || !matches(s, authority, machine)) { invalidate(s); return; }
        if (control.expectedStateRevision() != machine.stateRevision()) rejection = "stale state";
        else if (control.operation() == GrinderControl.Operation.GRIND || control.operation() == GrinderControl.Operation.JUICE) {
            // The trusted core captures its own proof; no client-supplied StartProof is accepted.
            var proof = machine.startProof();
            var selected = control.operation() == GrinderControl.Operation.JUICE
                    ? com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram.Operation.JUICE
                    : com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram.Operation.GRIND;
            if (proof == null || !machine.startTrusted(proof, machine.getLevel().getGameTime(), true, selected)) rejection = "processing unavailable";
        } else if (machine.program() == null || !machine.program().canEject()) rejection = "machine busy";
        else {
            var hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var ledger = LevelItemLocationLedgers.forLevel((ServerLevel) machine.getLevel());
            List<com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot> slots = new ArrayList<>();
            if (control.operation() == GrinderControl.Operation.EJECT_BEAKER) slots.add(machine.beakerSlot());
            else {
                var chamber = machine.chamberSlots();
                if (control.operation() == GrinderControl.Operation.EJECT_ONE) {
                    if (control.slotIndex() >= 0 && control.slotIndex() < chamber.size()) slots.add(chamber.get(control.slotIndex()));
                } else if (control.operation() == GrinderControl.Operation.EJECT_ALL) slots.addAll(chamber);
            }
            boolean moved = false;
            for (var slot : slots) if (slot != null && !slot.snapshot().stack().isEmpty()) {
                // A successful take replaces LiveHands and advances its revision. Resolve the
                // current authority/hands for each remaining slot in the same authenticated session.
                var currentAuthority = HandActorAuthority.resolve(player).orElse(null);
                var currentHands = currentAuthority == null ? null
                        : currentAuthority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                var snap = slot.snapshot();
                var result = currentAuthority == null || currentHands == null ? BodyMachineItemSlotTransfer.Result.DENIED
                        : BodyMachineItemSlotTransfer.ejectToHandsOrWorld(currentAuthority, slot, currentHands.revision(), snap.revision(),
                          ledger.snapshot().revision(), ledger, () -> matches(s, HandActorAuthority.resolve(player).orElse(null), loaded(s)));
                  if (result == BodyMachineItemSlotTransfer.Result.SUCCESS) { moved = true; }
                else { rejection = "eject rejected"; break; }
            }
             if (moved) machine.slotTransferCommitted(control.operation() == GrinderControl.Operation.EJECT_BEAKER, false);
             if (!moved && rejection == null) rejection = "slot empty";
        }
        send(s, machine, rejection);
    }

    public static void machineChanged(ReagentGrinderBlockEntity machine) {
        if (machine == null || !machine.isLoadedValid()) return;
        for (Session s : List.copyOf(SESSIONS.values())) if (s.placement().equals(machine.placementIdentity())) send(s, machine, null);
    }
    public static void serverTick(ServerTickEvent.Post event) {
        for (Session s : List.copyOf(SESSIONS.values())) {
            if (!(s.player().level() instanceof ServerLevel l) || l.getServer() != event.getServer()) continue;
            var a = HandActorAuthority.resolve(s.player()).orElse(null); var be = loaded(s);
            if (!matches(s, a, be)) { invalidate(s); continue; }
            boolean powered = be.isPowered();
            if (shouldPublish(be.stateRevision(), powered, REVISIONS.getOrDefault(s.id(), -1L), LAST_POWER.get(s.id()))) send(s, be, null);
        }
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) for (Session s : List.copyOf(SESSIONS.values())) if (s.player() == p) remove(s);
    }
    public static void serverStopped(ServerStoppedEvent e) { SESSIONS.clear(); REVISIONS.clear(); LAST_POWER.clear(); }
    static boolean shouldPublish(long revision, boolean powered, long publishedRevision, Boolean publishedPower) {
        return revision != publishedRevision || publishedPower == null || powered != publishedPower;
    }
    private static boolean valid(HandActorAuthority.Snapshot a, ReagentGrinderBlockEntity be) {
        return a != null && be != null && be.isLoadedValid() && a.body().level() == be.getLevel();
    }
    private static boolean matches(Session s, HandActorAuthority.Snapshot a, ReagentGrinderBlockEntity be) {
        return valid(a, be) && a.body().getUUID().equals(s.body()) && a.epoch() == s.epoch()
                && be.placementIdentity().equals(s.placement())
                && ((ServerLevel) be.getLevel()).dimension().location().equals(s.dimension())
                && be.getBlockPos().equals(s.position()) && HandActorAuthority.revalidate(s.player(), a)
                && com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe.lineOfSight(
                    (ServerLevel) be.getLevel(), a.body(), s.position());
    }
    private static ReagentGrinderBlockEntity loaded(Session s) {
        if (!(s.player().level() instanceof ServerLevel level) || !level.dimension().location().equals(s.dimension()) || !level.hasChunkAt(s.position())) return null;
        return level.getBlockEntity(s.position()) instanceof ReagentGrinderBlockEntity be && be.placementIdentity().equals(s.placement()) ? be : null;
    }
    private static void send(Session s, ReagentGrinderBlockEntity be, String rejection) {
        var slots = new ArrayList<GrinderSnapshot.Slot>(); var beaker = be.beakerSlot();
         if (beaker != null) { var x = beaker.snapshot(); slots.add(new GrinderSnapshot.Slot(-1, x.revision(), null, x.stack())); }
         int i=0; for (var slot : be.chamberSlots()) { var x=slot.snapshot(); slots.add(new GrinderSnapshot.Slot(i++, x.revision(), null, x.stack())); }
         var p=be.program(); boolean powered = be.isPowered(); REVISIONS.put(s.id(), be.stateRevision()); LAST_POWER.put(s.id(), powered);
          boolean active = p != null && p.active();
          s.publish().accept(new GrinderSnapshot(s.id(), s.placement(), s.body(), s.epoch(), s.dimension(), s.position(), be.stateRevision(), beaker == null ? 0 : beaker.snapshot().revision(), slots, powered, active, p == null ? -1 : p.startTick(), p == null ? -1 : p.endTick(),
                  be.canStart(com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram.Operation.GRIND),
                  be.canStart(com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram.Operation.JUICE),
                  active ? p.operation() : null, true, rejection));
    }
    private static void invalidate(Session s) { remove(s); s.publish().accept(new GrinderSnapshot(s.id(), s.placement(), s.body(), s.epoch(), s.dimension(), s.position(), 0, 0, List.of(), false, false, -1, -1, false, false, null, false, "interaction invalid")); }
    private static void remove(Session s) { SESSIONS.remove(s.id()); REVISIONS.remove(s.id()); LAST_POWER.remove(s.id()); }
}
