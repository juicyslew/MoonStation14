package com.juicyslew.moonstation14.entities.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorParticleProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renders the resolved SS14 sprite family at the entity's synchronized strength state. */
public final class ParticleAcceleratorParticleRenderer extends EntityRenderer<ParticleAcceleratorParticleEntity> {
    public ParticleAcceleratorParticleRenderer(EntityRendererProvider.Context context) { super(context); }
    private static ResourceLocation texture(ParticleAcceleratorParticleEntity entity) {
        ParticleAcceleratorParticleProfile profile=entity.resolvedProfile();
        ResourceLocation stem=profile==null?null:ResourceLocation.tryParse(profile.sprite());
        if(stem==null) stem=ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"textures/entity/pa_particle/particle");
        return ResourceLocation.fromNamespaceAndPath(stem.getNamespace(),stem.getPath()+Math.max(0,Math.min(3,entity.visualStrength()))+".png");
    }
    @Override public void render(ParticleAcceleratorParticleEntity entity,float yaw,float partialTick,PoseStack pose,
                                 MultiBufferSource buffers,int light) {
        pose.pushPose();
        try {
            pose.scale(.5f,.5f,.5f);
            float[] uv=ParticleAcceleratorFrameUv.bounds(entity.tickCount+(int)(partialTick*2),
                    ParticleAcceleratorFrameUv.direction(entity.getDeltaMovement().x,entity.getDeltaMovement().z));
            ProjectileBillboard.renderCrossed(pose,buffers,RenderType.entityCutoutNoCull(texture(entity)),15728880,
                    uv[0],uv[1],uv[2],uv[3]);
        } finally { pose.popPose(); }
        super.render(entity,yaw,partialTick,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(ParticleAcceleratorParticleEntity entity) { return texture(entity); }
}
