package com.juicyslew.moonstation14.ms14.atmos.analyzer.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Exact selected finite cell marker; selection itself is from controlled-body eye/look, not camera hit result. */
public final class GasAnalyzerCellOutlineRenderer {
    private GasAnalyzerCellOutlineRenderer() { }
    public static void render(RenderLevelStageEvent event) {
        if(event==null||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();var workspace=GasAnalyzerWorkspace.INSTANCE;
        var snapshot=com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerClientState.current();
        var body=com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.currentGasAnalyzerBody();
        if(!workspace.visible()||snapshot==null||snapshot.closed()||body==null||mc.level==null
                ||!snapshot.body().equals(body.getUUID())||snapshot.epoch()!=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch()
                ||!com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.hasGasAnalyzerSource(snapshot.body(),snapshot.epoch(),snapshot.sourceToken()))return;
        var cell=workspace.highlight(body);
        if(cell==null||!mc.level.hasChunkAt(cell)||mc.level.isOutsideBuildHeight(cell))return;
        var camera=event.getCamera().getPosition();
        AABB bounds=new AABB(cell).inflate(.015).move(-camera.x,-camera.y,-camera.z);
        VertexConsumer lines=mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(event.getPoseStack(),lines,bounds,0.2f,0.9f,1f,0.95f);
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }
}
