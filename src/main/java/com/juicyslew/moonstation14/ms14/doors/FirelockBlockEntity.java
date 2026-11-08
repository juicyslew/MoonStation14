package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;
import com.juicyslew.moonstation14.ms14.doors.prototype.ModFirelocks;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkSavedData;
import com.juicyslew.moonstation14.ms14.devicenet.DevicePayload;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmReceiverState;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity;

/** Authoritative lower-cell firelock world host. */
public final class FirelockBlockEntity extends BlockEntity implements FirelockHost {
    private final FirelockController controller=new FirelockController();
    private FirelockController.Presentation clientSnapshot;
    
    public FirelockBlockEntity(BlockPos pos,BlockState blockState){super(ModBlockEntities.FIRELOCK.get(),pos,blockState);}
     public UUID identity(){return controller.identity();}
     public FirelockController firelockController(){return controller;}
     public FirelockHost firelockHost(){return this;}
     @Override public BlockEntity owner(){return this;}
     @Override public BlockPos root(){return worldPosition;}
     @Override public net.minecraft.core.Direction facing(){return getBlockState().getValue(FirelockBlock.FACING);}
     @Override public net.minecraft.world.level.Level level(){return level;}
     @Override public void state(FirelockState state){controller.state(state);}
     @Override public boolean initialized(){return controller.initialized();}
     @Override public PowerRuntime.VentMeterObservation powerObservation(){return poweredObservation();}
     @Override public boolean pairCollides(LivingEntity actor){return AirlockPassagePolicy.pairCollides(actor,this);}
     @Override public boolean obstructed(ServerLevel server){return !DoorCloseAdmission.obstructingBodies(server,doorwayBounds(),this::pairCollides).isEmpty();}
     @Override public void changedAndSync(){sync();}
     @Override public void projectPassability(boolean passable){if(level==null)return;for(BlockPos p:new BlockPos[]{worldPosition,worldPosition.above()})if(level.getBlockState(p).getBlock() instanceof FirelockBlock)level.setBlock(p,level.getBlockState(p).setValue(FirelockBlock.OPEN,passable),3);}
     @Override public void scheduleAt(long gameTime){if(level instanceof ServerLevel server)schedule(server,gameTime);}
     @Override public void play(net.minecraft.sounds.SoundEvent sound){if(level!=null)level.playSound(null,worldPosition,sound,net.minecraft.sounds.SoundSource.BLOCKS,1f,1f);}
     @Override public boolean live(ServerLevel server){var c=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);return !isRemoved()&&getLevel()==server&&c!=null&&c.getBlockEntity(worldPosition)==this;}
     @Override public String displayName(){return getBlockState().getBlock().getName().getString();}
    public FirelockData prototype(){if(level==null||!(getBlockState().getBlock() instanceof FirelockBlock))return null;var data=(level.isClientSide?PrototypeRuntime.clientFirelocks():PrototypeRuntime.serverFirelocks()).get(ModFirelocks.STANDARD);if(data==null)return null;try{data.validate();return data;}catch(RuntimeException invalid){return null;}}
     public FirelockState state(){return controller.state()==null?(clientSnapshot==null?null:clientSnapshot.state()):controller.state();}
     public FirelockController.Presentation firelockPresentation(){return level!=null&&level.isClientSide?clientSnapshot:controller.presentation(this);}
     public boolean projectionCollidable(){FirelockState value=state();return value==null||!value.passable();}
     boolean isCrushExempt(net.minecraft.world.phys.shapes.CollisionContext context){if(!(context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext)||entityContext.getEntity()==null)return false;var entity=entityContext.getEntity();if(!controller.isCrushVictim(entity.getUUID()))return false;if(!entity.getBoundingBox().intersects(doorwayBounds())){controller.removeCrushVictim(entity.getUUID());return false;}return true;}
    /** Resolved initial controller.state is applied once; persisted root controller.state always wins. */
     public void initializeFromResolvedPrototype(){controller.initialize(this);}
     public void refreshProjection(){if(controller.state()!=null)projectState();}
     @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel server){controller.prepareLoad();initializeFromResolvedPrototype();PowerRuntime.firelockChanged(server,worldPosition);if(prototype()!=null)DeviceNetworkRuntime.forLevel(server).loaded(networkEndpoint(server));FirelockData data=prototype();if(data!=null){if(controller.state()!=null&&controller.state().transitionAt()>server.getGameTime())schedule(server,controller.state().transitionAt());else if(controller.crushReopenAt()>server.getGameTime())schedule(server,controller.crushReopenAt());else scheduleMaintenance(server,data);}}}
     @Override public void onChunkUnloaded(){if(level instanceof ServerLevel server)DeviceNetworkRuntime.forLevel(server).unloaded(new DeviceIdentity(controller.identity()));super.onChunkUnloaded();}
     @Override public void setRemoved(){if(level instanceof ServerLevel server){var runtime=DeviceNetworkRuntime.forLevel(server);var c=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);if(c!=null&&!(c.getBlockState(worldPosition).getBlock() instanceof FirelockBlock))runtime.retired(new DeviceIdentity(controller.identity()));else runtime.unloaded(new DeviceIdentity(controller.identity()));PowerRuntime.firelockRemoved(server,worldPosition);}super.setRemoved();}
    public PowerRuntime.VentMeterObservation poweredObservation(){return level instanceof ServerLevel server?PowerRuntime.firelockPower(server,this):PowerRuntime.VentMeterObservation.UNKNOWN;}
     public boolean setPaidPower(boolean powered){return controller.setPaidPower(this,powered);}
     public boolean activate(boolean trustedUserOverride){return controller.activate(this,trustedUserOverride);}
     public boolean toggle(boolean trustedUserOverride){return controller.toggle(this,trustedUserOverride);}
    /** Commit an admitted pry after its timed interaction has revalidated the live target. */
     public boolean completePry(boolean strongTool,boolean poweredTool){return controller.completePry(this,strongTool,poweredTool);}
    /** Alarm/solver closure is not blanket-power-gated, but still honors live obstruction and pry cooldown. */
     public boolean emergencyPressureStop(){return controller.emergencyPressureStop(this);}
     public boolean projectionPassable(){FirelockState value=state();return value!=null&&value.passable();}
      public void onScheduledTick(ServerLevel server){controller.tick(this,server);}

     private DeviceEndpoint networkEndpoint(ServerLevel server){return controller.endpoint(this,server);}
    private AABB doorwayBounds(){return new AABB(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),worldPosition.getX()+1,worldPosition.getY()+2,worldPosition.getZ()+1);}
    private void scheduleMaintenance(ServerLevel server,FirelockData data){schedule(server,server.getGameTime()+Math.max(1,(long)Math.ceil(data.retrySeconds()*20)));}
    private void schedule(ServerLevel server,long at){server.scheduleTick(worldPosition,com.juicyslew.moonstation14.block.ModBlocks.FIRELOCK.get(),(int)Math.max(1,Math.min(Integer.MAX_VALUE,at-server.getGameTime())));}
     private void projectState(){if(level==null||controller.state()==null)return;projectPassability(controller.state().passable());}
     private void sync(){setChanged();projectState();if(level!=null){BlockState s=getBlockState();level.sendBlockUpdated(worldPosition,s,s,3);if(level instanceof ServerLevel server&&controller.state()!=null&&controller.state().transitionAt()>server.getGameTime())server.scheduleTick(worldPosition,com.juicyslew.moonstation14.block.ModBlocks.FIRELOCK.get(),(int)Math.max(1,controller.state().transitionAt()-server.getGameTime()));}}
     @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);controller.save(tag);}
     @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);controller.load(tag);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
     @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();FirelockPresentationNbt.write(tag,"FirelockView",controller.presentation(this));return tag;}
      @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){clientSnapshot=FirelockPresentationNbt.read(tag,"FirelockView").orElse(null);}
    @Override public void onDataPacket(Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),registries);}
}
