package com.juicyslew.moonstation14.ms14.hands.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry;

/** Depth-tested physical-bounds marker for the locally nominated visible loose item. */
public final class SelectedLooseOutlineRenderer {

    private SelectedLooseOutlineRenderer() { }

    public static void render(RenderLevelStageEvent event) {
        if (event == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        BodyHandClient.OutlineSelection selection = BodyHandClient.outlineSelection();
        net.minecraft.world.entity.item.ItemEntity entity = selection == null ? null : selection.entity();
        if (entity == null || mc.level == null || entity.level() != mc.level || !entity.isAlive()) return;
        if (!(mc.getCameraEntity() instanceof net.minecraft.world.entity.Mob body)
                || body != com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForHud()
                || com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch() <= 0) return;
        var ray = selection.ray();
        if (ray == null) return;
        var camera = ray.from();
        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        if(entity instanceof LooseItemEntity){
            LooseItemQueryGeometry shape=selection.geometry();
            if(shape==null)return;
            Vec3 pivot=shape.pivot().subtract(camera);
            pose.pushPose();
            pose.translate(pivot.x,pivot.y,pivot.z);
            pose.mulPose(new org.joml.Quaternionf(shape.orientation()));
            LevelRenderer.renderLineBox(pose,lines,shape.localBounds(),OutlinePriority.RED,OutlinePriority.GREEN,OutlinePriority.BLUE,OutlinePriority.ALPHA);
            pose.popPose();
        }else{
            AABB capturedBounds = selection.ordinaryRenderBounds();
            if (capturedBounds == null) return;
            AABB b = capturedBounds.move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(pose, lines, b, OutlinePriority.RED, OutlinePriority.GREEN, OutlinePriority.BLUE, OutlinePriority.ALPHA);
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    static AABB interpolatedBounds(AABB bounds, Vec3 currentPosition, Vec3 renderPosition, Vec3 cameraPosition) {
        return bounds.move(renderPosition.subtract(currentPosition))
                .move(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
    }
}
