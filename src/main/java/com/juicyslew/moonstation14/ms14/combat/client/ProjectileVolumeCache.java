package com.juicyslew.moonstation14.ms14.combat.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CPU mesh cache. Native images are closed immediately after copying alpha. */
@net.neoforged.fml.common.EventBusSubscriber(modid=com.juicyslew.moonstation14.MoonStation14.MOD_ID,value=net.neoforged.api.distmarker.Dist.CLIENT,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class ProjectileVolumeCache {
    private static final Map<Key,ClosedEffectMesh.FrameVolume> CACHE=new HashMap<>();
    private static ResourceManager manager;
    private ProjectileVolumeCache() { }
    @net.neoforged.bus.api.SubscribeEvent
    public static void onReloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>() {
            @Override protected Void prepare(ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler){return null;}
            @Override protected void apply(Void ignored,ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler){invalidate();}
        });
    }
    public static synchronized void invalidate(){CACHE.clear();manager=null;}
    public record FrameAlpha(float u0,float u1,float v0,float v1,int opaqueShortSpan,float coreU,float coreV) { }
    public static synchronized FrameAlpha alphaFrame(ResourceLocation texture,int width,int height,int columns,int index,float u0,float u1,float v0,float v1){
        var a=frameVolume(texture,width,height,columns,index,u0,u1,v0,v1);
        return new FrameAlpha(a.opaqueU0(),a.opaqueU1(),a.opaqueV0(),a.opaqueV1(),a.opaqueShortSpan(),a.coreU(),a.coreV());
    }
    public static synchronized float[] opaqueSample(ResourceLocation texture,int width,int height,int columns,int index,float u0,float u1,float v0,float v1){
        FrameAlpha a=alphaFrame(texture,width,height,columns,index,u0,u1,v0,v1);return new float[]{a.coreU(),a.coreV()};
    }
    public static synchronized List<ClosedEffectMesh.Vertex> frame(ResourceLocation texture,int width,int height,int columns,int index,float u0,float u1,float v0,float v1) {
        return frameVolume(texture,width,height,columns,index,u0,u1,v0,v1).mesh();
    }
    private static synchronized ClosedEffectMesh.FrameVolume frameVolume(ResourceLocation texture,int width,int height,int columns,int index,float u0,float u1,float v0,float v1) {
        ResourceManager current=Minecraft.getInstance().getResourceManager();
        if(current!=manager){CACHE.clear();manager=current;}
        Key key=new Key(texture,width,height,columns,index);
        return CACHE.computeIfAbsent(key,k->load(k,u0,u1,v0,v1));
    }
    private static ClosedEffectMesh.FrameVolume load(Key k,float u0,float u1,float v0,float v1) {
        try(var resource=manager.getResource(k.texture).orElseThrow().open();NativeImage image=NativeImage.read(resource)) {
            int fw=k.width,fh=k.height,col=k.index%k.columns,row=k.index/k.columns;byte[] alpha=new byte[fw*fh];
            for(int y=0;y<fh;y++)for(int x=0;x<fw;x++)alpha[y*fw+x]=(byte)(image.getPixelRGBA(col*fw+x,row*fh+y)>>>24);
            return ClosedEffectMesh.frameVolume(fw,fh,alpha,u0,u1,v0,v1);
        }catch(IOException|RuntimeException e){return ClosedEffectMesh.frameVolume(0,0,new byte[0],u0,u1,v0,v1);}
    }
    private record Key(ResourceLocation texture,int width,int height,int columns,int index) { }
}
