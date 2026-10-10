package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockGeometry;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.WeakHashMap;

/** Draws the exported baked component models, clipping animated panels in canonical door space. */
public final class AirlockBlockEntityRenderer implements BlockEntityRenderer<AirlockBlockEntity> {
    private static final String BASE = "block/airlock/vertical_all/standard_basic/";
    private static final List<String> COMPONENTS = java.util.Arrays.stream(AirlockGeometry.Part.values())
            .map(part -> part.name().toLowerCase(java.util.Locale.ROOT)).toList();
    private static final ResourceLocation BLOCK_ATLAS = TextureAtlas.LOCATION_BLOCKS;
    private static final Map<BakedModel, List<DecodedQuad>> DECODED_MODELS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private record LocalVertex(float x, float y, float z, float u, float v) { }
    private record DecodedQuad(List<LocalVertex> vertices, Direction direction, boolean shade, ResourceLocation sprite) { }

    public AirlockBlockEntityRenderer(BlockEntityRendererProvider.Context context) { }

    public static List<ModelResourceLocation> additionalModels() {
        ArrayList<ModelResourceLocation> models = new ArrayList<>();
        for (String component : COMPONENTS) models.add(modelLocation(BASE + "components/" + component));
        for (String state : List.of("closed_unlit_00_header", "opening_unlit_00_header", "opening_unlit_00_reader",
                "bolted_unlit_00_header", "deny_unlit_00_header", "deny_unlit_00_reader"))
            models.add(modelLocation(BASE + "overlays/" + state));
        return List.copyOf(models);
    }

