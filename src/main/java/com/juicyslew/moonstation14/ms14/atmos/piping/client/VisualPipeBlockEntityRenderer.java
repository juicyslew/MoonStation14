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
        drawBox(material, VisualPipeLaneGeometry.center(minimum), poseStack, buffers, packedLight, packedOverlay);

        for (Direction direction : Direction.values()) {
            if (!pipe.hasLayerArm(layer, direction)) continue;
            drawBox(material, VisualPipeLaneGeometry.arm(minimum, direction), poseStack, buffers, packedLight, packedOverlay);
        }
    }

    private static void drawBox(BlockState material, VisualPipeLaneGeometry.Bounds bounds, PoseStack poseStack,
                                MultiBufferSource buffers, int packedLight, int packedOverlay) {
        drawBox(material, bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                poseStack, buffers, packedLight, packedOverlay);
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

