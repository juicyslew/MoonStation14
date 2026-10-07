package com.juicyslew.moonstation14.ms14.doors.ui;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.doors.AirlockToolLineOfSight;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Authenticated body-owned airlock maintenance menu and wire command capability. */
public final class AirlockWireMenuService {
    static final class WireSession {
        final int windowId; final UUID session; final HandActorAuthority.Snapshot authority;
        final ServerLevel level; final net.minecraft.core.BlockPos root; final AirlockBlockEntity door; final UUID identity;
        final com.juicyslew.moonstation14.ms14.hands.live.LiveHands openingHands;
        long lastRequest = -1;
        WireSession(int windowId, UUID session, HandActorAuthority.Snapshot authority, ServerLevel level,
                    net.minecraft.core.BlockPos root, AirlockBlockEntity door, UUID identity) {
            this.windowId=windowId; this.session=session; this.authority=authority; this.level=level;
            this.root=root.immutable(); this.door=door; this.identity=identity;
            this.openingHands=authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        }
    }
    record Replay(long lastRequest) { }
    private static final AirlockWireLayoutRegistry LAYOUTS = new AirlockWireLayoutRegistry();
    private static final Map<ServerPlayer, WireSession> SESSIONS = new WeakHashMap<>();
    private static int nextWindowId;
    private AirlockWireMenuService() { }

    /** Intercepts only cutting/pulsing USE on a currently open, visible, accessible panel. */
    public static boolean dispatchToolMenu(ServerPlayer actor, BodyBlockUseRequest request,
                                           BodyHandRequestService.BlockUseAdmission admission, ItemStack held) {
        if (actor == null || request == null || admission == null || held == null
                || !(admission.authority().body().level() instanceof ServerLevel level)) return false;
        if (!CharacterControlSystem.canAct(admission.authority().body())) return true;
        var hit = admission.hit().pos();
        if (!level.hasChunkAt(hit) || !(level.getBlockState(hit).getBlock() instanceof AirlockBlock)) return false;
        var state = level.getBlockState(hit);
        var root = state.getValue(AirlockBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? hit.below() : hit;
        if (!loadedRoot(level, root)) return false;
        var door = (AirlockBlockEntity) level.getBlockEntity(root);
        var data = door.prototype();
        if (data == null || ToolQualities.has(held, data.wiresPanel().openingToolQuality())) return false;
        boolean cutting = ToolQualities.has(held, ToolProfile.Quality.CUTTING);
        boolean pulsing = ToolQualities.has(held, ToolProfile.Quality.PULSING);
        if ((!cutting && !pulsing) || !door.panelOpen() || !door.panelVisible()
                || !data.wiresPanelSecurity().wiresAccessible()) return false;
        if (!openForBody(actor, request, admission, level, root, door)) return false;
        if(admission.authority().body() instanceof net.minecraft.world.entity.LivingEntity body)
            door.electrifiedContact(body,com.juicyslew.moonstation14.ms14.doors.AirlockElectrificationSystem.Contact.USING);
        return true;
    }

    private static boolean openForBody(ServerPlayer actor, BodyBlockUseRequest request,
                                       BodyHandRequestService.BlockUseAdmission admission, ServerLevel level,
                                       net.minecraft.core.BlockPos root, AirlockBlockEntity door) {
        var authority = admission.authority();
        var hands = admission.hands();
        if (actor.containerMenu != actor.inventoryMenu || !CharacterControlSystem.canAct(authority.body())
                || !HandActorAuthority.revalidate(actor, authority)
                || !(authority.body().level() instanceof ServerLevel)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(authority.body()) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand())
                || !java.util.Objects.equals(request.expectedToken(), hands.token(request.handId()).orElse(null))) return false;
        UUID id = door.identity(), session = UUID.randomUUID();
        if (!AirlockToolLineOfSight.canReachRoot(authority.body(), level, root, door, id)) return false;
        int windowId = nextWindowId++ & Integer.MAX_VALUE;
        var wireSession = new WireSession(windowId, session, authority, level, root, door, id);
        WireSession previous = SESSIONS.get(actor);
        if (previous != null) close(actor, previous);
        SESSIONS.put(actor, wireSession);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(actor, new AirlockWireOpen(windowId, session));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(actor, snapshot(wireSession, 0, true));
        return true;
    }

