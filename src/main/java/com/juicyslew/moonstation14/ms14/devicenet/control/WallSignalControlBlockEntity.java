package com.juicyslew.moonstation14.ms14.devicenet.control;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceAddressPolicy;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkSavedData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persisted physical controller identity/state and resolved shared network endpoint. */
public final class WallSignalControlBlockEntity extends BlockEntity implements DeviceEndpoint {
    private static final String ID_TAG="DeviceIdentity", ON_TAG="On";
    private DeviceIdentity identity=DeviceIdentity.random();
    private boolean on;
    public WallSignalControlBlockEntity(BlockPos pos,BlockState state) { super(ModBlockEntities.WALL_SIGNAL_CONTROL.get(),pos,state); }
    @Override public DeviceIdentity identity() { return identity; }
    public boolean isOn() { return on; }
    private void syncOnState() {
        if(level==null || !(getBlockState().getBlock() instanceof WallSignalControlBlock))return;
        BlockState state=getBlockState();
        if(state.getValue(WallSignalControlBlock.ON)!=on) level.setBlock(worldPosition,state.setValue(WallSignalControlBlock.ON,on),3);
    }
    public ResourceLocation prototypeId() { return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()); }
    @Override public net.minecraft.resources.ResourceKey<Level> dimension() { return level.dimension(); }
    @Override public BlockPos position() { return worldPosition; }
    @Override public String displayName() { return getBlockState().getBlock().getName().getString(); }
    @Override public boolean available() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return false;
        var chunk=server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk!=null && chunk.getBlockEntity(worldPosition)==this
                && chunk.getBlockState(worldPosition).getBlock() instanceof WallSignalControlBlock;
    }
    @Override public DeviceComposition composition() {
        SignalControlData data=prototype();
        if(data==null)return DeviceComposition.empty();
        try {
            DeviceComposition resolved=data.deviceComponents().resolve(level.isClientSide ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager());
            data.validateResolvedOutputs(resolved);
            return DeviceAddressPolicy.retained(resolved,level instanceof ServerLevel server
                    ? DeviceLinkSavedData.existing(server).flatMap(saved->saved.defaultAddress(identity)) : java.util.Optional.empty());
        } catch(RuntimeException invalid) { return DeviceComposition.empty(); }
    }
    @Override public void receive(com.juicyslew.moonstation14.ms14.devicenet.DevicePayload payload,String sourceAddress) { }
    @Override public void onLoad() {
        super.onLoad();
        syncOnState();
        if(level instanceof ServerLevel server) {
            DeviceNetworkRuntime runtime=DeviceNetworkRuntime.forLevel(server);
            runtime.loaded(this);
            initializeRememberedStatus(runtime);
        }
    }
    @Override public void setRemoved() {
        if(level instanceof ServerLevel server)DeviceNetworkRuntime.forLevel(server).unloaded(identity);
        super.setRemoved();
    }
    /** Server-authoritative action for ordinary use and the controlled-body item-use adapter. */
    public boolean activate(Player user) {
        if(!(level instanceof ServerLevel server)||user==null||isRemoved()||!available())return false;
        SignalControlData data=prototype();
        if(data==null)return false;
        on=!on; syncOnState(); setChanged();
        ResourceLocation soundId=ResourceLocation.parse(data.clickSound());
        var sound=server.registryAccess().registryOrThrow(Registries.SOUND_EVENT).get(soundId);
        if(sound==null) throw new IllegalStateException("unregistered signal-control click sound: "+soundId);
        server.playSound(null, worldPosition, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
        DeviceNetworkRuntime runtime=DeviceNetworkRuntime.forLevel(server);
        for(SignalControlData.Output output:data.outputs(on))runtime.links().invoke(runtime.graph(),this,output.port(),output.signal());
        return true;
    }
    private void initializeRememberedStatus(DeviceNetworkRuntime runtime) {
        DeviceLinkSavedData saved=DeviceLinkSavedData.forMutation((ServerLevel) level);
        SignalControlData data=prototype();
        var manager=PrototypeRuntime.serverManager();
        java.util.Map<String,DeviceSignal> defaults=new java.util.HashMap<>();
        if(data!=null)data.deviceComponents().source().ifPresent(source->source.lastSignals().forEach((portId,signal)-> {
            var port=manager.snapshot(com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks.PORT_TYPE).get(portId);
            if(port!=null)defaults.put(port.portId(),signal);
        }));
        saved.graph().initializeRemembered(identity,defaults);
        saved.setDirty();
    }
    private SignalControlData prototype() { return level==null?null:ModSignalControls.catalog(level).get(prototypeId()); }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries); tag.putUUID(ID_TAG,identity.value()); tag.putBoolean(ON_TAG,on);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); if(tag.hasUUID(ID_TAG))identity=new DeviceIdentity(tag.getUUID(ID_TAG)); on=tag.getBoolean(ON_TAG); syncOnState();
    }
}
