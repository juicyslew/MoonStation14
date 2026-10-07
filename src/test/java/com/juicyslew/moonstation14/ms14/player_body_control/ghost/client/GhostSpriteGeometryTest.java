package com.juicyslew.moonstation14.ms14.player_body_control.ghost.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostSpriteGeometryTest {
    @Test
    void centeredQuadStaysAtEyeHeightAndKeepsSquareSizeWhenPitchedAndRotated() {
        float eyeHeight = 1.53F;
        Matrix4f pose = new Matrix4f().translate(0, eyeHeight, 0)
                .rotate(new Quaternionf().rotationXYZ(0.73F, -1.12F, 0.41F))
                .scale(GhostSpriteGeometry.SIZE);

        Vector3f center = pose.transformPosition(0, 0, 0, new Vector3f());
        assertEquals(0, center.x, 1.0e-6F);
        assertEquals(eyeHeight, center.y, 1.0e-6F);
        assertEquals(0, center.z, 1.0e-6F);

        Vector3f left = pose.transformPosition(-GhostSpriteGeometry.HALF_SIZE, 0, 0, new Vector3f());
        Vector3f right = pose.transformPosition(GhostSpriteGeometry.HALF_SIZE, 0, 0, new Vector3f());
        Vector3f bottom = pose.transformPosition(0, -GhostSpriteGeometry.HALF_SIZE, 0, new Vector3f());
        Vector3f top = pose.transformPosition(0, GhostSpriteGeometry.HALF_SIZE, 0, new Vector3f());
        assertEquals(GhostSpriteGeometry.SIZE, left.distance(right), 1.0e-5F);
        assertEquals(GhostSpriteGeometry.SIZE, bottom.distance(top), 1.0e-5F);
    }
}
