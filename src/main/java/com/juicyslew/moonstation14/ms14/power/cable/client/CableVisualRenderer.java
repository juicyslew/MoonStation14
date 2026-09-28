package com.juicyslew.moonstation14.ms14.power.cable.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
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
        var visible = cache.visible(minecraft.level.dimension().location(), cameraPos.x, cameraPos.y, cameraPos.z,
                48, CableVisualClientCache.MAX_VISIBLE_RECORDS);
        if (visible.isEmpty()) return;
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
                addCenter(builder, matrix, cx, cy, cz, node, color.red(), color.green(), color.blue());
                // Use every synchronized topology record, not the visible-record render budget.
                for (CableFaceNode neighbor : cache.topologyNeighbors(node)) {
                    addSpoke(builder, matrix, cx, cy, cz, node, neighbor,
                            color.red(), color.green(), color.blue());
                }
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
        // Do not ask the client level for a block state in an unloaded chunk.
        if (!minecraft.level.hasChunkAt(hostPos)) return false;
        BlockState host = minecraft.level.getBlockState(hostPos);
        if (host.isAir() || host.getCollisionShape(minecraft.level, hostPos).isEmpty()
                || !host.isFaceSturdy(minecraft.level, hostPos, face)) return false;
        boolean floor = host.getBlock() instanceof com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
        boolean tileFinish = floor && host.getValue(com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock.TILE_FINISH)
                != com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock.TileFinish.NONE;
        return CableFacePresentation.isVisible(face, floor, tileFinish);
    }

    private static void addCenter(BufferBuilder b, Matrix4f m, float cx, float cy, float cz, CableFaceNode node,
                                  float r, float g, float blue) {
        Direction face = node.face();
        float ox = face.getStepX() * .502F, oy = face.getStepY() * .502F, oz = face.getStepZ() * .502F;
        for (CableVisualGeometry.Vector3 corner : CableVisualGeometry.centerCorners(node, .05F))
            vertex(b, m, cx + corner.x() + ox, cy + corner.y() + oy, cz + corner.z() + oz, r, g, blue);
    }
    private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, float z, float r, float g, float blue) {
        b.addVertex(m, x, y, z).setColor(r, g, blue, 1F);
    }

    private static void addSpoke(BufferBuilder b, Matrix4f m, float cx, float cy, float cz, CableFaceNode node,
        CableFaceNode neighbor, float r, float g, float blue) {
        Direction face = node.face();
        float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        for (CableVisualGeometry.Vector3 corner : CableVisualGeometry.spokeCorners(node, neighbor, .03F))
            vertex(b, m, cx + corner.x() + nx * .502F, cy + corner.y() + ny * .502F,
                    cz + corner.z() + nz * .502F, r, g, blue);
    }
}
