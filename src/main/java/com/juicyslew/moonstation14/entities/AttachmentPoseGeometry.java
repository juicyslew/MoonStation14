package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.standing.PronePoseGeometry;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;

/** Shared CPU-only native-root frame for capturing and following body attachments. */
public final class AttachmentPoseGeometry {
    public enum PoseMode { STANDING, PRONE, KNOCKDOWN }
    private AttachmentPoseGeometry() { }

    public static Matrix4f relativeRootMatrix(double modelScale, double entityScale, float yaw, PoseMode mode, boolean faceUp) {
        if (mode == PoseMode.PRONE) return PronePoseGeometry.relativeRootMatrix(modelScale, entityScale, yaw, faceUp);
        Matrix4f matrix = new Matrix4f().scale((float)entityScale).rotateY((float)Math.toRadians(180-yaw));
        if (mode == PoseMode.KNOCKDOWN) matrix.rotateZ((float)Math.PI / 2);
        return matrix.scale(-1,-1,1).scale((float)modelScale).translate(0,-1.501F,0);
    }

    private record Frame(double modelScale, double entityScale, float yaw, PoseMode mode, boolean faceUp) { }
    private static Frame frame(LivingEntity entity) {
        var posture = entity.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
        if (PostureSystem.isDownPosture(entity)) return new Frame(PronePoseGeometry.modelScale(entity), entity.getScale(),
                posture == null ? entity.getYRot() : posture.data().movementFacingYaw(), PoseMode.PRONE,
                posture != null && posture.data().faceUp());
        if (com.juicyslew.moonstation14.ms14.character.KnockdownRenderPolicy.shouldRenderProne(entity))
            return new Frame(PronePoseGeometry.modelScale(entity), entity.getScale(), entity.getYRot(), PoseMode.KNOCKDOWN, false);
        return new Frame(PronePoseGeometry.modelScale(entity), entity.getScale(), entity.getYRot(), PoseMode.STANDING, false);
    }
    public static Vec3 captureLocalPoint(LivingEntity entity, Vec3 worldPoint) {
        Frame f=frame(entity); return captureLocalPoint(worldPoint, entity.position(), f.modelScale, f.entityScale, f.yaw, f.mode, f.faceUp);
    }
    public static Vec3 bodyToWorld(LivingEntity entity, Vec3 nativeModelPoint) {
        Frame f=frame(entity); return bodyToWorld(nativeModelPoint, entity.position(), f.modelScale, f.entityScale, f.yaw, f.mode, f.faceUp);
    }
    public static Vec3 captureLocalDirection(LivingEntity entity, Vec3 direction) {
        Frame f=frame(entity); return captureLocalDirection(direction, f.modelScale, f.entityScale, f.yaw, f.mode, f.faceUp);
    }

    public static Vec3 captureLocalPoint(Vec3 worldPoint, Vec3 entityPosition, double modelScale,
                                         double entityScale, float yaw, boolean faceUp) { return captureLocalPoint(worldPoint,entityPosition,modelScale,entityScale,yaw,PoseMode.PRONE,faceUp); }
    public static Vec3 captureLocalPoint(Vec3 worldPoint, Vec3 entityPosition, double modelScale,
                                         double entityScale, float yaw, PoseMode mode, boolean faceUp) {
        Vec3 relative = worldPoint.subtract(entityPosition);
        Vector3f local = relativeRootMatrix(modelScale, entityScale, yaw, mode, faceUp).invert()
                .transformPosition(new Vector3f((float)relative.x, (float)relative.y, (float)relative.z));
        return new Vec3(local.x, local.y, local.z);
    }

    public static Vec3 bodyToWorld(Vec3 modelPoint, Vec3 entityPosition, double modelScale,
                                   double entityScale, float yaw, boolean faceUp) { return bodyToWorld(modelPoint,entityPosition,modelScale,entityScale,yaw,PoseMode.PRONE,faceUp); }
    public static Vec3 bodyToWorld(Vec3 modelPoint, Vec3 entityPosition, double modelScale,
                                   double entityScale, float yaw, PoseMode mode, boolean faceUp) {
        Vector3f result = relativeRootMatrix(modelScale, entityScale, yaw, mode, faceUp)
                .transformPosition(new Vector3f((float)modelPoint.x, (float)modelPoint.y, (float)modelPoint.z));
        return entityPosition.add(result.x, result.y, result.z);
    }

    public static Vec3 captureLocalDirection(Vec3 direction, double modelScale, double entityScale,
                                             float yaw, boolean faceUp) {
        return captureLocalDirection(direction, modelScale, entityScale, yaw, PoseMode.PRONE, faceUp);
    }
    public static Vec3 captureLocalDirection(Vec3 direction, double modelScale, double entityScale, float yaw, PoseMode mode, boolean faceUp) {
        Vector3f local = relativeRootMatrix(modelScale, entityScale, yaw, mode, faceUp).invert()
                .transformDirection(new Vector3f((float)direction.x, (float)direction.y, (float)direction.z));
        return new Vec3(local.x, local.y, local.z).normalize();
    }
}
