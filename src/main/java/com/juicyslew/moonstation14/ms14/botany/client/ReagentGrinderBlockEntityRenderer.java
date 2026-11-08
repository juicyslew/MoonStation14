package com.juicyslew.moonstation14.ms14.botany.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlock;
import com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlockEntity;
import java.util.WeakHashMap;
import net.minecraft.util.RandomSource;

/** Draws the existing baked state model once, only while the machine's real program is active. */
public final class ReagentGrinderBlockEntityRenderer implements BlockEntityRenderer<ReagentGrinderBlockEntity> {
    private record MotionEntry(long epoch, long lastNanos, GrinderJitterMotion motion) { }
    private final WeakHashMap<ReagentGrinderBlockEntity, MotionEntry> motions = new WeakHashMap<>();
    private final java.util.Random cosmeticRandom = new java.util.Random();
    public ReagentGrinderBlockEntityRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public void render(ReagentGrinderBlockEntity grinder, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var state = grinder.getBlockState();
        if (!state.hasProperty(ReagentGrinderBlock.WORKING) || !state.getValue(ReagentGrinderBlock.WORKING)) {
            motions.remove(grinder);
            return;
        }
        long now = System.nanoTime();
        MotionEntry entry = motions.get(grinder);
        if (entry == null || entry.epoch() != grinder.clientVisualEpoch())
            entry = new MotionEntry(grinder.clientVisualEpoch(), now, new GrinderJitterMotion());
        double dt = Math.min(3.5, Math.max(0, (now - entry.lastNanos()) / 1_000_000_000d));
        var offset = entry.motion().sample(dt, cosmeticRandom);
        motions.put(grinder, new MotionEntry(entry.epoch(), now, entry.motion()));
        pose.pushPose();
        try {
            pose.translate(offset.x(), 0, offset.z());
            var mc = Minecraft.getInstance();
            var dispatcher = mc.getBlockRenderer();
            var model = dispatcher.getBlockModel(state);
            var random = RandomSource.create(state.getSeed(grinder.getBlockPos()));
            var modelData = grinder.getModelData();
            for (var type : model.getRenderTypes(state, random, modelData))
                dispatcher.getModelRenderer().tesselateBlock(grinder.getLevel(), model, state, grinder.getBlockPos(), pose,
                        buffers.getBuffer(type), true, random, state.getSeed(grinder.getBlockPos()), packedOverlay, modelData, type);
        } finally { pose.popPose(); }
    }
}
