package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Gameplay targeting bounds for a prone rendered body; deliberately separate from physical collision/culling. */
public final class BodyInteractionBounds {
    private BodyInteractionBounds() { }

    public static AABB of(LivingEntity body) {
        AABB physical = body.getBoundingBox();
        if (!PostureSystem.isDownPosture(body)) return physical;
        var standing = body.getDimensions(Pose.STANDING);
        PostureData posture = posture(body);
        return downed(physical, body.position(), standing.width(), standing.height(),
                PronePoseGeometry.modelScale(body), body.getScale(), posture.movementFacingYaw(), posture.faceUp());
    }

    private static PostureData posture(LivingEntity body) {
        var data = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
        return data == null ? PostureData.STANDING : data.data();
    }

    static AABB downed(AABB physical, Vec3 position, double width, double height, float yaw, boolean faceUp) {
        return downed(physical, position, width, height, 1, 1, yaw, faceUp);
    }

    static AABB downed(AABB physical, Vec3 position, double width, double height,
                       double modelScale, double entityScale, float yaw, boolean faceUp) {
        AABB result = physical;
        for (double x : new double[]{-1, 1}) for (double y : new double[]{-1, 1})
            for (double z : new double[]{-1, 1}) {
                Vec3 corner = PronePoseGeometry.transformScaledCorner(position, modelScale, entityScale,
                        yaw, faceUp, x, y, z);
                result = result.minmax(new AABB(corner, corner));
            }
        return result.inflate(1.0e-7);
    }
}

