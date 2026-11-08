package com.juicyslew.moonstation14.ms14.loose_objects.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies loose entities route rendering through the shared presentation owner. */
class LooseItemRendererLayerTest {
    @Test void entityDriverUsesSharedOwnerAndOverridesOnlyItsJugMaterial() throws Exception {
        String source=readRenderer();
        assertTrue(source.contains("Runnable capture = () -> LooseCargoCaptureClient.observeModelDraw("));
        assertTrue(source.contains("WorldItemPresentationRenderer.renderCameraFacing("));
        assertTrue(source.contains("WorldItemPresentationRenderer.render(stack, profile"));
        assertEquals(1,occurrences(source,"Sheets.cutoutBlockSheet()"));
        assertFalse(source.contains("getModelRenderer().renderModel("));
        assertFalse(source.contains("getItemRenderer().renderStatic("));

        String owner=read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/WorldItemPresentationRenderer.java");
        assertTrue(owner.contains("RenderType jugRenderType"));
        assertTrue(owner.contains("jugVertexConsumer(buffers, vertexAlpha, tintRgb, count, jugRenderType)"));
        String compactOwner = owner.replaceAll("\\s+", "");
        assertTrue(compactOwner.contains("presentationBuffers(buffers,alpha,tint,count).getBuffer(alpha==255&&tint==0xFFFFFF?jugRenderType:RenderType.entityTranslucent"));
        assertTrue(owner.contains("float red = ((color >> 16) & 255) / 255f"));
        assertTrue(owner.contains("float green = ((color >> 8) & 255) / 255f"));
        assertTrue(owner.contains("float blue = (color & 255) / 255f"));
        assertTrue(owner.contains("light, OverlayTexture.NO_OVERLAY"));
        assertTrue(owner.contains("JugItemAppearance.fillTier("));
        assertTrue(owner.contains("JugItemAppearance.color("));
        assertTrue(owner.contains("RenderType.cutout()"),"default material remains for other callers");
    }

    private static String readRenderer() throws Exception { return read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseItemRenderer.java"); }
    private static String read(String path) throws Exception {
        Path root=Path.of(System.getProperty("user.dir"));
        while(root!=null&&!Files.exists(root.resolve(path)))
            root=root.getParent();
        assertNotNull(root,"workspace root");
        return Files.readString(root.resolve(path));
    }

    private static int occurrences(String source,String needle) {
        int count=0,at=0;
        while((at=source.indexOf(needle,at))>=0){count++;at+=needle.length();}
        return count;
    }
}
