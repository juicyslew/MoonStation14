package com.juicyslew.moonstation14.ms14.atmos.machine.ui;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.machine.*;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

/** Authenticated entry and per-exact-menu settings capability. */
public final class AtmosMachineMenuService {
    /** Server-owned capabilities; deliberately independent of the currently displayed container. */
    private static final java.util.Map<UUID, AtmosMachineMenu> SESSIONS = new java.util.HashMap<>();
    private AtmosMachineMenuService(){}
    /** Read the live server-owned session for this actor and target without consulting its active container. */
    public static AtmosMachineMenu session(ServerPlayer actor, BlockPos pos) {
        if (actor == null || pos == null) return null;
        for (var menu : java.util.List.copyOf(SESSIONS.values()))
            if (menu.authority() != null && menu.authority().actor() == actor && menu.pos().equals(pos)
                    && menu.stillValid(actor)) return menu;
        return null;
    }
    public static boolean open(ServerPlayer actor,BodyBlockUseRequest request,BodyHandRequestService.BlockUseAdmission admission,MenuType<?> type){
        var target=admittedTarget(actor,request,admission);
        if(target==null||type==null||!supportsMenu(target.machine().resolvedProfile()))return false;
        var auth=target.authority();var level=target.level();var be=target.machine();BlockPos pos=target.pos();
        UUID session=UUID.randomUUID();String profile=be.profileIdentity();UUID identity=be.placementIdentity();
        actor.openMenu(new SimpleMenuProvider((id,inv,p)->new AtmosMachineMenu(type,id,pos,session,profile,level,be,auth,request,identity),Component.literal("Atmospheric Machine")),b->{b.writeBlockPos(pos);b.writeUUID(session);b.writeUtf(profile,64);});
          if (actor.containerMenu instanceof AtmosMachineMenu m&&session.equals(m.session())) {
              SESSIONS.put(session, m);
              AtmosMachineNetworking.send(actor, new AtmosMachineSessionState(session, true, ""));
              AtmosMachineNetworking.send(actor, snapshot(m, -1, true));
             return true;
         }
         return false;
    }
    /** Admit and create a workspace capability without opening/replacing a Minecraft container. */
    public static UUID openWorkspace(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission) {
        var target=admittedTarget(actor,request,admission);
        if(target==null||!supportsMenu(target.machine().resolvedProfile()))return null;
        var m=new AtmosMachineMenu(com.juicyslew.moonstation14.block.ModMenus.ATMOS_MACHINE.get(),0,
                target.pos(),UUID.randomUUID(),target.machine().profileIdentity(),target.level(),target.machine(),
                target.authority(),request,target.machine().placementIdentity());
        SESSIONS.put(m.session(),m);
        AtmosMachineNetworking.send(actor,new AtmosMachineSessionState(m.session(),true,""));
        AtmosMachineNetworking.send(actor,snapshot(m,-1,true));
        return m.session();
    }
    private record Target(HandActorAuthority.Snapshot authority,ServerLevel level,BlockPos pos,AtmosMachineBlockEntity machine){}
    private static Target admittedTarget(ServerPlayer actor,BodyBlockUseRequest request,BodyHandRequestService.BlockUseAdmission admission){
        if(actor==null||request==null||admission==null)return null;
        var resolved=HandActorAuthority.resolve(actor);if(resolved.isEmpty()||!resolved.get().equals(admission.authority()))return null;
        var auth=resolved.get();if(!HandActorAuthority.revalidate(actor,auth)||!(auth.body().level() instanceof ServerLevel level)||admission.hands()==null||admission.hands()!=auth.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())||!admission.hands().compatible(auth.body())||admission.hands().revision()!=request.expectedRevision())return null;
        var hit=BodyBlockUseTargetProbe.probe(level,auth.body(),request.target(),request.face());if(hit==null||!hit.equals(admission.hit()))return null;
        BlockPos pos=hit.pos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||!chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)||!(chunk.getBlockEntity(pos) instanceof AtmosMachineBlockEntity be)||be.getLevel()!=level||be.isRemoved()||be.resolvedProfile()==null||!be.settingsValid()||be.settingsSnapshot()==null||be.placementIdentity()==null)return null;
        return new Target(auth,level,pos,be);
    }
    public static boolean activateValve(ServerPlayer actor,BodyBlockUseRequest request,BodyHandRequestService.BlockUseAdmission admission){
        var target=admittedTarget(actor,request,admission);if(target==null)return false;
        var be=target.machine();if(be.resolvedProfile().valve()==null)return false;
        var current=be.settingsSnapshot();if(current==null||current.valveOpen().isEmpty())return false;
        boolean changed = HandActorAuthority.revalidate(actor,target.authority())&&be.applySettings(current.revision(),AtmosMachineSettings.Operation.SET_VALVE_OPEN,new AtmosMachineSettings.BooleanValue(!current.valveOpen().get()));
        if (changed && be.settingsSnapshot() != null && be.settingsSnapshot().valveOpen().orElse(false) != current.valveOpen().get()
                && be.openInternalGasEdges().size() == (be.settingsSnapshot().valveOpen().orElse(false) ? 1 : 0))
            playValveActivation(target.level(), be, target.pos());
        return changed;
    }
    private static void playValveActivation(ServerLevel level, AtmosMachineBlockEntity be, BlockPos pos) {
        var cue = be.resolvedProfile().activationAudio().orElse(null);
        if (cue == null) return;
        var sound = activationSoundId(cue);
        if (sound == null) return;
        float pitch = 1f + (level.getRandom().nextFloat() * 2f - 1f) * cue.pitchVariation();
        com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter.play(level, sound,
                net.minecraft.sounds.SoundSource.BLOCKS, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                cue.volume(), pitch);
    }
    /** Production cue resolution deliberately permits any registered event declared by the resolved profile. */
    public static net.minecraft.resources.ResourceLocation activationSoundId(AtmosMachineProfile.ActivationAudio cue) {
        if (cue == null) return null;
        try { return net.minecraft.resources.ResourceLocation.parse(cue.sound()); }
        catch (RuntimeException invalid) { return null; }
    }
    public static boolean supportsMenu(AtmosMachineProfile profile){return profile!=null&&(profile.pump()!=null||profile.mixer()!=null||profile.filter()!=null||profile.thermostat()!=null);}
    private static boolean eligible(AtmosMachineBlockEntity be){return supportsMenu(be.resolvedProfile());}
    public static AtmosMachineControlSnapshot handle(ServerPlayer actor,AtmosMachineControlRequest r){
        AtmosMachineMenu m = r == null ? null : SESSIONS.get(r.session());
        if(m==null||!m.stillValid(actor)){ if(m!=null) invalidate(m,"invalid"); return denied(r); }
        if(!admit(m,r))return denied(r);
        m.lastRequest(r.requestId());var be=m.machine();boolean accepted=false;
        if(r.operation()!=null&&be.settingsRevision()==r.expectedRevision())try{
            var value=switch(r.operation()){
                case SET_ENABLED->new AtmosMachineSettings.BooleanValue(r.desired());
                case SET_VALVE_OPEN->new AtmosMachineSettings.BooleanValue(r.desired());
                case SET_TARGET_PRESSURE_KPA,SET_TRANSFER_RATE_M3_PER_SECOND,SET_MIXER_FRACTION_A,SET_THERMOSTAT_SETPOINT_K->new AtmosMachineSettings.NumericValue(r.numericValue());
                case SET_FILTER_GAS->new AtmosMachineSettings.GasValue(GasType.values()[r.gasIndex()]);
                case CLEAR_FILTER_GAS->AtmosMachineSettings.ClearGasValue.INSTANCE;
            };accepted=eligible(be)&&m.stillValid(actor)&&be.applySettings(r.expectedRevision(),r.operation(),value);
        }catch(RuntimeException ignored){}
        return snapshot(m,r.requestId(),accepted);
    }
    public static void close(ServerPlayer actor, AtmosMachineSessionClose request) {
        if(actor==null||request==null)return;
        AtmosMachineMenu owner=SESSIONS.get(request.session());
        if(owner!=null&&owner.authority()!=null&&owner.authority().actor()==actor) invalidate(owner,"dismissed");
    }
    private static void invalidate(AtmosMachineMenu menu,String reason) {
        if(SESSIONS.remove(menu.session(),menu)) AtmosMachineNetworking.send(menu.authority().actor(),new AtmosMachineSessionState(menu.session(),false,reason));
    }
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) for(var m:java.util.List.copyOf(SESSIONS.values())) if(m.authority().actor()==player) invalidate(m,"logout");
    }
    public static void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { SESSIONS.clear(); }
    public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for(var m:java.util.List.copyOf(SESSIONS.values())) if(m.serverLevel().getServer()==event.getServer()&&!m.stillValid(m.authority().actor())) invalidate(m,"invalid");
    }
    public static boolean admitRequest(int menuId,UUID session,long watermark,AtmosMachineControlRequest r,long currentRevision){return r!=null&&r.containerId()==menuId&&session!=null&&session.equals(r.session())&&r.requestId()>watermark;}
    private static boolean admit(AtmosMachineMenu m,AtmosMachineControlRequest r){return r!=null&&m.session().equals(r.session())&&r.requestId()>m.lastRequest();}
    public static AtmosMachineControlSnapshot snapshot(AtmosMachineMenu m,long ack,boolean accepted){var be=m.machine();var s=be.settingsSnapshot();return new AtmosMachineControlSnapshot(m.menuId(),m.session(),ack,accepted,s,AtmosMachineControlSnapshot.Controls.from(be.profileIdentity(),be.resolvedProfile()),be.apcAllocatedWatts(),be.apcAllocationKnown());}
    private static AtmosMachineControlSnapshot denied(AtmosMachineControlRequest r){var controls=new AtmosMachineControlSnapshot.Controls("invalid",AtmosMachineProfile.Kind.PRESSURE_PUMP,false,false,false,false,false,false,0,0,0,0);return new AtmosMachineControlSnapshot(r.containerId(),r.session(),r.requestId(),false,new AtmosMachineSettings.Values(0,java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.empty()),controls,0,false);}
    static boolean persistentValidity(Player player,AtmosMachineMenu m){if(!(player instanceof ServerPlayer actor)||m.machine()==null||m.authority()==null||actor!=m.authority().actor()||actor.level()!=m.serverLevel()||!HandActorAuthority.revalidate(actor,m.authority()))return false;var be=m.machine();var level=m.serverLevel();var p=m.pos();var c=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);if(c==null||!c.getFullStatus().isOrAfter(FullChunkStatus.FULL)||c.getBlockEntity(p)!=be||be.isRemoved()||!be.profileIdentity().equals(m.profileId())||!java.util.Objects.equals(be.placementIdentity(),m.placementIdentity())||be.resolvedProfile()==null||!be.settingsValid()||be.settingsSnapshot()==null||!eligible(be)||!level.getWorldBorder().isWithinBounds(p)||level.isOutsideBuildHeight(p))return false;var shape=c.getBlockState(p).getShape(c,p);if(shape.isEmpty())return false;var b=shape.bounds();var point=new net.minecraft.world.phys.Vec3(p.getX()+(b.minX+b.maxX)*.5,p.getY()+(b.minY+b.maxY)*.5,p.getZ()+(b.minZ+b.maxZ)*.5);return com.juicyslew.moonstation14.ms14.hands.network.BodyMenuReach.check(level,m.authority().body(),p,point)==com.juicyslew.moonstation14.ms14.hands.network.BodyMenuReach.Result.VALID;}
}
