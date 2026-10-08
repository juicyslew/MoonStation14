package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Shared prone model basis. Coordinates are physical upright-model offsets from the feet. */
public final class PronePoseMath {
    private PronePoseMath() { }

    public static final float PITCH_DEGREES = -90.0F;
    public static final float ROLL_DEGREES = 180.0F;

    /** Neutral torso outerwear depth in model pixels; collision width is irrelevant. */
    /** Solid torso depth used for floor contact; outerwear may sink by 0.25px. */
    public static final float TORSO_OUTER_DEPTH_PIXELS = 2.0F;
    /** Solid head depth is 4px; headwear may sink by 0.5px. */
    public static final float HEAD_GROUNDING_OFFSET_PIXELS = 4.0F - TORSO_OUTER_DEPTH_PIXELS;

    public static Vec3 pivotOffset(double width) { return new Vec3(0, TORSO_OUTER_DEPTH_PIXELS / 16.0, 0); }

    /** Position offset in ModelPart pixels, outward from the body front normal. */
    public static float headGroundingOffsetPixels(boolean faceUp) {
        return faceUp ? -HEAD_GROUNDING_OFFSET_PIXELS : HEAD_GROUNDING_OFFSET_PIXELS;
    }
    public static void applyRenderPose(PoseStack poseStack, float width, float modelScale, boolean faceUp) {
        poseStack.translate(0.0F, (float)(pivotOffset(width).y * modelScale), 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(PITCH_DEGREES));
        // Stack multiplication applies the later local-Y roll first to vertices;
        // it reverses the front normal while leaving the longitudinal head axis fixed.
        if (faceUp) poseStack.mulPose(Axis.YP.rotationDegrees(ROLL_DEGREES));
    }
    /** Root correction for vanilla humanoid vertices after native renderer transforms. */
    public static float nativeRootCenterOffset(float modelScale) {
        // Entity scale is already on the PoseStack before setupRotations. This
        // local correction cancels the renderer's translated native model origin.
        return -1.001F * modelScale;
    }

    public static void applySharedRootPose(PoseStack poseStack, float width, float modelScale, boolean faceUp) {
        applyRenderPose(poseStack, width, modelScale, faceUp);
        poseStack.translate(0, nativeRootCenterOffset(modelScale), 0);
    }

    /** Maps upright head-up/front (-Z) basis through vanilla's outer (-1,-1,1) scale and body yaw. */
    public static Vec3 transform(Vec3 position, Vec3 uprightOffset, float bodyYaw, boolean faceUp) {
        double yaw = Math.toRadians(180.0 - bodyYaw);
        double c = Math.cos(yaw), s = Math.sin(yaw);
        // Vanilla renderer scale(-1,-1,1) turns model +Y into physical head-up.
        // Roll is around the longitudinal axis and therefore cannot exchange head/feet.
        double x = uprightOffset.x;
        double y = uprightOffset.y;
        double z = uprightOffset.z;
        if (faceUp) { x = -x; z = -z; }
        // X(-90): head-up (-Y after vanilla scale) points toward the yawed forward axis;
        // the model front (-Z) points down. Face-up reverses only that front/back normal.
        double lyingY = z;
        double lyingZ = -y;
        double worldX = c * x + s * lyingZ;
        double worldZ = -s * x + c * lyingZ;
        return position.add(worldX, lyingY, worldZ);
    }

    public static boolean shouldRestArms(PostureData posture) {
        return posture != null && posture.down() && !posture.ownCrawling();
    }
}
