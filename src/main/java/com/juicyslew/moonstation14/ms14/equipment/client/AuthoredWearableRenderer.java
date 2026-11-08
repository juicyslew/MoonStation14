package com.juicyslew.moonstation14.ms14.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel;

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
        return render(garment, model, pose, buffers, light, elapsedSeconds, null);
    }
    static boolean render(EquipmentRenderPlan.Garment garment, PlayerModel<?> model, PoseStack pose,
                           MultiBufferSource buffers, int light, double elapsedSeconds,
                           java.util.Set<EquipmentRenderPlan.Anchor> allowedBones) {
        return render(garment, elapsedSeconds, AuthoredWearableMeshes::get,
                mesh -> buffers.getBuffer(mesh.material() == AuthoredWearableMesh.Material.TRANSLUCENT
                        ? RenderType.entityTranslucent(mesh.texture()) : RenderType.entityCutoutNoCull(mesh.texture())),
                 anchor -> EquipmentPlaceholderGeometry.boneFor(model, anchor), pose, light, allowedBones);
    }

    static boolean render(EquipmentRenderPlan.Garment garment, double elapsedSeconds,
                          java.util.function.Function<net.minecraft.resources.ResourceLocation, java.util.Optional<AuthoredWearableMesh>> lookup,
                           java.util.function.Function<AuthoredWearableMesh, VertexConsumer> consumers,
                          java.util.function.Function<EquipmentRenderPlan.Anchor, net.minecraft.client.model.geom.ModelPart> bones,
                           PoseStack pose, int light) { return render(garment, elapsedSeconds, lookup, consumers, bones, pose, light, null); }
    static boolean render(EquipmentRenderPlan.Garment garment, double elapsedSeconds,
                          java.util.function.Function<net.minecraft.resources.ResourceLocation, java.util.Optional<AuthoredWearableMesh>> lookup,
                          java.util.function.Function<AuthoredWearableMesh, VertexConsumer> consumers,
                          java.util.function.Function<EquipmentRenderPlan.Anchor, net.minecraft.client.model.geom.ModelPart> bones,
                          PoseStack pose, int light, java.util.Set<EquipmentRenderPlan.Anchor> allowedBones) {
        if (garment.wornModel().isEmpty()) return false;
        var selected = selectedMesh(garment.wornModel().get(), elapsedSeconds);
        var mesh = lookup.apply(selected).orElse(null);
        if (mesh == null) return false;
        VertexConsumer consumer = consumers.apply(mesh);
        emit(mesh, bones, pose, consumer, light, allowedBones);
        return true;
    }

    static void emit(AuthoredWearableMesh mesh,
                     java.util.function.Function<EquipmentRenderPlan.Anchor, net.minecraft.client.model.geom.ModelPart> bones,
                     PoseStack pose, VertexConsumer consumer, int light) {
        emit(mesh, bones, pose, consumer, light, null);
    }
    static void emit(AuthoredWearableMesh mesh,
                     java.util.function.Function<EquipmentRenderPlan.Anchor, net.minecraft.client.model.geom.ModelPart> bones,
                     PoseStack pose, VertexConsumer consumer, int light,
                     java.util.Set<EquipmentRenderPlan.Anchor> allowedBones) {
        for (var entry : mesh.bones().entrySet()) {
            if (allowedBones != null && !allowedBones.contains(entry.getKey())) continue;
            var quads = entry.getValue();
            if (quads.isEmpty()) continue;
            pose.pushPose();
            try {
                bones.apply(entry.getKey()).translateAndRotate(pose);
                var matrix = pose.last();
                boolean arm = entry.getKey() == EquipmentRenderPlan.Anchor.LEFT_ARM || entry.getKey() == EquipmentRenderPlan.Anchor.RIGHT_ARM;
                var bone = bones.apply(entry.getKey());
                for (var quad : quads) {
                    var normal = quad.normal();
                    if (arm && bone.hasChild("forearm")) {
                        var parts = clipAtElbow(quad);
                        pose.pushPose();
                        try {
                            if (bone.hasChild(ArticulatedHarnessModel.UPPER_GEOMETRY_CHILD))
                                bone.getChild(ArticulatedHarnessModel.UPPER_GEOMETRY_CHILD).translateAndRotate(pose);
                            emitQuad(parts.upper(), pose.last(), consumer, light, normal);
                        } finally { pose.popPose(); }
                        if (parts.lower() != null) {
                            pose.pushPose();
                            try {
                                var forearm = bone.getChild("forearm");
                                forearm.translateAndRotate(pose);
                                if (forearm.hasChild(ArticulatedHarnessModel.LOWER_GEOMETRY_CHILD))
                                    forearm.getChild(ArticulatedHarnessModel.LOWER_GEOMETRY_CHILD).translateAndRotate(pose);
                                pose.translate(0, -.25, 0);
                                emitQuad(parts.lower(), pose.last(), consumer, light, normal);
                            } finally { pose.popPose(); }
                        }
                    } else emitQuad(quad.vertices(), matrix, consumer, light, normal);
                }
            } finally { pose.popPose(); }
        }
    }

    record Clipped(java.util.List<AuthoredWearableMesh.Vertex> upper, java.util.List<AuthoredWearableMesh.Vertex> lower) { }
    static Clipped clipAtElbow(AuthoredWearableMesh.Quad quad) {
        var upper = clipSide(quad.vertices(), true);
        var lower = clipSide(quad.vertices(), false);
        return new Clipped(upper, lower.isEmpty() ? null : lower);
    }
    private static java.util.List<AuthoredWearableMesh.Vertex> clipSide(java.util.List<AuthoredWearableMesh.Vertex> in, boolean upper) {
        var out = new java.util.ArrayList<AuthoredWearableMesh.Vertex>();
        for (int i=0;i<in.size();i++) {
            var a=in.get(i); var b=in.get((i+1)%in.size());
            boolean ia=upper ? a.y()<=.25f : a.y()>=.25f, ib=upper ? b.y()<=.25f : b.y()>=.25f;
            if(ia) out.add(a);
            if(ia!=ib) { float t=(.25f-a.y())/(b.y()-a.y()); out.add(new AuthoredWearableMesh.Vertex(a.x()+(b.x()-a.x())*t,.25f,a.z()+(b.z()-a.z())*t,a.u()+(b.u()-a.u())*t,a.v()+(b.v()-a.v())*t)); }
        }
        return out;
    }
    private static void emitQuad(java.util.List<AuthoredWearableMesh.Vertex> vertices, com.mojang.blaze3d.vertex.PoseStack.Pose matrix, VertexConsumer consumer, int light, AuthoredWearableMesh.Normal normal) {
        if(vertices==null || vertices.size()<3) return;
        if(vertices.size()==4) {
            for(var vertex:vertices) consumer.addVertex(matrix,vertex.x(),vertex.y(),vertex.z()).setColor(255,255,255,255).setUv(vertex.u(),vertex.v()).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,normal.x(),normal.y(),normal.z());
            return;
        }
        for(int i=1;i<vertices.size()-1;i++) for(var vertex : java.util.List.of(vertices.get(0),vertices.get(i),vertices.get(i+1),vertices.get(i+1)))
            consumer.addVertex(matrix,(float)vertex.x(),(float)vertex.y(),(float)vertex.z()).setColor(255,255,255,255).setUv(vertex.u(),vertex.v()).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,normal.x(),normal.y(),normal.z());
    }
}
