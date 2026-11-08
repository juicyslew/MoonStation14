package com.juicyslew.moonstation14.ms14.tesla.client;

import com.juicyslew.moonstation14.ms14.tesla.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.singularity.client.SingularityBillboardBasis;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.ArrayDeque;
import java.util.Iterator;

/** Bounded presentation-only cache for authored jump departure cues. */
@EventBusSubscriber(modid="moonstation14",value=Dist.CLIENT)
public final class TeslaClientJumpPulses {
    private static final int MAX=128;
    private static final ArrayDeque<Pulse> PULSES=new ArrayDeque<>();
    static { TeslaArcNetworking.installJumpHandler(TeslaClientJumpPulses::accept); }
    private TeslaClientJumpPulses(){}
    private static void accept(TeslaJumpPulsePayload p){if(p==null)return;if(PULSES.size()>=MAX)PULSES.removeFirst();PULSES.addLast(new Pulse(p,System.nanoTime()+800_000_000L));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){PULSES.clear();}
    @SubscribeEvent public static void unload(LevelEvent.Unload e){if(e.getLevel().isClientSide())PULSES.clear();}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();if(mc.level==null){PULSES.clear();return;}
        long now=System.nanoTime();var cam=e.getCamera().getPosition();var pose=e.getPoseStack();
        var type=RenderType.entityTranslucent(TeslaVisualResources.EMP_PULSE);var out=mc.renderBuffers().bufferSource().getBuffer(type);
        Iterator<Pulse> it=PULSES.iterator();while(it.hasNext()){
            Pulse pulse=it.next();var p=pulse.payload;
            if(now>=pulse.expires||!p.dimension().equals(mc.level.dimension().location())){it.remove();continue;}
            int frame=TeslaVisualResources.EMP_PULSE_SHEET.frameAt(0,(800_000_000L-(pulse.expires-now))/1_000_000L);
            float u=TeslaVisualResources.EMP_PULSE_SHEET.u(0,frame)/96f,v=TeslaVisualResources.EMP_PULSE_SHEET.v(0,frame)/96f;
            Vec3 worldCenter=new Vec3(p.x(),p.y(),p.z());
            double size=.8;pose.pushPose();try{
                pose.translate(worldCenter.x-cam.x,worldCenter.y-cam.y,worldCenter.z-cam.z);
                pose.mulPose(SingularityBillboardBasis.between(worldCenter,cam).rotation());
                var m=pose.last();vertex(out,m,new Vec3(-size,-size,0),u,v+1f/3);vertex(out,m,new Vec3(size,-size,0),u+1f/3,v+1f/3);vertex(out,m,new Vec3(size,size,0),u+1f/3,v);vertex(out,m,new Vec3(-size,size,0),u,v);
            }finally{pose.popPose();}
        }
        mc.renderBuffers().bufferSource().endBatch(type);
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer o,PoseStack.Pose p,Vec3 v,float u,float w){o.addVertex(p.pose(),(float)v.x,(float)v.y,(float)v.z).setColor(255,255,255,255).setUv(u,w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(p,0,1,0);}
    private record Pulse(TeslaJumpPulsePayload payload,long expires){}
}
