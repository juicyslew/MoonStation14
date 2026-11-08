package com.juicyslew.moonstation14.ms14.tesla.client;

import com.juicyslew.moonstation14.ms14.tesla.TeslaArcNetworking;
import com.juicyslew.moonstation14.ms14.tesla.TeslaArcPayload;
import com.juicyslew.moonstation14.ms14.tesla.TeslaVisualResources;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Client-only bounded cache for server-authored Tesla arc visuals. */
@EventBusSubscriber(modid="moonstation14", value=Dist.CLIENT)
public final class TeslaClientArcs {
    private static final ResourceLocation TEXTURE=TeslaVisualResources.LIGHTNING;
    private static final int MAX_ARCS=256;
    private static final ArrayDeque<Arc> ARCS=new ArrayDeque<>();
    static { TeslaArcNetworking.installClientHandler(TeslaClientArcs::accept); }
    private TeslaClientArcs() { }
    public static void accept(TeslaArcPayload payload) {
        if(payload==null) return;
        if(ARCS.size()>=MAX_ARCS) ARCS.removeFirst();
        ARCS.addLast(new Arc(payload,System.nanoTime()+ (long)(payload.lifetimeSeconds()*1_000_000_000L)));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ARCS.clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if(event.getLevel().isClientSide()) ARCS.clear(); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc=Minecraft.getInstance(); if(mc.level==null) { ARCS.clear(); return; }
        long now=System.nanoTime(); var camera=event.getCamera().getPosition();
        var pose=event.getPoseStack(); var consumer=mc.renderBuffers().bufferSource().getBuffer(RenderType.entityTranslucent(TEXTURE));
        Iterator<Arc> iterator=ARCS.iterator();
        while(iterator.hasNext()) {
            Arc arc=iterator.next(); if(now>=arc.expires || !arc.payload.dimension().equals(mc.level.dimension().location())) { iterator.remove(); continue; }
            Vec3 start=arc.payload.from().subtract(camera), end=arc.payload.to().subtract(camera), axis=end.subtract(start);
            if(axis.lengthSqr()<1.0e-8) continue;
            Vec3 side=axis.normalize().cross(camera.subtract(arc.payload.from()).normalize());
            if(side.lengthSqr()<1.0e-8) side=axis.normalize().cross(new Vec3(0,1,0));
            side=side.normalize().scale(arc.payload.beamHalfWidth() > 0 ? arc.payload.beamHalfWidth() : .09);
            double phase=(now/40_000_000L+(arc.payload.seed()&0xffff))%1024/1024.0;
            pose.pushPose();
            try {
                var matrix=pose.last(); var out=consumer;
                vertex(out,matrix,start.add(side),0,phase); vertex(out,matrix,start.subtract(side),1,phase);
                vertex(out,matrix,end.subtract(side),1,phase+1); vertex(out,matrix,end.add(side),0,phase+1);
            } finally { pose.popPose(); }
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.entityTranslucent(TEXTURE));
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack.Pose pose,Vec3 p,float u,double v) {
        out.addVertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z).setColor(255,255,255,255).setUv(u,(float)v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose,0,1,0);
    }
    private record Arc(TeslaArcPayload payload,long expires) { }
}
