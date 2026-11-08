package com.juicyslew.moonstation14.ms14.construction.tool;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Delayed body-hand wrench action for anchoring/unanchoring an exposed disposal tube. */
public final class BodyDisposalTubeAnchorUse {
    private static final Map<MinecraftServer, Map<UUID, Pending>> PENDING = new WeakHashMap<>();

    private BodyDisposalTubeAnchorUse() { }

    public static void register(IEventBus bus) {
        bus.addListener(BodyDisposalTubeAnchorUse::onPostServerTick);
        bus.addListener(BodyDisposalTubeAnchorUse::onDamage);
        bus.addListener(BodyDisposalTubeAnchorUse::onServerStopped);
    }

    /** Called only from the already admitted primary-use body-hit route. */
    public static boolean begin(ServerPlayer actor, BodyBlockUseRequest request,
                                BodyHandRequestService.BlockUseAdmission admission) {
        if (actor == null || request == null || admission == null || request.intent() != BodyBlockUseRequest.Intent.USE)
            return false;
        HandActorAuthority.Snapshot authority = admission.authority();
        LiveHands hands = admission.hands();
        LivingEntity body = authority.body();
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || request.expectedRevision() != hands.revision()
                || !request.handId().equals(hands.activeHand()) || !authority.handIds().contains(request.handId())
                || !request.target().equals(admission.hit().pos()) || request.face() != admission.hit().face()) return false;
        ItemStack toolStack = hands.stackCopy(request.handId()).orElse(null);
        if (toolStack == null || !toolStack.is(ModItems.WRENCH)
                || !hands.token(request.handId()).filter(token -> java.util.Objects.equals(token, request.expectedToken())).isPresent()
                || !HandActorAuthority.revalidate(actor, authority)
                || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != hands)
            return false;
        var manager = PrototypeRuntime.serverManager();
        if (!manager.registeredTypes().contains(ModTools.TOOL_TYPE)) return false;
        var catalog = manager.snapshot(ModTools.TOOL_TYPE);
        if (!ModTools.hasQuality(catalog, ModTools.WRENCH, ToolPrototypeData.Quality.Anchoring)) return false;
        Optional<Double> speed = ModTools.speedModifier(catalog, ModTools.WRENCH, ToolPrototypeData.Quality.Anchoring);
        if (speed.isEmpty()) return false;
        long delay = delayTicks(speed.orElseThrow());

        if (!exposed(level, request.target()) || BodyBlockUseTargetProbe.probe(level, body,
                request.target(), request.face()) == null) return false;
        DisposalTubeWorldData data = DisposalTubeWorldData.existing(level).orElse(null);
        if (data == null) return false;
        var lookup = data.lookup(level, request.target());
        if (lookup.kind() != DisposalTubeWorldData.LookupKind.PRESENT || lookup.record() == null) return false;
        DisposalTubeRecord tube = lookup.record();
        long deadline;
        try { deadline = Math.addExact(level.getGameTime(), delay); }
        catch (ArithmeticException overflow) { return false; }
        Pending action = new Pending(actor, authority, level, data, request.target(), request.face(), tube.id(),
                tube.revision(), tube.anchored(), request.handId(), request.expectedToken(), hands.revision(),
                toolStack, body.position(), speed.orElseThrow(), deadline);
        PENDING.computeIfAbsent(level.getServer(), ignored -> new HashMap<>()).put(body.getUUID(), action);
        return true;
    }

    static long delayTicks(double speedModifier) {
        if (!Double.isFinite(speedModifier) || speedModifier <= 0) throw new IllegalArgumentException("invalid tool speed");
        return Math.max(1L, (long) Math.ceil(20.0 / speedModifier));
    }

    private static void onPostServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Pending> actions = PENDING.get(server);
        if (actions == null || actions.isEmpty()) return;
        for (Pending action : new ArrayList<>(actions.values())) {
            UUID bodyId = action.authority.body().getUUID();
            if (actions.get(bodyId) != action) continue;
            if (!stillValid(action)) {
                actions.remove(bodyId, action);
                continue;
            }
            if (action.level.getGameTime() < action.deadline) continue;
            var lookup = action.world.lookup(action.level, action.target);
            DisposalTubeRecord exact = lookup.record();
            if (lookup.kind() == DisposalTubeWorldData.LookupKind.PRESENT
                    && sameTube(exact, action.tubeId, action.tubeRevision, action.wasAnchored)
                    && exposed(action.level, action.target)) {
                action.world.anchor(action.level, exact, !action.wasAnchored,
                        pos -> exposed(action.level, pos));
            }
            actions.remove(bodyId, action);
        }
        if (actions.isEmpty()) PENDING.remove(server);
    }

    private static boolean stillValid(Pending action) {
        LivingEntity body = action.authority.body();
        ServerPlayer actor = action.actor;
        if (action.level.isClientSide || action.level.getServer() == null
                || action.level.getEntity(body.getId()) != body || body.isRemoved() || !body.isAlive()
                || !body.isAddedToLevel() || !action.level.hasChunkAt(body.blockPosition())
                || !actor.isAlive() || actor.connection == null
                || actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor
                || !HandActorAuthority.revalidate(actor, action.authority)
                || !body.position().equals(action.origin)) return false;
        LiveHands hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || hands.revision() != action.handsRevision
                || !action.hand.equals(hands.activeHand())
                || !hands.token(action.hand).filter(action.token::equals).isPresent()
                || hands.stackCopy(action.hand).filter(stack -> ItemStack.isSameItemSameComponents(stack, action.tool)
                && stack.getCount() == action.tool.getCount()).isEmpty()) return false;
        var manager = PrototypeRuntime.serverManager();
        if (!manager.registeredTypes().contains(ModTools.TOOL_TYPE)) return false;
        var catalog = manager.snapshot(ModTools.TOOL_TYPE);
        if (ModTools.speedModifier(catalog, ModTools.WRENCH, ToolPrototypeData.Quality.Anchoring)
                .filter(speed -> Double.compare(speed, action.speed) == 0).isEmpty()) return false;
        if (!exposed(action.level, action.target)
                || BodyBlockUseTargetProbe.probe(action.level, body, action.target, action.face) == null) return false;
        var target = action.world.lookup(action.level, action.target);
        return target.kind() == DisposalTubeWorldData.LookupKind.PRESENT
                && sameTube(target.record(), action.tubeId, action.tubeRevision, action.wasAnchored);
    }

    static boolean sameTube(DisposalTubeRecord current, UUID expectedId, long expectedRevision, boolean expectedAnchored) {
        return current != null && current.id().equals(expectedId) && current.revision() == expectedRevision
                && current.anchored() == expectedAnchored;
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)) return;
        Map<UUID, Pending> actions = PENDING.get(level.getServer());
        if (actions != null) actions.remove(event.getEntity().getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent event) { PENDING.remove(event.getServer()); }

    private static boolean exposed(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                && DisposalTubeWorldData.existing(level).map(data -> data.lookup(level,pos).kind()==DisposalTubeWorldData.LookupKind.PRESENT).orElse(false);
    }

    private record Pending(ServerPlayer actor, HandActorAuthority.Snapshot authority, ServerLevel level,
                           DisposalTubeWorldData world, BlockPos target, net.minecraft.core.Direction face,
                           UUID tubeId, long tubeRevision, boolean wasAnchored, String hand, String token,
                           long handsRevision, ItemStack tool, Vec3 origin, double speed, long deadline) {
        private Pending {
            target = target.immutable();
            tool = tool.copy();
        }
    }
}

