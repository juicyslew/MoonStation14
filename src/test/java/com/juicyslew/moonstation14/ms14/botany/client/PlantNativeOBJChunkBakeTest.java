package com.juicyslew.moonstation14.ms14.botany.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.PlantInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
import net.neoforged.neoforge.client.model.obj.ObjModel;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CPU-only compatibility regression: the production OBJ geometry baked by NeoForge survives chunk calls. */
class PlantNativeOBJChunkBakeTest {
    private static ResourceManager loaderResources;
    private static PackResources loaderPack;
    private static final String ROOT = "/assets/moonstation14/models/block/botany/plants/apple/";
    private static final ResourceLocation OBJ = ResourceLocation.parse("moonstation14:models/block/botany/plants/apple/harvest.obj");
    private static final ResourceLocation MTL = ResourceLocation.parse("moonstation14:models/block/botany/plants/apple/harvest.mtl");
    private static final ResourceLocation ATLAS = ResourceLocation.parse("moonstation14:block/botany/plants/apple/harvest");

    @Test
    void nativeBananaPeelObjBakeHas66TexturedQuadsAndGroundsAtProfileTransform() {
        assertItemModelParent("trash_banana_peel", "moonstation14:item/botany/peels/trash_banana_peel");
        assertItemModelParent("trash_mimana_peel", "moonstation14:item/botany/peels/trash_mimana_peel");
        assertEquipmentBinding("trash_banana_peel", "botany_banana_peel");
        assertEquipmentBinding("trash_mimana_peel", "botany_mimana_peel");
        assertPeelNativeBake("trash_banana_peel");
        assertPeelNativeBake("trash_mimana_peel");
    }

    private static void assertItemModelParent(String item, String parent) {
        String json = classpathText("/assets/moonstation14/models/item/" + item + ".json");
        assertTrue(json.contains("\"parent\": \"" + parent + "\""), item + " canonical item parent");
        String model = classpathText("/assets/moonstation14/models/item/botany/peels/" + item + ".json");
        assertTrue(model.contains("\"loader\": \"neoforge:obj\""));
        assertTrue(model.contains("\"model\": \"moonstation14:models/item/botany/peels/" + item + ".obj\""));
    }

    private static void assertEquipmentBinding(String item, String mesh) {
        String profile = classpathText("/data/moonstation14/moonstation14/equipment_item/" + item + ".json");
        assertTrue(profile.contains("moonstation14:wearables/meshes/hats/" + mesh + ".json"), item + " retained worn mesh binding");
    }

