package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Standalone host adapter delegating all authored geometry to the shared firelock renderer. */
public final class StandaloneFirelockBlockEntityRenderer implements BlockEntityRenderer<FirelockBlockEntity> {
    public StandaloneFirelockBlockEntityRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public AABB getRenderBoundingBox(FirelockBlockEntity entity) {
        return FirelockRenderer.renderBounds(entity.getBlockPos());
    }
    @Override public void render(FirelockBlockEntity entity, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (entity.getLevel() == null) return;
        var host = entity.firelockHost();
        var data = host == null ? null : host.prototype();
        long now = entity.getLevel().getGameTime();
        FirelockRenderer.renderPresentation(entity.firelockPresentation(), data, now + partialTick,
                pose, buffers, packedLight, packedOverlay);
    }
}
