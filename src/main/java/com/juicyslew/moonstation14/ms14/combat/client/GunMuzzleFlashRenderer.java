package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.combat.ProjectileSpriteFrames;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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
                Vec3 p=entry.position(); pose.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                pose.mulPose(com.mojang.math.Axis.ZP.rotation(com.juicyslew.moonstation14.ms14.combat.ProjectileSpritePose.rotation(entry.direction(),mc.getEntityRenderDispatcher().cameraOrientation(),sprite.noRot())));
                var frame=ProjectileSpriteFrames.at(sprite,(int)(com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock()-entry.startTick()),partial);
                var consumer=buffers.getBuffer(RenderType.entityTranslucent(texture));
                ProjectileSpriteQuadRenderer.emit(pose,consumer,frame,sample.alpha(),sprite.unshaded(),15728880);
            } finally { pose.popPose(); }
        }
        buffers.endBatch();
    }
}
