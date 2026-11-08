package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.custom.AtmosphereAnalyzerItem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived, controlled-body-bound read-only analyzer session. Main-thread only. */
public final class GasAnalyzerSessionService {
    private static final Map<ServerPlayer, Session> SESSIONS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, GasAnalyzerSnapshot> LAST_SNAPSHOTS = new IdentityHashMap<>();
    private static long nextGeneration;
    private GasAnalyzerSessionService() { }
    private static final class Session {
        final UUID id = UUID.randomUUID();
        final ServerPlayer actor;
        final UUID body;
        final long epoch, generation;
        final net.minecraft.resources.ResourceLocation dimension;
        final String sourceToken;
        int distance;
        long sequence;
        long serverTick;
        long nextUpdate;
        long lastControl;
        GasAnalyzerCellSelection.Result lastSelection;
        BlockPos pinnedPos;
        GasAnalyzerPipeSamples.Readout pinnedReadout;
        net.minecraft.world.level.block.entity.BlockEntity pinnedEntity;
        Session(ServerPlayer actor, HandActorAuthority.Snapshot authority, String token, int distance, long generation) {
            this.actor=actor; this.body=authority.body().getUUID(); this.epoch=authority.epoch();
            this.dimension=authority.body().level().dimension().location(); this.sourceToken=token;
            this.distance=distance; this.generation=generation;
        }
    }

    public static com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason scanTarget(
            ServerPlayer actor, com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.BlockUseAdmission admission,
            String handId, String token, long revision) {
        if (actor == null || admission == null || !(admission.authority().body().level() instanceof ServerLevel level))
            return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        var authority = admission.authority(); var hands = admission.hands();
        if (!HandActorAuthority.revalidate(actor, authority) || !hands.compatible(authority.body())
                || hands.revision() != revision || !handId.equals(hands.activeHand())
                || !token.equals(hands.token(handId).orElse(null)))
            return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.STALE_HAND;
        var stack=hands.stackCopy(handId).orElse(null);
        if(stack==null||!(stack.getItem() instanceof AtmosphereAnalyzerItem))return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        var pos=admission.hit().pos(); var readout=GasAnalyzerPipeSamples.read(level,pos,ModGasAnalyzers.resolve(level)).orElse(null);
        if(readout==null){
            var result=open(actor,authority,hands,handId,token,revision);
            Session current=SESSIONS.get(actor);
            if(current==null&&result==com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK){
                result=open(actor,authority,hands,handId,token,revision);current=SESSIONS.get(actor);
            }
            if(current!=null&&result==com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK){
                current.pinnedPos=null;current.pinnedReadout=null;current.pinnedEntity=null;
                current.nextUpdate=0;update(current,authority.body(),level,ModGasAnalyzers.resolve(level));
            }
            return result;
        }
        Session session=SESSIONS.get(actor);
        if(session==null||!session.body.equals(authority.body().getUUID())||session.epoch!=authority.epoch()
                ||!session.dimension.equals(level.dimension().location())||!session.sourceToken.equals(token)) {
            var opened=open(actor,authority,hands,handId,token,revision);
            if(opened!=com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK)return opened;
            session=SESSIONS.get(actor);
        }
        session.pinnedPos=pos.immutable();session.pinnedReadout=readout;session.pinnedEntity=level.getBlockEntity(pos);
        session.nextUpdate=0;update(session,authority.body(),level,ModGasAnalyzers.resolve(level));
        return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK;
    }

    public static com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason open(
            ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands hands,
            String handId, String token, long expectedRevision) {
        if (actor == null || authority == null || hands == null || token == null
                || !(authority.body().level() instanceof ServerLevel level))
            return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        var profile = ModGasAnalyzers.resolve(level);
        var stack = hands.stackCopy(handId).orElse(null);
        if (stack == null || !(stack.getItem() instanceof AtmosphereAnalyzerItem)
                || hands.revision() != expectedRevision || !hands.compatible(authority.body())
                || !token.equals(hands.token(handId).orElse(null))
                || !HandActorAuthority.revalidate(actor, authority))
            return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.STALE_HAND;
        if(nextGeneration==Long.MAX_VALUE)throw new IllegalStateException("analyzer session generation exhausted");
        Session existing=SESSIONS.get(actor);
        if(existing!=null && existing.body.equals(authority.body().getUUID())
                && existing.epoch==authority.epoch()
                && existing.dimension.equals(authority.body().level().dimension().location())
                && existing.sourceToken.equals(token)) {
            // Repeated USE_IN_HAND is a presentation re-open, not a new sampling session.
            if(authority.body().level() instanceof ServerLevel existingLevel)
                update(existing,authority.body(),existingLevel,profile);
            return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK;
        }
        long generation = ++nextGeneration;
        Session session = new Session(actor, authority, token, profile.defaultSampleDistance(), generation);
        SESSIONS.put(actor, session);
        update(session, authority.body(), level, profile);
        return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK;
    }

