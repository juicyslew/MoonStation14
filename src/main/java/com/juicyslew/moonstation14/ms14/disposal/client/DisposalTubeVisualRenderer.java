package com.juicyslew.moonstation14.ms14.disposal.client;

import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Client-only thick disposal mesh. This is a Minecraft geometry adaptation, not an upstream 3D asset. */
public final class DisposalTubeVisualRenderer {
    private DisposalTubeVisualRenderer() { }
    public static void render(RenderLevelStageEvent event, DisposalTubeClientCache cache) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var minecraft=Minecraft.getInstance();if(minecraft.level==null||minecraft.player==null)return;
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();if(pose==null)return;
        ResourceLocation dim=minecraft.level.dimension().location();
        var records=cache.visible(dim,camera.x,camera.y,camera.z,48);
        if(records.isEmpty())return;
        MultiBufferSource.BufferSource buffers=minecraft.renderBuffers().bufferSource();
        for(DisposalTubeRecord record:records){
            BlockPos pos=record.pos();
            if(record.hostKind()!=DisposalTubeRecord.HostKind.EXPOSED || !minecraft.level.getBlockState(pos).is(com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_TUBE.get()))continue;
            ResourceLocation prototypeId=record.prototypeId().indexOf(':')<0?ModDisposals.id(record.prototypeId()):ResourceLocation.tryParse(record.prototypeId());
            if(prototypeId==null||!prototypeId.getNamespace().equals(ModDisposals.BASE.getNamespace()))continue;
            var data=PrototypeRuntime.clientDisposals().get(prototypeId);
            if(data==null||data.tube()==null||data.geometry()==null)continue;
            ResourceLocation frame=DisposalTubePresentation.texture(record.prototypeId());if(frame==null)continue;
            int index=DisposalTubePresentation.frame(record.frame());
            frame=ResourceLocation.fromNamespaceAndPath(frame.getNamespace(),frame.getPath()+index+".png");
            var consumer=buffers.getBuffer(RenderType.entityCutout(frame));
            double ox=pos.getX()-camera.x,oy=pos.getY()-camera.y,oz=pos.getZ()-camera.z;
            renderGhostBoxes(consumer,pose,data.geometry().boxes(data.tube(),record.frame()),ox,oy,oz,255,255,255,255);
        }
        buffers.endBatch();
    }
    /** Shared geometry path for construction ghosts; the caller owns selection and validity state. */
    public static void renderGhostBoxes(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack stack,java.util.List<net.minecraft.world.phys.AABB> boxes,double ox,double oy,double oz,int red,int green,int blue,int alpha){
        for(var box:unionBoxes(boxes))drawBox(out,stack,box.minX+ox,box.minY+oy,box.minZ+oz,box.maxX+ox,box.maxY+oy,box.maxZ+oz,red,green,blue,alpha);
    }
    /** POSITION_COLOR mesh for previews: no block-atlas UVs, lighting, or texture sampling. */
    public static void renderColoredGhostBoxes(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack stack,java.util.List<net.minecraft.world.phys.AABB> boxes,double ox,double oy,double oz,int red,int green,int blue,int alpha){
        for(var box:unionBoxes(boxes))drawColoredBox(out,stack,box.minX+ox,box.minY+oy,box.minZ+oz,box.maxX+ox,box.maxY+oy,box.maxZ+oz,red,green,blue,alpha);
    }
    private static java.util.List<net.minecraft.world.phys.AABB> unionBoxes(java.util.List<net.minecraft.world.phys.AABB> boxes){
        var union=net.minecraft.world.phys.shapes.Shapes.empty();for(var box:boxes)union=net.minecraft.world.phys.shapes.Shapes.or(union,net.minecraft.world.phys.shapes.Shapes.create(box));
        return union.optimize().toAabbs();
    }
    private static void drawBox(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack stack,double x0,double y0,double z0,double x1,double y1,double z1,int red,int green,int blue,int alpha){
        var p=stack.last(); face(out,p,x0,y0,z0,x1,y1,z1,net.minecraft.core.Direction.DOWN,red,green,blue,alpha);
        face(out,p,x0,y0,z0,x1,y1,z1,net.minecraft.core.Direction.UP,red,green,blue,alpha);face(out,p,x0,y0,z0,x1,y1,z1,net.minecraft.core.Direction.NORTH,red,green,blue,alpha);
        face(out,p,x0,y0,z0,x1,y1,z1,net.minecraft.core.Direction.SOUTH,red,green,blue,alpha);face(out,p,x0,y0,z0,x1,y1,z1,net.minecraft.core.Direction.WEST,red,green,blue,alpha);face(out,p,x0,y0,z0,x1,y1,z1,net.minecraft.core.Direction.EAST,red,green,blue,alpha);
    }
    private static void face(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack.Pose p,double x,double y,double z,double X,double Y,double Z,net.minecraft.core.Direction d,int red,int green,int blue,int alpha){
        double[][] q=switch(d){case DOWN->new double[][]{{x,y,z},{x,y,Z},{X,y,Z},{X,y,z}};case UP->new double[][]{{x,Y,z},{X,Y,z},{X,Y,Z},{x,Y,Z}};case NORTH->new double[][]{{X,y,z},{x,y,z},{x,Y,z},{X,Y,z}};case SOUTH->new double[][]{{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};case WEST->new double[][]{{x,y,z},{x,y,Z},{x,Y,Z},{x,Y,z}};case EAST->new double[][]{{X,y,Z},{X,y,z},{X,Y,z},{X,Y,Z}};};
        int[][] uv={{0,0},{0,1},{1,1},{1,0}};for(int i=0;i<4;i++)out.addVertex(p,(float)q[i][0],(float)q[i][1],(float)q[i][2]).setColor(red,green,blue,alpha).setUv(uv[i][0],uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00f000f0).setNormal(p,d.getStepX(),d.getStepY(),d.getStepZ());
    }
    private static void drawColoredBox(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack stack,double x,double y,double z,double X,double Y,double Z,int r,int g,int b,int a){
        var p=stack.last();for(var d:net.minecraft.core.Direction.values()){
            double[][] q=switch(d){case DOWN->new double[][]{{x,y,z},{x,y,Z},{X,y,Z},{X,y,z}};case UP->new double[][]{{x,Y,z},{X,Y,z},{X,Y,Z},{x,Y,Z}};case NORTH->new double[][]{{X,y,z},{x,y,z},{x,Y,z},{X,Y,z}};case SOUTH->new double[][]{{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};case WEST->new double[][]{{x,y,z},{x,y,Z},{x,Y,Z},{x,Y,z}};case EAST->new double[][]{{X,y,Z},{X,y,z},{X,Y,z},{X,Y,Z}};};
            for(var v:q)out.addVertex(p,(float)v[0],(float)v[1],(float)v[2]).setColor(r,g,b,a);
        }
    }
}
