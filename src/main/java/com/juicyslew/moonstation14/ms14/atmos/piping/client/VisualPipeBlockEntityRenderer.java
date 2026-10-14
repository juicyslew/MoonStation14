package com.juicyslew.moonstation14.ms14.atmos.piping.client;

import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Draws the cosmetic lanes beyond the PRIMARY lane supplied by the block model. */
public final class VisualPipeBlockEntityRenderer implements BlockEntityRenderer<VisualPipeBlockEntity> {
    private static final BlockState SECONDARY_MATERIAL = Blocks.IRON_BLOCK.defaultBlockState();
    private static final BlockState TERTIARY_MATERIAL = Blocks.COPPER_BLOCK.defaultBlockState();

    public VisualPipeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(VisualPipeBlockEntity pipe, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (pipe.hasLayer(VisualPipeBlockEntity.SECONDARY)) {
            renderLane(pipe, VisualPipeBlockEntity.SECONDARY, 2, SECONDARY_MATERIAL,
                    poseStack, buffers, packedLight, packedOverlay);
        }
        if (pipe.hasLayer(VisualPipeBlockEntity.TERTIARY)) {
            renderLane(pipe, VisualPipeBlockEntity.TERTIARY, 12, TERTIARY_MATERIAL,
                    poseStack, buffers, packedLight, packedOverlay);
        }
    }

    private static void renderLane(VisualPipeBlockEntity pipe, int layer, int minimum,
                                   BlockState material, PoseStack poseStack, MultiBufferSource buffers,
                                   int packedLight, int packedOverlay) {
        float low = minimum / 16.0F;
        float high = (minimum + 2) / 16.0F;
        drawBox(material, low, low, low, high, high, high, poseStack, buffers, packedLight, packedOverlay);

        for (Direction direction : Direction.values()) {
            if (!pipe.hasLayerArm(layer, direction)) continue;
            float x1 = low, y1 = low, z1 = low;
            float x2 = high, y2 = high, z2 = high;
            switch (direction) {
                case WEST -> x1 = 0;
                case EAST -> x2 = 1;
                case DOWN -> y1 = 0;
                case UP -> y2 = 1;
                case NORTH -> z1 = 0;
                case SOUTH -> z2 = 1;
            }
            drawBox(material, x1, y1, z1, x2, y2, z2, poseStack, buffers, packedLight, packedOverlay);
        }
    }

    private static void drawBox(BlockState material, float minX, float minY, float minZ,
                                float maxX, float maxY, float maxZ, PoseStack poseStack,
                                MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(minX, minY, minZ);
        poseStack.scale(maxX - minX, maxY - minY, maxZ - minZ);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(material, poseStack, buffers,
                packedLight, packedOverlay);
        poseStack.popPose();
    }
}