    public static void control(ServerPlayer actor, GasAnalyzerControl control) {
        Session session = SESSIONS.get(actor);
        if (session == null || !GasAnalyzerSessionPolicy.controlCurrent(session.id, session.generation,
                session.epoch, session.lastControl, control)) return;
        session.lastControl = control.sequence();
        if (control.close()) { close(session, GasAnalyzerSnapshot.Status.INVALIDATED); return; }
        var resolved = HandActorAuthority.resolve(actor).orElse(null);
        if (!validSource(session, resolved) || !(resolved.body().level() instanceof ServerLevel level)) {
            close(session, GasAnalyzerSnapshot.Status.INVALIDATED); return;
        }
        if (!validActiveSource(session, resolved)) return;
        if(session.pinnedPos!=null){session.pinnedPos=null;session.pinnedReadout=null;session.pinnedEntity=null;}
        var profile = ModGasAnalyzers.resolve(level);
        if (!GasAnalyzerSessionPolicy.validDistance(control.distance(), profile)) return;
        session.distance = control.distance();
        session.nextUpdate = 0;
        update(session, resolved.body(), level, profile);
    }

    public static void serverTick(ServerTickEvent.Post event) {
        for (Session session : java.util.List.copyOf(SESSIONS.values())) {
            var authority = HandActorAuthority.resolve(session.actor).orElse(null);
            if (!validSource(session, authority) || !(authority.body().level() instanceof ServerLevel level)) {
                close(session, GasAnalyzerSnapshot.Status.INVALIDATED); continue;
            }
            var profile = ModGasAnalyzers.resolve(level);
            session.distance = Math.min(session.distance, profile.maximumRange());
            long now = level.getGameTime();
            if(session.pinnedPos!=null){
                if(!level.hasChunkAt(session.pinnedPos)){
                    session.pinnedPos=null;session.pinnedReadout=null;session.pinnedEntity=null;session.nextUpdate=0;
                    update(session,authority.body(),level,profile);continue;
                }
                if(level.getBlockEntity(session.pinnedPos)!=session.pinnedEntity){close(session,GasAnalyzerSnapshot.Status.INVALIDATED);continue;}
                if(!com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe.lineOfSight(level,authority.body(),session.pinnedPos)){
                    session.pinnedPos=null;session.pinnedReadout=null;session.pinnedEntity=null;session.nextUpdate=0;
                    update(session,authority.body(),level,profile);
                } else if(now>=session.nextUpdate) update(session,authority.body(),level,profile);
                continue;
            }
            var selection=GasAnalyzerCellSelection.select(level,authority.body().getEyePosition(),authority.body().getLookAngle(),session.distance);
            if(GasAnalyzerSessionPolicy.needsRefresh(now,session.nextUpdate,session.lastSelection,selection))
                update(session, authority.body(), level, profile,selection);
        }
    }

