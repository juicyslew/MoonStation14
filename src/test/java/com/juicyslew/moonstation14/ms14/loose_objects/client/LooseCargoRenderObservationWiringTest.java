package com.juicyslew.moonstation14.ms14.loose_objects.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LooseCargoRenderObservationWiringTest {
    @Test void dispatcherTargetsMatchCurrentMappedClientDescriptorsAndOnlyObserveActualResults() throws Exception {
        var cull = EntityRenderDispatcher.class.getDeclaredMethod("shouldRender", Entity.class, Frustum.class,
                double.class, double.class, double.class);
        assertEquals(boolean.class, cull.getReturnType());
        var render = EntityRenderDispatcher.class.getDeclaredMethod("render", Entity.class,
                double.class, double.class, double.class, float.class, float.class,
                PoseStack.class, MultiBufferSource.class, int.class);
        assertEquals(void.class, render.getReturnType());
        String mixin = read("src/main/java/com/juicyslew/moonstation14/mixin/client/LooseCargoRenderDispatcherObservationMixin.java");
        assertTrue(mixin.contains("shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"));
        assertTrue(mixin.contains("render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"));
        assertTrue(mixin.contains("at = @At(\"RETURN\"), require = 1"));
        assertTrue(mixin.contains("at = @At(\"HEAD\"), require = 1"));
        assertTrue(mixin.contains("at = @At(\"RETURN\"), require = 1"));
        assertTrue(mixin.contains("observeDispatcherReturn(entity)"));
        assertFalse(mixin.contains("cancellable = true"));
        assertFalse(mixin.contains("setReturnValue"));
        assertFalse(mixin.contains("frustum.isVisible"));
        assertTrue(mixin.contains("callback.getReturnValue()"));

        var config = com.google.gson.JsonParser.parseString(read("src/main/resources/moonstation14.mixins.json")).getAsJsonObject();
        assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(e ->
                e.getAsString().equals("client.LooseCargoRenderDispatcherObservationMixin")));
        assertFalse(config.getAsJsonArray("mixins").asList().stream().anyMatch(e ->
                e.getAsString().equals("client.LooseCargoRenderDispatcherObservationMixin")));
        assertTrue(config.getAsJsonObject("injectors").get("defaultRequire").getAsInt() == 1);
    }

    @Test void actualLooseRendererOffsetAndEveryModelDrawBranchRecordExistingValuesWithoutChangingThem() throws Exception {
        String renderer = read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseItemRenderer.java");
        int offset = renderer.indexOf("@Override public net.minecraft.world.phys.Vec3 getRenderOffset");
        int offsetEnd = renderer.indexOf("/** Applies the production", offset);
        String offsetBody = renderer.substring(offset, offsetEnd);
        assertEquals(1, occurrences(offsetBody, "entity.presentationOffset(partialTick)"));
        assertEquals(1, occurrences(offsetBody, "LooseCargoCaptureClient.observeRenderOffset(entity, partialTick, offset)"));
        assertTrue(offsetBody.indexOf("observeRenderOffset") < offsetBody.indexOf("return offset"));
        int cullingStart=renderer.indexOf("@Override public boolean shouldRender");
        String culling=renderer.substring(cullingStart,renderer.indexOf("public void render(LooseItemEntity entity",cullingStart));
        assertTrue(culling.contains("super.shouldRender(entity,frustum,cameraX,cameraY,cameraZ)"));
        assertTrue(culling.contains("entity.shouldRender(cameraX,cameraY,cameraZ)"));
        assertTrue(culling.contains(".forPresentedEntity(entity,partial)"));
        assertTrue(culling.contains("frustum.isVisible(conservative)"));
        assertTrue(culling.contains("return frustum.isVisible(conservative)"),"custom branch still performs frustum visibility");

        int renderStart = renderer.indexOf("public void render(LooseItemEntity entity");
        int renderEnd = renderer.indexOf("private static org.joml.Quaternionf applyEntityOrientation", renderStart);
        String render = renderer.substring(renderStart, renderEnd);
        assertEquals(4, occurrences(render, "LooseCargoCaptureClient.observeModelDraw("));
        assertEquals(3, occurrences(render, "getItemRenderer().renderStatic("));
        assertEquals(1, occurrences(render, "getModelRenderer().renderModel("));
        assertEquals(2, occurrences(render, "observeModelSkipped(entity,"));
        assertTrue(render.contains("profile.renderKind(), partialTick, light, pose, appliedRotation"));
        assertTrue(render.contains("profile-unavailable"));
        assertTrue(render.contains("empty-stack"));
    }

    @Test void captureHooksFastFilterExactEntityAndCopyRenderMatrixOnlyForTheArmedObserver() throws Exception {
        String manager = read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoCaptureClient.java");
        assertTrue(manager.contains("private static Capture target(Entity entity)"));
        assertTrue(manager.contains("entity != capture.entity"));
        assertTrue(manager.contains("entity.getId() != capture.tape.entityId()"));
        assertTrue(manager.contains("!capture.tape.entityUuid().equals(entity.getUUID())"));
        assertTrue(manager.contains("entity.level() != capture.level"));
        assertTrue(manager.contains("float[] components = new float[16]"));
        assertTrue(manager.contains("pose.last().pose().get(components)"));
        assertTrue(manager.contains("List.copyOf(copy)"));
        assertTrue(manager.contains("BodyHandClient.outlineRenderSerial()"));
        assertTrue(manager.contains("Sable.HELPER.getContaining(capture.entity)"));
        assertTrue(manager.contains("EntitySubLevelUtil.shouldKick(capture.entity)"));
        assertTrue(manager.contains("appliedLocalRotation()"));
        assertTrue(manager.contains("capture.tape.markIncomplete(\"render-camera-unavailable\")"));
        assertTrue(manager.contains("pendingCullByFrame"));
        assertTrue(manager.contains("capture.currentInvocation()"));
        assertTrue(read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoObservationTape.java")
                .contains("missingCullDispatch"));
        for (String phase : new String[] {"DISPATCH_CULL", "DISPATCH_RENDER", "RENDER_OFFSET", "MODEL_DRAW", "MODEL_SKIPPED"})
            assertTrue(manager.contains("appendRender(capture, \""+phase+"\""));
    }

    private static String read(String path) throws Exception {
        Path root=Path.of(System.getProperty("user.dir"));
        while(root!=null&&!Files.exists(root.resolve(path))) root=root.getParent();
        assertNotNull(root,path);
        return Files.readString(root.resolve(path));
    }
    private static int occurrences(String source,String needle) {
        int count=0,at=0;
        while((at=source.indexOf(needle,at))>=0){count++;at+=needle.length();}
        return count;
    }
}
