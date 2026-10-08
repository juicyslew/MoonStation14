package com.juicyslew.moonstation14.ms14.hands.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.OptionalDouble;

/** Vanilla-like wire outline of the controlled body's block target. */
public final class BodyBlockOutlineRenderer {
    private static final RenderType TARGET_LINES = RenderType.create("ms14_body_target_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(4.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    private BodyBlockOutlineRenderer() { }

    /** World-stage presentation only: never change the carrier's hit result or send interaction intent. */
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.activeIntent()) return;
        if (mc.player == null || !mc.player.isSpectator() || mc.level == null || mc.getConnection() == null
                || mc.screen != null && (!(mc.screen instanceof com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow window)
                || window.capturesKeyboard()) || mc.options.hideGui || GhostControlClient.committedCharacterEpoch() <= 0) return;
        Mob body = GhostControlClient.ownedCharacterForHud();
        if (body == null || mc.getCameraEntity() != body || body.level() != mc.level) return;
        render(event, body);
    }

    public static void render(RenderLevelStageEvent event, Mob body) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (body == null || body != GhostControlClient.ownedCharacterForHud()
                || GhostControlClient.committedCharacterEpoch() <= 0 || mc.getCameraEntity() != body
                || mc.level == null || body.level() != mc.level || !body.isAlive()
                || mc.screen != null && (!(mc.screen instanceof com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow window)
                || window.capturesKeyboard()) || mc.options.hideGui) return;
        var start = body.getEyePosition();
        var end = start.add(body.getLookAngle().scale(6.0));
        var hit = body.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, body));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        var pos = ((BlockHitResult) hit).getBlockPos();
        var state = body.level().getBlockState(pos);
        var shape = state.getShape(body.level(), pos, CollisionContext.of(body));
        if (shape.isEmpty()) return;
        Camera camera = event.getCamera();
        var cameraPos = camera.getPosition();
        var pose = event.getPoseStack();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515); // LEQUAL: occluded edges stay behind world geometry.
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            double x = pos.getX() - cameraPos.x;
            double y = pos.getY() - cameraPos.y;
            double z = pos.getZ() - cameraPos.z;
            var buffers = mc.renderBuffers().bufferSource();
            var lines = buffers.getBuffer(TARGET_LINES);
            // Shape edges, not the boxes composing the shape: no internal seams on stairs/slabs.
            shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> edge(lines, pose,
                    x + x0, y + y0, z + z0, x + x1, y + y1, z + z1));
            buffers.endBatch(TARGET_LINES);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(515);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void edge(VertexConsumer lines, com.mojang.blaze3d.vertex.PoseStack pose,
                             double x0, double y0, double z0, double x1, double y1, double z1) {
        // Supply the line shader's perpendicular direction; its shader expands lines into
        // screen-space geometry instead of relying on driver-clamped GL line widths.
        float dx = (float)(x1 - x0), dy = (float)(y1 - y0), dz = (float)(z1 - z0);
        float nx = -dy, ny = dx, nz = 0.0F;
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) { nx = 0.0F; ny = -dz; nz = dy; length = (float)Math.sqrt(ny * ny + nz * nz); }
        if (length < 1.0E-6F) return;
        nx /= length; ny /= length; nz /= length;
        lines.addVertex(pose.last(), (float)x0, (float)y0, (float)z0).setColor(0.82F, 0.98F, 1.0F, 0.95F).setNormal(pose.last(), nx, ny, nz);
        lines.addVertex(pose.last(), (float)x1, (float)y1, (float)z1).setColor(0.82F, 0.98F, 1.0F, 0.95F).setNormal(pose.last(), nx, ny, nz);
    }

}
