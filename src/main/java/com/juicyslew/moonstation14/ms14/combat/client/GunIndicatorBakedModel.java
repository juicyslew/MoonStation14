package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

/** Additively flattens only artist-authored indicator faces in an otherwise native item model. */
public final class GunIndicatorBakedModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel wrappedModel;
    private final List<GunIndicatorRegions.Region> regions;
    private final Map<BakedModel, GunIndicatorBakedModel> wrappers;
    public GunIndicatorBakedModel(BakedModel base, List<GunIndicatorRegions.Region> regions) { this(base, regions, new IdentityHashMap<>()); }
    GunIndicatorBakedModel(BakedModel base, List<GunIndicatorRegions.Region> regions, Map<BakedModel, GunIndicatorBakedModel> wrappers) {
        super(base); this.wrappedModel = base; this.regions = List.copyOf(regions); this.wrappers = wrappers;
    }
    BakedModel getWrappedModel() { return wrappedModel; }
    static GunIndicatorBakedModel wrap(BakedModel model, List<GunIndicatorRegions.Region> regions, Map<BakedModel, GunIndicatorBakedModel> cache) {
        if (model instanceof GunIndicatorBakedModel wrapped) return wrapped;
        return cache.computeIfAbsent(model, key -> new GunIndicatorBakedModel(key, regions, cache));
    }
    @Override public ItemOverrides getOverrides() {
        ItemOverrides nativeOverrides = super.getOverrides();
        return new ItemOverrides() {
            @Override public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
                BakedModel selected = nativeOverrides.resolve(model, stack, level, entity, seed);
                if (selected == null || selected == model) return selected;
                return wrap(selected, regions, wrappers);
            }
        };
    }
    @Override public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state, Direction side, RandomSource rand, ModelData data, RenderType type) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, type);
        List<BakedQuad> result = null;
        for (int i = 0; i < base.size(); i++) {
            BakedQuad quad = base.get(i);
            GunIndicatorRegions.Region match = regions.stream().filter(region -> GunIndicatorRegions.matches(quad, region)).findFirst().orElse(null);
            if (match == null) continue;
            if (result == null) result = new ArrayList<>(base);
            BakedQuad clone = new BakedQuad(quad.getVertices().clone(), match.tintIndex() == null ? quad.getTintIndex() : match.tintIndex(), quad.getDirection(), quad.getSprite(), match.emissive() ? false : quad.isShade(), quad.hasAmbientOcclusion());
            if (match.emissive()) clone = QuadTransformers.settingMaxEmissivity().process(clone);
            result.set(i, clone);
        }
        return result == null ? base : List.copyOf(result);
    }
    @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        List<BakedModel> nativePasses = super.getRenderPasses(stack, fabulous);
        if (nativePasses.size() == 1 && nativePasses.get(0) == this) return nativePasses;
        return nativePasses.stream().<BakedModel>map(model -> wrap(model, regions, wrappers)).toList();
    }
}
