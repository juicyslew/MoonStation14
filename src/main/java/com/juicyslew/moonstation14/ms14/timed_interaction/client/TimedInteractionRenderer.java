package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Camera-facing, depth-tested world presentation for actor-owned timed operations. */
@OnlyIn(Dist.CLIENT)
public final class TimedInteractionRenderer {
    private static final ResourceLocation FRAME=ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,
            "textures/gui/timed_interaction_progress.png");
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,
            "textures/gui/white_pixel.png");
    private TimedInteractionRenderer() { }

    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null) return;
        var camera=event.getCamera(); var cameraPos=camera.getPosition();
        boolean firstPerson=mc.options.getCameraType().isFirstPerson();
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        var snapshots=TimedInteractionClient.snapshotView();
        for(var entry:snapshots.entrySet()) {
            var s=entry.getValue(); LivingEntity actor=mc.level.getEntity(entry.getKey()) instanceof LivingEntity living?living:null;
            if(actor==null || actor.isRemoved() || actor.isInvisible() || !actor.getUUID().equals(s.bodyUuid())) continue;
            if(!s.dimension().equals(mc.level.dimension().location())) continue;
            double tick=TimedInteractionClient.estimatedServerTick(entry.getKey(),event.getPartialTick().getGameTimeDeltaPartialTick(false));
            int index=0;
            for(TimedInteractionSnapshot.View v:s.views()) {
                float progress=TimedInteractionClient.predictedProgress(v,tick);
                float fillAlpha=v.phase()==TimedOperation.Phase.CANCELLED
                        ?TimedInteractionMath.cancellationFillAlpha(v.terminalTick(),tick):1f;
                int color=TimedInteractionMath.color(progress,v.phase());
                int light=LevelRenderer.getLightColor(mc.level,actor.blockPosition());
                PoseStack pose=event.getPoseStack(); pose.pushPose();
                try {
                    float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
                    double x=Mth.lerp(partial,actor.xo,actor.getX());
                    double y=Mth.lerp(partial,actor.yo,actor.getY());
                    double z=Mth.lerp(partial,actor.zo,actor.getZ());
                    boolean local=actor==GhostControlClient.ownedCharacterForHud();
                    // The controlled CHARACTER is the active view body even though Minecraft's
                    // camera entity remains the player/ghost; the camera mixin proxies its view.
                    boolean cameraAnchor=local && firstPerson;
                    if(cameraAnchor) {
                        var anchor=firstPersonAnchor(cameraPos,camera.getLookVector(),camera.getUpVector(),index,event.getProjectionMatrix());
                        pose.translate(anchor.x-cameraPos.x,anchor.y-cameraPos.y,anchor.z-cameraPos.z);
                    } else {
                        pose.translate(x-cameraPos.x,y+actor.getBbHeight()+TimedInteractionMath.ACTOR_BAR_CLEARANCE-cameraPos.y+TimedInteractionMath.stackOffset(index)*TimedInteractionMath.ACTOR_SCALE,z-cameraPos.z);
                    }
                    // camera.rotation() already faces the quad toward the view. A further Y
                    // half-turn mirrors its X axis and makes progress run right-to-left.
                    pose.mulPose(camera.rotation());
                    float scale=cameraAnchor?TimedInteractionMath.FIRST_PERSON_SCALE:TimedInteractionMath.ACTOR_SCALE;
                    pose.scale(scale,-scale,scale);
                    Matrix4f m=pose.last().pose(); var consumer=buffers.getBuffer(actor==GhostControlClient.ownedCharacterForHud()?RenderType.entityTranslucentEmissive(FRAME):RenderType.entityTranslucent(FRAME));
                    draw(consumer,m,-12,-3,12,4,1f,light,OverlayTexture.NO_OVERLAY);
                    int fill=TimedInteractionMath.fillPixels(progress);
                    if(fill>0 && fillAlpha>0) drawFill(buffers,actor==GhostControlClient.ownedCharacterForHud(),m,TimedInteractionMath.FILL_LEFT,TimedInteractionMath.FILL_TOP,TimedInteractionMath.FILL_LEFT+fill,TimedInteractionMath.FILL_TOP+TimedInteractionMath.FILL_HEIGHT,color,fillAlpha,light);
                } finally { pose.popPose(); }
                index++;
            }
        }
        LivingEntity own=GhostControlClient.ownedCharacterForHud();
        if(own!=null && mc.level.getEntity(own.getId())==own && !own.isRemoved() && !own.isInvisible()) {
            var snapshot=snapshots.get(own.getId());
            int index=snapshot==null?0:snapshot.views().size();
            for(var p:TimedInteractionClient.provisionalView(own.getUUID()).values()) {
                float progress=TimedInteractionMath.progress(0,p.durationTicks(),p.elapsedTicks());
                drawBar(event,own,cameraPos,buffers,progress,TimedOperation.Phase.ACTIVE,1f,index++);
            }
        }
        buffers.endBatch(RenderType.entityTranslucent(FRAME));
        buffers.endBatch(RenderType.entityTranslucentEmissive(FRAME));
        buffers.endBatch(RenderType.entityTranslucent(WHITE));
        buffers.endBatch(RenderType.entityTranslucentEmissive(WHITE));
    }
    private static void drawBar(RenderLevelStageEvent event,LivingEntity actor,net.minecraft.world.phys.Vec3 cameraPos,
                                MultiBufferSource.BufferSource buffers,float progress,TimedOperation.Phase phase,float alpha,int index) {
        int color=TimedInteractionMath.color(progress,phase), light=0xF000F0;
        PoseStack pose=event.getPoseStack(); pose.pushPose();
        try {
            float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
            double x=Mth.lerp(partial,actor.xo,actor.getX()), y=Mth.lerp(partial,actor.yo,actor.getY()), z=Mth.lerp(partial,actor.zo,actor.getZ());
            boolean cameraAnchor=actor==GhostControlClient.ownedCharacterForHud()
                    && Minecraft.getInstance().options.getCameraType().isFirstPerson();
            if(cameraAnchor) {
                var anchor=firstPersonAnchor(cameraPos,event.getCamera().getLookVector(),event.getCamera().getUpVector(),index,event.getProjectionMatrix());
                pose.translate(anchor.x-cameraPos.x,anchor.y-cameraPos.y,anchor.z-cameraPos.z);
            } else pose.translate(x-cameraPos.x,y+actor.getBbHeight()+TimedInteractionMath.ACTOR_BAR_CLEARANCE-cameraPos.y+TimedInteractionMath.stackOffset(index)*TimedInteractionMath.ACTOR_SCALE,z-cameraPos.z);
            pose.mulPose(event.getCamera().rotation());
            float scale=cameraAnchor?TimedInteractionMath.FIRST_PERSON_SCALE:TimedInteractionMath.ACTOR_SCALE;
            pose.scale(scale,-scale,scale);
            Matrix4f m=pose.last().pose(); var consumer=buffers.getBuffer(RenderType.entityTranslucentEmissive(FRAME));
            draw(consumer,m,-12,-3,12,4,alpha,light,OverlayTexture.NO_OVERLAY);
            int fill=TimedInteractionMath.fillPixels(progress); if(fill>0) drawFill(buffers,true,m,TimedInteractionMath.FILL_LEFT,TimedInteractionMath.FILL_TOP,TimedInteractionMath.FILL_LEFT+fill,TimedInteractionMath.FILL_TOP+TimedInteractionMath.FILL_HEIGHT,color,alpha,light);
        } finally { pose.popPose(); }
    }
    private static net.minecraft.world.phys.Vec3 firstPersonAnchor(net.minecraft.world.phys.Vec3 camera,
            org.joml.Vector3f forward,org.joml.Vector3f up,int index,Matrix4f projection) {
        return TimedInteractionMath.firstPersonAnchor(camera,new net.minecraft.world.phys.Vec3(forward.x,forward.y,forward.z),
                new net.minecraft.world.phys.Vec3(up.x,up.y,up.z),index,projection==null?Double.NaN:projection.m11());
    }
    private static void draw(com.mojang.blaze3d.vertex.VertexConsumer b,Matrix4f m,float x0,float y0,float x1,float y1,float a,int light,int overlay) {
        b.addVertex(m,x0,y1,0).setColor(255,255,255,(int)(255*a)).setUv(0,1).setOverlay(overlay).setLight(light).setNormal(0,0,1);
        b.addVertex(m,x1,y1,0).setColor(255,255,255,(int)(255*a)).setUv(1,1).setOverlay(overlay).setLight(light).setNormal(0,0,1);
        b.addVertex(m,x1,y0,0).setColor(255,255,255,(int)(255*a)).setUv(1,0).setOverlay(overlay).setLight(light).setNormal(0,0,1);
        b.addVertex(m,x0,y0,0).setColor(255,255,255,(int)(255*a)).setUv(0,0).setOverlay(overlay).setLight(light).setNormal(0,0,1);
    }
    private static void drawFill(MultiBufferSource.BufferSource buffers,boolean local,Matrix4f m,float x0,float y0,float x1,float y1,int color,float a,int light) {
        var b=buffers.getBuffer(local?RenderType.entityTranslucentEmissive(WHITE):RenderType.entityTranslucent(WHITE));
        float r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,bl=(color&255)/255f;
        b.addVertex(m,x0,y1,0).setColor(r,g,bl,a).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,1);
        b.addVertex(m,x1,y1,0).setColor(r,g,bl,a).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,1);
        b.addVertex(m,x1,y0,0).setColor(r,g,bl,a).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,1);
        b.addVertex(m,x0,y0,0).setColor(r,g,bl,a).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,1);
    }
}
