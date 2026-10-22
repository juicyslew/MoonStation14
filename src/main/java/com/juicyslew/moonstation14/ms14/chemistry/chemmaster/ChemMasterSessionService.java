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
import net.minecraft.resources.ResourceKey;
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
    private static java.util.function.Consumer<InvalidationDiagnostic> testInvalidationObserver;
    private record Session(UUID id, ServerPlayer player, UUID machine, ResourceLocation dimension,
                           net.minecraft.core.BlockPos pos, UUID body, long epoch) { }
    public record InvalidationDiagnostic(UUID player, UUID sessionId, String reason, long tick,
                                         UUID sessionBody, long sessionEpoch,
                                         UUID currentBody, long currentEpoch, boolean authorityRevalidated,
                                         boolean machineLoaded, UUID sessionPlacement, UUID currentPlacement,
                                         double distanceSquared, double uiRange,
                                         com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ChemMasterPowerDiagnostic power) { }
    private ChemMasterSessionService() { }

    /** Narrow fixture seam: reports the exact validation predicates immediately before invalidation. */
    public static void setTestInvalidationObserver(java.util.function.Consumer<InvalidationDiagnostic> observer) {
        testInvalidationObserver = observer;
    }

    public static boolean opensWorkspace(com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent intent,
            boolean emptyHand, boolean sneaking) {
        return intent == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.ACTIVATE_WORLD
                || emptyHand && !sneaking && intent == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE;
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
        String invalidReason = sessionInvalidReason(session, authority, machine,
                request.operation() == ChemMasterControlRequest.Operation.EJECT
                        || request.operation() == ChemMasterControlRequest.Operation.EJECT_OUTPUT);
        if (invalidReason != null) { invalidate(session, invalidReason); return; }
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
        boolean reagentChanged = false;
        if (request.operation() != ChemMasterControlRequest.Operation.QUERY
                && (request.expectedStateRevision() != machine.stateRevision()
                || request.expectedSlotRevision() != machine.inputSlot().snapshot().revision())) {
            rejection = Optional.of("stale state");
        } else if (request.operation() != ChemMasterControlRequest.Operation.QUERY
                && (!CharacterControlSystem.canAct(authority.body())
                || !BodyActionAuthority.eligibleForComplexInteraction(player, authority))) {
            rejection = Optional.of("action blocked");
        } else if (request.operation() == ChemMasterControlRequest.Operation.SET_MODE
                || request.operation() == ChemMasterControlRequest.Operation.SET_SORT) {
            var mode = request.operation() == ChemMasterControlRequest.Operation.SET_MODE ? request.mode() : machine.mode();
            var sort = request.operation() == ChemMasterControlRequest.Operation.SET_SORT ? request.sort() : machine.sort();
            changed = machine.configure(request.expectedStateRevision(), mode, sort);
            if (!changed) rejection = Optional.of("stale state");
        } else if (request.operation() == ChemMasterControlRequest.Operation.SET_DRAW_SOURCE) {
            var source = request.drawSource().orElseThrow() == ChemMasterControlRequest.DrawSource.BUFFER
                    ? ChemMasterBlockEntity.PackagingSource.BUFFER : ChemMasterBlockEntity.PackagingSource.INPUT;
            changed = machine.configureDrawSource(request.expectedStateRevision(), source);
            if (!changed) rejection = Optional.of("source rejected");
        } else if (request.operation() == ChemMasterControlRequest.Operation.SET_PILL_TYPE) {
            changed = machine.configurePillType(request.expectedStateRevision(), request.pillType());
            if (!changed) rejection = Optional.of("pill type rejected");
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
                machine.slotChanged(); ejected = true; changed = true;
            }
            else rejection = Optional.of("eject rejected");
        } else if (request.operation() == ChemMasterControlRequest.Operation.EJECT_OUTPUT) {
            var handAuthority = HandActorAuthority.resolve(player).orElse(null);
            var body = handAuthority == null ? null : handAuthority.body();
            var hands = body == null ? null : body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var slot = machine.outputSlot();
            var ledger = LevelItemLocationLedgers.forLevel((ServerLevel) machine.getLevel());
            var slotState = slot == null ? null : slot.snapshot();
            var result = handAuthority == null || hands == null || slotState == null
                    || slotState.revision() != request.expectedOutputSlotRevision() ? BodyMachineItemSlotTransfer.Result.DENIED
                    : BodyMachineItemSlotTransfer.ejectToHandsOrWorld(handAuthority, slot, hands.revision(),
                            slotState.revision(), ledger.snapshot().revision(), ledger,
                            () -> machine.isLoadedValid() && machine.placementIdentity().equals(session.machine()));
            if (result == BodyMachineItemSlotTransfer.Result.SUCCESS) { machine.slotChanged(); ejected = true; changed = true; }
            else rejection = Optional.of("output eject rejected");
        } else if (request.operation() == ChemMasterControlRequest.Operation.INPUT_REAGENT
                || request.operation() == ChemMasterControlRequest.Operation.BUFFER_REAGENT) {
            long beforeRevision = machine.stateRevision();
            if (!process(machine, request)) {
                rejection = Optional.of("transfer rejected");
            } else { changed = true; reagentChanged = machine.stateRevision() != beforeRevision; }
        } else if (request.operation() == ChemMasterControlRequest.Operation.CREATE_PILLS
                || request.operation() == ChemMasterControlRequest.Operation.OUTPUT_TO_BOTTLE) {
            var output = machine.outputSlot().snapshot();
            if (output.revision() != request.expectedOutputSlotRevision()
                    || machine.drawSource() == ChemMasterBlockEntity.PackagingSource.BUFFER
                        && machine.bufferRevision() != request.expectedBufferRevision()) {
                rejection = Optional.of("stale output state");
            } else {
                var input = machine.inputSlot().snapshot();
                var packageRequest = request.outputRequest().orElseThrow();
                var source = machine.drawSource();
                var result = request.operation() == ChemMasterControlRequest.Operation.CREATE_PILLS
                        ? machine.createPills(session.machine(), request.expectedStateRevision(), input.revision(),
                            request.expectedBufferRevision(), output.revision(), input.token(), output.token(), source,
                            packageRequest.count(), packageRequest.doseUnits(), packageRequest.label(), machine.pillType())
                        : machine.outputToBottle(session.machine(), request.expectedStateRevision(), input.revision(),
                            request.expectedBufferRevision(), output.revision(), input.token(), output.token(), source,
                            packageRequest.doseUnits(), packageRequest.label());
                if (result == ChemMasterBlockEntity.PackagingResult.SUCCESS) changed = true;
                else rejection = Optional.of(packagingRejection(result));
            }
        } else if (request.operation() != ChemMasterControlRequest.Operation.QUERY) {
            rejection = Optional.of("unsupported action");
        }
        if (rejection.isEmpty() && changed && request.operation() != ChemMasterControlRequest.Operation.QUERY
                && request.operation() != ChemMasterControlRequest.Operation.CLOSE) {
            if (ejected) ChemMasterAudio.playEject((ServerLevel) machine.getLevel(), machine.getBlockPos(), machine.prototype());
            else ChemMasterAudio.playClick((ServerLevel) machine.getLevel(), machine.getBlockPos(), machine.prototype());
        }
        boolean updateLabel = rejection.isEmpty() && shouldRegenerateLabel(request.operation(), machine.mode(), reagentChanged);
        for (Session viewer : List.copyOf(SESSIONS.values())) if (viewer.machine().equals(session.machine()))
            send(viewer, machine, viewer.id().equals(session.id()) ? request.requestId() : -1,
                    viewer.id().equals(session.id()) ? rejection : Optional.empty(), true, Optional.empty(), updateLabel);
    }

    private static String packagingRejection(ChemMasterBlockEntity.PackagingResult result) {
        return switch (result) {
            case MISSING_SOURCE -> "selected source is empty or unavailable";
            case INSUFFICIENT_SOURCE -> "selected source has insufficient mixture";
            case OUTPUT_FULL -> "output package has insufficient capacity";
            case INVALID_REQUEST -> "invalid packaging request";
            case INVALID_OUTPUT -> "output package unavailable";
            case OWNERSHIP_REJECTED -> "output package unavailable";
            case STALE_STATE -> "stale output state";
            case SUCCESS -> "";
        };
    }
    static boolean shouldRegenerateLabel(ChemMasterControlRequest.Operation operation,
            ChemMasterControlRequest.Mode mode, boolean successfulChangedReagentOperation) {
        if (!successfulChangedReagentOperation || mode == null) return false;
        return operation == ChemMasterControlRequest.Operation.INPUT_REAGENT && mode == ChemMasterControlRequest.Mode.TRANSFER
                || operation == ChemMasterControlRequest.Operation.BUFFER_REAGENT;
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
            String invalidReason = sessionInvalidReason(s, a, machine, false);
            if (invalidReason != null) { invalidate(s, invalidReason); continue; }
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

    /** Returns the first failed predicate in the existing session-validity order. */
    private static String sessionInvalidReason(Session s, BodyActionAuthority.Snapshot a,
                                               ChemMasterBlockEntity be, boolean allowUnpowered) {
        if (a == null) return "body authority unavailable";
        if (!a.body().getUUID().equals(s.body()) || a.identity().epoch() != s.epoch()) return "body binding changed";
        if (!BodyActionAuthority.revalidate(s.player(), a)) return "body authority stale";
        if (be == null || !be.isLoadedValid() || a.body().level() != be.getLevel() || be.prototype() == null)
            return "machine invalid";
        if (a.body().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))
                > (double) be.prototype().uiRange() * be.prototype().uiRange()) return "out of range";
        if (!allowUnpowered && !com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.hasFullFreshChemMasterPower(
                (ServerLevel) be.getLevel(), be, s.machine())) return "power unavailable";
        return null;
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
        var observer = testInvalidationObserver;
        if (observer != null) observer.accept(invalidationDiagnostic(s, reason));
        if (SESSIONS.remove(s.id()) != null) {
            LAST_REQUEST.remove(s.id()); closeFrame(s, reason); FRAMES.remove(s.id());
        }
    }
    private static InvalidationDiagnostic invalidationDiagnostic(Session s, String reason) {
        var authority = BodyActionAuthority.resolve(s.player()).orElse(null);
        ServerLevel level = s.player().level() instanceof ServerLevel server ? server : null;
        var machine = loadedMachine(s.player(), s);
        boolean authorityMatches = authority != null && authority.body().getUUID().equals(s.body())
                && authority.identity().epoch() == s.epoch() && BodyActionAuthority.revalidate(s.player(), authority);
        double distance = authority == null || machine == null ? Double.NaN
                : authority.body().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(machine.getBlockPos()));
        double range = machine == null || machine.prototype() == null ? Double.NaN : machine.prototype().uiRange();
        var power = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime
                .chemMasterPowerDiagnostic(level, machine);
        return new InvalidationDiagnostic(s.player().getUUID(), s.id(), reason, level == null ? -1 : level.getGameTime(),
                s.body(), s.epoch(), authority == null ? null : authority.body().getUUID(),
                authority == null ? -1 : authority.identity().epoch(), authorityMatches, machine != null,
                s.machine(), machine == null ? null : machine.placementIdentity(), distance, range, power);
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
        send(s, be, ack, rejection, open, closeReason, false);
    }
    private static void send(Session s, ChemMasterBlockEntity be, long ack, Optional<String> rejection,
                             boolean open, Optional<String> closeReason, boolean updateLabel) {
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
        var output = outputState(be);
        var state = new ChemMasterWindowState(FRAMES.merge(s.id(), 1L, Long::sum), be.stateRevision(), slot.revision(), be.bufferRevision(), acknowledged,
                rejection, s.id(), s.machine(), s.body(), s.epoch(), dim, p.getX(), p.getY(), p.getZ(), open, closeReason,
                item.isEmpty() ? Optional.empty() : Optional.of(item.getHoverName()), capacity, volume, input, rows, be.mode(), be.sort(),
                output, be.drawSource() == ChemMasterBlockEntity.PackagingSource.BUFFER ? ChemMasterControlRequest.DrawSource.BUFFER
                        : ChemMasterControlRequest.DrawSource.INPUT, be.pillType(), be.prototype() == null ? 0 : be.prototype().pillDosageLimit(), updateLabel);
        ChemMasterNetworking.send(s.player(), state);
    }

    private static ChemMasterWindowState.OutputSlot outputState(ChemMasterBlockEntity machine) {
        var slot = machine.outputSlot().snapshot();
        if (slot.stack().isEmpty()) return new ChemMasterWindowState.OutputSlot(slot.revision(),
                ChemMasterWindowState.OutputKind.EMPTY, Optional.empty(), 0, 0, List.of(), 0, 0, 0, Optional.empty(), List.of());
        ItemStack item = slot.stack();
        Optional<String> label = Optional.ofNullable(item.get(ModDataComponents.CHEM_OUTPUT_LABEL.get()))
                .map(com.juicyslew.moonstation14.ms14.chemistry.output.ChemOutputLabel::value);
        var profileId = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.prototype(item);
        var profile = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolve(machine.getLevel(), profileId);
        if (item.is(com.juicyslew.moonstation14.item.ModItems.BOTTLE) && profile != null && profile.solution().isPresent()) {
            long cap = ReagentUnits.fromDouble(profile.solution().orElseThrow().capacity());
            var component = item.get(ModDataComponents.REAGENT.get());
            var values = component == null ? Map.<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>of()
                    : component.centContents();
            var reagentRows = values.entrySet().stream().map(e -> new ChemMasterWindowState.ReagentRow(e.getKey(), e.getValue())).toList();
            long volume = ReagentUnits.total(values.values());
            return new ChemMasterWindowState.OutputSlot(slot.revision(), ChemMasterWindowState.OutputKind.BOTTLE,
                    Optional.of(item.getHoverName()), cap, volume, reagentRows, 0, 0, 0, label, List.of());
        }
        if (item.is(com.juicyslew.moonstation14.item.ModItems.PILL_CANISTER) && profile != null && profile.storage().isPresent()) {
            var contents = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(item).orElse(null);
            if (contents == null) return new ChemMasterWindowState.OutputSlot(slot.revision(),
                    ChemMasterWindowState.OutputKind.PILL_CANISTER, Optional.of(item.getHoverName()), 0, 0, List.of(),
                    com.juicyslew.moonstation14.ms14.storage.grid.GridContents.width(item),
                    com.juicyslew.moonstation14.ms14.storage.grid.GridContents.height(item), 0, label, List.of());
            int width = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.width(item);
            int height = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.height(item);
            var geometry = com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Snapshot.<String>empty(
                    com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.StorageMask.rectangle(width, height));
            var pills = new ArrayList<ChemMasterWindowState.PillDescriptor>();
            for (var entry : contents.entries()) {
                var shape = com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forStack(entry.stack());
                var placed = geometry.place(entry.token(), shape,
                        new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(entry.x(), entry.y()), entry.rotation());
                if (!placed.accepted()) continue;
                geometry = placed.snapshot();
                var child = entry.stack(); var type = child.get(ModDataComponents.PILL_TYPE.get());
                var reagent = child.get(ModDataComponents.REAGENT.get());
                var childRows = reagent == null ? List.<ChemMasterWindowState.ReagentRow>of() : reagent.centContents().entrySet().stream()
                        .map(e -> new ChemMasterWindowState.ReagentRow(e.getKey(), e.getValue())).toList();
                var childLabel = Optional.ofNullable(child.get(ModDataComponents.CHEM_OUTPUT_LABEL.get()))
                        .map(com.juicyslew.moonstation14.ms14.chemistry.output.ChemOutputLabel::value);
                pills.add(new ChemMasterWindowState.PillDescriptor(entry.x(), entry.y(), type == null ? 0 : type.type(), childLabel, childRows));
            }
            int used = geometry.placements().values().stream().mapToInt(p -> p.occupiedCells().size()).sum();
            return new ChemMasterWindowState.OutputSlot(slot.revision(), ChemMasterWindowState.OutputKind.PILL_CANISTER,
                    Optional.of(item.getHoverName()), 0, 0, List.of(), width, height, Math.max(0, width * height - used), label, pills);
        }
        return new ChemMasterWindowState.OutputSlot(slot.revision(), ChemMasterWindowState.OutputKind.EMPTY,
                Optional.empty(), 0, 0, List.of(), 0, 0, 0, Optional.empty(), List.of());
    }

    static long acknowledgedRequest(long requestedAck, long sessionLastRequest) {
        return Math.max(requestedAck, sessionLastRequest);
    }
}
