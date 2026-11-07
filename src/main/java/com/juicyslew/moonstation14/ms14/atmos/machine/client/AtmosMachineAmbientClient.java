package com.juicyslew.moonstation14.ms14.atmos.machine.client;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

/** Local playback adapter for server-derived atmos ambience. */
@EventBusSubscriber(modid="moonstation14", value=Dist.CLIENT)
public final class AtmosMachineAmbientClient {
    private static final AtmosAmbientPlaybackOwner OWNER = new AtmosAmbientPlaybackOwner((s,g) -> {
        ResourceLocation id=(ResourceLocation)s.cue();
        Loop loop=new Loop(id,g,s.range(),s.x(),s.y(),s.z());
        Minecraft.getInstance().getSoundManager().play(loop);
        return loop;
    });
    private AtmosMachineAmbientClient() { }

    public static void observe(AtmosMachineBlockEntity machine) { OWNER.observe(snapshot(machine)); }
    public static void observe(VentilationDeviceBlockEntity ventilation) { OWNER.observe(snapshot(ventilation)); }
    private static AtmosAmbientPlaybackOwner.Snapshot snapshot(BlockEntity be) {
        Minecraft mc=Minecraft.getInstance(); var camera=mc.getCameraEntity(); var level=be.getLevel(); BlockPos p=be.getBlockPos();
        boolean loaded=mc.level!=null && level==mc.level && !be.isRemoved() && isExactLoadedHost(mc,be);
        boolean eligible=loaded && camera!=null && (be instanceof AtmosMachineBlockEntity machine && machine.ambientEligible()
                || be instanceof VentilationDeviceBlockEntity ventilation && ventilation.ambientEligible());
        com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio cue=eligible ? be instanceof AtmosMachineBlockEntity machine
                ? machine.resolvedAmbientAudio() : be instanceof VentilationDeviceBlockEntity ventilation
                ? ventilation.resolvedAmbientAudio() : null : null;
        ResourceLocation id=cue==null?null:ResourceLocation.tryParse(cue.sound());
        if(id!=null && BuiltInRegistries.SOUND_EVENT.getOptional(id).isEmpty()) id=null;
        java.util.UUID identity=be instanceof AtmosMachineBlockEntity machine ? machine.placementIdentity()
                : be instanceof VentilationDeviceBlockEntity ventilation ? ventilation.deviceId() : null;
        float gain=be instanceof AtmosMachineBlockEntity machine ? machine.resolvedAmbientVolume()
                : be instanceof VentilationDeviceBlockEntity ventilation ? ventilation.resolvedAmbientVolume() : 0f;
        return new AtmosAmbientPlaybackOwner.Snapshot(be,level,identity,p.getX(),p.getY(),p.getZ(),id,gain,cue==null?0:cue.range(),eligible && identity!=null,id!=null && loaded && identity!=null,camera==null?0:camera.getX(),camera==null?0:camera.getY(),camera==null?0:camera.getZ());
    }
    private static boolean isExactLoadedHost(Minecraft mc,BlockEntity be) { BlockPos p=be.getBlockPos(); var c=mc.level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4); return c!=null && c.getBlockEntity(p)==be; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null || mc.getCameraEntity()==null) { OWNER.clear(); return; }
         for(Object source:OWNER.sources()) {
             if(source instanceof AtmosMachineBlockEntity machine) observe(machine);
             else if(source instanceof VentilationDeviceBlockEntity ventilation) observe(ventilation);
             else OWNER.remove(source);
         }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){OWNER.clear();}
    @SubscribeEvent public static void unload(LevelEvent.Unload e){if(e.getLevel().isClientSide()) OWNER.clear();}
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload e){if(e.getLevel().isClientSide()) OWNER.removeChunk(e.getLevel(),e.getChunk().getPos().x,e.getChunk().getPos().z);}
    public static void registerReloadListener(RegisterClientReloadListenersEvent e) { e.registerReloadListener(new SimplePreparableReloadListener<Void>() {
        protected Void prepare(ResourceManager r,ProfilerFiller p){return null;}
        protected void apply(Void v,ResourceManager r,ProfilerFiller p){OWNER.clear();}
    }); }
    private static final class Loop extends AbstractTickableSoundInstance implements AtmosAmbientPlaybackOwner.Handle {
        Loop(ResourceLocation id,float initialGain,float range,int bx,int by,int bz){super(BuiltInRegistries.SOUND_EVENT.get(id),SoundSource.BLOCKS,RandomSource.create()); looping=true; attenuation=Attenuation.NONE; volume=initialGain; x=bx+.5;y=by+.5;z=bz+.5;}
        public void tick() { }
        public void updateGain(float gain){volume=gain;}
        public void stopHandle(){Minecraft.getInstance().getSoundManager().stop(this);}
        public boolean isStoppedHandle(){return isStopped();}
    }
    static float attenuatedGain(float gain,float range,double distance){return AtmosAmbientPlaybackOwner.attenuated(gain,range,distance);}
}
