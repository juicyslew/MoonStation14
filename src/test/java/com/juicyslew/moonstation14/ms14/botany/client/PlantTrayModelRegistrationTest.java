package com.juicyslew.moonstation14.ms14.botany.client;

import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.PlantInstance;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlantTrayModelRegistrationTest {
    @Test void installsOnlyRequestedTrayModelsWithSharedFilteredNativeAssetCache() {
        PlantModelManifest manifest = PlantModelManifest.load();
        var plantId = manifest.model("wheat", "stage-1");
        var trayA = ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.parse("test:tray_a"));
        var trayB = ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.parse("test:tray_b"));
        var unrelated = ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.parse("test:unrelated"));
        BakedModel sourcePlant = model(List.of(quad(Direction.UP)), Map.of());
        BakedModel baseTray = model(List.of(), Map.of());
        Map<ModelResourceLocation, BakedModel> registry = new java.util.HashMap<>(Map.of(
                plantId, sourcePlant, trayA, baseTray, trayB, baseTray, unrelated, baseTray));
        PlantTrayModelRegistration.install(registry, manifest, List.of(trayA, trayB));
        assertInstanceOf(PlantTrayBakedModel.class, registry.get(trayA));
        assertInstanceOf(PlantTrayBakedModel.class, registry.get(trayB));
        assertSame(baseTray, registry.get(unrelated));
        var a = (PlantTrayBakedModel) registry.get(trayA);
        var b = (PlantTrayBakedModel) registry.get(trayB);
        assertTrue(a.sharesAssets(b));
        assertTrue(a.sharesNativeModel(plantId));
        assertFalse(a.sharesNativeModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.parse("test:not_a_plant"))));
    }

    @Test void BERPredicateDoesNotFlattenAndSharedCacheBuildsOnlyOnceForBothWrappers() {
        PlantModelManifest manifest = PlantModelManifest.load();
        var id = manifest.model("wheat", "stage-1");
        var bungoId = manifest.model("bungo", "stage-1");
        AtomicInteger nativeQuadCalls = new AtomicInteger();
        BakedModel counted = new CountingModel(model(List.of(quad(Direction.UP)), Map.of()), nativeQuadCalls);
        var assets = new PlantTrayBakedModel.NativePlantAssets(Map.of(id, counted, bungoId, counted));
        var first = new PlantTrayBakedModel(model(List.of(), Map.of()), manifest, assets);
        var second = new PlantTrayBakedModel(model(List.of(), Map.of()), manifest, assets);
        var snapshot = new HydroponicsTrayBlockEntity.AppearanceSnapshot("wheat", PlantInstance.Status.GROWING, 1);
        BlockPos interior = new BlockPos(4, 4, 4);
        for (int i = 0; i < 1000; i++) {
            assertFalse(PlantTrayBakedModel.shouldRenderInBer(first, snapshot, interior));
            assertTrue(first.rendersPlant(snapshot, interior));
        }
        assertEquals(0, nativeQuadCalls.get(), "render-thread route predicate never interrogates native quads");
        first.modelDataForAppearance(snapshot, interior, ModelData.EMPTY);
        second.modelDataForAppearance(snapshot, interior, ModelData.EMPTY);
        assertEquals(7, nativeQuadCalls.get(), "six faces and unculled list flattened once across wrappers");
        var boundary = new HydroponicsTrayBlockEntity.AppearanceSnapshot("bungo", PlantInstance.Status.GROWING, 1);
        assertTrue(PlantTrayBakedModel.shouldRenderInBer(first, boundary, new BlockPos(15, 4, 4)),
                "oversized authored bounds select the BER fallback");
    }

    private static BakedQuad quad(Direction direction) { return new BakedQuad(new int[32], -1, direction, null, true, true); }
    private static SimpleBakedModel model(List<BakedQuad> unculled, Map<Direction, List<BakedQuad>> sides) {
        var culled = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (Direction direction : Direction.values()) culled.put(direction, sides.getOrDefault(direction, List.of()));
        return new SimpleBakedModel(unculled, culled, false, true, true, null, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
    }
    private static final class CountingModel extends SimpleBakedModel {
        private final AtomicInteger calls;
        CountingModel(SimpleBakedModel base, AtomicInteger calls) {
            super(List.of(quad(Direction.UP)), new EnumMap<>(Direction.class), false, true, true, null, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
            this.calls = calls;
        }
        @Override public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state, Direction side,
                                                    RandomSource rand, ModelData data, RenderType renderType) {
            calls.incrementAndGet();
            return side == null ? List.of(quad(Direction.UP)) : List.of();
        }
    }
}
