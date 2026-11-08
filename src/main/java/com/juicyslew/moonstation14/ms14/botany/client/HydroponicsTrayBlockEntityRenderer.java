package com.juicyslew.moonstation14.ms14.botany.client;

import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
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

    private PlantModelManifest manifestFor(HydroponicsTrayBlockEntity tray) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getBlockModel(tray.getBlockState());
        return model instanceof PlantTrayBakedModel wrapper ? wrapper.manifest() : manifest;
    }

    @Override public AABB getRenderBoundingBox(HydroponicsTrayBlockEntity tray) {
        var appearance = tray.appearanceSnapshot();
        return manifestFor(tray).renderBoundingBox(appearance.key(), appearance.status() != com.juicyslew.moonstation14.ms14.botany.PlantInstance.Status.EMPTY, tray.getBlockPos());
    }

    @Override public void render(HydroponicsTrayBlockEntity tray, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var appearance = tray.appearanceSnapshot();
        var currentModel = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getBlockModel(tray.getBlockState());
        var activeManifest = currentModel instanceof PlantTrayBakedModel wrapper ? wrapper.manifest() : manifest;
        var modelId = activeManifest.model(appearance);
        if (appearance.status() != com.juicyslew.moonstation14.ms14.botany.PlantInstance.Status.EMPTY && modelId == null) {
            String key = appearance.key() + "/" + appearance.status() + "/" + appearance.stage();
            if (WARNED.add(key)) LOGGER.warning("No hydroponics plant model binding for " + key);
        }
        var mc = Minecraft.getInstance();
        var blockState = tray.getBlockState();
        boolean berPlantPath = PlantTrayBakedModel.shouldRenderInBer(currentModel, appearance, tray.getBlockPos());
        if (modelId != null && berPlantPath) {
            pose.pushPose();
            var model = mc.getModelManager().getModel(modelId);
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()),
                    blockState, model, 1, 1, 1, packedLight, OverlayTexture.NO_OVERLAY);
            pose.popPose();
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
