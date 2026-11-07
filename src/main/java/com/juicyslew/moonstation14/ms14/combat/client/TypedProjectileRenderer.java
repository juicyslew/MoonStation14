package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.entities.TypedProjectileEntity;
import com.juicyslew.moonstation14.ms14.combat.ProjectileProfile;
import com.juicyslew.moonstation14.ms14.combat.ProjectileSpriteFrames;
import com.juicyslew.moonstation14.ms14.combat.ProjectileSpritePose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class TypedProjectileRenderer extends EntityRenderer<TypedProjectileEntity> {
    public TypedProjectileRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(TypedProjectileEntity entity) {
        ProjectileProfile.SpriteData sprite=entity.visual();
        return sprite==null?null:ResourceLocation.fromNamespaceAndPath(sprite.texture().getNamespace(),"textures/"+sprite.texture().getPath()+"/"+sprite.state()+".png");
    }
    @Override public void render(TypedProjectileEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        ProjectileProfile.SpriteData s=e.visual(); if(s==null)return;
        pose.pushPose();
        if(!s.noRot()) pose.mulPose(ProjectileSpritePose.volumeRotation(e.getDeltaMovement(),new net.minecraft.world.phys.Vec3(0,1,0),s.rotationOffset()));
        ProjectileSpriteFrames.Frame frame=ProjectileSpriteFrames.at(s,e.tickCount,partial);
        var out=buffers.getBuffer(RenderType.entityCutout(getTextureLocation(e)));
        var mesh=ProjectileVolumeCache.frame(getTextureLocation(e),s.frameWidth(),s.frameHeight(),s.frameColumns(),frame.index(),frame.u0(),frame.u1(),frame.v0(),frame.v1());
        ClosedEffectMesh.emitQuads(pose.last(),out,mesh,s.color(),255,light,s.unshaded());
        pose.popPose(); super.render(e,yaw,partial,pose,buffers,light);
    }
}
