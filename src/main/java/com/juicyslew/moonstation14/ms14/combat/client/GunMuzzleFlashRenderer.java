package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.combat.ProjectileSpriteFrames;
import com.juicyslew.moonstation14.ms14.combat.ProjectileSpritePose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws locally predicted muzzle flashes; has no projectile or gameplay side effects. */
@EventBusSubscriber(modid=MoonStation14.MOD_ID, value=Dist.CLIENT)
public final class GunMuzzleFlashRenderer {
    private GunMuzzleFlashRenderer() { }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc=Minecraft.getInstance(); var level=mc.level;
        if(level==null) { GunMuzzleFlashClient.clear(); return; }
        GunMuzzleFlashClient.reconcileWorld(level);
        var body=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        long epoch=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
        var dimension=level.dimension().location();
        if(body==null||epoch<=0) GunMuzzleFlashClient.reconcile(null,0,dimension); else GunMuzzleFlashClient.reconcile(body.getUUID(),epoch,dimension);
        var samples=GunMuzzleFlashClient.sampleWorld(dimension,com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock());
        if(samples.isEmpty()) return;
        Vec3 camera=event.getCamera().getPosition(); var buffers=mc.renderBuffers().bufferSource();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var sample:samples) {
            var entry=sample.entry(); var sprite=entry.effect().sprite();
            ResourceLocation texture=ResourceLocation.fromNamespaceAndPath(sprite.texture().getNamespace(),"textures/"+sprite.texture().getPath()+"/"+sprite.state()+".png");
            PoseStack pose=event.getPoseStack(); pose.pushPose();
             try {
                  sample=GunMuzzleFlashClient.resolveSample(sample,com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer.currentRenderGeneration());
                  if(sample==null)continue;
                  Vec3 direction=sample.direction().normalize(); Vec3 visualPosition=entry.endpoint()!=null?sample.origin():sample.pointOrigin();
                  if(entry.endpoint()!=null){
                     if(!sample.hasForwardTravel(1.0e-4))continue;
                     Vec3 beamFrom=visualPosition,beamTo=entry.endpoint();
                     if(beamFrom.distanceTo(beamTo)<=1.0e-5)continue;
                     Vec3 end=beamTo.subtract(camera); float alpha=sample.alpha(); int light=sprite.unshaded()?15728880:LightTexture.FULL_BRIGHT;
                    var frame=ProjectileSpriteFrames.at(sprite,(int)(com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock()-entry.startTick()),partial);
                    var consumer=buffers.getBuffer(RenderType.entityTranslucent(texture)); var mat=pose.last().pose(); var norm=pose.last();
                     Vec3 start=beamFrom.subtract(camera);
                     var alphaFrame=ProjectileVolumeCache.alphaFrame(texture,sprite.frameWidth(),sprite.frameHeight(),sprite.frameColumns(),frame.index(),frame.u0(),frame.u1(),frame.v0(),frame.v1());
                     float capU=alphaFrame.coreU(),capV=alphaFrame.coreV();
                     double radius=Math.max(1,alphaFrame.opaqueShortSpan())/64.0;
                     ClosedEffectMesh.emitQuads(pose.last(),consumer,ClosedEffectMesh.tube(start,end,radius,alphaFrame.u0(),alphaFrame.u1(),alphaFrame.v0(),alphaFrame.v1(),capU,capV),sprite.color(),Math.max(0,Math.min(255,Math.round(alpha*255))),light,sprite.unshaded()); continue;
                }
                 Vec3 p=visualPosition; pose.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);
                   if(entry.effect().worldAligned()) { if(!sprite.noRot()) { Vec3 up=sample.sourceFrame()!=null?sample.sourceFrame().up():new Vec3(0,1,0); pose.mulPose(ProjectileSpritePose.volumeRotation(direction,up,sprite.rotationOffset())); } }
                   else if(!sprite.noRot()) pose.mulPose(ProjectileSpritePose.volumeRotation(direction,new Vec3(0,1,0),sprite.rotationOffset()));
                var frame=ProjectileSpriteFrames.at(sprite,(int)(com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock()-entry.startTick()),partial);
                 var consumer=buffers.getBuffer(RenderType.entityTranslucent(texture));
                 var mesh=ProjectileVolumeCache.frame(texture,sprite.frameWidth(),sprite.frameHeight(),sprite.frameColumns(),frame.index(),frame.u0(),frame.u1(),frame.v0(),frame.v1());
                 int effectAlpha=Math.max(0,Math.min(255,Math.round(255*sample.alpha())));
                 ClosedEffectMesh.emitQuads(pose.last(),consumer,mesh,sprite.color(),effectAlpha,15728880,sprite.unshaded());
            } finally { pose.popPose(); }
        }
        buffers.endBatch();
    }
    private static void beamVertex(com.mojang.blaze3d.vertex.VertexConsumer out,org.joml.Matrix4f matrix,com.mojang.blaze3d.vertex.PoseStack.Pose pose,Vec3 p,float u,float v,float alpha,int light,int color){out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setColor((color>>>16)&255,(color>>>8)&255,color&255,Math.max(0,Math.min(255,Math.round(((color>>>24)&255)*alpha)))).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);}
}
