package com.juicyslew.moonstation14.ms14.loose_objects.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the jug's live model-render call uses the block-atlas entity cutout layer. */
class LooseItemRendererLayerTest {
    @Test void jugUsesEntityMappedBlockCutoutLayerAndPreservesItsExistingDrawInputs() throws Exception {
        String source=readRenderer();
        int jugStart=source.indexOf("if (\"jug\".equals(profile.renderKind())");
        int jugEnd=source.indexOf("} else {",jugStart);
        assertTrue(jugStart>=0&&jugEnd>jugStart);
        String jug=source.substring(jugStart,jugEnd);

        assertTrue(jug.contains("buffers.getBuffer(Sheets.cutoutBlockSheet())"));
        assertFalse(jug.contains("RenderType.cutout()"));
        assertFalse(jug.contains("chunkBufferLayers"));
        assertEquals(1,occurrences(jug,"getModelRenderer().renderModel("));
        assertTrue(jug.contains("pose.last(), consumer"));
        assertTrue(jug.contains("null, model, red, green, blue, light, OverlayTexture.NO_OVERLAY"));
        assertTrue(jug.indexOf("observeModelDraw(")<jug.indexOf("renderModel("));
        assertTrue(jug.contains("JugItemAppearance.fillTier("));
        assertTrue(jug.contains("JugItemAppearance.color("));

        String nonJug=source.substring(jugEnd);
        assertEquals(3,occurrences(nonJug,"getItemRenderer().renderStatic("),
                "other item-renderer branches retain their existing entity pipeline");
    }

    private static String readRenderer() throws Exception {
        Path root=Path.of(System.getProperty("user.dir"));
        while(root!=null&&!Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseItemRenderer.java")))
            root=root.getParent();
        assertNotNull(root,"workspace root");
        return Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseItemRenderer.java"));
    }

    private static int occurrences(String source,String needle) {
        int count=0,at=0;
        while((at=source.indexOf(needle,at))>=0){count++;at+=needle.length();}
        return count;
    }
}
