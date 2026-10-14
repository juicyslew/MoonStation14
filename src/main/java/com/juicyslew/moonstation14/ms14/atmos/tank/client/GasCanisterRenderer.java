package com.juicyslew.moonstation14.ms14.atmos.tank.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.entities.GasCanisterEntity;
import com.juicyslew.moonstation14.ms14.doors.client.AirlockBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.List;

/** Draws the synced, resolved canister appearance as exported OBJ components. */
public final class GasCanisterRenderer extends EntityRenderer<GasCanisterEntity> {
    public GasCanisterRenderer(EntityRendererProvider.Context context) { super(context); }

    public static List<ModelResourceLocation> additionalModels() {
        ArrayList<ModelResourceLocation> result = new ArrayList<>();
        for (String skin : List.of("yellow", "orange"))
            for (String part : List.of("shell", "head", "pressure_0", "pressure_1", "pressure_2", "pressure_3",
                    "lock_locked", "lock_unlocked", "tank_inserted"))
                result.add(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,
                        "block/canisters/" + skin + "/components/" + part)));
        return List.copyOf(result);
    }

    @Override public void render(GasCanisterEntity entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        var appearance = entity.appearance();
        if (appearance.isEmpty()) return;
        var plan = GasCanisterRenderPlan.from(appearance.get());
        pose.pushPose();
        try {
            pose.mulPose(com.mojang.math.Axis.YP.rotation(entity.getRotationAngle(partialTick)));
            pose.translate(-.5, 0, -.5);
            var manager = Minecraft.getInstance().getModelManager();
            for (var component : plan.components()) {
                var model = manager.getModel(ModelResourceLocation.standalone(component.model()));
                AirlockBlockEntityRenderer.drawComponent(model, pose, buffers,
                        component.fullbright() ? 0x00F000F0 : light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0, 0, false, false);
            }
        } finally { pose.popPose(); }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(GasCanisterEntity entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
