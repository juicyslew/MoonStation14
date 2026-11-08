package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GunIndicatorBakedModelTest {
    @Test void nativeOverrideSelectionIsDelegatedAndSelectedVariantIsWrapped() {
        BakedModel variant = mock(BakedModel.class);
        BakedModel root = mock(BakedModel.class);
        when(root.getOverrides()).thenReturn(new ItemOverrides() {
            @Override public BakedModel resolve(BakedModel model, ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.entity.LivingEntity entity, int seed) { return variant; }
        });
        var rootWrapper = new GunIndicatorBakedModel(root, List.of());
        BakedModel resolved = rootWrapper.getOverrides().resolve(rootWrapper, ItemStack.EMPTY, null, null, 0);
        assertInstanceOf(GunIndicatorBakedModel.class, resolved);
        assertSame(variant, ((GunIndicatorBakedModel) resolved).getWrappedModel());
    }

    @Test void matchedIndicatorIsClonedFullbrightAndUnshadedWhileBodyQuadIsUnchanged() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(0f); when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV0()).thenReturn(0f); when(sprite.getV1()).thenReturn(1f);
        int[] matchedVertices = vertices(.64f, .005f);
        int[] ordinaryVertices = vertices(.5f, .5f);
        BakedQuad matched = new BakedQuad(matchedVertices, -1, Direction.UP, sprite, true);
        BakedQuad ordinary = new BakedQuad(ordinaryVertices, -1, Direction.NORTH, sprite, true);
        BakedModel base = mock(BakedModel.class);
        when(base.getQuads(any(), any(), any(), any(), any())).thenReturn(List.of(matched, ordinary));
        var model = new GunIndicatorBakedModel(base, List.of(new GunIndicatorRegions.Region(.63671875, .00390625, .7109375, .01171875)));

        var result = model.getQuads(null, null, RandomSource.create(1), ModelData.EMPTY, (RenderType) null);
        assertNotSame(matched, result.get(0));
        assertNotSame(matchedVertices, result.get(0).getVertices());
        assertArrayEquals(matchedVertices, matched.getVertices());
        assertFalse(result.get(0).isShade());
        int[] transformed = result.get(0).getVertices();
        for (int i = 0; i < 4; i++) assertEquals(0x00F000F0, transformed[i * net.neoforged.neoforge.client.model.IQuadTransformer.STRIDE + net.neoforged.neoforge.client.model.IQuadTransformer.UV2]);
        assertSame(ordinary, result.get(1));
        assertSame(ordinaryVertices, ordinary.getVertices());
    }

    @Test void tintRegionChangesOnlyTintAndPreservesLightingAndSource() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(0f); when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV0()).thenReturn(0f); when(sprite.getV1()).thenReturn(1f);
        int[] src = vertices(.15f, .272f); src[6] = 12345; src[7] = 67890;
        BakedQuad quad = new BakedQuad(src, -1, Direction.UP, sprite, true);
        BakedModel base = mock(BakedModel.class);
        when(base.getQuads(any(), any(), any(), any(), any())).thenReturn(List.of(quad));
        var region = new GunIndicatorRegions.Region(.13671875, .26953125, .173046875, .27484375, 1, false);
        var result = new GunIndicatorBakedModel(base, List.of(region)).getQuads(null, null, RandomSource.create(1), ModelData.EMPTY, null).get(0);
        assertEquals(1, result.getTintIndex());
        assertTrue(result.isShade());
        assertArrayEquals(src, quad.getVertices());
        assertArrayEquals(src, result.getVertices());
    }

    private static int[] vertices(float u, float v) {
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) { int base = i * 8; data[base + 3] = -1; data[base + 4] = Float.floatToIntBits(u); data[base + 5] = Float.floatToIntBits(v); }
        return data;
    }
}
