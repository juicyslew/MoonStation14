package com.juicyslew.moonstation14.ms14.singularity.pa.control;

import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.singularity.pa.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import java.util.*;

/** Small PA-specific session authority. Machine configuration remains on its existing block entity. */
public final class PaControlSessionService {
    private record Session(UUID id, ServerPlayer player, UUID machine, BlockPos position, UUID body, long epoch,
                           net.minecraft.resources.ResourceLocation dimension, net.minecraft.resources.ResourceLocation profile,
                           long lastRequest, long lastPublished) { }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private PaControlSessionService() { }

    public static Optional<PaControlSnapshot> open(ServerPlayer player, ParticleAcceleratorPartBlockEntity control,
            HandActorAuthority.Snapshot authority, long correlation) {
        if(!validActor(player,authority)||!validTarget(control,authority.body().level())
                || !reachable(authority.body(),control) || !apcPowered(control)) return Optional.empty();
        SESSIONS.values().removeIf(old -> { if(old.player()==player){PaControlNetworking.sendOpenSnapshot(player,closed(old,old.lastRequest()));return true;}return false; });
        UUID id=UUID.randomUUID();Session s=new Session(id,player,control.placementIdentity(),control.getBlockPos().immutable(),authority.body().getUUID(),authority.epoch(),
                player.level().dimension().location(),control.profileId(),-1,player.getServer().getTickCount());SESSIONS.put(id,s);
        return Optional.of(snapshot(s,control,correlation,true,false));
    }
    public static Optional<PaControlSnapshot> handle(ServerPlayer player,PaControlRequest request) {
        if(player==null||request==null||request.sessionId()==null||request.operation()==null
                ||!(player.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return Optional.empty();
        Session s=SESSIONS.get(request.sessionId());if(s==null||s.player()!=player)return Optional.empty();
        var authority=HandActorAuthority.resolve(player).orElse(null);
        if(authority==null){SESSIONS.remove(s.id());return Optional.of(closed(s,request.requestId()));}
        var control=level.hasChunkAt(s.position())&&level.getBlockEntity(s.position()) instanceof ParticleAcceleratorPartBlockEntity pa?pa:null;
        var policyPin=pin(s);
        var policyFacts=new PaControlSessionPolicy.Facts(player,request.sessionId(),authority.body().getUUID(),authority.epoch(),
                level.dimension().location(),control==null?null:control.placementIdentity(),s.position(),control==null?null:control.profileId(),
                validActor(player,authority)&&s.dimension().equals(level.dimension().location()),control!=null&&validTarget(control,level),
                control!=null&&reachable(authority.body(),control),control!=null&&apcPowered(control));
        if(PaControlSessionPolicy.match(policyPin,policyFacts)==PaControlSessionPolicy.Match.INVALID){
            SESSIONS.remove(s.id());return Optional.of(closed(s,request.requestId()));}
        if(request.operation()==PaControlRequest.Operation.CLOSE){SESSIONS.remove(s.id());return Optional.of(closed(s,request.requestId()));}
        var admission=PaControlSessionPolicy.actionAdmission(s.lastRequest(),request.requestId(),request.revision(),control.operatorRevision());
        if(admission.decision()==PaControlSessionPolicy.ActionDecision.REPLAY)return Optional.of(snapshot(s,control,request.requestId(),false,false));
        s=new Session(s.id(),s.player(),s.machine(),s.position(),s.body(),s.epoch(),s.dimension(),s.profile(),admission.lastRequestId(),s.lastPublished());SESSIONS.put(s.id(),s);
        boolean accepted=admission.decision()==PaControlSessionPolicy.ActionDecision.ACCEPTED;
        if(accepted)switch(request.operation()){
            case ENABLE -> {control.setEnabled(request.enabled());accepted=control.enabled()==request.enabled();}
             case STRENGTH -> {var value=Arrays.stream(ParticleAcceleratorStrength.values()).filter(v->v.level()==request.strength()).findFirst().orElse(null);if(value==null)accepted=false;
                 else {control.setStrength(value);accepted=control.strength()==value;}}
            case RESCAN -> control.requestAssemblyRescan();
            case CLOSE -> { }
        }
        return Optional.of(snapshot(s,control,request.requestId(),accepted,false));
    }
    public static void close(ServerPlayer player,UUID id){Session s=SESSIONS.get(id);if(s!=null&&s.player()==player){SESSIONS.remove(id);PaControlNetworking.sendOpenSnapshot(player,closed(s,s.lastRequest()));}}
    public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        for(Session s:new ArrayList<>(SESSIONS.values())){
            ServerPlayer p=s.player(); if(p.getServer()!=event.getServer())continue;
            var authority=HandActorAuthority.resolve(p).orElse(null);
            if(authority==null||!authority.body().getUUID().equals(s.body())||authority.epoch()!=s.epoch()||!validActor(p,authority)||!s.dimension().equals(p.level().dimension().location())){SESSIONS.remove(s.id());PaControlNetworking.sendOpenSnapshot(p,closed(s,s.lastRequest()));continue;}
            if(event.getServer().getTickCount()-s.lastPublished()<5)continue;
            if(!(p.level() instanceof ServerLevel level)||!level.hasChunkAt(s.position())||!(level.getBlockEntity(s.position()) instanceof ParticleAcceleratorPartBlockEntity b)||!s.machine().equals(b.placementIdentity())||!s.profile().equals(b.profileId())||!validTarget(b,level)||!reachable(authority.body(),b)||!apcPowered(b)){SESSIONS.remove(s.id());PaControlNetworking.sendOpenSnapshot(p,closed(s,s.lastRequest()));continue;}
            var snap=snapshot(s,b,s.lastRequest(),true,false); PaControlNetworking.sendOpenSnapshot(p,snap);
            SESSIONS.put(s.id(),new Session(s.id(),p,s.machine(),s.position(),s.body(),s.epoch(),s.dimension(),s.profile(),s.lastRequest(),event.getServer().getTickCount()));
        }
    }
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)SESSIONS.values().removeIf(s->{if(s.player()==p){PaControlNetworking.sendOpenSnapshot(p,closed(s,s.lastRequest()));return true;}return false;});}
    public static void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e){SESSIONS.clear();}
    private static boolean validActor(ServerPlayer p,HandActorAuthority.Snapshot a){return p!=null&&a!=null&&a.actor()==p&&p.connection!=null
            &&p.getServer()!=null&&p.getServer().getPlayerList().getPlayer(p.getUUID())==p&&p.getServer().isSameThread()
            &&HandActorAuthority.revalidate(p,a)&&CharacterControlSystem.canAct(a.body());}
    private static boolean validTarget(ParticleAcceleratorPartBlockEntity b,net.minecraft.world.level.Level l){return b!=null&&b.getLevel()==l&&!b.isRemoved()
            &&l instanceof ServerLevel sl&&sl.hasChunkAt(b.getBlockPos())&&sl.getBlockEntity(b.getBlockPos())==b&&b.role().equals("control");}
    private static boolean reachable(net.minecraft.world.entity.LivingEntity body,ParticleAcceleratorPartBlockEntity b){return body.level()==b.getLevel()&&b.getLevel() instanceof ServerLevel sl
            &&com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.withinInteractionReach(body,net.minecraft.world.phys.AABB.ofSize(net.minecraft.world.phys.Vec3.atCenterOf(b.getBlockPos()),1,1,1))
            &&sl.hasChunkAt(b.getBlockPos());}
    private static boolean apcPowered(ParticleAcceleratorPartBlockEntity b){
        var demand=b.apcPowerDemandWatts();if(demand==null||!(b.getLevel() instanceof ServerLevel level))return false;
        var meter=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.apcPowerMeter(level,b);
        return meter.known()&&meter.deliveredWatts()>=demand*0.999;
    }
    private static PaControlSessionPolicy.Pin pin(Session s){return new PaControlSessionPolicy.Pin(s.id(),s.player(),s.machine(),s.position(),s.body(),s.epoch(),s.dimension(),s.profile());}
    private static PaControlSnapshot snapshot(Session s,ParticleAcceleratorPartBlockEntity b,long request,boolean accepted,boolean closed){
        var profile=ModParticleAccelerators.catalog(b.getLevel()).get(b.profileId());
        if(profile==null)return closed(s,request);
        boolean[] present=new boolean[profile.parts().size()];var roles=new ArrayList<String>();int i=0;
        for(var p:profile.parts().values())roles.add(p.role());
        var physical=ParticleAcceleratorAssembly.presence(b.getLevel(),b.getBlockPos(),b.orientation(),profile);
        for(Boolean found:physical)present[i++]=found;
        double draw=profile.strength(b.enabled()?b.strength():ParticleAcceleratorStrength.STANDBY).powerBoxMv();
        return new PaControlSnapshot(s.id(),request,b.placementIdentity(),s.body(),s.epoch(),s.dimension(),b.getBlockPos().getX(),b.getBlockPos().getY(),b.getBlockPos().getZ(),
                b.profileId(),b.operatorRevision(),profile.maximumStrength().level(),b.strength().level(),b.enabled(),b.assemblyValid(),apcPowered(b),draw,b.receivedWatts(),roles,present,accepted,closed);
    }
    private static PaControlSnapshot closed(Session s,long request){return new PaControlSnapshot(s.id(),request,s.machine(),s.body(),s.epoch(),s.dimension(),0,0,0,
            ModParticleAccelerators.TYPE.typeId(),-1,0,0,false,false,false,0,0,List.of(),new boolean[0],false,true);}
}
