package com.juicyslew.moonstation14.ms14.botany.client;

import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class HydroponicsTrayBlockEntityRenderer implements BlockEntityRenderer<HydroponicsTrayBlockEntity> {
    private static final Logger LOGGER = Logger.getLogger(HydroponicsTrayBlockEntityRenderer.class.getName());
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private final PlantModelManifest manifest;

    public HydroponicsTrayBlockEntityRenderer(BlockEntityRendererProvider.Context context) { manifest = PlantModelManifest.load(); }

    public static java.util.List<net.minecraft.client.resources.model.ModelResourceLocation> additionalModels() {
        PlantModelManifest manifest = PlantModelManifest.load();
        return java.util.stream.Stream.concat(manifest.plants().values().stream().flatMap(states -> states.values().stream()), manifest.indicators().values().stream()).distinct().toList();
    }

    @Override public AABB getRenderBoundingBox(HydroponicsTrayBlockEntity tray) {
        BlockPos pos = tray.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }

    @Override public void render(HydroponicsTrayBlockEntity tray, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var status = tray.appearanceStatus();
        String state = switch (status) {
            case EMPTY -> null;
            case DEAD -> "dead";
            case HARVEST -> "harvest";
            case GROWING -> "stage-" + tray.appearanceStage();
        };
        var modelId = state == null ? null : manifest.model(tray.appearanceKey(), state);
        if (state != null && modelId == null) {
            String key = tray.appearanceKey() + "/" + state;
            if (WARNED.add(key)) LOGGER.warning("No hydroponics plant model binding for " + key);
            return;
        }
        var mc = Minecraft.getInstance();
        var blockState = tray.getBlockState();
        if (modelId != null) {
            var model = mc.getModelManager().getModel(modelId);
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()),
                    blockState, model, 1, 1, 1, packedLight, OverlayTexture.NO_OVERLAY);
        }
        String[] indicators = {"harvest", "health", "alert", "nutrition", "water"};
        for (int i = 0; i < indicators.length; i++) {
            if ((tray.warningBits() & (1 << i)) == 0) continue;
            var indicator = manifest.indicators().get(indicators[i]);
            if (indicator != null) mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()),
                    blockState, mc.getModelManager().getModel(indicator), 1, 1, 1, packedLight, OverlayTexture.NO_OVERLAY);
        }
    }
}
