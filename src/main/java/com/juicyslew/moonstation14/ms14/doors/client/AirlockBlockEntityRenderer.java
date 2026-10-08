package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.doors.AirlockGeometry;
import com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Minecraft 3D adaptation of the upstream 32px airlock art, animated per controller. */
public final class AirlockBlockEntityRenderer implements BlockEntityRenderer<AirlockBlockEntity> {
    private static final ResourceLocation CLOSED = texture("closed");
    private static final ResourceLocation OPEN = texture("open");
    private static final ResourceLocation PANEL = texture("panel_open");
    private static final ResourceLocation BOLTED = texture("bolted_unlit");
    private static final ResourceLocation NEUTRAL_METAL = texture("neutral_metal");

    public AirlockBlockEntityRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public AABB getRenderBoundingBox(AirlockBlockEntity door) {
        return renderBounds(door.getBlockPos());
    }

    static AABB renderBounds(BlockPos root) {
        return AirlockGeometry.renderBounds(root);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "textures/block/airlock/" + name + ".png");
    }

    @Override public void render(AirlockBlockEntity door, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var view = door.clientView();
        AirlockData data = door.prototype();
        if (view == null) return;
        long now = door.getLevel() == null ? 0 : door.getLevel().getGameTime();
        double elapsed = Math.max(0, now + partialTick - view.transitionStartedAt()) / 20.0;
        double openOne = data == null ? 0.4 : data.door().openTimeOneSeconds();
        double openTwo = data == null ? 0.2 : data.door().openTimeTwoSeconds();
        double closeOne = data == null ? 0.4 : data.door().closeTimeOneSeconds();
        double closeTwo = data == null ? 0.2 : data.door().closeTimeTwoSeconds();
        AirlockFramePlanner.Plan plan = AirlockFramePlanner.plan(view.phase(), elapsed, openOne, openTwo,
                closeOne, closeTwo, view.powered(), view.boltsDown() && view.boltLightsVisible(),
                view.panelVisible() && view.panelOpen());
        // Moving 3D leaves always use the static closed artwork. The source 2D
        // opening/closing frames are retained for the frame planner only.

        Direction facing = door.getBlockState().getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Direction.toYRot is the inverse of the positive-Y rotation needed by this +Z-front quad.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        double split = AirlockGeometry.openness(view.phase(),elapsed,openOne,openTwo,closeOne,closeTwo);
        for (DrawLayer layer : drawPlan()) {
            switch (layer) {
                case NEUTRAL_GEOMETRY -> { drawFrame(pose,buffers,packedLight,packedOverlay); drawLeaves(pose,buffers,split,packedLight,packedOverlay); }
                case CLOSED_FRAME -> drawFrameArt(pose,buffers,CLOSED,packedLight,packedOverlay);
                case CLOSED_LEAVES -> drawLeafArt(pose,buffers,CLOSED,split,packedLight,packedOverlay);
            }
        }
        if (plan.glowVisible()) { drawLeafArt(pose,buffers,texture("closed_unlit"),split,0x00F000F0,packedOverlay); drawFrameArt(pose,buffers,texture("closed_unlit"),0x00F000F0,packedOverlay); }
        if (plan.boltOverlay()) { drawLeafArt(pose,buffers,BOLTED,split,packedLight,packedOverlay); drawFrameArt(pose,buffers,BOLTED,packedLight,packedOverlay); }
        if (plan.panelOverlay()) drawPanel(pose, buffers, PANEL, packedLight, packedOverlay);
        pose.popPose();
    }

    enum DrawLayer { NEUTRAL_GEOMETRY, CLOSED_FRAME, CLOSED_LEAVES }
    static java.util.List<DrawLayer> drawPlan() {
        return java.util.List.of(DrawLayer.NEUTRAL_GEOMETRY,DrawLayer.CLOSED_FRAME,DrawLayer.CLOSED_LEAVES);
    }

    private static void drawFrame(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (AirlockGeometry.Box part : AirlockGeometry.frame())
            box(pose,buffers,NEUTRAL_METAL,part.x0(),part.y0(),part.z0(),part.x1(),part.y1(),part.z1(),light,overlay);
    }

    private static void drawLeaves(PoseStack pose, MultiBufferSource buffers, double openness, int light, int overlay) {
        var leaves=AirlockGeometry.leaves(openness);
        var metal=NEUTRAL_METAL;
        for(var leaf:leaves) box(pose,buffers,metal,leaf.x0(),leaf.y0(),leaf.z0(),leaf.x1(),leaf.y1(),leaf.z1(),light,overlay);
    }
    private static void drawFrameArt(PoseStack p,MultiBufferSource b,ResourceLocation art,int l,int o) {
        patch(p,b,art,0,0,.13,2,.921,l,o); patch(p,b,art,.87,0,1,2,.921,l,o);
        patch(p,b,art,.13,0,.87,.13,.921,l,o); patch(p,b,art,.13,1.87,.87,2,.921,l,o);
    }
    private static void drawLeafArt(PoseStack p,MultiBufferSource b,ResourceLocation art,double openness,int l,int o) {
        var leaves=AirlockGeometry.leaves(openness);
        double[] left=AirlockGeometry.leafUv(0),right=AirlockGeometry.leafUv(1);
        leafPatch(p,b,art,leaves.get(0),left[0],left[1],l,o);
        leafPatch(p,b,art,leaves.get(1),right[0],right[1],l,o);
    }
    private static void leafPatch(PoseStack p,MultiBufferSource b,ResourceLocation art,AirlockGeometry.Box leaf,double u0,double closedX1,int l,int o) {
        double xStart=leaf.x0(),xEnd=leaf.x1(), y0=leaf.y0(),y1=leaf.y1(), zf=leaf.z1()+.002,zb=leaf.z0()-.002;
        float lo=(float)u0, hi=(float)closedX1, top=(float)(1-y0/2), bottom=(float)(1-y1/2);
        quad(p,b,art,new AirlockGeometry.Face(new AirlockGeometry.Point(xStart,y0,zf),new AirlockGeometry.Point(xEnd,y0,zf),new AirlockGeometry.Point(xEnd,y1,zf),new AirlockGeometry.Point(xStart,y1,zf),0,0,1),l,o,lo,top,hi,bottom);
        quad(p,b,art,new AirlockGeometry.Face(new AirlockGeometry.Point(xEnd,y0,zb),new AirlockGeometry.Point(xStart,y0,zb),new AirlockGeometry.Point(xStart,y1,zb),new AirlockGeometry.Point(xEnd,y1,zb),0,0,-1),l,o,hi,top,lo,bottom);
    }
    private static void patch(PoseStack p,MultiBufferSource b,ResourceLocation art,double x0,double y0,double x1,double y1,double z,int l,int o) {
        double u0=x0,u1=x1,v0=1-y0/2,v1=1-y1/2;
        var front=new AirlockGeometry.Face(new AirlockGeometry.Point(x0,y0,z),new AirlockGeometry.Point(x1,y0,z),new AirlockGeometry.Point(x1,y1,z),new AirlockGeometry.Point(x0,y1,z),0,0,1);
        quad(p,b,art,front,l,o,(float)u0,(float)v0,(float)u1,(float)v1);
        // Keep the rear artwork just outside every base cuboid face to avoid z-fighting.
        var back=new AirlockGeometry.Face(new AirlockGeometry.Point(x1,y0,.078),new AirlockGeometry.Point(x0,y0,.078),new AirlockGeometry.Point(x0,y1,.078),new AirlockGeometry.Point(x1,y1,.078),0,0,-1);
        quad(p,b,art,back,l,o,(float)u1,(float)v0,(float)u0,(float)v1);
    }
    private static void drawPanel(PoseStack pose, MultiBufferSource buffers, ResourceLocation art, int light, int overlay) {
        // Visible hinged maintenance hatch above the status lamp; not an access lock.
        AirlockGeometry.Box hatch=AirlockGeometry.hatch(true);
        PanelUv uv=panelUv();
        double[] planes=AirlockGeometry.hatchPlanes();
        var f=new AirlockGeometry.Face(new AirlockGeometry.Point(hatch.x0(),hatch.y0(),planes[0]),new AirlockGeometry.Point(hatch.x1(),hatch.y0(),planes[0]),new AirlockGeometry.Point(hatch.x1(),hatch.y1(),planes[0]),new AirlockGeometry.Point(hatch.x0(),hatch.y1(),planes[0]),0,0,1);
        quad(pose,buffers,art,f,light,overlay,uv.u0(),uv.vAtBottom(),uv.u1(),uv.vAtTop());
        // Put the rear hatch beyond the rear cuboid face, not inside the front
        // slab (the hatch's Box describes its front-side depth).
        var back=new AirlockGeometry.Face(new AirlockGeometry.Point(hatch.x1(),hatch.y0(),planes[1]),new AirlockGeometry.Point(hatch.x0(),hatch.y0(),planes[1]),new AirlockGeometry.Point(hatch.x0(),hatch.y1(),planes[1]),new AirlockGeometry.Point(hatch.x1(),hatch.y1(),planes[1]),0,0,-1);
        quad(pose,buffers,art,back,light,overlay,uv.u1(),uv.vAtBottom(),uv.u0(),uv.vAtTop());
    }
    record PanelUv(float u0,float vAtTop,float u1,float vAtBottom) { }
    static PanelUv panelUv() {
        // Exact nontransparent panel_open.png bounds: x=23..27, y=14..25
        // inclusive in its 32x32 canvas. Convert pixel-edge bounds to UVs.
        return new PanelUv(23f/32f,14f/32f,28f/32f,26f/32f);
    }
    private static void box(PoseStack p, MultiBufferSource b, ResourceLocation t,double x0,double y0,double z0,double x1,double y1,double z1,int l,int o) {
        for(AirlockGeometry.Face f:new AirlockGeometry.Box(x0,y0,z0,x1,y1,z1).faces())
            quad(p,b,t,f,l,o);
    }
    private static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,AirlockGeometry.Face f,int light,int overlay) {
        quad(pose,buffers,texture,f,light,overlay,0,1,1,0);
    }
    private static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,AirlockGeometry.Face f,int light,int overlay,float u0,float v0,float u1,float v1) {
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture)); var matrix=pose.last().pose();
        var normal=pose.last().normal().transform(new org.joml.Vector3f(f.nx(),f.ny(),f.nz())).normalize();
        AirlockGeometry.Point[] points={f.a(),f.b(),f.c(),f.d()};
        float[][] uv={{u0,v0},{u1,v0},{u1,v1},{u0,v1}};
        for(int i=0;i<4;i++) { var point=points[i]; vertex(out,matrix,normal,(float)point.x(),(float)point.y(),(float)point.z(),uv[i][0],uv[i][1],light,overlay); }
    }

    private static void vertex(VertexConsumer out, org.joml.Matrix4f pose, org.joml.Vector3f normal,
                               float x, float y, float z, float u, float v, int light, int overlay) {
        out.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(normal.x, normal.y, normal.z);
    }
}
