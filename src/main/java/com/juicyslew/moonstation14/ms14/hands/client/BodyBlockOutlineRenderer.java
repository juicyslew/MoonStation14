package com.juicyslew.moonstation14.ms14.hands.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Vanilla-like wire outline of the controlled body's block target. */
public final class BodyBlockOutlineRenderer {
    private BodyBlockOutlineRenderer() { }

    /** World-stage presentation only: never change the carrier's hit result or send interaction intent. */
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isSpectator() || mc.level == null || mc.getConnection() == null
                || mc.screen != null || mc.options.hideGui || GhostControlClient.committedCharacterEpoch() <= 0) return;
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
                || mc.screen != null || mc.options.hideGui) return;
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
        Matrix4f matrix = pose != null ? pose.last().pose() : event.getModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515); // LEQUAL: occluded edges stay behind world geometry.
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.lineWidth(2.0F);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES,
                    DefaultVertexFormat.POSITION_COLOR);
            double x = pos.getX() - cameraPos.x;
            double y = pos.getY() - cameraPos.y;
            double z = pos.getZ() - cameraPos.z;
            // Shape edges, not the boxes composing the shape: no internal seams on stairs/slabs.
            shape.forAllEdges((x0, y0, z0, x1, y1, z1) ->
                    edge(builder, matrix, x + x0, y + y0, z + z0, x + x1, y + y1, z + z1));
            var mesh = builder.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.lineWidth(1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(515);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void edge(BufferBuilder b, Matrix4f m, double x0, double y0, double z0,
                             double x1, double y1, double z1) {
        b.addVertex(m, (float)x0, (float)y0, (float)z0).setColor(0.0F, 0.0F, 0.0F, 0.4F);
        b.addVertex(m, (float)x1, (float)y1, (float)z1).setColor(0.0F, 0.0F, 0.0F, 0.4F);
    }
}