    private static String classpathText(String path) {
        try (InputStream input = PlantNativeOBJChunkBakeTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "Missing production resource " + path);
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static void assertPeelNativeBake(String peel) {
        String root = "/assets/moonstation14/models/item/botany/peels/";
        ResourceLocation obj = ResourceLocation.parse("moonstation14:models/item/botany/peels/" + peel + ".obj");
        ResourceLocation mtl = ResourceLocation.parse("moonstation14:models/item/botany/peels/" + peel + ".mtl");
        ResourceLocation atlas = ResourceLocation.parse("moonstation14:item/botany/peels/" + peel);
        try (var contents = new SpriteContents(ResourceLocation.parse("test:" + peel), new FrameSize(16, 16),
                     new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            when(loaderResources.getResource(obj)).thenReturn(java.util.Optional.of(new Resource(loaderPack, () -> classpath(root + peel + ".obj"))));
            when(loaderResources.getResource(mtl)).thenReturn(java.util.Optional.of(new Resource(loaderPack, () -> classpath(root + peel + ".mtl"))));
            var unbaked = ObjLoader.INSTANCE.loadModel(new ObjModel.ModelSettings(obj, false, true, false, false, null));
            var sprite = new FixtureSprite(contents);
            var baked = unbaked.bake(StandaloneGeometryBakingContext.create(ResourceLocation.parse("moonstation14:" + peel),
                            Map.of("atlas", atlas, "particle", atlas)), mock(ModelBaker.class), material -> sprite,
                    BlockModelRotation.X0_Y0, ItemOverrides.EMPTY);
            var quads = baked.getQuads(null, null, RandomSource.create(0));
            assertEquals(66, quads.size(), peel + " native OBJ quads");
            assertTrue(quads.stream().allMatch(q -> q.getSprite() == sprite));
            double minY = Double.POSITIVE_INFINITY;
            for (var quad : quads) for (int vertex = 0; vertex < 4; vertex++) {
                float y = Float.intBitsToFloat(quad.getVertices()[vertex * 8 + 1]);
                minY = Math.min(minY, y);
            }
            // WorldItemPresentationRenderer uses NONE (item renderer's centered mesh), then 1.2 scale + .6 floor offset.
            assertEquals(0d, 1.2d * (minY - .5d) + .6d, 1e-5, peel + " ground-contact after world pose");
            assertTrue(classpathResource("/assets/moonstation14/textures/item/botany/peels/" + peel + ".png"));
        }
    }

    private static boolean classpathResource(String path) {
        try (InputStream input = PlantNativeOBJChunkBakeTest.class.getResourceAsStream(path)) { return input != null; }
        catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    @Test
    void nativeAppleHarvestObjHasEquivalentLegacyAndChunkCutoutGeometry() {
        var minecraft = mock(Minecraft.class);
        var resources = mock(ResourceManager.class);
        loaderResources = resources;
        when(minecraft.getResourceManager()).thenReturn(resources);
        var pack = mock(PackResources.class);
        loaderPack = pack;
        when(pack.packId()).thenReturn("test-apple-obj");
        // This scope must precede ObjLoader's first class initialization: its native singleton
        // captures Minecraft's manager in its constructor, and the fixture keeps that real loader path.
        try (MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class);
             var spriteContents = new SpriteContents(ResourceLocation.parse("test:apple"), new FrameSize(16, 16),
                     new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(minecraft);
            when(resources.getResource(OBJ)).thenReturn(java.util.Optional.of(new Resource(pack,
                    () -> classpath(ROOT + "harvest.obj"))));
            when(resources.getResource(MTL)).thenReturn(java.util.Optional.of(new Resource(pack,
                    () -> classpath(ROOT + "harvest.mtl"))));
            for (String peel : List.of("trash_banana_peel", "trash_mimana_peel")) {
                String peelRoot = "/assets/moonstation14/models/item/botany/peels/";
                var peelObj = ResourceLocation.parse("moonstation14:models/item/botany/peels/" + peel + ".obj");
                var peelMtl = ResourceLocation.parse("moonstation14:models/item/botany/peels/" + peel + ".mtl");
                when(resources.getResource(peelObj)).thenReturn(java.util.Optional.of(new Resource(pack, () -> classpath(peelRoot + peel + ".obj"))));
                when(resources.getResource(peelMtl)).thenReturn(java.util.Optional.of(new Resource(pack, () -> classpath(peelRoot + peel + ".mtl"))));
            }

            ObjModel unbaked = ObjLoader.INSTANCE.loadModel(new ObjModel.ModelSettings(OBJ, false, true, false, false, null));
            assertFalse(unbaked.getRootComponentNames().isEmpty(), "real OBJ groups were parsed");
            var sprite = new FixtureSprite(spriteContents);
            var context = StandaloneGeometryBakingContext.create(ResourceLocation.parse("moonstation14:apple_harvest"),
                    Map.of("atlas", ATLAS, "particle", ATLAS));
            var baked = unbaked.bake(context, mock(ModelBaker.class), material -> sprite,
                    BlockModelRotation.X0_Y0, ItemOverrides.EMPTY);

            int legacyCount = baked.getQuads(null, null, RandomSource.create(0)).size();
            assertTrue(legacyCount > 0, "native baked apple OBJ has unculled plant geometry");
            for (Direction side : Direction.values()) {
                List<BakedQuad> legacy = baked.getQuads(null, side, RandomSource.create(0));
                List<BakedQuad> chunk = baked.getQuads(null, side, RandomSource.create(0), ModelData.EMPTY, RenderType.cutout());
                assertEquals(legacy.size(), chunk.size(), "native side quads " + side);
            }
            List<BakedQuad> oldUnculled = baked.getQuads(null, null, RandomSource.create(0));
            List<BakedQuad> chunkUnculled = baked.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, RenderType.cutout());
            assertEquals(oldUnculled.size(), chunkUnculled.size(), "native unculled quads on cutout pass");

            var manifest = PlantModelManifest.load();
            var id = manifest.model("apple", "harvest");
            var base = new SimpleBakedModel(List.of(), Map.of(), false, true, true, sprite,
                    ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
            var wrapper = new PlantTrayBakedModel(base, manifest,
                    new PlantTrayBakedModel.NativePlantAssets(Map.of(id, baked)));
            var appearance = new HydroponicsTrayBlockEntity.AppearanceSnapshot("apple", PlantInstance.Status.HARVEST, 1);
            var data = wrapper.modelDataForAppearance(appearance, new BlockPos(4, 4, 4), ModelData.EMPTY);
            assertNotSame(ModelData.EMPTY, data);
            assertEquals(oldUnculled.size(), wrapper.getQuads(null, null, RandomSource.create(0), data, RenderType.cutout()).size());
            assertTrue(wrapper.getQuads(null, null, RandomSource.create(0), data, RenderType.solid()).isEmpty());
            assertTrue(wrapper.getQuads(null, null, RandomSource.create(0), data, RenderType.cutout()).stream()
                    .allMatch(q -> !q.hasAmbientOcclusion()), "wrapper disables AO without mutating source geometry");
            assertTrue(oldUnculled.stream().allMatch(q -> q.getSprite() == sprite), "native atlas sprite is retained");
            assertTrue(oldUnculled.stream().allMatch(BakedQuad::isShade), "native OBJ shade attribute is retained");
        }
    }

    private static InputStream classpath(String path) {
        InputStream input = PlantNativeOBJChunkBakeTest.class.getResourceAsStream(path);
        if (input == null) throw new AssertionError("Missing production OBJ resource " + path);
        return input;
    }

    private static final class FixtureSprite extends TextureAtlasSprite {
        private FixtureSprite(SpriteContents contents) {
            super(ResourceLocation.parse("test:atlas"), contents, 16, 16, 0, 0);
        }
    }
}
