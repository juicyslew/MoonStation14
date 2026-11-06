package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData;
import com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

/** Root controller for both airlock blocks. State survives chunk unload and is never client-authored. */
public final class AirlockBlockEntity extends BlockEntity {
    /** Opt-in only: at most 128 records during the first 200 game ticks per controller. */
    static final int CORRECTION_TRACE_MAX_RECORDS=128;
    static final int CORRECTION_TRACE_WINDOW_TICKS=200;
    static final class CorrectionTraceBudget {
        private int records;
        private long startedAt=Long.MIN_VALUE;
        private boolean armed;
        boolean arm(boolean enabled,long tick){
            if(!enabled||armed)return false;
            armed=true;startedAt=tick;return true;
        }
        boolean permit(boolean enabled,long tick){
            if(!enabled||!armed||records>=CORRECTION_TRACE_MAX_RECORDS||tick-startedAt>=CORRECTION_TRACE_WINDOW_TICKS)return false;
            records++;
            return true;
        }
        boolean armed(){return armed;}
    }
    private final CorrectionTraceBudget correctionTraceBudget=new CorrectionTraceBudget();
    private static boolean doorCorrectionTraceEnabled(){return Boolean.getBoolean("moonstation14.airlockCorrectionTrace");}
    private boolean doorCorrectionTracePermit(){
        try{
            if(!doorCorrectionTraceEnabled()||!correctionTraceBudget.armed()||level==null)return false;
            return correctionTraceBudget.permit(true,level.getGameTime());
        }
        catch(RuntimeException ignored){return false;}
    }
    private boolean armDoorCorrectionTrace(){
        try{
            if(level==null||!doorCorrectionTraceEnabled()||correctionTraceBudget.armed())return false;
            return correctionTraceBudget.arm(true,level.getGameTime());
        }
        catch(RuntimeException ignored){return false;}
    }
    private void doorCorrectionTrace(String event,String details){
        try {
            long tick=level.getGameTime();
            ClientView view=clientView();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[airlock-correction] side={} tick={} root={} pos={} revision={} phase={} started={} transitionAt={} passable={} event={} {}",
                    level.isClientSide?"client":"server",tick,view.identity(),worldPosition,
                    view.revision(),view.phase(),view.transitionStartedAt(),view.transitionAt(),view.passable(),event,details);
        }catch(RuntimeException ignored){ /* Optional diagnostics must never affect door state or packet handling. */ }
    }
    public void traceNativeBumpContact(UUID actor,UUID frame,BlockPos selected){
        if(armDoorCorrectionTrace()||correctionTraceBudget.armed())
            if(doorCorrectionTracePermit())doorCorrectionTrace("native-contact","bodyUUID="+actor+",inputSeq=unknown,frame="+frame+",selected="+selected);
    }
    public void traceClientNativeBumpContact(UUID actor,UUID frame,BlockPos root,BlockPos selected){
        if(!hasClientSnapshot()||(!armDoorCorrectionTrace()&&!correctionTraceBudget.armed()))return;
        if(doorCorrectionTracePermit())doorCorrectionTrace("client-native-contact","bodyUUID="+actor+",inputSeq=unknown,frame="+frame+",root="+root+",selected="+selected);
    }
    private com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint networkEndpoint;
    private AirlockState state;
    private long transitionStartedAt;
    /** One owned vanilla tick, always aimed at the earliest root-state deadline. */
    private long scheduledDeadline;
    /** Bounded retry while a crushed actor remains exempt through a blackout. */
    private long crushReopenRetryAt;
    /** Transient one-solve-window retry for a closed-edge bump denied by an unknown meter. */
    private UUID deferredContactActor;
    private long deferredContactUntil;
    private long deferredContactAt;
    private boolean panelOpen;
    private boolean persistedState;
    private boolean persistedElectrification;
    /** Server prototype defaults have been applied, or valid persisted state was decoded. */
    private boolean initialized;
    private final AirlockOpeningPrediction clientPrediction=new AirlockOpeningPrediction();
    private AirlockElectrificationSystem.State electrification = AirlockElectrificationSystem.State.initial(
            com.juicyslew.moonstation14.ms14.doors.prototype.ElectrifiedData.AIRLOCK_DEFAULTS);
    /** Transient, root-owned exemption for actors crushed by this door only. */
    private final Set<UUID> crushVictims=new HashSet<>();
    private Consumer<com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal> doorStatusListener;
    private AirlockState.DoorPhase lastPublishedPhase=AirlockState.DoorPhase.CLOSED;
    public void setDoorStatusListener(Consumer<com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal> listener){doorStatusListener=listener;}
    private void publishDoorStatus(){if(doorStatusListener!=null)doorStatusListener.accept(AirlockSignalConsumer.doorStatus(state.phase()));}
    /** Receives an already-routed device-network input on the authoritative server thread. */
    public boolean receiveSignal(String port,com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal signal){
        return receiveSignal(port,signal,"direct");
    }
    private boolean receiveSignal(String port,com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal signal,String transactionId){
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!ensureInitialized())return false;
        var command=AirlockSignalConsumer.command(port,signal);if(command==null)return signalRejected(port,signal,"unknown-command",transactionId);
        switch(command){
            case OPEN -> {if(state.phase()!=AirlockState.DoorPhase.CLOSED)return signalRejected(port,signal,"open-phase",transactionId);return activate(false,transactionId);}
            case CLOSE -> {if(state.phase()!=AirlockState.DoorPhase.OPEN)return signalRejected(port,signal,"close-phase",transactionId);return activate(false,transactionId);}
            case TOGGLE -> {return activate(false,transactionId);}
            case AUTO_CLOSE_HOLD -> {AirlockState next=state.receiveSignal(port,signal);if(next==state)return true;state=next;sync();return true;}
            case BOLT_TOGGLE, BOLT_DOWN, BOLT_UP -> {boolean down=command==AirlockSignalConsumer.Command.BOLT_TOGGLE?!state.boltsDown():command==AirlockSignalConsumer.Command.BOLT_DOWN;
                if(!state.powerAvailable()||state.boltsDown()==down)return false;
                state=state.receiveSignal(port,signal);play(down?com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_BOLTS_DOWN.get():com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_BOLTS_UP.get());
                if(!down)refreshAutoCloseOnPowerChange(state.powerAvailable(),true,prototype(),server.getGameTime());sync();return true;}
        }return signalRejected(port,signal,"unsupported-command",transactionId);
    }
    private boolean signalRejected(String port,com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal signal,String reason,String transactionId){
        String power="not-sampled";
        if(level instanceof ServerLevel server&&com.juicyslew.moonstation14.MoonStation14.LOGGER.isDebugEnabled())
            power=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,this).toString();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.debug(
                "[airlock-signal-receive] tx={} root={} pos={} port={} signal={} accepted=false reason={} powerSample={} phase={} powered={} bolts={}",
                transactionId,state.identity(),worldPosition,port,signal,reason,power,state.phase(),state.powerAvailable(),state.boltsDown());
        return false;
    }
    public record ClientView(UUID identity,long revision,AirlockState.DoorPhase phase,long transitionStartedAt,long transitionAt,long autoCloseAt,
                             boolean passable,boolean powered,boolean boltsDown,boolean boltLightsVisible,boolean panelOpen,boolean panelVisible) { }
    public AirlockBlockEntity(BlockPos pos,BlockState blockState) { super(ModBlockEntities.AIRLOCK.get(),pos,blockState); state=AirlockState.initial(UUID.randomUUID(),true,true); }
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel)ensureInitialized();
        if(level instanceof ServerLevel server){com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockChanged(server,worldPosition);reconcileScheduledTick(true);registerDeviceEndpoint(server);}}
    private boolean ensureInitialized(){
        if(initialized)return true;
        if(!(level instanceof ServerLevel))return false;
        if(persistedState){
            if(!persistedElectrification){AirlockData data=prototype();if(data==null)return false;
                electrification=AirlockElectrificationSystem.State.initial(data.electrified());persistedElectrification=true;}
            initialized=true;return true;
        }
        AirlockData data=prototype();
        if(data==null)return false;
        state=AirlockState.initial(state.identity(),data.airlock().safety(),data.airlock().autoClose(),data.doorBolt().boltLightsEnabled());
        electrification=AirlockElectrificationSystem.State.initial(data.electrified());
        persistedElectrification=true;
        initialized=true;setChanged();return true;
    }
     @Override public void setRemoved(){clientPrediction.reset();crushVictims.clear();clearDeferredContact();if(level instanceof ServerLevel server){
        var runtime=com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(server);
        var id=new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(state.identity());
        // Chunk teardown invokes setRemoved after the FULL cache entry is gone. Never
        // resolve block state through ServerLevel here: that can force-load the chunk
        // being removed, producing an unload/load loop. A cached loaded chunk is enough
        // to distinguish actual block replacement (retire) from lifecycle unload.
        var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
        boolean replaced=chunk!=null&&!(chunk.getBlockState(worldPosition).getBlock() instanceof AirlockBlock);
        if(networkEndpoint!=null&&replaced)runtime.retired(id);else runtime.unloaded(id);
        com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockRemoved(server,worldPosition);
    }super.setRemoved();}
    private void registerDeviceEndpoint(ServerLevel server){
        try{
            networkEndpoint=new com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint(){
                public com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity identity(){return new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(state.identity());}
                public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension(){return server.dimension();}
                public BlockPos position(){return worldPosition;}
                public boolean acceptsBlockTarget(BlockPos target){
                    if(!available()||!server.hasChunkAt(target))return false;
                    BlockState clicked=server.getBlockState(target);
                    if(!(clicked.getBlock() instanceof AirlockBlock))return false;
                    if(target.equals(worldPosition))return clicked.getValue(AirlockBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                            &&server.getBlockEntity(target)==AirlockBlockEntity.this;
                    return target.equals(worldPosition.above())
                            &&clicked.getValue(AirlockBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                            &&server.hasChunkAt(worldPosition)
                            &&server.getBlockState(worldPosition).getBlock() instanceof AirlockBlock
                            &&server.getBlockState(worldPosition).getValue(AirlockBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                            &&server.getBlockEntity(worldPosition)==AirlockBlockEntity.this;
                }
                public String displayName(){return getBlockState().getBlock().getName().getString();}
                public com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition composition(){
                    AirlockData data=prototype();if(data==null)return com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition.empty();
                     try{var composition=data.deviceComponents().resolve(PrototypeRuntime.serverManager());var id=new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(state.identity());var saved=com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkSavedData.existing(server);return com.juicyslew.moonstation14.ms14.devicenet.DeviceAddressPolicy.retained(composition,saved.flatMap(value->value.defaultAddress(id)));}
                    catch(RuntimeException invalid){return com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition.empty();}
                }
                public boolean available(){var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);return !isRemoved()&&getLevel()==server&&chunk!=null&&chunk.getBlockEntity(worldPosition)==AirlockBlockEntity.this&&chunk.getBlockState(worldPosition).getBlock() instanceof AirlockBlock&&prototype()!=null;}
                public boolean powered(){return state.powerAvailable();}
                public String diagnosticPowerState(){
                    var observed=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,AirlockBlockEntity.this);
                    return "cached="+state.powerAvailable()+",sample="+observed;
                }
                public void receive(com.juicyslew.moonstation14.ms14.devicenet.DevicePayload payload,String sourceAddress){
                    receive(payload,sourceAddress,"direct");
                }
                public void receive(com.juicyslew.moonstation14.ms14.devicenet.DevicePayload payload,String sourceAddress,
                                    String transactionId){
                    if(available()&&payload instanceof com.juicyslew.moonstation14.ms14.devicenet.SignalPayload signal){
                        boolean accepted=receiveSignal(signal.portId(),signal.signal(),transactionId);
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.debug(
                                "[airlock-signal-receive] tx={} root={} pos={} sourceAddress={} port={} signal={} accepted={}",
                                transactionId,state.identity(),worldPosition,sourceAddress,signal.portId(),signal.signal(),accepted);
                    }
                }
            };
            var runtime=com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(server);runtime.loaded(networkEndpoint);
            var composition=networkEndpoint.composition();if(composition.network().isPresent()){
                var saved=com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkSavedData.forMutation(server);
                composition.sourceConfig().ifPresent(source->saved.graph().initializeRemembered(networkEndpoint.identity(),source.lastSignals()));saved.setDirty();
            }
            doorStatusListener=signal->{var current=networkEndpoint.composition();for(var port:current.sources())if(port.id().equals("DoorStatus"))com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(server).links().invoke(com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(server).graph(),networkEndpoint,port.id(),signal);};
            lastPublishedPhase=AirlockSignalConsumer.logicalDoorState(state.phase());
            publishDoorStatus();
        }catch(RuntimeException unavailable){networkEndpoint=null;}
    }
    public UUID identity() { return state.identity(); }
    public AirlockState state() { return state; }
    public long crushReopenRetryAt(){return crushReopenRetryAt;}
    public AirlockElectrificationSystem.State electrificationState(){return electrification;}
     private ClientView stateView(){return new ClientView(state.identity(),state.revision(),state.phase(),transitionStartedAt,state.transitionAt(),state.autoCloseAt(),state.doorPassable(),state.powerAvailable(),state.boltsDown(),state.boltLightsVisible(),panelOpen,panelVisible());}
     public ClientView authoritativeClientView(){return level!=null&&level.isClientSide?clientPrediction.authoritative():stateView();}
      public ClientView clientView(){if(level==null||!level.isClientSide)return stateView();ClientView effective=clientPrediction.effective();return effective!=null?effective:stateView();}
      public boolean predictClientBump(long now){if(level==null||!level.isClientSide||clientPrediction.authoritative()==null)return false;AirlockData data=prototype();if(data==null)return false;return clientPrediction.start(now,data);}
     public void tickClientPrediction(long now){if(level==null||!level.isClientSide)return;clientPrediction.tick(now,prototype());}
     public void clearClientPrediction(){clientPrediction.clear();}
     public boolean hasClientPrediction(){return level!=null&&level.isClientSide&&clientPrediction.active();}
     public boolean hasClientSnapshot(){return level!=null&&level.isClientSide&&clientPrediction.authoritative()!=null;}
    public boolean panelOpen(){return panelOpen;}
    boolean isCrushExempt(net.minecraft.world.phys.shapes.CollisionContext context){
        if(!(context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext))return false;
        net.minecraft.world.entity.Entity entity=entityContext.getEntity();
        boolean clientSnapshotKnown=level!=null&&level.isClientSide&&hasClientSnapshot();
        Set<UUID> victims=clientSnapshotKnown
                ?clientPrediction.authoritativeCrushVictims():crushVictims;
        if(entity==null||!victims.contains(entity.getUUID()))return false;
        if(!entity.getBoundingBox().intersects(doorwayBounds())){if(!clientSnapshotKnown)victims.remove(entity.getUUID());return false;}
        return true;
    }
    public boolean panelVisible(){return resolvesPanelVisible(panelOpen,state.phase(),prototype());}
    public static boolean resolvesPanelVisible(boolean panelOpen,AirlockState.DoorPhase phase,AirlockData data){return data!=null&&phase!=null&&panelOpen&&(data.airlock().openPanelVisible()||phase!=AirlockState.DoorPhase.OPEN);}
    /** Mutation for the server-side admitted timed-interaction completion only. */
    public boolean togglePanel(UUID expectedIdentity,long expectedRevision,boolean expectedPanelOpen){if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!ensureInitialized())return false;AirlockData data=prototype();if(data==null||!state.identity().equals(expectedIdentity)||state.revision()!=expectedRevision||panelOpen!=expectedPanelOpen)return false;panelOpen=!panelOpen;state=state.bumpRevision();sync();playPanel(panelOpen?data.wiresPanel().openSound():data.wiresPanel().closeSound());return true;}
    private void playPanel(String soundId){switch(soundId){case "moonstation14:airlock/panel_open"->play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_PANEL_OPEN.get());case "moonstation14:airlock/panel_close"->play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_PANEL_CLOSE.get());default->throw new IllegalStateException("unsupported resolved airlock panel sound: "+soundId);}}
    public AirlockData prototype() {
        if(level==null||!(getBlockState().getBlock() instanceof AirlockBlock)) return null;
        AirlockData data=(level.isClientSide?PrototypeRuntime.clientAirlocks():PrototypeRuntime.serverAirlocks()).get(ModAirlocks.STANDARD);
        if(data==null)return null; try { data.validate(); return data; } catch(RuntimeException e){return null;}
    }
    public boolean setPowered(boolean powered) {
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!ensureInitialized()||prototype()==null)return false;
        AirlockState before=state;boolean previousBolts=state.boltsDown(),previousAvailability=state.powerAvailable();
        state=state.withPower(powered);refreshAutoCloseOnPowerChange(previousAvailability,previousBolts,prototype(),server.getGameTime());
        if(state==before)return false;
        if(previousBolts!=state.boltsDown())play(state.boltsDown()?com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_BOLTS_DOWN.get():com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_BOLTS_UP.get());
        sync(); return true;
    }
    /** Stable root-UUID/revision API for the wire-panel follow-up. */
    public boolean applyWire(UUID expectedIdentity,long expectedRevision,AirlockState.Wire wire,AirlockState.WireAction action) {
        return applyWireActor(expectedIdentity,expectedRevision,wire,action,null);
    }
    /** Authenticated wire-menu entry; actor is the resolved controlled body, never the player/item carrier. */
    public boolean applyWireActor(UUID expectedIdentity,long expectedRevision,AirlockState.Wire wire,AirlockState.WireAction action,LivingEntity actor) {
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!ensureInitialized())return false;
        AirlockData data=prototype();if(data==null
                ||!state.identity().equals(expectedIdentity)||state.revision()!=expectedRevision)return false;
        if(wire==null||action==null)return false;String actionId=switch(wire){case POWER_ONE->"power_one";case POWER_TWO->"power_two";case LOG->"log";case BOLT->"bolt";case BOLT_LIGHT->"bolt_light";case TIMING->"timing";case SAFETY->"safety";case AI_INTERACT->"ai_interact";};if(!data.wires().supportedActions().contains(actionId))return false;
        boolean previousBolts=state.boltsDown(),previousAvailability=state.powerAvailable();
        AirlockState next=state.applyWire(wire,action,server.getGameTime(),data.wires());if(next==state)return false;
        boolean wasElectrified=electrification.enabled();
        boolean powerWire=wire==AirlockState.Wire.POWER_ONE||wire==AirlockState.Wire.POWER_TWO;
        if(powerWire){
            var observation=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,this);
            boolean freshPaid=AirlockElectrificationSystem.freshFullyPowered(observation.known(),
                    !state.receiverPowerDisabled(),observation.deliveredWatts(),data.apcPowerReceiver().requestedWatts());
            boolean shockAdmitted=freshPaid&&(data.electrified().probability()>=1f
                    ||server.random.nextFloat()<data.electrified().probability());
            var electricalAction=switch(action){case CUT->AirlockElectrificationSystem.WireAction.CUT;case MEND->AirlockElectrificationSystem.WireAction.MEND;case PULSE->AirlockElectrificationSystem.WireAction.PULSE;};
            var attempt=AirlockElectrificationSystem.beforePowerWireAction(electrification,electricalAction,
                    server.getGameTime(),data.electrified(),actor,shockAdmitted);
            electrification=attempt.state();
            var shockPlan=com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem.sourceShockPlan(
                    data.electrified().shockDamage(),data.electrified().siemensCoefficient(),data.electrified().shockSeconds());
            if(actor!=null&&shockAdmitted&&attempt.attemptedShock()==com.juicyslew.moonstation14.ms14.effect.EffectResult.APPLIED
                    &&(shockPlan.damage()>0||shockPlan.stunTicks().isPresent()))emitShockFx(server);
        }
        state=next;
        // Reconcile the measured APC input immediately; do not invalidate the unrelated APC graph.
        if(!state.receiverPowerDisabled()){var supply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,this);if(supply.known())state=state.withPower(supply.deliveredWatts()==data.apcPowerReceiver().requestedWatts());}
        if(powerWire){
            electrification=AirlockElectrificationSystem.updateAfterWireMutation(electrification,
                    state.powerWireOneCut()&&state.powerWireTwoCut(),!state.powerWireOneCut()&&!state.powerWireTwoCut(),
                    state.powerAvailable(),server.getGameTime(),data.electrified());
        }
        if(wasElectrified!=electrification.enabled())play(electrification.enabled()
                ?com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_ELECTRIFY_ON.get()
                :com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_ELECTRIFY_OFF.get());
        refreshAutoCloseOnPowerChange(previousAvailability,previousBolts,data,server.getGameTime());
        if(previousBolts!=state.boltsDown())play(state.boltsDown()?com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_BOLTS_DOWN.get():com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_BOLTS_UP.get());
        sync(); if(state.nextDeadline()>0)scheduleAt(state.nextDeadline()); return true;
    }
    private void emitShockFx(ServerLevel server){
        double x=worldPosition.getX()+.5,y=worldPosition.getY()+1.0,z=worldPosition.getZ()+.5;
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,x,y,z,8,.25,.45,.25,.04);
        server.playSound(null,worldPosition,com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_SHOCK.get(),
                net.minecraft.sounds.SoundSource.BLOCKS,.65f,1.15f);
    }
    public boolean electrifiedContact(LivingEntity actor,AirlockElectrificationSystem.Contact contact){
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||actor==null||prototype()==null)return false;
        var observation=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,this);
        var data=prototype();
        boolean paid=AirlockElectrificationSystem.freshFullyPowered(observation.known(),
                !state.receiverPowerDisabled(),observation.deliveredWatts(),data.apcPowerReceiver().requestedWatts());
        boolean shockAdmitted=paid&&(data.electrified().probability()>=1f
                ||server.random.nextFloat()<data.electrified().probability());
        var result=AirlockElectrificationSystem.contact(electrification,data.electrified(),contact,actor,shockAdmitted);
        var shockPlan=com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem.sourceShockPlan(
                data.electrified().shockDamage(),data.electrified().siemensCoefficient(),data.electrified().shockSeconds());
        if(result==com.juicyslew.moonstation14.ms14.effect.EffectResult.APPLIED&&shockPlan.damage()>0){emitShockFx(server);return true;}
        return false;
    }
    public boolean activate() {
        return activate(false);
    }
    /** Contact opening shares ordinary power/bolt/state gates but deliberately emits no denial feedback. */
    public boolean bumpActivate() {
        if (prototype() == null || !prototype().door().bumpOpen()) return false;
        return activate(true);
    }
    public boolean bumpActivate(LivingEntity actor) {
        electrifiedContact(actor,AirlockElectrificationSystem.Contact.BUMP);
        return bumpActivate();
    }
    private boolean activate(boolean quiet) {
        return activate(quiet,"direct");
    }
    private boolean activate(boolean quiet,String transactionId) {
        if(!(level instanceof ServerLevel server)||!ensureInitialized())return false;AirlockData data=prototype(); if(data==null)return false;
        var supply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,this);
        if(!supply.known()){
            String powerDiagnostic=com.juicyslew.moonstation14.MoonStation14.LOGGER.isDebugEnabled()
                    ?com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPowerDiagnostic(server,this)
                    :"debug-disabled";
            com.juicyslew.moonstation14.MoonStation14.LOGGER.debug(
                    "[airlock-activation] tx={} root={} pos={} accepted=false reason=power-unknown quiet={} powerSample={} powerGate={} phase={} powered={} bolts={}",
                    transactionId,state.identity(),worldPosition,quiet,supply,powerDiagnostic,state.phase(),state.powerAvailable(),state.boltsDown());return false;}
        if(!state.receiverPowerDisabled())setPowered(supply.deliveredWatts()==data.apcPowerReceiver().requestedWatts());
        if(!state.canOpenOrClose()){
            com.juicyslew.moonstation14.MoonStation14.LOGGER.debug(
                    "[airlock-activation] tx={} root={} pos={} accepted=false reason=power-or-bolts quiet={} powerSample={} phase={} powered={} receiverDisabled={} bolts={}",
                    transactionId,state.identity(),worldPosition,quiet,supply,state.phase(),state.powerAvailable(),state.receiverPowerDisabled(),state.boltsDown());
            if(AirlockFeedbackPolicy.deny(supply.known(),state.powerAvailable(),quiet))play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_DENY.get());return false;}
        if(state.phase()==AirlockState.DoorPhase.OPEN) return beginClose(server,data);
        if(state.phase()!=AirlockState.DoorPhase.CLOSED){
            com.juicyslew.moonstation14.MoonStation14.LOGGER.debug(
                    "[airlock-activation] tx={} root={} pos={} accepted=false reason=phase quiet={} powerSample={} phase={} powered={} bolts={}",
                    transactionId,state.identity(),worldPosition,quiet,supply,state.phase(),state.powerAvailable(),state.boltsDown());
            if(!quiet)play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_DENY.get());return false;}
        // SS14 safety evaluates obstruction while closing only; a body already
        // touching a closed leaf may be reopened by the scheduled bump check.
        AirlockState next=state.open(server.getGameTime(),false,data.door().openTimeOneSeconds());
        if(next==state){if(!quiet)play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_DENY.get());return false;} transitionStartedAt=server.getGameTime();state=next;if(doorCorrectionTracePermit())doorCorrectionTrace("activation-start","quiet="+quiet);play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_OPEN.get());sync(); schedule(server,data.door().openTimeOneSeconds()); return true;
    }
    public boolean pry() {
        if(!(level instanceof ServerLevel server)||!ensureInitialized())return false;AirlockData data=prototype(); if(data==null)return false;
        long now=server.getGameTime();AirlockState next=state.pry(now,data.door().openTimeOneSeconds());
        if(next==state)return false;transitionStartedAt=now;state=next;play(state.phase()==AirlockState.DoorPhase.CLOSING?com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_CLOSE.get():com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_OPEN.get());sync();schedule(server,data.door().openTimeOneSeconds());return true;
    }
    private boolean beginClose(ServerLevel server,AirlockData data) {
        AirlockState next=state.close(server.getGameTime(),obstructed(server),data.door().closeTimeOneSeconds());
        if(next==state)return false;
        transitionStartedAt=server.getGameTime();state=next;play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_CLOSE.get());sync(); schedule(server,data.door().closeTimeOneSeconds()); return true;
    }
    private boolean obstructed(ServerLevel level) {
        AABB box=doorwayBounds();
        var actors=AirlockDoorwayActors.query(level,worldPosition,box,false);
        return !actors.known()||!actors.actors().isEmpty();
    }
    private java.util.Map<LivingEntity,net.minecraft.world.phys.Vec3> crushOccupants(ServerLevel level,AirlockData data) {
        AABB box=doorwayBounds();
        java.util.Map<LivingEntity,net.minecraft.world.phys.Vec3> positions=new java.util.LinkedHashMap<>();
        var query=AirlockDoorwayActors.query(level,worldPosition,box,false);
        if(!query.known())return positions;
        for(LivingEntity actor:query.actors()) {
            positions.put(actor,actor.position());
            crushVictims.add(actor.getUUID());
            com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(actor,java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT,(float)data.door().crushDamageBlunt()),1f,false);
            // SharedDoorSystem extends the paralyze through OpenTimeOne after
            // its configured DoorStunTime; this keeps the actor down until the
            // partial-close transition has completed.
            com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.applyParalyze(actor,
                    (float)(data.door().crushStunSeconds()+data.door().openTimeOneSeconds()));
        }
        if(!positions.isEmpty())crushReopenRetryAt=level.getGameTime()+40;
        sync();
        return positions;
    }
    private AABB doorwayBounds(){return new AABB(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),worldPosition.getX()+1,worldPosition.getY()+2,worldPosition.getZ()+1);}
    private java.util.List<LivingEntity> doorwayContactActors(ServerLevel server){
        AABB doorway=doorwayBounds();
        return AirlockDoorwayActors.query(server,worldPosition,doorway,true).actors();
    }
    void onScheduledTick(ServerLevel server) {
        scheduledDeadline=0;
        AirlockData data=prototype(); if(data==null)return;
        long now=server.getGameTime();AirlockState before=state;boolean passableBefore=state.doorPassable(),previousAvailability=state.powerAvailable(),previousBolts=state.boltsDown();
        if(crushVictims.isEmpty()&&crushReopenRetryAt>0){crushReopenRetryAt=0;setChanged();}
        boolean crushRetryDue=state.phase()==AirlockState.DoorPhase.CLOSED&&crushReopenRetryAt>0&&now>=crushReopenRetryAt&&!crushVictims.isEmpty();
        // The door seals at the normal second transition. The paid reopen is a
        // separate two-second-from-crush deadline, not the short close phase.
        boolean crushReopen=crushRetryDue;
        if(crushRetryDue){var supply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,this);
            if(supply.known()&&!state.receiverPowerDisabled())state=state.withPower(supply.deliveredWatts()==data.apcPowerReceiver().requestedWatts());
             AirlockState reopened=supply.known()?state.reopenAfterCrush(now,data.door().openTimeOneSeconds()):state;
             if(reopened!=state){state=reopened;transitionStartedAt=now;crushReopenRetryAt=0;play(com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_OPEN.get());}
            else {
                crushReopenRetryAt=now+20;
            }
        }
        if(!crushReopen)state=state.tick(now,data.door().openTimeTwoSeconds(),data.door().closeTimeTwoSeconds(),data.airlock().autoCloseSeconds());
        if(before.powerPulseLatched()&&!state.powerPulseLatched())
            electrification=new AirlockElectrificationSystem.State(electrification.enabled(),electrification.wireCut(),
                    false,electrification.pulseCancelAt(),electrification.activatedUntil());
        var electricalBefore=electrification;
        if(electrification.pulseCancelAt()>0&&now>=electrification.pulseCancelAt()){
            electrification=AirlockElectrificationSystem.onPulseCancel(electrification,
                    !state.powerWireOneCut()&&!state.powerWireTwoCut());
        }
        refreshAutoCloseOnPowerChange(previousAvailability,previousBolts,data,now);
        boolean partialCloseDue=before.phase()==AirlockState.DoorPhase.CLOSING&&before.transitionAt()>0&&now>=before.transitionAt()
                &&state.phase()==AirlockState.DoorPhase.CLOSING_PASSABLE;
        if(partialCloseDue){
            if(state.safetyActive(now)&&obstructed(server))state=state.afterPartialCloseBlocked(now,data.door().openTimeTwoSeconds());
            else {
                // Capture exact victims and their positions before the collision shape
                // becomes solid. The normal actor movement path is left untouched.
                var crushed=crushOccupants(server,data);
                if(passableBefore&&!state.doorPassable())setOpenBlocks(false);
                for(var entry:crushed.entrySet()){
                    LivingEntity actor=entry.getKey();var p=entry.getValue();
                    if(actor.getY()>p.y())actor.setPos(p.x(),p.y(),p.z());
                }
                // Enabling collision does not necessarily generate a fresh start-contact
                // for stationary overlap or face-tangent bodies.
                for(LivingEntity actor:doorwayContactActors(server))
                    electrifiedContact(actor,AirlockElectrificationSystem.Contact.BUMP);
            }
        }
        boolean normalClosedContact=state.phase()==AirlockState.DoorPhase.CLOSED&&before.phase()!=AirlockState.DoorPhase.CLOSED
                &&!(crushReopenRetryAt>0&&!crushVictims.isEmpty());
        if(state.phase()!=AirlockState.DoorPhase.CLOSED)clearDeferredContact();
        if(normalClosedContact){clearDeferredContact();for(LivingEntity actor:doorwayContactActors(server))
            if(AirlockBumpSystem.isBumpOpener(actor)){if(!bumpActivate()){
                var supply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,this);
                if(!supply.known()&&!state.boltsDown()&&!state.receiverPowerDisabled()){
                    deferredContactActor=actor.getUUID();deferredContactUntil=now+com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.TICKS_PER_SOLVE;deferredContactAt=now+1;
                }
            }else break;}}
        if(deferredContactActor!=null){
            LivingEntity actor=server.getEntity(deferredContactActor) instanceof LivingEntity living?living:null;
            if(state.phase()!=AirlockState.DoorPhase.CLOSED||now>deferredContactUntil||actor==null
                    ||!doorwayContactActors(server).contains(actor)||!AirlockBumpSystem.isBumpOpener(actor)
                    ||state.boltsDown()||state.receiverPowerDisabled())clearDeferredContact();
            else if(now>=deferredContactAt){var supply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,this);
                if(supply.known()){clearDeferredContact();if(supply.deliveredWatts()==data.apcPowerReceiver().requestedWatts())bumpActivate();}
                else deferredContactAt=now+1;
            }
        }
        if(state!=before){if(state.phase()!=before.phase())transitionStartedAt=now;
            if(passableBefore!=state.doorPassable()){if(doorCorrectionTracePermit())doorCorrectionTrace("passability-change", "before="+passableBefore+",after="+state.doorPassable());setOpenBlocks(state.doorPassable());}
            if(state.phase()!=before.phase()&&doorCorrectionTracePermit())doorCorrectionTrace("phase-change","before="+before.phase());sync();}
        if(!electricalBefore.equals(electrification))setChanged();
        if(electricalBefore.enabled()!=electrification.enabled()){
            play(electrification.enabled()?com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_ELECTRIFY_ON.get()
                    :com.juicyslew.moonstation14.sounds.ModSounds.AIRLOCK_ELECTRIFY_OFF.get());
        }

        // Match the upstream one-second retry after a blocked/power-denied auto-close.
        // Wire deadlines are processed above on every callback, so retries cannot starve pulses.
        if(state.phase()==AirlockState.DoorPhase.OPEN&&state.autoCloseAt()>0&&now>=state.autoCloseAt()){
            var supply=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(server,this);
            if(supply.known()&&!state.receiverPowerDisabled())setPowered(supply.deliveredWatts()==data.apcPowerReceiver().requestedWatts());
            if(!supply.known()||!beginClose(server,data)){
                if(state.phase()==AirlockState.DoorPhase.OPEN){state=state.scheduleAutoCloseAt(now+20);sync();}
            }
        }
        reconcileScheduledTick(false);
    }
    private void setOpenBlocks(boolean open) {
        if(level==null)return;
        if(open)crushVictims.clear();
        for(BlockPos pos:new BlockPos[]{worldPosition,worldPosition.above()}) { BlockState s=level.getBlockState(pos); if(s.getBlock() instanceof AirlockBlock&&s.getValue(AirlockBlock.OPEN)!=open)level.setBlock(pos,s.setValue(AirlockBlock.OPEN,open),3); }
        if(doorCorrectionTracePermit()){
            BlockState lower=level.getBlockState(worldPosition),upper=level.getBlockState(worldPosition.above());
            doorCorrectionTrace("block-passability-written","requestedOpen="+open+",lower="+(lower.hasProperty(AirlockBlock.OPEN)?lower.getValue(AirlockBlock.OPEN):"missing")+",upper="+(upper.hasProperty(AirlockBlock.OPEN)?upper.getValue(AirlockBlock.OPEN):"missing"));
        }
    }
    private void schedule(ServerLevel level,double seconds){scheduleAt(level.getGameTime()+Math.max(1,(long)Math.ceil(seconds*20)));}
    private void refreshAutoCloseOnPowerChange(boolean previousAvailability,boolean previousBolts,AirlockData data,long now){
        if(data!=null)state=state.powerAvailabilityChanged(previousAvailability,previousBolts,now,data.airlock().autoCloseSeconds());
    }
    private void scheduleAt(long ignoredRequestedTick){reconcileScheduledTick(false);}
    /** Vanilla permits one tick per (block, position); explicitly replace it when the earliest deadline moves. */
    private void reconcileScheduledTick(boolean force){
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread())return;
        long now=server.getGameTime(),deadline=state.nextDeadline();
        if(deferredContactActor!=null&&(deadline==0||deferredContactAt<deadline))deadline=deferredContactAt;
        if(crushReopenRetryAt>0&&(deadline==0||crushReopenRetryAt<deadline))deadline=crushReopenRetryAt;
        if(electrification.pulseCancelAt()>0&&(deadline==0||electrification.pulseCancelAt()<deadline))deadline=electrification.pulseCancelAt();
        long desired=deadline>0?Math.max(now+1,deadline):0;
        if(!force&&desired==scheduledDeadline)return;
        BlockPos pos=worldPosition;
        server.getBlockTicks().clearArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()));
        scheduledDeadline=0;
        if(deadline>0){int delay=(int)Math.max(1,Math.min(Integer.MAX_VALUE,deadline-now));server.scheduleTick(pos,getBlockState().getBlock(),delay);scheduledDeadline=now+delay;}
    }
    private void clearDeferredContact(){deferredContactActor=null;deferredContactAt=0;deferredContactUntil=0;}
     private void sync(){setChanged();if(level instanceof ServerLevel&&doorCorrectionTracePermit())doorCorrectionTrace("server-sync","logical="+AirlockSignalConsumer.logicalDoorState(state.phase()));AirlockState.DoorPhase logical=AirlockSignalConsumer.logicalDoorState(state.phase());if(lastPublishedPhase!=logical){lastPublishedPhase=logical;publishDoorStatus();} if(level instanceof ServerLevel) { BlockState s=getBlockState(); level.sendBlockUpdated(worldPosition,s,s,3);reconcileScheduledTick(false); } }
     @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
      @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();ClientView v=stateView();tag.putUUID("ViewIdentity",v.identity());tag.putLong("ViewRevision",v.revision());tag.putString("ViewPhase",v.phase().name());tag.putLong("ViewStarted",v.transitionStartedAt());tag.putLong("ViewTransition",v.transitionAt());tag.putLong("ViewAutoClose",v.autoCloseAt());tag.putBoolean("ViewPassable",v.passable());tag.putBoolean("ViewPowered",v.powered());tag.putBoolean("ViewBolts",v.boltsDown());tag.putBoolean("ViewBoltLights",v.boltLightsVisible());tag.putBoolean("ViewPanelOpen",v.panelOpen());tag.putBoolean("ViewPanelVisible",v.panelVisible());net.minecraft.nbt.ListTag victims=new net.minecraft.nbt.ListTag();for(UUID id:crushVictims){CompoundTag entry=new CompoundTag();entry.putUUID("Id",id);victims.add(entry);}tag.put("CrushVictims",victims);return tag;}
      @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){
          try{
                ClientView received=new ClientView(tag.getUUID("ViewIdentity"),tag.getLong("ViewRevision"),AirlockState.DoorPhase.valueOf(tag.getString("ViewPhase")),tag.getLong("ViewStarted"),tag.getLong("ViewTransition"),tag.getLong("ViewAutoClose"),tag.getBoolean("ViewPassable"),tag.getBoolean("ViewPowered"),tag.getBoolean("ViewBolts"),tag.getBoolean("ViewBoltLights"),tag.getBoolean("ViewPanelOpen"),tag.getBoolean("ViewPanelVisible"));
               net.minecraft.nbt.ListTag victims=tag.getList("CrushVictims",net.minecraft.nbt.Tag.TAG_COMPOUND);
               java.util.Set<UUID> receivedVictims=new HashSet<>();
               for(int i=0;i<victims.size();i++)if(victims.getCompound(i).hasUUID("Id"))receivedVictims.add(victims.getCompound(i).getUUID("Id"));
               if(!clientPrediction.accept(received,receivedVictims))return;
           }catch(RuntimeException ignored){clientPrediction.reset();return;}
           if(doorCorrectionTracePermit()){ClientView accepted=clientPrediction.authoritative();doorCorrectionTrace("client-update-tag","snapshotPhase="+accepted.phase()+",snapshotPassable="+accepted.passable());}
      }
     @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
         CompoundTag tag=packet.getTag();
         if(tag!=null&&doorCorrectionTracePermit())doorCorrectionTrace("client-packet-received","packetIdentity="+(tag.hasUUID("ViewIdentity")?tag.getUUID("ViewIdentity"):"missing")+",packetRevision="+tag.getLong("ViewRevision")+",packetPhase="+tag.getString("ViewPhase")+",packetPassable="+tag.getBoolean("ViewPassable"));
         if(tag!=null) handleUpdateTag(tag,registries);
    }
    private void play(net.minecraft.sounds.SoundEvent sound){if(level!=null)level.playSound(null,worldPosition,sound,net.minecraft.sounds.SoundSource.BLOCKS,1f,1f);}
    static void onNeighborChanged(ServerLevel level,BlockPos pos) { if(level.getBlockEntity(pos) instanceof AirlockBlockEntity be && be.state.phase()==AirlockState.DoorPhase.OPEN&&be.prototype()!=null&&be.state.autoClose()&&be.state.powerAvailable()&&!be.state.boltsDown()){be.state=be.state.scheduleAutoCloseAt(level.getGameTime()+1);be.sync();} }
       @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider registries){super.saveAdditional(t,registries);t.putUUID("AirlockIdentity",state.identity());t.putBoolean("AirlockPanelOpen",panelOpen);t.putLong("AirlockTransitionStartedAt",transitionStartedAt);t.putLong("AirlockRevision",state.revision());t.putString("AirlockPhase",state.phase().name());t.putBoolean("AirlockPowered",state.powered());t.putBoolean("AirlockBolts",state.boltsDown());t.putBoolean("AirlockSafety",state.safety());t.putBoolean("AirlockAutoClose",state.autoClose());t.putLong("AirlockTransitionAt",state.transitionAt());t.putLong("AirlockAutoCloseAt",state.autoCloseAt());t.putBoolean("AirlockPowerWireOneCut",state.powerWireOneCut());t.putBoolean("AirlockPowerWireTwoCut",state.powerWireTwoCut());t.putLong("AirlockPowerPulseUntil",state.powerPulseUntil());t.putBoolean("AirlockPowerPulseLatched",state.powerPulseLatched());t.putBoolean("AirlockBoltWireCut",state.boltWireCut());t.putBoolean("AirlockBoltLightWireCut",state.boltLightWireCut());t.putBoolean("AirlockTimingWireCut",state.timingWireCut());t.putBoolean("AirlockSafetyWireCut",state.safetyWireCut());t.putBoolean("AirlockAiInteractWireCut",state.aiInteractWireCut());t.putBoolean("AirlockLogWireCut",state.logWireCut());t.putBoolean("AirlockLogCircuitDisabled",state.logCircuitDisabled());t.putLong("AirlockLogPulseUntil",state.logPulseUntil());t.putBoolean("AirlockBoltLights",state.boltLightsEnabled());t.putBoolean("AirlockReceiverDisabled",state.receiverPowerDisabled());t.putDouble("AirlockTimingModifier",state.autoCloseModifier());t.putLong("AirlockTimingModifierUntil",state.timingModifierUntil());t.putDouble("AirlockSafetyModifier",state.safetyModifier());t.putLong("AirlockSafetyModifierUntil",state.safetyModifierUntil());t.putBoolean("AirlockElectrified",electrification.enabled());t.putBoolean("AirlockElectrifiedWireCut",electrification.wireCut());t.putBoolean("AirlockElectrifiedPulsed",electrification.pulsed());t.putLong("AirlockElectrifiedCancelAt",electrification.pulseCancelAt());t.putLong("AirlockElectrifiedUntil",electrification.activatedUntil());t.putLong("AirlockCrushReopenRetryAt",crushReopenRetryAt);}
      @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider registries){super.loadAdditional(t,registries);transitionStartedAt=t.getLong("AirlockTransitionStartedAt");panelOpen=t.getBoolean("AirlockPanelOpen");crushReopenRetryAt=t.getLong("AirlockCrushReopenRetryAt");persistedState=false;persistedElectrification=false;initialized=false;try{if(t.contains("AirlockPhase")){AirlockState decoded=new AirlockState(t.hasUUID("AirlockIdentity")?t.getUUID("AirlockIdentity"):UUID.randomUUID(),Math.max(0,t.getLong("AirlockRevision")),AirlockState.DoorPhase.valueOf(t.getString("AirlockPhase")),t.getBoolean("AirlockPowered"),t.getBoolean("AirlockBolts"),t.getBoolean("AirlockSafety"),t.getBoolean("AirlockAutoClose"),t.getLong("AirlockTransitionAt"),t.getLong("AirlockAutoCloseAt"),t.getBoolean("AirlockPowerWireOneCut"),t.getBoolean("AirlockPowerWireTwoCut"),t.getLong("AirlockPowerPulseUntil"),t.getBoolean("AirlockPowerPulseLatched"),t.getBoolean("AirlockBoltWireCut"),t.getBoolean("AirlockBoltLightWireCut"),t.getBoolean("AirlockTimingWireCut"),t.getBoolean("AirlockSafetyWireCut"),t.getBoolean("AirlockBoltLights"),t.getBoolean("AirlockReceiverDisabled"),t.contains("AirlockTimingModifier")?t.getDouble("AirlockTimingModifier"):1,t.contains("AirlockSafetyModifier")?t.getDouble("AirlockSafetyModifier"):1,t.getLong("AirlockSafetyModifierUntil"),t.getLong("AirlockTimingModifierUntil"),t.getBoolean("AirlockAiInteractWireCut"),t.getBoolean("AirlockLogWireCut"),t.getBoolean("AirlockLogCircuitDisabled"),t.getLong("AirlockLogPulseUntil"));state=decoded;if(t.contains("AirlockElectrified")){electrification=new AirlockElectrificationSystem.State(t.getBoolean("AirlockElectrified"),t.getBoolean("AirlockElectrifiedWireCut"),t.getBoolean("AirlockElectrifiedPulsed"),t.getLong("AirlockElectrifiedCancelAt"),t.getLong("AirlockElectrifiedUntil"));persistedElectrification=true;}persistedState=true;initialized=true;}}catch(RuntimeException bad){state=AirlockState.initial(UUID.randomUUID(),true,true);electrification=AirlockElectrificationSystem.State.initial(com.juicyslew.moonstation14.ms14.doors.prototype.ElectrifiedData.AIRLOCK_DEFAULTS);persistedState=false;persistedElectrification=false;initialized=false;}}
}

