package com.juicyslew.moonstation14.ms14.hands.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Depth-tested physical-bounds marker for the locally nominated visible loose item. */
public final class SelectedLooseOutlineRenderer {

    private SelectedLooseOutlineRenderer() { }

    public static void render(RenderLevelStageEvent event) {
        if (event == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.item.ItemEntity entity = BodyHandClient.selectedItemForOutline();
        if (entity == null || mc.level == null || entity.level() != mc.level || !entity.isAlive()) return;
        if (!(mc.getCameraEntity() instanceof net.minecraft.world.entity.Mob body)
                || body != com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForHud()
                || com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch() <= 0) return;
        Vec3 start = body.getEyePosition();
        Vec3 end = start.add(body.getLookAngle().scale(6.0));
        var blockHit = body.level().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, body));
        if (!OutlinePriority.showItem(entity.getBoundingBox(), start, end, blockHit)) return;
        var camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // Match LevelRenderer's entity rendering history fields rather than getPosition's xo/yo/zo interpolation.
        Vec3 renderPosition = new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        AABB b = interpolatedBounds(entity.getBoundingBox(), entity.position(), renderPosition, camera);
        if (entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose)
            b = b.move(0, com.juicyslew.moonstation14.ms14.loose_objects.client.LooseItemRenderLayers.offset(loose), 0);
        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, lines, b, OutlinePriority.RED, OutlinePriority.GREEN, OutlinePriority.BLUE, OutlinePriority.ALPHA);
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    static AABB interpolatedBounds(AABB bounds, Vec3 currentPosition, Vec3 renderPosition, Vec3 cameraPosition) {
        return bounds.move(renderPosition.subtract(currentPosition))
                .move(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
    }
}

