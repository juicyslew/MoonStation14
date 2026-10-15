package com.juicyslew.moonstation14.ms14.radiation.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.doors.client.AirlockBlockEntityRenderer;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Draws accepted atlas-derived shell art and one pivoted native panel element set. */
public final class RadiationCollectorBlockEntityRenderer implements BlockEntityRenderer<RadiationCollectorBlockEntity> {
    private static final String ROOT = "block/reactor_bindings/collector/";

    public RadiationCollectorBlockEntityRenderer(BlockEntityRendererProvider.Context context) { }

    public static List<ModelResourceLocation> additionalModels() {
        var result = new java.util.ArrayList<ModelResourceLocation>();
        for (int tank = 0; tank <= 1; tank++) for (int pressure = 0; pressure <= 3; pressure++)
            result.add(location(ROOT + "shell_tank" + tank + "_pressure" + pressure));
        result.add(location(ROOT + "panel_inactive"));
        result.add(location(ROOT + "panel_active"));
        for (int band = 0; band < 4; band++) result.add(location(ROOT + "pressure_overlay_p" + band));
        return List.copyOf(result);
    }

    private static ModelResourceLocation location(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path));
    }

    @Override public AABB getRenderBoundingBox(RadiationCollectorBlockEntity entity) {
        return RadiationCollectorRenderBounds.at(entity.getBlockPos().getX(), entity.getBlockPos().getY(), entity.getBlockPos().getZ());
    }

    @Override public void render(RadiationCollectorBlockEntity entity, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var snapshot = entity.visualSnapshot();
        long now = entity.getLevel() == null ? 0 : entity.getLevel().getGameTime();
        RadiationCollectorRenderPlan plan = RadiationCollectorRenderPlan.from(snapshot, now);
        var manager = Minecraft.getInstance().getModelManager();
        var shell = manager.getModel(ModelResourceLocation.standalone(plan.shellModel()));
        var panel = manager.getModel(ModelResourceLocation.standalone(plan.panelModel()));
        var pressure = manager.getModel(ModelResourceLocation.standalone(plan.pressureModel()));
        pose.pushPose();
        try {
            AirlockBlockEntityRenderer.drawComponent(shell, pose, buffers, packedLight, packedOverlay, 0, 0, false, false);
            // The accepted pressure indicator is its own tile-sized, unshaded overlay.
            AirlockBlockEntityRenderer.drawComponent(pressure, pose, buffers, 0x00F000F0, packedOverlay, 0, 0, false, true);
            pose.pushPose();
            try {
                // Source pivot [0,10,3.5] plus X/Z +8 exactly once; local native block units.
                pose.translate(.5, 10.0 / 16.0, 11.5 / 16.0);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(plan.panelAngleDegrees()));
                pose.translate(-.5, -10.0 / 16.0, -11.5 / 16.0);
                AirlockBlockEntityRenderer.drawComponent(panel, pose, buffers, packedLight, packedOverlay, 0, 0, false, false);
            } finally { pose.popPose(); }
        } finally { pose.popPose(); }
    }
}
