package com.juicyslew.moonstation14.ms14.devicenet.item.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListSnapshot.MemberPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.OptionalDouble;

/** Client-only rendering for the server-provided membership locations. */
public final class NetworkConfiguratorMembershipOverlay {
    private static final RenderType LINES=RenderType.create("ms14_configurator_membership",DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,1536,RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0))).setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    static { NeoForge.EVENT_BUS.addListener(NetworkConfiguratorMembershipOverlay::render); }
    private NetworkConfiguratorMembershipOverlay() { }
    public static void initialize() { }

    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.getConnection()==null||mc.player==null){NetworkConfiguratorListWindowState.INSTANCE.clearOverlay();return;}
        if(mc.options.hideGui)return;
        var positions=NetworkConfiguratorListWindowState.INSTANCE.overlayPositions(); if(positions.isEmpty())return;
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
        try {
            VertexConsumer lines=buffers.getBuffer(LINES);
            for(MemberPosition member:positions) {
                ResourceLocation dimension=ResourceLocation.tryParse(member.dimension());
                if(dimension==null||!dimension.equals(mc.level.dimension().location()))continue;
                var pos=new net.minecraft.core.BlockPos(member.x(),member.y(),member.z());
                if(!mc.level.hasChunkAt(pos))continue;
                double x=member.x()+.5-camera.x,y=member.y()+.5-camera.y,z=member.z()+.5-camera.z;
                float[][] edges={{-.35f,0,0,.35f,0,0},{0,-.35f,0,0,.35f,0},{0,0,-.35f,0,0,.35f}};
                for(float[] e:edges) {
                    float dx=e[3]-e[0],dy=e[4]-e[1],dz=e[5]-e[2];float nx=-dy,ny=dx,nz=0;float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                    if(len<1e-6f){ny=-dz;nz=dy;len=(float)Math.sqrt(ny*ny+nz*nz);}if(len<1e-6f)continue;nx/=len;ny/=len;nz/=len;
                    vertex(lines,pose,x+e[0],y+e[1],z+e[2],nx,ny,nz);vertex(lines,pose,x+e[3],y+e[4],z+e[5],nx,ny,nz);
                }
            }
            buffers.endBatch(LINES);
        } finally {RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.disableBlend();}
    }
    private static void vertex(VertexConsumer out,com.mojang.blaze3d.vertex.PoseStack pose,double x,double y,double z,float nx,float ny,float nz){
        out.addVertex(pose.last(),(float)x,(float)y,(float)z).setColor(0.2f,1f,.45f,.9f).setNormal(pose.last(),nx,ny,nz);
    }
}
