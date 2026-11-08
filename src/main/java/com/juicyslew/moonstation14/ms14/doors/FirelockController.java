package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.atmos.monitor.*;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.devicenet.*;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload;
import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;

/** Owner-independent firelock mechanics and state. World operations are supplied by a host. */
public final class FirelockController {
    private UUID identity = UUID.randomUUID();
    private FirelockState state;
    private boolean initialized;
    private long crushReopenAt;
    private final Set<UUID> crushVictims = new HashSet<>();
    private DeviceEndpoint networkEndpoint;
    private final AtmosAlarmReceiverState alarmReports = new AtmosAlarmReceiverState();
    private long transitionStartedAt;
    private long pressureWarningStartedAt;
    private long temperatureWarningStartedAt;
    public record Presentation(FirelockState state, boolean authoritativeLatch, long motionStartedAt,
                               long transitionAt, long pressureWarningStartedAt, long temperatureWarningStartedAt,
                               net.minecraft.core.Direction facing) {
        public Presentation { java.util.Objects.requireNonNull(state);java.util.Objects.requireNonNull(facing);if(authoritativeLatch!=(state.phase()==AirlockState.DoorPhase.CLOSED))throw new IllegalArgumentException("firelock latch must reflect authoritative CLOSED phase"); }
    }
    public Presentation presentation(FirelockHost host) { return presentation(host.facing()); }
    public Presentation presentation(net.minecraft.core.Direction facing) { return state == null ? null : new Presentation(state, authoritativeLatch(state), transitionStartedAt, state.transitionAt(), pressureWarningStartedAt, temperatureWarningStartedAt,facing); }
    public static boolean authoritativeLatch(FirelockState state) { return state != null && state.phase() == AirlockState.DoorPhase.CLOSED; }

    public FirelockController() {}
    public UUID identity() { return identity; }
    public FirelockState state() { return state; }
    public void state(FirelockState value) { state = value; }
    public long motionStartedAt(){return transitionStartedAt;}
    public boolean initialized() { return initialized; }
    public long crushReopenAt() { return crushReopenAt; }
    public long nextDeadline() { return state == null ? crushReopenAt : state.transitionAt() > 0 && crushReopenAt > 0 ? Math.min(state.transitionAt(), crushReopenAt) : Math.max(state.transitionAt(), crushReopenAt); }
    public void prepareLoad() { alarmReports.markPowerUncertain(); }
    public void clearClientState() { state = null; }
    public void onLoaded() { alarmReports.markPowerUncertain(); }
    public void onUnloaded() { networkEndpoint = null; }
    public void onRemoved() { networkEndpoint = null; }
    public boolean isCrushVictim(UUID id) { return crushVictims.contains(id); }
    public boolean removeCrushVictim(UUID id) { return crushVictims.remove(id); }

    public void initialize(FirelockHost host) {
        if (initialized || !(host.level() instanceof ServerLevel) || host.prototype() == null) return;
        state = FirelockState.initial(host.prototype()); initialized = true; host.projectPassability(state.passable());
    }
    /** Build an unattached controller candidate without projecting any world state. */
    public boolean prepareInstallation(FirelockData prototype){if(initialized||prototype==null)return false;prototype.validate();state=FirelockState.initial(prototype);initialized=true;return true;}

