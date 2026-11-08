package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.juicyslew.moonstation14.entities.ItemAttachment;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.joml.Matrix4f;

/** Draws the existing attached loose-item entity in its target renderer's current root pose. */
public final class AttachedThrownItemLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final RenderLayerParent<T, M> layerParent;
    public AttachedThrownItemLayer(RenderLayerParent<T, M> parent) { super(parent); this.layerParent = parent; }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, T body,
                                 float limbSwing, float limbSwingAmount, float partialTick,
                                 float ageInTicks, float netHeadYaw, float headPitch) {
        if (body.level().isClientSide == false || body.isRemoved()) return;
        for (LooseItemEntity item : com.juicyslew.moonstation14.entities.ItemAttachmentQueries.attachedTo(body)) {
            ItemAttachment attachment = item.attachment().orElse(null);
            if (attachment == null || !attachment.targetUUID().equals(body.getUUID())
                    || attachment.targetEntityId() != body.getId() || item.getItem().isEmpty()) continue;
            final LooseObjectProfile profile;
            try { profile = LooseObjectProfile.resolveForStack(item.getItem(), true); }
            catch (RuntimeException catalogNotReady) { continue; }

            var thrown = com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.resolveForStack(item.getItem(), true).orElse(null);
            if (thrown == null) continue;
            boolean ensnared = attachment.kind() != ItemAttachment.AttachmentKind.EMBEDDED;
            var model = Minecraft.getInstance().getItemRenderer().getModel(item.getItem(), body.level(), null, item.getId());
            var data = thrown.embedding().orElse(null);
            EmbeddedItemPose embeddedPose = ensnared ? null : data == null ? null : EmbeddedItemPose.calculate(model, profile,
                    com.juicyslew.moonstation14.ms14.botany.ProduceWorldAppearance.scale(item.getItem()), attachment, data);
            if (!ensnared && embeddedPose == null) continue;

            pose.pushPose();
            try {
                float rendererScale = layerParent instanceof com.juicyslew.moonstation14.ms14.mobs.client.AnimalMobRenderer animal
                        ? animal.selectedModelScale() : 1f;
                AttachedItemPose.apply(pose, attachment, rendererScale);
                if (ensnared) pose.translate(0, 0, 0);
                else { pose.translate(embeddedPose.anchor().x, embeddedPose.anchor().y, embeddedPose.anchor().z); pose.mulPose(embeddedPose.rotation()); }
                WorldItemPresentationRenderer.renderCameraFacing(item.getItem(), profile, pose, buffers,
                        light, body.level(), item.getId(), 1f);
            } finally { pose.popPose(); }
        }
    }

    /** Local presentation axis is MC model XY (not the upstream throwing-angle convention). */
    static record EmbeddedItemPose(Vector3f anchor, Quaternionf rotation) {
        static EmbeddedItemPose calculate(net.minecraft.client.resources.model.BakedModel model, LooseObjectProfile profile,
                                           float appearance, ItemAttachment attachment,
                                           com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.EmbeddingData data) {
            Vector3f direction = new Vector3f((float)attachment.targetLocalDirection().x, (float)attachment.targetLocalDirection().y,
                    (float)attachment.targetLocalDirection().z);
            if (direction.lengthSquared() < 1e-8f) return null;
            direction.normalize();
            Quaternionf rotation = ThrownSpritePose.rotation(data.spriteForwardDegrees(), direction);
            if (rotation == null) return null;
            var extent = LoosePresentationBounds.flatSpriteProjection(model, profile, appearance,
                    new Matrix4f().rotate(rotation), new Vector3f(direction));
            if (extent == null) return null;
            Vector3f tip = data.spriteTipUv().map(tipUv -> LoosePresentationBounds.flatSpritePointAtUv(
                    model, profile, appearance, new Matrix4f().rotate(rotation), tipUv.u(), tipUv.v())).orElse(null);
            if (tip == null) tip = new Vector3f((float)extent.maxPoint().x, (float)extent.maxPoint().y, (float)extent.maxPoint().z);
            Vector3f nativeOffset = new Vector3f(direction).mul((float)-data.offsetX())
                    .add(new Vector3f(-direction.y, direction.x, 0).mul((float)data.offsetY()));
            Vector3f anchor = new Vector3f(-tip.x, -tip.y, -tip.z)
                    .add(nativeOffset);
            return new EmbeddedItemPose(anchor, rotation);
        }
    }

    /** Convert yaw-local metre contact to the renderer's model-root frame, removing inherited scale. */
    static final class AttachedItemPose {
        private AttachedItemPose() {}
        static void apply(PoseStack pose, ItemAttachment attachment, float extraScale) {
            // Attachment vectors were captured in body-yaw space. The renderer root already
            // contains that yaw (and current posture), so applying it again double-rotates.
            var contact = attachment.targetLocalContact();
            // Contact is already in the native model-root frame. Keep the renderer's complete
            // yaw/posture matrix; only undo its linear scale so item dimensions stay in metres.
            float f = extraScale > 1e-6f ? extraScale : 1f;
            pose.translate(contact.x / f, 1.501f - (1.501f - contact.y) / f, contact.z / f);
            var matrix = pose.last().pose();
            // Matrix columns are transformed local axes; their norms are the inherited
            // per-axis scale. Translation is untouched by this local inverse scale.
            float sx = (float)Math.sqrt(matrix.m00()*matrix.m00()+matrix.m01()*matrix.m01()+matrix.m02()*matrix.m02());
            float sy = (float)Math.sqrt(matrix.m10()*matrix.m10()+matrix.m11()*matrix.m11()+matrix.m12()*matrix.m12());
            float sz = (float)Math.sqrt(matrix.m20()*matrix.m20()+matrix.m21()*matrix.m21()+matrix.m22()*matrix.m22());
            if (sx > 1e-6f && sy > 1e-6f && sz > 1e-6f) pose.scale(1f/sx, 1f/sy, 1f/sz);
        }
    }
}