    private static boolean valid(ServerPlayer actor, WireSession s) {
        if (actor == null || s == null || actor.containerMenu != actor.inventoryMenu
                || !HandActorAuthority.revalidate(actor, s.authority)
                || !(s.authority.body().level() instanceof ServerLevel level) || level != s.level
                || !loadedRoot(level, s.root) || !level.mayInteract(actor, s.root)) return false;
        AirlockBlockEntity door = (AirlockBlockEntity) level.getBlockEntity(s.root);
        if (door != s.door || !door.identity().equals(s.identity) || door.prototype() == null
                || !door.panelOpen() || !door.panelVisible() || !door.prototype().wiresPanelSecurity().wiresAccessible()) return false;
        var hands = s.authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return hands != null && hands == s.openingHands && hands.compatible(s.authority.body())
                && AirlockToolLineOfSight.canReachRoot(s.authority.body(), level, s.root, door, s.identity);
    }

    static AirlockWireSnapshot apply(ServerPlayer actor, AirlockWireRequest request) {
        WireSession s = actor == null ? null : SESSIONS.get(actor);
        if (s == null || !valid(actor, s) || !admit(s.windowId, s.session, new Replay(s.lastRequest), request)) {
            if (actor != null && s != null) close(actor, s);
            return null;
        }
        if (request.operation() != AirlockWireRequest.Operation.QUERY
                && !CharacterControlSystem.canAct(s.authority.body()))
            return snapshot(s, request.requestId(), false, AirlockWireSnapshot.Rejection.STUNNED);
        s.lastRequest = request.requestId();
        var door = s.door; var state = door.state(); boolean accepted = false;
        if (request.operation() == AirlockWireRequest.Operation.QUERY) return snapshot(s, request.requestId(), true);
        if (AirlockWireCommandPolicy.revisionMatches(state.revision(), request.expectedRootRevision())) {
            var hands = s.authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            ItemStack active = hands == null ? ItemStack.EMPTY : hands.stackCopy(hands.activeHand()).orElse(ItemStack.EMPTY);
            var wire = LAYOUTS.resolve(door.prototype().wires().layoutId(), request.rowId());
            boolean cutting = ToolQualities.has(active, ToolProfile.Quality.CUTTING);
            boolean pulsing = ToolQualities.has(active, ToolProfile.Quality.PULSING);
            var operation = request.operation() == AirlockWireRequest.Operation.CLICK
                    ? AirlockWireCommandPolicy.resolveClick(cutting, pulsing, wire != null && isCut(state, wire))
                    : request.operation();
            if (wire != null && operation != null && AirlockWireCommandPolicy.toolAllows(operation, cutting, pulsing, isCut(state, wire))) {
                var action = switch (operation) {
                    case CUT -> AirlockState.WireAction.CUT; case MEND -> AirlockState.WireAction.MEND;
                    case PULSE -> AirlockState.WireAction.PULSE; case QUERY, CLICK -> null;
                };
                if (action != null && s.authority.body() instanceof net.minecraft.world.entity.LivingEntity body)
                    accepted = door.applyWireActor(s.identity, request.expectedRootRevision(), wire, action, body);
                if (accepted && (operation == AirlockWireRequest.Operation.CUT || operation == AirlockWireRequest.Operation.MEND)) {
                    var soundId = ToolQualities.acceptedWireActionSound(active, accepted,
                            operation == AirlockWireRequest.Operation.CUT || operation == AirlockWireRequest.Operation.MEND);
                    if ("moonstation14:tools/wirecutter".equals(soundId))
                        s.level.playSound(null, s.root, com.juicyslew.moonstation14.sounds.ModSounds.WIRECUTTER_USE.get(),
                                net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
                }
            }
        }
        return snapshot(s, request.requestId(), accepted, accepted ? AirlockWireSnapshot.Rejection.NONE : AirlockWireSnapshot.Rejection.REFRESH);
    }

    static boolean admit(int windowId, UUID session, Replay previous, AirlockWireRequest request) {
        return request != null && session != null && windowId == request.containerId() && session.equals(request.session())
                && (previous == null || request.requestId() > previous.lastRequest());
    }

    private static AirlockWireSnapshot snapshot(WireSession menu, long requestId, boolean accepted) {
        return snapshot(menu,requestId,accepted,accepted?AirlockWireSnapshot.Rejection.NONE:AirlockWireSnapshot.Rejection.REFRESH);
    }
    private static AirlockWireSnapshot snapshot(WireSession menu, long requestId, boolean accepted, AirlockWireSnapshot.Rejection rejection) {
        var door = menu.door; var state = door.state(); var data = door.prototype();
        var rows = LAYOUTS.describe(data.wires().layoutId(), data.wires().supportedActions(), state).stream()
                .map(r -> new AirlockWireSnapshot.Row(r.rowId(), r.argb(), r.letter(), r.cut())).toList();
        var indicators = AirlockWireIndicators.resolve(state, menu.level.getGameTime());
        var hands = menu.authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack stack = hands == null ? ItemStack.EMPTY : hands.stackCopy(hands.activeHand()).orElse(ItemStack.EMPTY);
        return new AirlockWireSnapshot(menu.windowId, menu.session, requestId, accepted, rejection, state.revision(),
                state.powerAvailable(), indicators.power(), indicators.bolts(), indicators.boltLights(), indicators.timing(), indicators.safety(),
                indicators.aiInteract(), indicators.log(),
                ToolQualities.has(stack, ToolProfile.Quality.CUTTING), ToolQualities.has(stack, ToolProfile.Quality.PULSING), rows);
    }

    private static boolean loadedRoot(ServerLevel level, net.minecraft.core.BlockPos root) {
        if (!level.hasChunkAt(root) || !level.hasChunkAt(root.above()) || !level.getWorldBorder().isWithinBounds(root)) return false;
        var chunk = level.getChunkSource().getChunkNow(root.getX() >> 4, root.getZ() >> 4);
        var upperChunk = level.getChunkSource().getChunkNow(root.above().getX() >> 4, root.above().getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                || upperChunk == null || !upperChunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)) return false;
        return chunk.getBlockState(root).getValue(AirlockBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                && chunk.getBlockState(root).getBlock() == ModBlocks.AIRLOCK.get()
                && chunk.getBlockEntity(root) instanceof AirlockBlockEntity
                && upperChunk.getBlockState(root.above()).getBlock() == ModBlocks.AIRLOCK.get()
                && upperChunk.getBlockState(root.above()).getValue(AirlockBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
    }

    private static boolean isCut(AirlockState state, AirlockState.Wire wire) {
        return switch (wire) {
            case POWER_ONE -> state.powerWireOneCut(); case POWER_TWO -> state.powerWireTwoCut();
            case LOG -> state.logWireCut();
            case BOLT -> state.boltWireCut(); case BOLT_LIGHT -> state.boltLightWireCut();
            case TIMING -> state.timingWireCut(); case SAFETY -> state.safetyWireCut();
            case AI_INTERACT -> state.aiInteractWireCut();
        };
    }

    static void close(ServerPlayer actor, WireSession session) {
        if (SESSIONS.remove(actor, session)) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(actor,
                new AirlockWireClosed(session.windowId, session.session));
    }
    static void closeRequest(ServerPlayer actor, AirlockWireClose request) {
        WireSession s=SESSIONS.get(actor);
        if(s!=null && s.windowId==request.windowId() && s.session.equals(request.session())) close(actor,s);
    }
    public static void onServerStopped() { SESSIONS.clear(); LAYOUTS.clear(); nextWindowId=0; }
}