    public void tick(FirelockHost host, ServerLevel server) {
        if (!initialized) return;
        processAlarm(host);
        FirelockData data = host.prototype(); if (data == null) return;
        long now = server.getGameTime();
        if (state.phase() == AirlockState.DoorPhase.CLOSED || state.phase() == AirlockState.DoorPhase.DENYING) {
            FirelockState observed = state.observe(data, sample(server, host.root().relative(host.facing())), sample(server, host.root().relative(host.facing().getOpposite())));
            if (observed != state) { recordWarnings(observed, now); state = observed; host.changedAndSync(); }
        }
        if (crushReopenAt > 0 && now >= crushReopenAt && state.phase() == AirlockState.DoorPhase.CLOSED) {
            FirelockState reopened = state.reopenAfterCrush(data, now);
            if (reopened != state) { transitionStartedAt=now; state = reopened; crushReopenAt = 0; host.changedAndSync(); }
        }
        FirelockState next = state.tick(data, now);
        if (next != state) {
            boolean enteringCollidable = state.passable() && !next.passable();
            if (enteringCollidable && host.obstructed(server)) { transitionAutomaticallyTo(next.partialCloseObstructed(now, data),now); host.changedAndSync(); }
            else { recordWarnings(next,now); transitionAutomaticallyTo(next,now); if (enteringCollidable && crush(host, server, data)) crushReopenAt = now + (long)Math.ceil(data.door().crushStunSeconds()*20); if (state.phase() == AirlockState.DoorPhase.CLOSED && crushReopenAt == 0) crushVictims.clear(); if (state.passable()) crushVictims.clear(); host.changedAndSync(); }
        }
        if (state.transitionAt() > now) host.scheduleAt(state.transitionAt());
        else if (crushReopenAt > now) host.scheduleAt(crushReopenAt);
        else host.scheduleAt(now + Math.max(1, (long)Math.ceil(data.retrySeconds()*20)));
    }
    private void recordWarnings(FirelockState next,long now){if(next.pressureWarning()!=state.pressureWarning())pressureWarningStartedAt=next.pressureWarning()?now:0;if(next.temperatureWarning()!=state.temperatureWarning())temperatureWarningStartedAt=next.temperatureWarning()?now:0;}
    private static boolean continuesMotion(AirlockState.DoorPhase from,AirlockState.DoorPhase to){return from==AirlockState.DoorPhase.OPENING&&to==AirlockState.DoorPhase.OPENING_PASSABLE||from==AirlockState.DoorPhase.CLOSING&&to==AirlockState.DoorPhase.CLOSING_PASSABLE;}
    void transitionAutomaticallyTo(FirelockState next,long now){if(state!=null&&next.phase()!=state.phase()&&!continuesMotion(state.phase(),next.phase()))transitionStartedAt=now;state=next;}

    public static FirelockState.Environment sampleEnvironment(ServerLevel server, BlockPos pos) {
        if (!server.hasChunkAt(pos) || !AtmosphereTopology.isPassable(server, pos)) return FirelockState.Environment.unavailable();
        var gas = AtmosphereService.INSTANCE.sample(server, pos).orElse(null);
        return gas == null ? FirelockState.Environment.unavailable() : new FirelockState.Environment(FirelockState.Observation.AVAILABLE, gas.pressureKpa(), gas.temperatureKelvin());
    }

    private FirelockState.Environment sample(ServerLevel server, BlockPos pos) { return sampleEnvironment(server, pos); }

