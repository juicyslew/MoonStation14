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

import static org.junit.jupiter.api.Assertions.*;

class PlantTrayBakedModelTest {
    @Test void nativePlantFacesFlattenOnceAndOnlyAppendOnNullCutoutSide() {
        PlantModelManifest manifest = PlantModelManifest.load();
        ModelResourceLocation id = manifest.model("wheat", "stage-1");
        BakedQuad plantQuad = quad(Direction.UP, true);
        var faces = new java.util.EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (Direction direction : Direction.values()) faces.put(direction, List.of(quad(direction, true)));
        SimpleBakedModel nativePlant = model(List.of(plantQuad), faces);
        var assets = new PlantTrayBakedModel.NativePlantAssets(Map.of(id, nativePlant));
        var wrapper = new PlantTrayBakedModel(model(List.of(), Map.of(Direction.UP, List.of(quad(Direction.UP, true)))), manifest, assets);
        var snapshot = new HydroponicsTrayBlockEntity.AppearanceSnapshot("wheat", PlantInstance.Status.GROWING, 1);
        BlockPos interior = new BlockPos(4, 4, 4);
        ModelData data = wrapper.modelDataForAppearance(snapshot, interior, ModelData.EMPTY);
        assertNotSame(ModelData.EMPTY, data);
        var emitted = wrapper.getQuads(null, null, RandomSource.create(1), data, RenderType.cutout());
        assertEquals(7, emitted.size());
        assertTrue(emitted.subList(1, emitted.size()).stream().allMatch(q -> !q.hasAmbientOcclusion() && !q.isShade()),
                "cached plant quads match the old BER's flat cutout color without AO or directional shade");
        assertEquals(1, wrapper.getQuads(null, Direction.UP, RandomSource.create(1), data, RenderType.cutout()).size());
        assertEquals(0, wrapper.getQuads(null, null, RandomSource.create(1), data, RenderType.solid()).size());
        assertTrue(wrapper.getRenderTypes(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), RandomSource.create(1), data).contains(RenderType.cutout()));
        assertTrue(plantQuad.hasAmbientOcclusion(), "native source quad is not modified");
        assertTrue(plantQuad.isShade(), "native source shade flag is not modified");
        assertEquals(1, wrapper.modelDataForAppearance(snapshot, interior, ModelData.EMPTY)
                .getProperties().size(), "the selected mesh is one immutable model-data property");
    }

    @Test void sectionRoutingHandlesNegativeCoordinatesAndAllEnvelopeAxes() {
        assertTrue(PlantTrayBakedModel.fitsCurrentSection(new net.minecraft.world.phys.AABB(-16, -16, -16, 0, 0, 0), new BlockPos(-1, -1, -1)));
        assertFalse(PlantTrayBakedModel.fitsCurrentSection(new net.minecraft.world.phys.AABB(15, 2, 2, 16.1, 3, 3), new BlockPos(15, 2, 2)));
        assertFalse(PlantTrayBakedModel.fitsCurrentSection(new net.minecraft.world.phys.AABB(2, 15, 2, 3, 17, 3), new BlockPos(2, 15, 2)));
        assertFalse(PlantTrayBakedModel.fitsCurrentSection(new net.minecraft.world.phys.AABB(2, 2, 15, 3, 3, 17), new BlockPos(2, 2, 15)));
    }

    @Test void missingBindingAndBoundaryKeepBaseDataAndUnknownNativeModelIsUnavailable() {
        PlantModelManifest manifest = PlantModelManifest.load();
        var assets = new PlantTrayBakedModel.NativePlantAssets(Map.of());
        var wrapper = new PlantTrayBakedModel(model(List.of(), Map.of()), manifest, assets);
        ModelData unchanged = ModelData.EMPTY;
        assertSame(unchanged, wrapper.modelDataForAppearance(HydroponicsTrayBlockEntity.AppearanceSnapshot.empty(), new BlockPos(1, 1, 1), unchanged));
        var known = new HydroponicsTrayBlockEntity.AppearanceSnapshot("wheat", PlantInstance.Status.GROWING, 1);
        assertSame(unchanged, wrapper.modelDataForAppearance(known, new BlockPos(1, 1, 1), unchanged));
        assertFalse(wrapper.rendersPlant(known, new BlockPos(1, 1, 1)));
    }

    @Test void nextChunkSnapshotSelectsNewModelAndEmptySnapshotRemovesPlantData() {
        PlantModelManifest manifest = PlantModelManifest.load();
        var wheat = manifest.model("wheat", "stage-1");
        var tomato = manifest.model("tomato", "stage-1");
        var assets = new PlantTrayBakedModel.NativePlantAssets(Map.of(
                wheat, model(List.of(quad(Direction.UP, true)), Map.of()),
                tomato, model(List.of(quad(Direction.UP, true), quad(Direction.DOWN, true)), Map.of())));
        var wrapper = new PlantTrayBakedModel(model(List.of(), Map.of()), manifest, assets);
        var pos = new BlockPos(4, 4, 4);
        var wheatData = wrapper.modelDataForAppearance(new HydroponicsTrayBlockEntity.AppearanceSnapshot("wheat", PlantInstance.Status.GROWING, 1), pos, ModelData.EMPTY);
        var tomatoData = wrapper.modelDataForAppearance(new HydroponicsTrayBlockEntity.AppearanceSnapshot("tomato", PlantInstance.Status.GROWING, 1), pos, ModelData.EMPTY);
        assertEquals(1, wrapper.getQuads(null, null, RandomSource.create(1), wheatData, RenderType.cutout()).size());
        assertEquals(2, wrapper.getQuads(null, null, RandomSource.create(1), tomatoData, RenderType.cutout()).size());
        assertSame(ModelData.EMPTY, wrapper.modelDataForAppearance(HydroponicsTrayBlockEntity.AppearanceSnapshot.empty(), pos, ModelData.EMPTY));
    }

    private static BakedQuad quad(Direction direction, boolean ao) {
        return new BakedQuad(new int[32], -1, direction, null, true, ao);
    }
    private static SimpleBakedModel model(List<BakedQuad> unculled, Map<Direction, List<BakedQuad>> bySide) {
        var culled = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (Direction direction : Direction.values()) culled.put(direction, bySide.getOrDefault(direction, List.of()));
        return new SimpleBakedModel(unculled, culled, false, true, true, null, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
    }
}
