package com.juicyslew.moonstation14.ms14.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Emits authored, already-baked BLOCK-coordinate quads on vanilla player bones. */
final class AuthoredWearableRenderer {
    private AuthoredWearableRenderer() { }

    static boolean canRender(java.util.Optional<com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData.WornModel> wornModel,
                             com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape shape,
                             boolean meshLoaded) {
        return meshLoaded && shape == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape.WIDE
                && wornModel.filter(w -> w.bodyShape() == shape).isPresent();
    }

    static net.minecraft.resources.ResourceLocation selectedMesh(com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData.WornModel model, double elapsedSeconds) {
        if (model.frames().isEmpty()) return model.mesh();
        double[] durations = model.frames().stream().mapToDouble(com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData.WornModel.Frame::durationSeconds).toArray();
        int index = com.juicyslew.moonstation14.ms14.singularity.SingularityFrameTiming.frameAt(elapsedSeconds, durations);
        return model.frames().get(index).mesh();
    }

    static boolean render(EquipmentRenderPlan.Garment garment, PlayerModel<?> model, PoseStack pose,
                          MultiBufferSource buffers, int light) {
        return render(garment, model, pose, buffers, light, 0);
    }
    static boolean render(EquipmentRenderPlan.Garment garment, PlayerModel<?> model, PoseStack pose,
                           MultiBufferSource buffers, int light, double elapsedSeconds) {
        return render(garment, elapsedSeconds, AuthoredWearableMeshes::get,
                texture -> buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),
                anchor -> EquipmentPlaceholderGeometry.boneFor(model, anchor), pose, light);
    }

    static boolean render(EquipmentRenderPlan.Garment garment, double elapsedSeconds,
                          java.util.function.Function<net.minecraft.resources.ResourceLocation, java.util.Optional<AuthoredWearableMesh>> lookup,
                          java.util.function.Function<net.minecraft.resources.ResourceLocation, VertexConsumer> consumers,
                          java.util.function.Function<EquipmentRenderPlan.Anchor, net.minecraft.client.model.geom.ModelPart> bones,
                          PoseStack pose, int light) {
        if (garment.wornModel().isEmpty()) return false;
        var selected = selectedMesh(garment.wornModel().get(), elapsedSeconds);
        var mesh = lookup.apply(selected).orElse(null);
        if (mesh == null) return false;
        VertexConsumer consumer = consumers.apply(mesh.texture());
        emit(mesh, bones, pose, consumer, light);
        return true;
    }

    static void emit(AuthoredWearableMesh mesh,
                     java.util.function.Function<EquipmentRenderPlan.Anchor, net.minecraft.client.model.geom.ModelPart> bones,
                     PoseStack pose, VertexConsumer consumer, int light) {
        for (var entry : mesh.bones().entrySet()) {
            var quads = entry.getValue();
            if (quads.isEmpty()) continue;
            pose.pushPose();
            try {
                bones.apply(entry.getKey()).translateAndRotate(pose);
                var matrix = pose.last();
                for (var quad : quads) for (var vertex : quad.vertices()) {
                    var normal = quad.normal();
                    consumer.addVertex(matrix, (float) vertex.x(), (float) vertex.y(), (float) vertex.z())
                            .setColor(255, 255, 255, 255).setUv((float) vertex.u(), (float) vertex.v())
                            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(matrix, normal.x(), normal.y(), normal.z());
                }
            } finally { pose.popPose(); }
        }
    }
}
