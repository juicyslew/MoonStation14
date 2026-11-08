package com.juicyslew.moonstation14.ms14.botany.client;

import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Adds native baked plant quads to an eligible tray's opaque chunk mesh. */
public final class PlantTrayBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<List<BakedQuad>> PLANT_QUADS = new ModelProperty<>();
    private final PlantModelManifest manifest;
    private final NativePlantAssets assets;

    /** One instance per baking-result epoch; share this asset holder across both tray wrappers. */
    public static final class NativePlantAssets {
        private final Map<ModelResourceLocation, BakedModel> models;
        private final Map<ModelResourceLocation, List<BakedQuad>> flattened = new ConcurrentHashMap<>();
        public NativePlantAssets(Map<ModelResourceLocation, BakedModel> models) { this.models = Map.copyOf(models); }
        boolean hasModel(ModelResourceLocation id) { return models.containsKey(id); }
        private List<BakedQuad> quads(ModelResourceLocation id) {
            if (!models.containsKey(id)) return null;
            return flattened.computeIfAbsent(id, key -> {
                BakedModel model = models.get(key);
                List<BakedQuad> quads = new ArrayList<>();
                for (Direction side : Direction.values()) append(quads, model.getQuads(null, side, RandomSource.create(0L), ModelData.EMPTY, RenderType.cutout()));
                append(quads, model.getQuads(null, null, RandomSource.create(0L), ModelData.EMPTY, RenderType.cutout()));
                return List.copyOf(quads);
            });
        }
        private static void append(List<BakedQuad> output, List<BakedQuad> input) {
            for (BakedQuad quad : input) {
                // Chunk rendering applies directional shade when isShade is true, unlike the old BER
                // cutout path. Keep cached plant colors flat while retaining world-derived packed light.
                output.add(quad.hasAmbientOcclusion() || quad.isShade()
                        ? new BakedQuad(quad.getVertices(), quad.getTintIndex(), quad.getDirection(), quad.getSprite(), false, false)
                        : quad);
            }
        }
    }

    public PlantTrayBakedModel(BakedModel base, PlantModelManifest manifest, NativePlantAssets assets) {
        super(base);
        this.manifest = manifest;
        this.assets = assets;
    }
    PlantModelManifest manifest() { return manifest; }
    boolean sharesAssets(PlantTrayBakedModel other) { return assets == other.assets; }
    boolean sharesNativeModel(ModelResourceLocation id) { return assets.hasModel(id); }

    /** True iff this snapshot is represented by this model's chunk geometry, not the BER fallback. */
    public boolean rendersPlant(HydroponicsTrayBlockEntity.AppearanceSnapshot appearance, BlockPos pos) {
        ModelResourceLocation id = manifest.model(appearance);
        return id != null && fitsCurrentSection(manifest.renderBoundingBox(appearance.key(), true, pos), pos) && assets.hasModel(id);
    }

    public static boolean shouldRenderInBer(BakedModel currentModel, HydroponicsTrayBlockEntity.AppearanceSnapshot appearance, BlockPos pos) {
        return !(currentModel instanceof PlantTrayBakedModel wrapper && wrapper.rendersPlant(appearance, pos));
    }

    static boolean fitsCurrentSection(net.minecraft.world.phys.AABB bounds, BlockPos pos) {
        double minX = Math.floorDiv(pos.getX(), 16) * 16d, minY = Math.floorDiv(pos.getY(), 16) * 16d, minZ = Math.floorDiv(pos.getZ(), 16) * 16d;
        return bounds.minX >= minX && bounds.minY >= minY && bounds.minZ >= minZ
                && bounds.maxX <= minX + 16 && bounds.maxY <= minY + 16 && bounds.maxZ <= minZ + 16;
    }

    /** Testable production path: resolves from the shared snapshot, adding no world/position cache. */
    ModelData modelDataForAppearance(HydroponicsTrayBlockEntity.AppearanceSnapshot appearance, BlockPos pos, ModelData baseData) {
        if (!rendersPlant(appearance, pos)) return baseData;
        List<BakedQuad> quads = assets.quads(manifest.model(appearance));
        return quads == null || quads.isEmpty() ? baseData : baseData.derive().with(PLANT_QUADS, quads).build();
    }

    @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData tileData) {
        ModelData baseData = super.getModelData(level, pos, state, tileData);
        if (level.getBlockEntity(pos) instanceof HydroponicsTrayBlockEntity tray)
            return modelDataForAppearance(tray.appearanceSnapshot(), pos, baseData);
        return baseData;
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
        List<BakedQuad> plant = data.get(PLANT_QUADS);
        if (side != null || renderType != RenderType.cutout() || plant == null || plant.isEmpty()) return base;
        List<BakedQuad> combined = new ArrayList<>(base.size() + plant.size());
        combined.addAll(base);
        combined.addAll(plant);
        return List.copyOf(combined);
    }

    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        ChunkRenderTypeSet base = super.getRenderTypes(state, rand, data);
        List<BakedQuad> plant = data.get(PLANT_QUADS);
        return plant == null || plant.isEmpty() ? base : ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(RenderType.cutout()));
    }
}
