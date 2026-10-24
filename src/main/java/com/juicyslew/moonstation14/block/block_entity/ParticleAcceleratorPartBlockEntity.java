package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.singularity.pa.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Persisted strict-identity and generic-power adapter for the seven registered PA hardware blocks. */
public final class ParticleAcceleratorPartBlockEntity extends BlockEntity implements ParticleAcceleratorPartIdentity, PowerRuntimeNode {
    private static final String UUID_TAG="PlacementIdentity", PROFILE_TAG="Profile";
    private UUID placementIdentity=UUID.randomUUID(), apcIdentity=UUID.randomUUID();
    private ResourceLocation profileId=ModParticleAccelerators.TYPE.typeId();
    private ParticleAcceleratorStrength strength=ParticleAcceleratorStrength.STANDBY;
    private boolean enabled, anchored=true, completed=true, graphKnown;
    private double receivedWatts;
    private ParticleAcceleratorAssembly.Resolved assembly;
    private UUID assembledPowerIdentity;
    private ParticleAcceleratorWaveSchedule waveSchedule = ParticleAcceleratorWaveSchedule.IDLE;
    private long operatorRevision;
    public long operatorRevision(){return operatorRevision;}
    /** Ask the existing authoritative assembly resolver to refresh this control's cached layout. */
    public void requestAssemblyRescan(){ if(level instanceof ServerLevel && role().equals("control")) resolveAssembly(); }

    public ParticleAcceleratorPartBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.PARTICLE_ACCELERATOR_PART.get(),pos,state); }
    public UUID placementIdentity(){return placementIdentity;}
    public ResourceLocation profileId(){return profileId;}
    public ParticleAcceleratorStrength strength(){return strength;}
    public boolean enabled(){return enabled;}
    public boolean assemblyValid(){return assembly!=null;}
    public Optional<ParticleAcceleratorAssembly.Resolved> assemblySnapshot(){return Optional.ofNullable(assembly);}
    public ParticleAcceleratorWaveSchedule waveScheduleSnapshot(){return waveSchedule;}
    public boolean firingEligible(){
        if(!role().equals("control")||!enabled||assembly==null||strength==ParticleAcceleratorStrength.STANDBY||!apcPowerReady())return false;
        var profile=profile(); if(profile==null)return false;
        for(var part:assembly.parts())if(part.profile().role().equals("power")){
            BlockEntity be=ParticleAcceleratorAssembly.loadedPartEntity(level,part.position());
            if(!(be instanceof ParticleAcceleratorPartBlockEntity power)||!power.enabled||power.strength!=strength
                    ||!profileId.equals(power.profileId)||!power.validPartProfile())return false;
            double demand=profile.strength(strength).powerBoxMv();
            var meter=PowerRuntime.mvPowerMeter((ServerLevel)level,power);
            return demand>0&&meter.known()&&meter.deliveredWatts()>=demand*0.999;
        }
        return false;
    }
    @Override public String role(){return ((ParticleAcceleratorPartBlock)getBlockState().getBlock()).role();}
    @Override public ResourceLocation prototypeId(){return ResourceLocation.fromNamespaceAndPath("moonstation14",((ParticleAcceleratorPartBlock)getBlockState().getBlock()).prototype());}
    @Override public Rotation orientation(){return ParticleAcceleratorPartBlock.rotation(getBlockState().getValue(ParticleAcceleratorPartBlock.FACING));}
    @Override public boolean anchored(){return anchored;}
    @Override public boolean completed(){return completed;}

    private ParticleAcceleratorControlProfile profile(){return level==null?null:ModParticleAccelerators.catalog(level).get(profileId);}
    private boolean validPartProfile(){
        ParticleAcceleratorControlProfile p=profile();
        if(p==null)return false;
        if(role().equals("control"))return profileId.equals(ModParticleAccelerators.TYPE.typeId());
        var expected=p.parts().get(role());
        return expected!=null&&prototypeId().toString().equals(expected.completedPrototype());
    }
    /** Authoritative controller state transition; invalid/missing profile and non-control callers fail closed. */
    public void setEnabled(boolean value){
        if(!(level instanceof ServerLevel server)||!role().equals("control")||!validPartProfile()||value&&assembly==null||enabled==value)return;
        enabled=value;if(!value)strength=ParticleAcceleratorStrength.STANDBY; operatorRevision++; propagateControlState(); publishClientState();
    }
    public void setStrength(ParticleAcceleratorStrength value){
        if(!(level instanceof ServerLevel server)||!role().equals("control")||value==null||!validPartProfile())return;
        ParticleAcceleratorControlProfile p=profile();
        if(value.level()>p.maximumStrength().level()||strength==value)return;
        strength=value; operatorRevision++; propagateControlState(); publishClientState();
    }
    private void propagateControlState(){
        if(assembly==null||level==null)return;
        for (var part : assembly.parts()) {
            if (part.profile().role().equals("control")) continue;
            BlockEntity blockEntity = ParticleAcceleratorAssembly.loadedPartEntity(level, part.position());
            if (!(blockEntity instanceof ParticleAcceleratorPartBlockEntity physical)
                    || !physical.placementIdentityMatches(part)) continue;
            physical.assembly = assembly;
            physical.applyControllerState(enabled, strength);
        }
    }
    private void publishClientState(){setChanged();if(level instanceof ServerLevel)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    private void applyControllerState(boolean on,ParticleAcceleratorStrength selected){
        if(role().equals("control")||!validPartProfile())return;
        boolean changed=enabled!=on||strength!=selected; enabled=on; strength=selected;
        if(!on)assembly=null;
        if(changed){
            publishClientState();
        }
    }
    private void resolveAssembly(){
        if(!role().equals("control")||level==null){assembly=null;return;}
        var previousAssembly=assembly;
        ParticleAcceleratorControlProfile p=profile(); if(p==null||!validPartProfile()){assembly=null;return;}
        var found=ParticleAcceleratorAssembly.resolve(worldPosition,orientation(),p,pos->
                ParticleAcceleratorAssembly.loadedPartEntity(level,pos) instanceof ParticleAcceleratorPartIdentity identity?identity:null).orElse(null);
        if(found!=null) for(var part:found.parts()){
            BlockEntity be=ParticleAcceleratorAssembly.loadedPartEntity(level,part.position());
            if(!(be instanceof ParticleAcceleratorPartBlockEntity pa)||!profileId.equals(pa.profileId())||!pa.validPartProfile()){found=null;break;}
        }
        assembly=found;
        if(!Objects.equals(previousAssembly,assembly)) operatorRevision++;
        if(assembly==null){
            if(previousAssembly!=null)previousAssembly.parts().stream()
                    .filter(part->!part.profile().role().equals("control"))
                    .map(part->ParticleAcceleratorAssembly.loadedPartEntity(level,part.position()))
                    .filter(ParticleAcceleratorPartBlockEntity.class::isInstance).map(ParticleAcceleratorPartBlockEntity.class::cast)
                    .forEach(part->part.applyControllerState(false,ParticleAcceleratorStrength.STANDBY));
            assembledPowerIdentity=null;
            if(enabled){enabled=false;setChanged();if(level instanceof ServerLevel server)PowerRuntime.deviceChanged(server,worldPosition);}
            var powerProfile=p.parts().get("power");
            if(powerProfile!=null){BlockPos powerPos=worldPosition.offset(ParticleAcceleratorAssembly.toWorldVec(powerProfile.offsetX(),powerProfile.offsetY(),orientation()));
                if(ParticleAcceleratorAssembly.loadedPartEntity(level,powerPos) instanceof ParticleAcceleratorPartBlockEntity power)power.applyControllerState(false,ParticleAcceleratorStrength.STANDBY);}
        }
        else {
            assembly.parts().stream().filter(part->part.profile().role().equals("power"))
                    .map(part->ParticleAcceleratorAssembly.loadedPartEntity(level,part.position()))
                    .filter(ParticleAcceleratorPartBlockEntity.class::isInstance).map(ParticleAcceleratorPartBlockEntity.class::cast)
                    .findFirst().ifPresent(power->assembledPowerIdentity=power.placementIdentity());
            propagateControlState();
        }
    }
    @Override public List<DevicePort> powerRuntimePorts(){
        if(role().equals("control"))return List.of(new DevicePort(worldPosition,Direction.NORTH,CableTier.APC));
        if(role().equals("power"))return List.of(new DevicePort(worldPosition,Direction.NORTH,CableTier.MV));
        return List.of();
    }
    @Override public Double apcPowerDemandWatts(){var p=profile();return role().equals("control")&&validPartProfile()&&p!=null?(double)p.controlPowerAp():null;}
    @Override public UUID apcPowerIdentity(){return role().equals("control")?apcIdentity:null;}
    @Override public Double mvPowerDemandWatts(){
        if(!role().equals("power")||!validPartProfile())return null;
        var profile=profile(); if(profile==null)return null;
        // SS14's disabled power box still requests the baseline standby row.
        return (double) profile.strength(enabled?strength:ParticleAcceleratorStrength.STANDBY).powerBoxMv();
    }
    public boolean apcPowerReady(){
        if(!(level instanceof ServerLevel server)||!role().equals("control")||!enabled)return false;
        var demand=apcPowerDemandWatts();if(demand==null)return false;
        var meter=PowerRuntime.apcPowerMeter(server,this);return meter.known()&&meter.deliveredWatts()>=demand*0.999;
    }
    public double receivedWatts(){return receivedWatts;}
    @Override public void receiveMvPowerAllocation(double watts,boolean known,long tick){receivedWatts=watts;graphKnown=known;}
    @Override public void receiveApcPowerAllocation(double watts,boolean known,long tick){receivedWatts=watts;graphKnown=known;}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel server){resolveAssembly();PowerRuntime.deviceChanged(server,worldPosition);}}
    @Override public void setRemoved(){if(level instanceof ServerLevel server)PowerRuntime.deviceRemoved(server,worldPosition);super.setRemoved();}
    public static void serverTick(ServerLevel level,BlockPos pos,BlockState state,ParticleAcceleratorPartBlockEntity entity){
        if(entity.isRemoved()||entity.level!=level)return;
        if(entity.role().equals("control")){
            entity.resolveAssembly();
            if(entity.assembly==null)entity.enabled=false;
        }
        entity.projectVisual();
        if(entity.role().equals("control")){
            var profile=entity.profile();
            if(profile==null||!entity.firingEligible()) { entity.waveSchedule=ParticleAcceleratorWaveSchedule.IDLE; return; }
            int interval=profile.waveSeconds()*20;
            var advance=entity.waveSchedule.advance(true,level.getGameTime(),interval);
            entity.waveSchedule=advance.schedule();
            if(advance.fire()) entity.fireWave(profile);
        }
    }
    private void fireWave(ParticleAcceleratorControlProfile profile){
        if(!(level instanceof ServerLevel server)||assembly==null)return;
        var particle=ModParticleAcceleratorParticles.catalog(server).get(profile.particleProfile());
        if(particle==null)return;
        var validated=new ArrayList<ParticleAcceleratorAssembly.Part>();
        for(var part:assembly.parts()){
            String role=part.profile().role();
            if(!role.equals("port")&&!role.equals("fore")&&!role.equals("starboard"))continue;
            BlockEntity be=ParticleAcceleratorAssembly.loadedPartEntity(server,part.position());
            if(!(be instanceof ParticleAcceleratorPartBlockEntity source)||!source.completed()||!source.anchored()
                    ||!profileId.equals(source.profileId)||!source.validPartProfile()||!source.placementIdentityMatches(part))return;
            validated.add(part);
        }
        if(validated.size()!=3){waveSchedule=ParticleAcceleratorWaveSchedule.IDLE;return;}
        var spawns=new ArrayList<com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity>(3);
        for(var part:validated){
            var source=(ParticleAcceleratorPartBlockEntity)ParticleAcceleratorAssembly.loadedPartEntity(server,part.position());
            Direction forward=part.rotation().rotate(Direction.SOUTH);
            Vec3 direction=Vec3.atLowerCornerOf(forward.getNormal());
            Vec3 origin=emissionOrigin(part.position(),forward);
            var bolt=new com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity(server,profile.particleProfile(),profile.strength(strength),source.placementIdentity());
            bolt.setPos(origin); bolt.setDeltaMovement(direction.scale(profile.particleSpeed()/20.0)); spawns.add(bolt);
        }
        for(var bolt:spawns)server.addFreshEntity(bolt);
    }
    /** Shared launch transform for live waves and fixture beam-clearance planning. */
    public static Vec3 emissionOrigin(BlockPos source, Direction forward) {
        Vec3 direction=Vec3.atLowerCornerOf(forward.getNormal());
        return Vec3.atCenterOf(source).add(direction.scale(.8)).add(0,.25,0);
    }
    private boolean placementIdentityMatches(ParticleAcceleratorAssembly.Part part){
        return role().equals(part.profile().role())&&orientation()==part.rotation()&&prototypeId().toString().equals(part.profile().completedPrototype());
    }
    private void projectVisual() {
        if (!(level instanceof ServerLevel server)) return;
        boolean control = role().equals("control");
        boolean powered = control ? apcReceiverPowered(server) : enabled && assembly != null;
        ParticleAcceleratorPartBlock.Visual visual = role().equals("end") || !powered
                ? ParticleAcceleratorPartBlock.Visual.COMPLETED : strengthVisual(strength);
        if (getBlockState().getValue(ParticleAcceleratorPartBlock.VISUAL) != visual)
            level.setBlock(worldPosition, getBlockState().setValue(ParticleAcceleratorPartBlock.VISUAL, visual), 2);
    }
    private boolean apcReceiverPowered(ServerLevel server) {
        var demand = apcPowerDemandWatts(); if (demand == null) return false;
        var meter = PowerRuntime.apcPowerMeter(server, this);
        return meter.known() && meter.deliveredWatts() >= demand * 0.999;
    }
    private static ParticleAcceleratorPartBlock.Visual strengthVisual(ParticleAcceleratorStrength value) {
        return switch (value) { case STANDBY -> ParticleAcceleratorPartBlock.Visual.UNLITP; case LEVEL_0 -> ParticleAcceleratorPartBlock.Visual.UNLITP0; case LEVEL_1 -> ParticleAcceleratorPartBlock.Visual.UNLITP1; case LEVEL_2 -> ParticleAcceleratorPartBlock.Visual.UNLITP2; case LEVEL_3 -> ParticleAcceleratorPartBlock.Visual.UNLITP3; };
    }
    @Override protected void saveAdditional(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.putUUID(UUID_TAG,placementIdentity);tag.putUUID("ApcIdentity",apcIdentity);tag.putString(PROFILE_TAG,profileId.toString());
        tag.putString("Strength",strength.name());tag.putBoolean("Enabled",enabled);tag.putBoolean("Anchored",anchored);tag.putBoolean("Completed",completed);
    }
    @Override protected void loadAdditional(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.hasUUID(UUID_TAG))placementIdentity=tag.getUUID(UUID_TAG);if(tag.hasUUID("ApcIdentity"))apcIdentity=tag.getUUID("ApcIdentity");
        if(tag.contains(PROFILE_TAG)){var id=ResourceLocation.tryParse(tag.getString(PROFILE_TAG));if(id!=null)profileId=id;}
        try{strength=ParticleAcceleratorStrength.valueOf(tag.getString("Strength"));}catch(IllegalArgumentException ignored){strength=ParticleAcceleratorStrength.STANDBY;}
        enabled=tag.getBoolean("Enabled");anchored=!tag.contains("Anchored")||tag.getBoolean("Anchored");completed=!tag.contains("Completed")||tag.getBoolean("Completed");waveSchedule=ParticleAcceleratorWaveSchedule.IDLE;
        receivedWatts=0;graphKnown=false;assembly=null;
    }

    /** Client presentation contains only the server-assigned placement identity and resolved profile. */
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries){
        CompoundTag tag=new CompoundTag();tag.putUUID(UUID_TAG,placementIdentity);tag.putString(PROFILE_TAG,profileId.toString());
        tag.putString("Strength",strength.name());tag.putBoolean("Enabled",enabled);tag.putBoolean("Anchored",anchored);tag.putBoolean("Completed",completed);
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){
        if(tag.hasUUID(UUID_TAG))placementIdentity=tag.getUUID(UUID_TAG);
        if(tag.contains(PROFILE_TAG)){var id=ResourceLocation.tryParse(tag.getString(PROFILE_TAG));if(id!=null)profileId=id;}
        if(tag.contains("Strength"))try{strength=ParticleAcceleratorStrength.valueOf(tag.getString("Strength"));}catch(IllegalArgumentException ignored){}
        if(tag.contains("Enabled"))enabled=tag.getBoolean("Enabled");
        if(tag.contains("Anchored"))anchored=tag.getBoolean("Anchored");
        if(tag.contains("Completed"))completed=tag.getBoolean("Completed");
    }
}

