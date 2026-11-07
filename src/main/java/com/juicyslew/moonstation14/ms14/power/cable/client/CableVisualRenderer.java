package com.juicyslew.moonstation14.ms14.power.cable.client;

import com.juicyslew.moonstation14.ms14.power.cable.CableVisualGeometry;
import com.juicyslew.moonstation14.ms14.power.cable.CableFacePresentation;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Client-only thin, texture-free six-face wire placeholder renderer. */
public final class CableVisualRenderer {
    private CableVisualRenderer() { }

    public static void render(RenderLevelStageEvent event, CableVisualClientCache cache) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        Camera camera = event.getCamera();
        var cameraPos = camera.getPosition();
        var visible = new java.util.ArrayList<>(cache.visible(minecraft.level.dimension().location(), cameraPos.x, cameraPos.y, cameraPos.z,
                48, CableVisualClientCache.MAX_VISIBLE_RECORDS));
        if (visible.isEmpty()) return;
        visible.sort(java.util.Comparator.comparingInt(entry -> CableVisualGeometry.drawPriority(entry.record().tier())));
        var pose = event.getPoseStack();
        Matrix4f matrix = pose != null ? pose.last().pose() : event.getModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515); // GL_LEQUAL
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            var nodes = new java.util.ArrayList<CableFaceNode>(visible.size());
            for (var entry : visible) {
                var record = entry.record();
                BlockPos hostPos = new BlockPos((int) Math.floor(entry.x()), record.y(), (int) Math.floor(entry.z()));
                CableFaceNode node = new CableFaceNode(hostPos, record.face(), record.tier());
                nodes.add(node);
            }
            for (int index = 0; index < visible.size(); index++) {
                var entry = visible.get(index);
                var record = entry.record();
                CableFaceNode node = nodes.get(index);
                if (!isVisible(minecraft, node.host(), record.face())) continue;
                CableVisualPresentation.Rgb color = CableVisualPresentation.color(record.tier());
                float cx = (float) (entry.x() - cameraPos.x), cy = (float) (entry.y() - cameraPos.y);
                float cz = (float) (entry.z() - cameraPos.z);
                // Neighbor topology is complete even when its record is outside the render budget.
                for (var quad : CableVisualGeometry.quads(node, cache.topologyNeighbors(node)))
                    drawQuad(builder, matrix, cx, cy, cz, node.face(), quad, color.red(), color.green(), color.blue());
            }
            // In Minecraft 1.21.1 buildOrThrow() throws when the builder contains no vertices;
            // build() finalizes an empty builder and returns null instead.
            var mesh = builder.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(515); // Restore Minecraft's baseline depth function.
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
        }
    }

    private static boolean isVisible(Minecraft minecraft, BlockPos hostPos, Direction face) {
        return CableFacePresentation.isVisible(minecraft.level, hostPos, face);
    }

    private static void drawQuad(BufferBuilder b, Matrix4f m, float cx, float cy, float cz, Direction face,
                                 java.util.List<CableVisualGeometry.Vector3> quad, float r, float g, float blue) {
        float ox = face.getStepX() * CableVisualGeometry.SURFACE_OFFSET;
        float oy = face.getStepY() * CableVisualGeometry.SURFACE_OFFSET;
        float oz = face.getStepZ() * CableVisualGeometry.SURFACE_OFFSET;
        for (var corner : quad) vertex(b, m, cx + corner.x() + ox, cy + corner.y() + oy, cz + corner.z() + oz, r, g, blue);
    }
    private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, float z, float r, float g, float blue) {
        b.addVertex(m, x, y, z).setColor(r, g, blue, 1F);
    }

}