    private boolean crush(FirelockHost host, ServerLevel server, FirelockData data) {
        BlockPos p=host.root(); AABB bounds=new AABB(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY()+2,p.getZ()+1); boolean applied=false;
        for(LivingEntity actor:DoorCloseAdmission.obstructingBodies(server,bounds,host::pairCollides)) {
            if(!crushVictims.add(actor.getUUID()))continue; applied=true;
            com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(actor,Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT,(float)data.door().crushDamageBlunt()),1f,false);
            com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.applyParalyze(actor,(float)(data.door().crushStunSeconds()+data.door().openTimeOneSeconds()));
        } return applied;
    }

    public boolean activate(FirelockHost host, boolean trustedUserOverride) {
        if (!(host.level() instanceof ServerLevel server)||!server.getServer().isSameThread()||!initialized||host.prototype()==null)return false;
        var meter=host.powerObservation();
        if (!meter.known()) {
            alarmReports.markPowerUncertain();
            host.play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_DENY.get());
            return false;
        }
        host.setPaidPower(meter.deliveredWatts()==host.prototype().apcPowerReceiver().requestedWatts());
        FirelockState next=state.open(host.prototype(),server.getGameTime(),trustedUserOverride);
        if(next==state){host.play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_DENY.get());return false;}
        state=next;transitionStartedAt=server.getGameTime();host.play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_OPEN.get());host.changedAndSync();return true;
    }
    public boolean toggle(FirelockHost host,boolean trustedUserOverride){if(!(host.level() instanceof ServerLevel server)||!server.getServer().isSameThread()||!initialized)return false;return state.phase()==AirlockState.DoorPhase.OPEN?close(host,server,false):activate(host,trustedUserOverride);}
    public boolean completePry(FirelockHost host,boolean strongTool,boolean poweredTool){
        if(!(host.level() instanceof ServerLevel server)||!server.getServer().isSameThread()||!initialized||host.prototype()==null)return false;
        FirelockData data=host.prototype();long now=server.getGameTime();var meter=host.powerObservation();if(!meter.known())return false;
        host.setPaidPower(meter.deliveredWatts()==data.apcPowerReceiver().requestedWatts());if(state.pryVeto(poweredTool,strongTool)==FirelockState.Pry.VETOED)return false;
        if(state.phase()==AirlockState.DoorPhase.CLOSED){FirelockState next=state.successfulPry(data,now,true,false);if(next==state)return false;state=next;transitionStartedAt=now;host.play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_OPEN.get());host.changedAndSync();return true;}
        if(state.phase()!=AirlockState.DoorPhase.OPEN||now<state.pryCooldownUntil()||host.obstructed(server))return false;
        FirelockState next=state.successfulPry(data,now,true,false);if(next==state)return false;state=next;transitionStartedAt=now;host.play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_CLOSE.get());host.changedAndSync();return true;
    }
    public boolean emergencyPressureStop(FirelockHost host){if(!(host.level() instanceof ServerLevel server)||!server.getServer().isSameThread()||!initialized)return false;return close(host,server,true);}
    private boolean close(FirelockHost host,ServerLevel server,boolean emergency){FirelockData d=host.prototype();if(d==null)return false;long now=server.getGameTime();if(state.phase()!=AirlockState.DoorPhase.OPEN||now<state.pryCooldownUntil()||host.obstructed(server))return false;FirelockState next=emergency?state.emergencyPressureStop(d,now,false).state():new FirelockState(AirlockState.DoorPhase.CLOSING,state.powered(),state.locked(),state.pressureWarning(),state.temperatureWarning(),now+(long)Math.ceil(d.door().closeTimeOneSeconds()*20),state.pryCooldownUntil(),state.revision()+1);if(next==state)return false;state=next;transitionStartedAt=now;host.play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_CLOSE.get());host.changedAndSync();return true;}

    public boolean setPaidPower(FirelockHost host,boolean powered){if(!(host.level() instanceof ServerLevel server)||!server.getServer().isSameThread()||!initialized)return false;FirelockState next=state.withPower(powered);if(next==state){if(powered&&alarmReports.synchronizationPending()&&requestRelaySync(server))alarmReports.markSyncAccepted();return false;}state=next;if(!powered)alarmReports.actualPowerDown();else if(requestRelaySync(server))alarmReports.markSyncAccepted();host.changedAndSync();return true;}

    /** Freshness boundary for runtime wiring; stale observations never erase reports or actuate. */
    public void observeNetworkPowerFreshness(FirelockHost host, boolean freshFull) {
        if (!freshFull) { alarmReports.markPowerUncertain(); return; }
        if (alarmReports.synchronizationPending() && host.level() instanceof ServerLevel server
                && requestRelaySync(server)) alarmReports.markSyncAccepted();
    }

    public DeviceEndpoint endpoint(FirelockHost host,ServerLevel server){
        if(networkEndpoint==null)networkEndpoint=new DeviceEndpoint(){
            private DeviceComposition resolved(){FirelockData d=host.prototype();if(d==null)return DeviceComposition.empty();var c=d.deviceComponents().resolve(com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager());var id=new DeviceIdentity(identity);return DeviceAddressPolicy.retained(c,DeviceLinkSavedData.existing(server).flatMap(saved->saved.defaultAddress(id)));}
            public DeviceIdentity identity(){return new DeviceIdentity(FirelockController.this.identity);}
            public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension(){return server.dimension();}
            public BlockPos position(){return host.root();}
            public boolean acceptsBlockTarget(BlockPos target){return target.equals(host.root())||target.equals(host.root().above());}
            public int targetPriority(BlockPos target){return host.owner() instanceof AirlockBlockEntity&&target.equals(host.root().above())?10:0;}
            public String displayName(){return host.displayName();}
            public DeviceComposition composition(){return resolved();}
            public boolean available(){return host.live(server)&&initialized&&server.getChunkSource().getChunkNow(host.root().getX()>>4,host.root().getZ()>>4)!=null&&host.prototype()!=null;}
            public boolean powered(){FirelockData d=host.prototype();var m=host.powerObservation();return d!=null&&m.known()&&m.deliveredWatts()==d.apcPowerReceiver().requestedWatts();}
            public void receive(DevicePayload payload,String address){if(!available())return;if(payload instanceof AtmosAlarmPayload.Relay r)acceptRelay(host,r,address);else if(payload instanceof AtmosAlarmPayload.RelayRemove r)acceptRemove(host,r,address);else if(payload instanceof AtmosAlarmPayload.RelayReset r)acceptReset(host,r,address);}
        };return networkEndpoint;
    }
    private boolean acceptSource(FirelockHost host,UUID id,String address){if(!(host.level() instanceof ServerLevel server)||id==null||address==null||address.isBlank())return false;var e=DeviceNetworkRuntime.forLevel(server).transport().lookup(new DeviceIdentity(id));if(e==null||!e.available()||!e.composition().network().map(n->n.address().equals(address)).orElse(false))return false;var c=server.getChunkSource().getChunkNow(e.position().getX()>>4,e.position().getZ()>>4);return c!=null&&c.getBlockEntity(e.position()) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity a&&id.equals(a.controllerId())&&a.prototype()!=null&&a.prototype().atmosAlarmable()!=null;}
    private void acceptRelay(FirelockHost h,AtmosAlarmPayload.Relay r,String address){if(!state.powered()||h.prototype()==null||h.prototype().atmosAlarmableData().ignoreAlarms()||!acceptSource(h,r.source(),address))return;ServerLevel s=(ServerLevel)h.level();var e=DeviceNetworkRuntime.forLevel(s).transport().lookup(new DeviceIdentity(r.source()));var c=s.getChunkSource().getChunkNow(e.position().getX()>>4,e.position().getZ()>>4);var a=(com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity)c.getBlockEntity(e.position());AtmosAlarmSeverity before=alarmReports.severity();if(!AtmosAlarmReportPolicy.applyRelay(alarmReports,r.source(),h.prototype().atmosAlarmableData().syncWith(),a.prototype().atmosAlarmable().sourceTags(),r))return;if(before!=alarmReports.severity()){AlarmTransitionAudio.play(s,h.root(),before,alarmReports.severity(),state.powered());processAlarm(h);publishSnapshot(s,h);}}
    private void acceptRemove(FirelockHost h,AtmosAlarmPayload.RelayRemove r,String address){if(!state.powered()||h.prototype()==null||h.prototype().atmosAlarmableData().ignoreAlarms()||!acceptSource(h,r.source(),address))return;ServerLevel s=(ServerLevel)h.level();var e=DeviceNetworkRuntime.forLevel(s).transport().lookup(new DeviceIdentity(r.source()));var c=s.getChunkSource().getChunkNow(e.position().getX()>>4,e.position().getZ()>>4);var a=(com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity)c.getBlockEntity(e.position());AtmosAlarmSeverity before=alarmReports.severity();if(!AtmosAlarmReportPolicy.applyRelay(alarmReports,r.source(),h.prototype().atmosAlarmableData().syncWith(),a.prototype().atmosAlarmable().sourceTags(),r))return;if(before!=alarmReports.severity())processAlarm(h);publishRemove(s,h,r.removedSource());}
    private void acceptReset(FirelockHost h,AtmosAlarmPayload.RelayReset r,String address){if(!acceptSource(h,r.source(),address)||h.prototype()==null||h.prototype().atmosAlarmableData().ignoreAlarms())return;ServerLevel s=(ServerLevel)h.level();var e=DeviceNetworkRuntime.forLevel(s).transport().lookup(new DeviceIdentity(r.source()));var c=s.getChunkSource().getChunkNow(e.position().getX()>>4,e.position().getZ()>>4);var a=(com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity)c.getBlockEntity(e.position());AtmosAlarmSeverity before=alarmReports.severity();if(!AtmosAlarmReportPolicy.applyRelay(alarmReports,r.source(),h.prototype().atmosAlarmableData().syncWith(),a.prototype().atmosAlarmable().sourceTags(),r))return;if(before!=alarmReports.severity()){processAlarm(h);publishReset(s);}}
     private void processAlarm(FirelockHost h){if(!(h.level() instanceof ServerLevel s)||!state.powered())return;var meter=h.powerObservation();if(!meter.known()){alarmReports.markPowerUncertain();return;}var prototype=h.prototype();if(prototype==null||prototype.atmosAlarmableData()==null||meter.deliveredWatts()!=prototype.apcPowerReceiver().requestedWatts())return;AtmosAlarmSeverity v=alarmReports.severity();if(v==AtmosAlarmSeverity.DANGER)emergencyPressureStop(h);else if(v==AtmosAlarmSeverity.NORMAL&&state.phase()==AirlockState.DoorPhase.CLOSED){FirelockState n=state.alarm(prototype,v,s.getGameTime(),false);if(n!=state){state=n;transitionStartedAt=s.getGameTime();h.play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_OPEN.get());h.changedAndSync();}}}
    private boolean requestRelaySync(ServerLevel s){if(networkEndpoint==null)return false;var self=DeviceNetworkRuntime.forLevel(s).transport().lookup(new DeviceIdentity(identity));var n=self==null?null:self.composition().network().orElse(null);if(n==null||!n.transmit())return false;return DeviceNetworkRuntime.forLevel(s).transport().enqueue(new DeviceNetworkPacket(self.identity(),n.networkId(),n.transmitFrequency(),n.address(),"",true,new AtmosAlarmPayload.RelaySync(identity),n.networkId(),n.transmitFrequency()));}
    private void publishSnapshot(ServerLevel s,FirelockHost h){if(networkEndpoint==null)return;var self=DeviceNetworkRuntime.forLevel(s).transport().lookup(new DeviceIdentity(identity));var n=self==null?null:self.composition().network().orElse(null);var d=h.prototype();if(n==null||d==null||!n.transmit())return;DeviceNetworkRuntime.forLevel(s).transport().enqueue(new DeviceNetworkPacket(self.identity(),n.networkId(),n.transmitFrequency(),n.address(),"",true,new AtmosAlarmPayload.Relay(identity,alarmReports.reports(),d.atmosAlarmableData().sourceTags()),n.networkId(),n.transmitFrequency()));}
    private void publishRemove(ServerLevel s,FirelockHost h,UUID removed){if(networkEndpoint==null)return;var self=DeviceNetworkRuntime.forLevel(s).transport().lookup(new DeviceIdentity(identity));var n=self==null?null:self.composition().network().orElse(null);var d=h.prototype();if(n==null||d==null||!n.transmit())return;DeviceNetworkRuntime.forLevel(s).transport().enqueue(new DeviceNetworkPacket(self.identity(),n.networkId(),n.transmitFrequency(),n.address(),"",true,new AtmosAlarmPayload.RelayRemove(identity,removed,d.atmosAlarmableData().sourceTags()),n.networkId(),n.transmitFrequency()));}
    private void publishReset(ServerLevel s){if(networkEndpoint==null)return;var self=DeviceNetworkRuntime.forLevel(s).transport().lookup(new DeviceIdentity(identity));var n=self==null?null:self.composition().network().orElse(null);if(n==null||!n.transmit())return;DeviceNetworkRuntime.forLevel(s).transport().enqueue(new DeviceNetworkPacket(self.identity(),n.networkId(),n.transmitFrequency(),n.address(),"",true,new AtmosAlarmPayload.RelayReset(identity),n.networkId(),n.transmitFrequency()));}

    public void save(CompoundTag tag){tag.putUUID("FirelockIdentity",identity);tag.putLong("FirelockCrushReopenAt",crushReopenAt);tag.putLong("FirelockMotionStartedAt",transitionStartedAt);tag.putLong("FirelockPressureWarningStartedAt",pressureWarningStartedAt);tag.putLong("FirelockTemperatureWarningStartedAt",temperatureWarningStartedAt);ListTag victims=new ListTag();for(UUID id:crushVictims){CompoundTag e=new CompoundTag();e.putUUID("Id",id);victims.add(e);}tag.put("FirelockCrushVictims",victims);if(state!=null){tag.putString("FirelockPhase",state.phase().name());tag.putBoolean("FirelockPowered",state.powered());tag.putBoolean("FirelockLocked",state.locked());tag.putBoolean("FirelockPressureWarning",state.pressureWarning());tag.putBoolean("FirelockTemperatureWarning",state.temperatureWarning());tag.putLong("FirelockTransitionAt",state.transitionAt());tag.putLong("FirelockPryCooldownUntil",state.pryCooldownUntil());tag.putLong("FirelockRevision",state.revision());}}
    public void load(CompoundTag tag){if(tag.hasUUID("FirelockIdentity"))identity=tag.getUUID("FirelockIdentity");crushReopenAt=tag.getLong("FirelockCrushReopenAt");transitionStartedAt=tag.getLong("FirelockMotionStartedAt");pressureWarningStartedAt=tag.getLong("FirelockPressureWarningStartedAt");temperatureWarningStartedAt=tag.getLong("FirelockTemperatureWarningStartedAt");crushVictims.clear();ListTag victims=tag.getList("FirelockCrushVictims",net.minecraft.nbt.Tag.TAG_COMPOUND);for(int i=0;i<victims.size();i++)if(victims.getCompound(i).hasUUID("Id"))crushVictims.add(victims.getCompound(i).getUUID("Id"));try{if(tag.contains("FirelockPhase")){state=new FirelockState(AirlockState.DoorPhase.valueOf(tag.getString("FirelockPhase")),tag.getBoolean("FirelockPowered"),tag.getBoolean("FirelockLocked"),tag.getBoolean("FirelockPressureWarning"),tag.getBoolean("FirelockTemperatureWarning"),tag.getLong("FirelockTransitionAt"),tag.getLong("FirelockPryCooldownUntil"),tag.getLong("FirelockRevision"));initialized=true;}}catch(RuntimeException bad){state=null;initialized=false;}}
}
