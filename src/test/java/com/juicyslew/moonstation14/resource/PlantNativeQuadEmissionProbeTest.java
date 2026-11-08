package com.juicyslew.moonstation14.resource;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.PlantInstance;
import com.juicyslew.moonstation14.ms14.botany.client.PlantModelManifest;
import com.juicyslew.moonstation14.ms14.botany.client.PlantTrayBakedModel;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CPU-only synthetic repeated-quad workload through Minecraft's native block renderer. */
class PlantNativeQuadEmissionProbeTest {
    private static final String MODEL = "/assets/moonstation14/models/block/botany/plants/wheat/stage-1.json";
    private static final int PLANTS = 65;
    private static final int WARMUP = 10;
    private static final int SAMPLES = 8;

    @Test
    void probesNativeEmissionForOnePlantGalleryAndDenseSyntheticCase() throws Exception {
        try (var contents = new SpriteContents(ResourceLocation.parse("test:plant"), new FrameSize(16, 16),
                new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            var sprite = new FixtureSprite(contents);
            BlockModel model;
            try (var stream = getClass().getResourceAsStream(MODEL)) {
                assertNotNull(stream, MODEL);
                model = BlockModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            var quads = new ArrayList<BakedQuad>();
            var bakery = new FaceBakery();
            for (var element : model.getElements()) for (var face : element.faces.entrySet())
                quads.add(bakery.bakeQuad(element.from, element.to, face.getValue(), sprite, face.getKey(),
                        BlockModelRotation.X0_Y0, element.rotation, element.shade));
            assertEquals(126, quads.size(), "authored wheat stage-1 native JSON faces baked");

            var renderer = new ModelBlockRenderer(new BlockColors());
            var pose = new PoseStack();
            var state = Blocks.AIR.defaultBlockState();
            var consumer = new CountingConsumer();
            runCase("one-plant", List.of(bakedModel(quads, sprite)), renderer, pose, state, consumer, 126);
            runCase("65-plants-repeated-wheat-stage-1", java.util.Collections.nCopies(PLANTS,
                    bakedModel(quads, sprite)), renderer, pose, state, consumer, PLANTS * 126);
            var denseQuads = new ArrayList<BakedQuad>(1260);
            for (int i = 0; i < 10; i++) denseQuads.addAll(quads);
            var dense = bakedModel(denseQuads, sprite);
            runCase("65-plants-10x-repeated-wheat-stage-1-synthetic", java.util.Collections.nCopies(PLANTS, dense),
                    renderer, pose, state, consumer, PLANTS * 1260);
            try (var backing = new ByteBufferBuilder(16 * 1024 * 1024)) {
                runBufferCase("one-plant", List.of(bakedModel(quads, sprite)), renderer, pose, state, backing, 126);
                runBufferCase("65-plants-repeated-wheat-stage-1", java.util.Collections.nCopies(PLANTS,
                        bakedModel(quads, sprite)), renderer, pose, state, backing, PLANTS * 126);
                runBufferCase("65-plants-10x-repeated-wheat-stage-1-synthetic", java.util.Collections.nCopies(PLANTS, dense),
                        renderer, pose, state, backing, PLANTS * 1260);

                PlantModelManifest manifest = PlantModelManifest.load();
                ModelResourceLocation wheatId = manifest.model("wheat", "stage-1");
                var assets = new PlantTrayBakedModel.NativePlantAssets(java.util.Map.of(wheatId, dense));
                var chunkModel = new PlantTrayBakedModel(bakedModel(List.of(), sprite), manifest, assets);
                var appearance = new HydroponicsTrayBlockEntity.AppearanceSnapshot("wheat", PlantInstance.Status.GROWING, 1);
                BlockPos interior = new BlockPos(4, 4, 4);
                BlockPos boundary = new BlockPos(4, 15, 4);
                assertFalse(PlantTrayBakedModel.shouldRenderInBer(chunkModel, appearance, interior));
                assertTrue(PlantTrayBakedModel.shouldRenderInBer(chunkModel, appearance, boundary));
                runRouteCase("steadyBerPlantEmission-all-65-interior-cached-synthetic", chunkModel, dense, appearance,
                        java.util.Collections.nCopies(PLANTS, interior), renderer, pose, state, backing, 0);
                runRouteCase("steadyBerPlantEmission-all-65-boundary-fallback-synthetic", chunkModel, dense, appearance,
                        java.util.Collections.nCopies(PLANTS, boundary), renderer, pose, state, backing, PLANTS * 1260);
                var mixed = new ArrayList<BlockPos>(PLANTS);
                for (int i = 0; i < 55; i++) mixed.add(interior);
                for (int i = 0; i < 10; i++) mixed.add(boundary);
                runRouteCase("steadyBerPlantEmission-55-interior-10-boundary-synthetic", chunkModel, dense, appearance,
                        mixed, renderer, pose, state, backing, 10 * 1260);
            }
        }
    }

    private static void runCase(String label, List<SimpleBakedModel> plants, ModelBlockRenderer renderer,
                                PoseStack pose, net.minecraft.world.level.block.state.BlockState state,
                                CountingConsumer consumer, int quadsPerFrame) {
        long expectedVertices = (long) quadsPerFrame * 4;
        for (int i = 0; i < WARMUP; i++) emit(plants, renderer, pose, state, consumer);
        long[] times = new long[SAMPLES];
        long[] allocated = new long[SAMPLES];
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocationBean = bean instanceof com.sun.management.ThreadMXBean t
                && t.isThreadAllocatedMemorySupported() ? t : null;
        if (allocationBean != null && !allocationBean.isThreadAllocatedMemoryEnabled()) {
            try { allocationBean.setThreadAllocatedMemoryEnabled(true); } catch (SecurityException ignored) { allocationBean = null; }
        }
        for (int i = 0; i < SAMPLES; i++) {
            consumer.vertices = 0;
            long thread = Thread.currentThread().getId();
            long beforeBytes = allocationBean != null && allocationBean.isThreadAllocatedMemoryEnabled()
                    ? allocationBean.getThreadAllocatedBytes(thread) : -1;
            long start = System.nanoTime();
            emit(plants, renderer, pose, state, consumer);
            times[i] = System.nanoTime() - start;
            long afterBytes = beforeBytes < 0 ? -1 : allocationBean.getThreadAllocatedBytes(thread);
            allocated[i] = beforeBytes < 0 ? -1 : afterBytes - beforeBytes;
            assertEquals(expectedVertices, consumer.vertices, label + " emitted vertices");
        }
        long nanos = java.util.Arrays.stream(times).sum() / SAMPLES;
        long bytes = allocated[0] < 0 ? -1 : java.util.Arrays.stream(allocated).sum() / SAMPLES;
        System.out.printf("PLANT_QUAD_PROBE case=%s plants=%d bakedQuadsPerFrame=%d emittedVerticesPerFrame=%d " +
                        "samples=%d meanUsPerFrame=%.1f allocatedBytesPerFrame=%d consumer=VertexConsumer-no-buffer%n",
                label, plants.size(), quadsPerFrame, expectedVertices, SAMPLES, nanos / 1000.0, bytes);
    }

    private static void emit(List<SimpleBakedModel> plants, ModelBlockRenderer renderer, PoseStack pose,
                             net.minecraft.world.level.block.state.BlockState state, CountingConsumer consumer) {
        for (var model : plants)
            renderer.renderModel(pose.last(), consumer, state, model, 1, 1, 1, 0x00F000F0, 0);
    }

    private static void runBufferCase(String label, List<SimpleBakedModel> plants, ModelBlockRenderer renderer,
                                     PoseStack pose, net.minecraft.world.level.block.state.BlockState state,
                                     ByteBufferBuilder backing, int quadsPerFrame) {
        long expectedVertices = (long) quadsPerFrame * 4;
        int expectedBytes = Math.toIntExact(expectedVertices * DefaultVertexFormat.BLOCK.getVertexSize());
        for (int i = 0; i < WARMUP; i++) {
            var warmup = emitBuffer(plants, renderer, pose, state, backing, expectedVertices, expectedBytes);
            warmup.close();
            backing.clear();
        }
        long[] times = new long[SAMPLES];
        long[] allocated = new long[SAMPLES];
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocationBean = bean instanceof com.sun.management.ThreadMXBean t
                && t.isThreadAllocatedMemorySupported() ? t : null;
        if (allocationBean != null && !allocationBean.isThreadAllocatedMemoryEnabled()) {
            try { allocationBean.setThreadAllocatedMemoryEnabled(true); } catch (SecurityException ignored) { allocationBean = null; }
        }
        for (int i = 0; i < SAMPLES; i++) {
            long thread = Thread.currentThread().getId();
            long beforeBytes = allocationBean != null && allocationBean.isThreadAllocatedMemoryEnabled()
                    ? allocationBean.getThreadAllocatedBytes(thread) : -1;
            long start = System.nanoTime();
            MeshData mesh = emitBuffer(plants, renderer, pose, state, backing, expectedVertices, expectedBytes);
            times[i] = System.nanoTime() - start;
            long afterBytes = beforeBytes < 0 ? -1 : allocationBean.getThreadAllocatedBytes(thread);
            allocated[i] = beforeBytes < 0 ? -1 : afterBytes - beforeBytes;
            mesh.close();
            backing.clear();
        }
        long nanos = java.util.Arrays.stream(times).sum() / SAMPLES;
        long bytes = allocated[0] < 0 ? -1 : java.util.Arrays.stream(allocated).sum() / SAMPLES;
        System.out.printf("PLANT_QUAD_PROBE case=%s plants=%d bakedQuadsPerFrame=%d emittedVerticesPerFrame=%d " +
                        "samples=%d meanUsPerFrame=%.1f allocatedBytesPerFrame=%d consumer=BufferBuilder-BLOCK " +
                        "meshBytes=%d finalization=included upload=excluded%n",
                label, plants.size(), quadsPerFrame, expectedVertices, SAMPLES, nanos / 1000.0, bytes, expectedBytes);
    }

    private static MeshData emitBuffer(List<SimpleBakedModel> plants, ModelBlockRenderer renderer, PoseStack pose,
                                       net.minecraft.world.level.block.state.BlockState state, ByteBufferBuilder backing,
                                       long expectedVertices, int expectedBytes) {
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        for (var model : plants)
            renderer.renderModel(pose.last(), builder, state, model, 1, 1, 1, 0x00F000F0, 0);
        MeshData mesh = builder.buildOrThrow();
        assertEquals(expectedVertices, mesh.drawState().vertexCount(), "native buffer vertex count");
        assertEquals(expectedBytes, mesh.vertexBuffer().remaining(), "native block-format mesh bytes");
        assertTrue(mesh.vertexBuffer().getInt(0) != 0 || mesh.vertexBuffer().getInt(4) != 0 ||
                mesh.vertexBuffer().getInt(8) != 0, "native output contains vertex data");
        return mesh;
    }

    private static void runRouteCase(String label, PlantTrayBakedModel currentModel, BakedModel fallbackModel,
                                     HydroponicsTrayBlockEntity.AppearanceSnapshot appearance, List<BlockPos> positions,
                                     ModelBlockRenderer renderer, PoseStack pose,
                                     net.minecraft.world.level.block.state.BlockState state, ByteBufferBuilder backing,
                                     int expectedQuads) {
        int expectedFallbacks = expectedQuads / 1260;
        int expectedBytes = Math.multiplyExact(expectedQuads * 4, DefaultVertexFormat.BLOCK.getVertexSize());
        for (int i = 0; i < WARMUP; i++) {
            RouteResult result = emitRouteFrame(currentModel, fallbackModel, appearance, positions, renderer, pose, state, backing);
            assertEquals(expectedFallbacks, result.fallbacks);
            if (result.mesh != null) result.mesh.close();
            backing.clear();
        }
        long[] times = new long[SAMPLES];
        long[] allocated = new long[SAMPLES];
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocationBean = bean instanceof com.sun.management.ThreadMXBean t
                && t.isThreadAllocatedMemorySupported() ? t : null;
        if (allocationBean != null && !allocationBean.isThreadAllocatedMemoryEnabled()) {
            try { allocationBean.setThreadAllocatedMemoryEnabled(true); } catch (SecurityException ignored) { allocationBean = null; }
        }
        for (int i = 0; i < SAMPLES; i++) {
            long thread = Thread.currentThread().getId();
            long before = allocationBean != null && allocationBean.isThreadAllocatedMemoryEnabled()
                    ? allocationBean.getThreadAllocatedBytes(thread) : -1;
            long start = System.nanoTime();
            RouteResult result = emitRouteFrame(currentModel, fallbackModel, appearance, positions, renderer, pose, state, backing);
            times[i] = System.nanoTime() - start;
            allocated[i] = before < 0 ? -1 : allocationBean.getThreadAllocatedBytes(thread) - before;
            assertEquals(expectedFallbacks, result.fallbacks, label + " BER fallback count");
            if (expectedBytes == 0) assertNull(result.mesh, label + " must not build an empty native buffer");
            else {
                assertNotNull(result.mesh, label + " fallback mesh");
                assertEquals((long) expectedQuads * 4, result.mesh.drawState().vertexCount());
                assertEquals(expectedBytes, result.mesh.vertexBuffer().remaining());
            }
            if (result.mesh != null) result.mesh.close();
            backing.clear();
        }
        long nanos = java.util.Arrays.stream(times).sum() / SAMPLES;
        long bytes = allocated[0] < 0 ? -1 : java.util.Arrays.stream(allocated).sum() / SAMPLES;
        System.out.printf("PLANT_QUAD_PROBE case=%s trays=%d berFallbacks=%d berEmittedQuadsPerFrame=%d " +
                        "berEmittedVerticesPerFrame=%d berMeshBytes=%d samples=%d meanRouteUsPerFrame=%.1f " +
                        "allocatedBytesPerFrame=%d route=production-PlantTrayBakedModel.shouldRenderInBer " +
                        "chunkMeshDraw=excluded upload=excluded%n", label, positions.size(), expectedFallbacks,
                expectedQuads, expectedQuads * 4, expectedBytes, SAMPLES, nanos / 1000.0, bytes);
    }

    private static RouteResult emitRouteFrame(PlantTrayBakedModel currentModel, BakedModel fallbackModel,
                                              HydroponicsTrayBlockEntity.AppearanceSnapshot appearance,
                                              List<BlockPos> positions, ModelBlockRenderer renderer, PoseStack pose,
                                              net.minecraft.world.level.block.state.BlockState state, ByteBufferBuilder backing) {
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        int fallbacks = 0;
        for (BlockPos pos : positions) {
            if (PlantTrayBakedModel.shouldRenderInBer(currentModel, appearance, pos)) {
                fallbacks++;
                renderer.renderModel(pose.last(), builder, state, fallbackModel, 1, 1, 1, 0x00F000F0, 0);
            }
        }
        return new RouteResult(fallbacks, builder.build());
    }

    private record RouteResult(int fallbacks, MeshData mesh) { }

    private static SimpleBakedModel bakedModel(List<BakedQuad> quads, TextureAtlasSprite sprite) {
        var culled = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (Direction direction : Direction.values()) culled.put(direction, List.of());
        return new SimpleBakedModel(quads, culled, false, true, true, sprite,
                ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
    }

    private static final class CountingConsumer implements VertexConsumer {
        long vertices;
        @Override public VertexConsumer addVertex(float x, float y, float z) { vertices++; return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    private static final class FixtureSprite extends TextureAtlasSprite {
        private FixtureSprite(SpriteContents contents) {
            super(ResourceLocation.parse("test:atlas"), contents, 16, 16, 0, 0);
        }
    }
}