    private static boolean validSource(Session session, HandActorAuthority.Snapshot authority) {
        if (authority == null || !GasAnalyzerSessionPolicy.sameBody(session.body, session.epoch,
                session.dimension, authority.body().getUUID(), authority.epoch(), authority.body().level().dimension().location())
                || !HandActorAuthority.revalidate(session.actor, authority)) return false;
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(authority.body())) return false;
        var sources = new java.util.ArrayList<GasAnalyzerSessionPolicy.HandSource>();
        for (String hand : authority.handIds()) {
            var stack = hands.stackCopy(hand).orElse(null);
            sources.add(new GasAnalyzerSessionPolicy.HandSource(hands.token(hand).orElse(null),
                    stack != null && stack.getItem() instanceof AtmosphereAnalyzerItem));
        }
        return GasAnalyzerSessionPolicy.sourceHeldInEitherHand(session.sourceToken, sources);
    }

    private static boolean validActiveSource(Session session,HandActorAuthority.Snapshot authority) {
        LiveHands hands=authority==null?null:authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if(hands==null||!hands.compatible(authority.body()))return false;
        var stack=hands.stackCopy(hands.activeHand()).orElse(null);
        return session.sourceToken.equals(hands.token(hands.activeHand()).orElse(null))
                &&stack!=null&&stack.getItem() instanceof AtmosphereAnalyzerItem;
    }

    private static void update(Session session, LivingEntity body, ServerLevel level, GasAnalyzerProfile profile) {
        update(session,body,level,profile,null);
    }
    private static void update(Session session, LivingEntity body, ServerLevel level, GasAnalyzerProfile profile,
                               GasAnalyzerCellSelection.Result knownSelection) {
        if (SESSIONS.get(session.actor) != session) return;
        BlockPos localPos = BlockPos.containing(body.getEyePosition());
        if(session.pinnedPos!=null){
            var current=GasAnalyzerPipeSamples.read(level,session.pinnedPos,profile).orElse(null);
            if(current==null){session.pinnedPos=null;session.pinnedReadout=null;session.pinnedEntity=null;update(session,body,level,profile);return;}
            if(!sameTargetIdentity(session.pinnedReadout,current)){close(session,GasAnalyzerSnapshot.Status.INVALIDATED);return;}
            session.pinnedReadout=current;session.serverTick=level.getGameTime();session.nextUpdate=session.serverTick+profile.updateIntervalTicks();
            send(new GasAnalyzerSnapshot(session.id,session.generation,session.sequence++,session.lastControl,session.serverTick,session.epoch,
                    session.body,session.sourceToken,session.dimension,localPos,session.pinnedPos,session.distance,GasAnalyzerSnapshot.Status.READY,
                    null,null,false,current.source(),current.entries()),session.actor);return;
        }
        var localReading = level.hasChunkAt(localPos) ? AtmosphereService.INSTANCE.readAtmosphere(level, localPos) : java.util.Optional.<com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading>empty();
        GasAnalyzerSample local = localReading.map(reading -> GasAnalyzerSample.from(reading, profile)).orElse(null);
        var selection = knownSelection==null?GasAnalyzerCellSelection.select(level, body.getEyePosition(), body.getLookAngle(), session.distance):knownSelection;
        session.lastSelection=selection;
        BlockPos targetCell = selection.cell();
        GasAnalyzerSample target = null;
        GasAnalyzerSnapshot.Status status;
        if (selection.status() == GasAnalyzerCellSelection.Status.BLOCKED) status = GasAnalyzerSnapshot.Status.BLOCKED;
        else if (selection.status() == GasAnalyzerCellSelection.Status.UNLOADED) status = GasAnalyzerSnapshot.Status.UNAVAILABLE;
        else {
            var reading = level.hasChunkAt(targetCell) ? AtmosphereService.INSTANCE.readAtmosphere(level, targetCell) : java.util.Optional.<com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading>empty();
            target = reading.map(value -> GasAnalyzerSample.from(value, profile)).orElse(null);
            status = target == null ? GasAnalyzerSnapshot.Status.UNAVAILABLE : GasAnalyzerSnapshot.Status.READY;
        }
        long interval = profile.updateIntervalTicks();
        session.nextUpdate = level.getGameTime() + interval;
        session.serverTick = level.getGameTime();
        send(new GasAnalyzerSnapshot(session.id, session.generation, session.sequence++, session.lastControl,
                session.serverTick, session.epoch, session.body, session.sourceToken, session.dimension,
                localPos, targetCell, session.distance, status, local, target, false), session.actor);
    }
    static boolean sameTargetIdentity(GasAnalyzerPipeSamples.Readout a,GasAnalyzerPipeSamples.Readout b){
        if(a==null||b==null||a.source()!=b.source()||!a.bindingIdentity().equals(b.bindingIdentity())
                ||a.entries().size()!=b.entries().size())return false;
        var identity=new java.util.HashMap<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey,GasAnalyzerPipeSamples.Stamp>();
        for(var e:a.entries())identity.put(e.stamp().node(),e.stamp());
        for(var e:b.entries()){var x=identity.get(e.stamp().node());var y=e.stamp();
            if(x==null||!x.incarnation().equals(y.incarnation())||Double.compare(x.volumeCubicMeters(),y.volumeCubicMeters())!=0)return false;}
        return true;
    }

    private static void close(Session session, GasAnalyzerSnapshot.Status reason) {
        if (!SESSIONS.remove(session.actor, session)) return;
        var authority = HandActorAuthority.resolve(session.actor).orElse(null);
        BlockPos cell = authority != null && authority.body().getUUID().equals(session.body)
                ? BlockPos.containing(authority.body().getEyePosition()) : BlockPos.ZERO;
        send(new GasAnalyzerSnapshot(session.id, session.generation, session.sequence++, session.lastControl,
                session.serverTick, session.epoch, session.body, session.sourceToken, session.dimension,
                cell, null, session.distance, reason, null, null, true), session.actor);
    }
    private static void send(GasAnalyzerSnapshot snapshot, ServerPlayer actor) {
        LAST_SNAPSHOTS.put(actor, snapshot);
        if (actor.getServer() != null && actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor)
            PacketDistributor.sendToPlayer(actor, snapshot);
    }
    /** Server-side last emitted state, also used by focused registered-player fixtures. */
    public static GasAnalyzerSnapshot latestSnapshot(ServerPlayer actor) { return LAST_SNAPSHOTS.get(actor); }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { SESSIONS.remove(player); LAST_SNAPSHOTS.remove(player); }
    }
}