    private static ModelResourceLocation modelLocation(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path));
    }

    @Override public AABB getRenderBoundingBox(AirlockBlockEntity door) {
        return AirlockGeometry.renderBounds(door.getBlockPos());
    }

    @Override public void render(AirlockBlockEntity door, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var view = door.clientView();
        if (view == null) return;
        long now = door.getLevel() == null ? 0 : door.getLevel().getGameTime();
        double elapsed = Math.max(0, now + partialTick - view.transitionStartedAt()) / 20.0;
        AirlockData data = door.prototype();
        double openOne = data == null ? .4 : data.door().openTimeOneSeconds();
        double openTwo = data == null ? .2 : data.door().openTimeTwoSeconds();
        double closeOne = data == null ? .4 : data.door().closeTimeOneSeconds();
        double closeTwo = data == null ? .2 : data.door().closeTimeTwoSeconds();
        double openness = AirlockGeometry.openness(view.phase(), elapsed, openOne, openTwo, closeOne, closeTwo);
        AirlockFramePlanner.Plan presentation = AirlockFramePlanner.plan(view.phase(), view.powered(),
                view.boltsDown(), view.boltLightsVisible(), view.panelVisible(), view.panelOpen());
        pose.pushPose();
        pose.translate(.5, 0, .5);
        // Exported components face +Z; the canonical airlock front is -Z, so turn them 180 degrees.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        Direction facing = door.getBlockState().getValue(AirlockBlock.FACING);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-.5, 0, -.5);
        var manager = Minecraft.getInstance().getModelManager();
        drawPart(manager,"fixed",pose,buffers,packedLight,packedOverlay,0,0,false);
        drawPart(manager,"header_lamps",pose,buffers,packedLight,packedOverlay,0,0,false);
        drawPart(manager,"reader_housing",pose,buffers,packedLight,packedOverlay,0,
                AirlockGeometry.movementY(AirlockGeometry.Part.READER_HOUSING,openness),false);
        double upper = AirlockGeometry.movementY(AirlockGeometry.Part.UPPER_LEAF,openness);
        double lower = AirlockGeometry.movementY(AirlockGeometry.Part.LOWER_LEAF,openness);
        drawPart(manager,"upper_leaf",pose,buffers,packedLight,packedOverlay,0,upper,false);
        drawPart(manager,"lower_leaf",pose,buffers,packedLight,packedOverlay,0,lower,false);
        drawPart(manager,"upper_infill",pose,buffers,packedLight,packedOverlay,0,AirlockGeometry.movementY(AirlockGeometry.Part.UPPER_INFILL,openness),false);
        if (presentation.maintenanceWires()) {
            drawPart(manager,"maintenance_wires",pose,buffers,packedLight,packedOverlay,0,AirlockGeometry.movementY(AirlockGeometry.Part.MAINTENANCE_WIRES,openness),false);
        } else {
            drawPart(manager,"reader_cover",pose,buffers,packedLight,packedOverlay,0,AirlockGeometry.movementY(AirlockGeometry.Part.READER_COVER,openness),false);
        }
        if (presentation.headerOverlay() != null)
            drawComponent(manager.getModel(modelLocation(BASE+"overlays/"+presentation.headerOverlay()+"_00_header")),
                    pose,buffers,packedLight,packedOverlay,0,0,true,view.powered());
        if (presentation.readerOverlay() != null)
            drawComponent(manager.getModel(modelLocation(BASE+"overlays/"+presentation.readerOverlay()+"_00_reader")),
                    pose,buffers,packedLight,packedOverlay,0,
                    AirlockGeometry.movementY(AirlockGeometry.Part.UPPER_LEAF,openness),true,view.powered());
        pose.popPose();
    }

    private static void drawPart(net.minecraft.client.resources.model.ModelManager manager,String part,PoseStack pose,
                                 MultiBufferSource buffers,int light,int overlay,double x,double y,boolean powered) {
        drawComponent(manager.getModel(modelLocation(BASE+"components/"+part)),pose,buffers,light,overlay,x,y,true,powered);
    }

    private static void drawComponent(BakedModel model, PoseStack pose, MultiBufferSource buffers,
                                       int light, int overlay, double xTranslation, double yTranslation,
                                      boolean clip, boolean powered) {
        if (model == null) return;
        for (DecodedQuad quad : decoded(model)) {
            TextureAtlasSprite target = Minecraft.getInstance().getTextureAtlas(BLOCK_ATLAS).apply(quad.sprite());
            if (target == null) continue;
            emitQuad(quad, pose, buffers, light, overlay, target, xTranslation, yTranslation, clip, powered);
        }
    }

    private static List<DecodedQuad> decoded(BakedModel model) {
        synchronized (DECODED_MODELS) {
            return DECODED_MODELS.computeIfAbsent(model, AirlockBlockEntityRenderer::decode);
        }
    }

    private static List<DecodedQuad> decode(BakedModel model) {
        ArrayList<DecodedQuad> decoded = new ArrayList<>();
        for (Direction direction : Direction.values())
            decodeQuads(model.getQuads(null, direction, RandomSource.create(0)), decoded);
        decodeQuads(model.getQuads(null, null, RandomSource.create(0)), decoded);
        return List.copyOf(decoded);
    }

    private static void decodeQuads(List<net.minecraft.client.renderer.block.model.BakedQuad> quads,
                                   List<DecodedQuad> decoded) {
        for (var quad : quads) {
            int[] raw = quad.getVertices();
            TextureAtlasSprite source = quad.getSprite();
            float width = source.getU1() - source.getU0(), height = source.getV1() - source.getV0();
            ArrayList<LocalVertex> vertices = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                int offset = i * 8;
                float localU = width == 0 ? 0 : (Float.intBitsToFloat(raw[offset + 4]) - source.getU0()) / width;
                float localV = height == 0 ? 0 : (Float.intBitsToFloat(raw[offset + 5]) - source.getV0()) / height;
                vertices.add(new LocalVertex(Float.intBitsToFloat(raw[offset]), Float.intBitsToFloat(raw[offset + 1]),
                        Float.intBitsToFloat(raw[offset + 2]), localU, localV));
            }
            decoded.add(new DecodedQuad(List.copyOf(vertices), quad.getDirection(), quad.isShade(), source.contents().name()));
        }
    }

    private static void emitQuad(DecodedQuad quad, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                 TextureAtlasSprite target, double xTranslation,double yTranslation, boolean shouldClip, boolean powered) {
        boolean inside = true;
        for (LocalVertex vertex : quad.vertices()) {
            float x = vertex.x() + (float) xTranslation;
            float y=vertex.y()+(float)yTranslation;
            if (x < 0 || x > 1 || y < 0 || y > 2 || vertex.z() < 0 || vertex.z() > 1) {
                inside = false;
                break;
            }
        }
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(BLOCK_ATLAS));
        var matrix = pose.last().pose();
        Vector3f normal = pose.last().normal().transform(new Vector3f(quad.direction().getStepX(),
                quad.direction().getStepY(), quad.direction().getStepZ())).normalize();
        int vertexLight = quadLight(quad.shade(), powered, light);
        if (!shouldClip || inside) {
            for (LocalVertex vertex : quad.vertices())
                vertex(out, matrix, normal, vertex.x() + (float) xTranslation, vertex.y()+(float)yTranslation, vertex.z(),
                        remapAtlasCoordinate(vertex.u(), target.getU0(), target.getU1()),
                        remapAtlasCoordinate(vertex.v(), target.getV0(), target.getV1()), vertexLight, overlay);
            return;
        }
        ArrayList<AirlockQuadClipper.Vertex> translated = new ArrayList<>(4);
        for (LocalVertex vertex : quad.vertices()) translated.add(toVertex(vertex, target, xTranslation,yTranslation));
        List<AirlockQuadClipper.Vertex> polygon = AirlockQuadClipper.clip(translated);
        if (polygon.size() < 3) return;
        if (polygon.size() == 4) {
            for (AirlockQuadClipper.Vertex vertex : polygon) vertex(out, matrix, normal, vertex, vertexLight, overlay);
        } else {
            // Clipping can turn one quad into a polygon. Fan-triangulate only that uncommon case;
            // duplicate each triangle's last vertex to fit the four-vertex consumer format.
            for (int i = 1; i < polygon.size() - 1; i++) {
                var a = polygon.get(0); var b = polygon.get(i); var c = polygon.get(i + 1);
                vertex(out, matrix, normal, a, vertexLight, overlay);
                vertex(out, matrix, normal, b, vertexLight, overlay);
                vertex(out, matrix, normal, c, vertexLight, overlay);
                vertex(out, matrix, normal, c, vertexLight, overlay);
            }
        }
    }

    private static AirlockQuadClipper.Vertex toVertex(LocalVertex vertex, TextureAtlasSprite target, double xTranslation,double yTranslation) {
        return new AirlockQuadClipper.Vertex(vertex.x() + (float) xTranslation, vertex.y()+(float)yTranslation, vertex.z(),
                remapAtlasCoordinate(vertex.u(), target.getU0(), target.getU1()),
                remapAtlasCoordinate(vertex.v(), target.getV0(), target.getV1()));
    }

    static float remapAtlasCoordinate(float localCoordinate, float low, float high) {
        return low + (high - low) * localCoordinate;
    }

    static int quadLight(boolean shaded, boolean powered, int packedLight) {
        return !shaded && powered ? 0x00F000F0 : packedLight;
    }

    private static void vertex(VertexConsumer out, org.joml.Matrix4f matrix, Vector3f normal,
                               AirlockQuadClipper.Vertex vertex, int light, int overlay) {
        vertex(out, matrix, normal, vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v(), light, overlay);
    }

    private static void vertex(VertexConsumer out, org.joml.Matrix4f matrix, Vector3f normal,
                               float x, float y, float z, float u, float v, int light, int overlay) {
        out.addVertex(matrix, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay)
                .setLight(light).setNormal(normal.x, normal.y, normal.z);
    }
}
