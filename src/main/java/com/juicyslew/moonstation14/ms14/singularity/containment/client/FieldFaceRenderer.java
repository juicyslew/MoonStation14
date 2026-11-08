package com.juicyslew.moonstation14.ms14.singularity.containment.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.singularity.client.FieldFaceVisualCache;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldFaceSnapshotPayload;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/** Draws only server-authoritative containment planes, never inferred/missing faces. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class FieldFaceRenderer {
    public static final ResourceLocation FIELD_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MoonStation14.MOD_ID, "textures/singularity/containment_field.png");
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MoonStation14.MOD_ID, "textures/singularity/field_substrate.png");
    private FieldFaceRenderer() { }

    @SubscribeEvent
    public static void renderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getPoseStack() == null
                || !snapshotMatchesDimension(FieldFaceVisualCache.dimension(), mc.level.dimension().location())) return;
        render(FieldFaceVisualCache.faces(), event.getPoseStack(), event.getCamera().getPosition(),
                mc.level.getGameTime() / 20.0);
    }

    /** Uses the level's shared source while its particle target is bound; flushes these render types before target changes. */
    public static void render(Collection<FieldFaceSnapshotPayload.Face> faces,
                               com.mojang.blaze3d.vertex.PoseStack pose, Vec3 cameraPosition, double tickTime) {
        if (faces == null || faces.isEmpty() || pose == null || cameraPosition == null) return;
        var source = Minecraft.getInstance().renderBuffers().bufferSource();
        var poseTransform = pose.last();
        var baseType = RenderType.entityTranslucent(WHITE_TEXTURE);
        var overlayType = RenderType.entityTranslucent(FIELD_TEXTURE);
        int frame = frameAtSeconds(tickTime);
        float pulse = .92f + .08f * (float)Math.sin(tickTime * Math.PI * 2 / 1.6);
        var base = source.getBuffer(baseType);
        for (var face : faces) {
            for (Cell cell : cells(face)) {
                double[] p = cell.origin(); int u=cell.uAxis(), v=cell.vAxis();
                // Continuous translucent substrate; a quad per unit keeps exact rectangular face coverage.
                put(base, poseTransform, p, u, v, cameraPosition, 0, 0, 0, 1, FieldPalette.SUBSTRATE_RED, FieldPalette.SUBSTRATE_GREEN, FieldPalette.SUBSTRATE_BLUE, FieldPalette.SUBSTRATE_ALPHA);
                put(base, poseTransform, p, u, v, cameraPosition, 1, 0, 0, 1, FieldPalette.SUBSTRATE_RED, FieldPalette.SUBSTRATE_GREEN, FieldPalette.SUBSTRATE_BLUE, FieldPalette.SUBSTRATE_ALPHA);
                put(base, poseTransform, p, u, v, cameraPosition, 1, 1, 0, 1, FieldPalette.SUBSTRATE_RED, FieldPalette.SUBSTRATE_GREEN, FieldPalette.SUBSTRATE_BLUE, FieldPalette.SUBSTRATE_ALPHA);
                put(base, poseTransform, p, u, v, cameraPosition, 0, 1, 0, 1, FieldPalette.SUBSTRATE_RED, FieldPalette.SUBSTRATE_GREEN, FieldPalette.SUBSTRATE_BLUE, FieldPalette.SUBSTRATE_ALPHA);

            }
        }
        source.endBatch(baseType);
        // Never retain one consumer across a getBuffer call for another type: shared sources may reuse builders.
        var overlay = source.getBuffer(overlayType);
        for (var face : faces) for (Cell cell : cells(face)) {
            double[] p=cell.origin(); int u=cell.uAxis(), v=cell.vAxis();
            float u0=(frame%8)/8f, v0=(frame/8)/8f, u1=u0+1f/8f, v1=v0+1f/8f;
            float alpha=0.22f*pulse;
            put(overlay,poseTransform,p,u,v,cameraPosition,0,0,u0,v1,FieldPalette.OVERLAY_RED,FieldPalette.OVERLAY_GREEN,FieldPalette.OVERLAY_BLUE,alpha);
            put(overlay,poseTransform,p,u,v,cameraPosition,1,0,u1,v1,FieldPalette.OVERLAY_RED,FieldPalette.OVERLAY_GREEN,FieldPalette.OVERLAY_BLUE,alpha);
            put(overlay,poseTransform,p,u,v,cameraPosition,1,1,u1,v0,FieldPalette.OVERLAY_RED,FieldPalette.OVERLAY_GREEN,FieldPalette.OVERLAY_BLUE,alpha);
            put(overlay,poseTransform,p,u,v,cameraPosition,0,1,u0,v0,FieldPalette.OVERLAY_RED,FieldPalette.OVERLAY_GREEN,FieldPalette.OVERLAY_BLUE,alpha);
        }
        source.endBatch(overlayType);
    }

    /** Exact one-cell tiling of a rectangular authoritative face; exposed for focused geometry tests. */
    public static List<Cell> cells(FieldFaceSnapshotPayload.Face face) {
        int axis=face.axis().ordinal(), u=(axis+1)%3, v=(axis+2)%3;
        List<Cell> cells=new ArrayList<>((face.maxU()-face.minU())*(face.maxV()-face.minV()));
        for(int a=face.minU();a<face.maxU();a++) for(int b=face.minV();b<face.maxV();b++) {
            double[] origin=new double[3]; origin[axis]=FieldGeometry.center(face.plane()); origin[u]=FieldGeometry.center(a); origin[v]=FieldGeometry.center(b);
            cells.add(new Cell(origin,u,v));
        }
        return List.copyOf(cells);
    }

    public record Cell(double[] origin, int uAxis, int vAxis) { }

    private static void put(com.mojang.blaze3d.vertex.VertexConsumer out, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                            double[] p, int u, int v, Vec3 camera, int du, int dv,
                             float texU, float texV, float red, float green, float blue, float alpha) {
        double[] q = p.clone(); q[u] += du; q[v] += dv;
        out.addVertex(pose, (float)(q[0]-camera.x), (float)(q[1]-camera.y), (float)(q[2]-camera.z))
                .setColor(red, green, blue, alpha).setUv(texU, texV).setOverlay(0).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, axisNormal(u,v,0), axisNormal(u,v,1), axisNormal(u,v,2));
    }

    private static float axisNormal(int u, int v, int component) { return 3-u-v == component ? 1f : 0f; }

    /** SS14 RSI animation timing: sixteen 0.1-second frames. */
    public static int frameAtSeconds(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return 0;
        return Math.floorMod((int)Math.floor(seconds * 10), 16);
    }

    public static boolean snapshotMatchesDimension(ResourceLocation snapshot, ResourceLocation level) {
        return snapshot != null && snapshot.equals(level);
    }
}
