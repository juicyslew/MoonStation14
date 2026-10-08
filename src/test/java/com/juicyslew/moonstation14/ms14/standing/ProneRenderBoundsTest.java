package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProneRenderBoundsTest {
    private static final double BOUNDS_EPSILON = 1e-9;

    @Test void nativeHumanoidMeshUsesFullRendererMatrixAndRootCenteredBounds() {
        for (float modelScale : new float[]{.9375F, 1F})
            for (float entityScale : new float[]{1F, 2F})
                for (float yaw : new float[]{0, 90, 180, 270})
                    for (boolean faceUp : new boolean[]{false, true}) {
                        Vec3 root = new Vec3(13, 64, -7);
                        // Independently encode the complete setupRotations + vanilla model stack.
                        Matrix4f matrix = new Matrix4f().translate((float)root.x, (float)root.y, (float)root.z)
                                .scale(entityScale)
                                .rotateY((float)Math.toRadians(180-yaw))
                                .translate(0, 2F/16F*modelScale, 0)
                                .rotateX((float)Math.toRadians(-90))
                                .rotateY(faceUp ? (float)Math.PI : 0)
                                .translate(0, -1.001F*modelScale, 0)
                                .scale(-1,-1,1).scale(modelScale)
                                .translate(0,-1.501F,0);
                        PoseStack productionPose = new PoseStack();
                        productionPose.translate(root.x, root.y, root.z);
                        productionPose.scale(entityScale, entityScale, entityScale);
                        productionPose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
                        PronePoseMath.applySharedRootPose(productionPose, .6F, modelScale, faceUp);
                        productionPose.scale(-1, -1, 1);
                        productionPose.scale(modelScale, modelScale, modelScale);
                        productionPose.translate(0, -1.501F, 0);
                        AABB bounds = new AABB(root.x-.3, root.y, root.z-.3, root.x+.3, root.y+1.8, root.z+.3);
                        AABB interaction = BodyInteractionBounds.downed(bounds, root, .6, 1.8,
                                modelScale, entityScale, yaw, faceUp);
                        AABB culling = ProneRenderBounds.calculate(bounds, root, .6, 1.8,
                                modelScale, entityScale, yaw, faceUp);
                        for (float modelY : new float[]{-.5F, 1.5F})
                            for (float x : new float[]{-6F/16, 6F/16})
                                for (float z : new float[]{-4F/16, 4F/16}) {
                                    Vector3f actual = matrix.transformPosition(new Vector3f(x, modelY, z));
                                    Vector3f production = productionPose.last().pose()
                                            .transformPosition(new Vector3f(x, modelY, z));
                                    assertEquals(actual.x, production.x, 1e-5, "production X stack parity");
                                    assertEquals(actual.y, production.y, 1e-5, "production Y stack parity");
                                    assertEquals(actual.z, production.z, 1e-5, "production Z stack parity");
                                    Vec3 point = new Vec3(actual.x, actual.y, actual.z);
                                    assertTrue(containsInclusive(interaction, point), "interaction misses native mesh " + point);
                                    assertTrue(containsInclusive(culling, point), "culling misses native mesh " + point);
                                }
                        // The un-depth-shifted mesh longitudinal endpoints straddle root evenly.
                        Vector3f head = matrix.transformPosition(new Vector3f(0,-.5F,0));
                        Vector3f feet = matrix.transformPosition(new Vector3f(0,1.5F,0));
                        assertEquals(root.x, (head.x + feet.x) / 2, 1e-5);
                        assertEquals(root.z, (head.z + feet.z) / 2, 1e-5);

                        // Solid torso: 2px half-depth around the shared pivot. Solid head:
                        // 4px half-depth with the existing 2px outward head support.
                        for (float depth : new float[]{-2F/16, 2F/16}) {
                            Vector3f torso = matrix.transformPosition(new Vector3f(0, 8F/16, depth));
                            assertTrue(torso.y >= root.y - 1e-5, "torso penetrates floor");
                        }
                        assertEquals(root.y, Math.min(
                                matrix.transformPosition(new Vector3f(0, 8F/16, -2F/16)).y,
                                matrix.transformPosition(new Vector3f(0, 8F/16, 2F/16)).y),
                                1e-5, "solid torso contacts floor");
                        float headSupport = PronePoseMath.headGroundingOffsetPixels(faceUp) / 16F;
                        for (float depth : new float[]{-4F/16, 4F/16}) {
                            Vector3f headPoint = matrix.transformPosition(new Vector3f(0,
                                    8F/16, headSupport + depth));
                            assertTrue(headPoint.y >= root.y - 1e-5, "head penetrates floor: " + headPoint.y);
                        }
                        assertEquals(root.y, matrix.transformPosition(new Vector3f(0, 8F/16,
                                headSupport + (faceUp ? 4F/16 : -4F/16))).y, 1e-5,
                                "solid head contacts floor");
                    }
    }
    @Test void sharedModelBasisKeepsHeadLeadingAndRollOnlyFlipsFrontBack() {
        Vec3 origin = Vec3.ZERO;
        Vec3 expectedForward0 = new Vec3(0, 0, 1);
        Vec3 expectedForward90 = new Vec3(-1, 0, 0);
        for (float yaw : new float[]{0, 90}) {
            Vec3 headDown = PronePoseMath.transform(origin, new Vec3(0, 1, 0), yaw, false);
            Vec3 headUp = PronePoseMath.transform(origin, new Vec3(0, 1, 0), yaw, true);
            Vec3 expected = yaw == 0 ? expectedForward0 : expectedForward90;
            assertEquals(expected.x, headDown.x, 1e-9);
            assertEquals(expected.z, headDown.z, 1e-9);
            assertEquals(headDown.x, headUp.x, 1e-9);
            assertEquals(headDown.y, headUp.y, 1e-9);
            assertEquals(headDown.z, headUp.z, 1e-9, "longitudinal roll must not exchange head and feet");
            Vec3 frontDown = PronePoseMath.transform(origin, new Vec3(0, 0, -1), yaw, false);
            Vec3 frontUp = PronePoseMath.transform(origin, new Vec3(0, 0, -1), yaw, true);
            assertEquals(-1, frontDown.y, 1e-9);
            assertEquals(1, frontUp.y, 1e-9);
        }
    }

    @Test void idleArmsRestForAnyLyingOrientationButOwnCrawlUsesMovementAnimation() {
        for (boolean faceUp : new boolean[]{false, true}) {
            PostureData idle = new PostureData(true, true, 0, 0, 2, 2, false, faceUp, false);
            assertTrue(PronePoseMath.shouldRestArms(idle));
            assertTrue(!PronePoseMath.shouldRestArms(idle.crawling(true)));
        }
        assertTrue(!PronePoseMath.shouldRestArms(PostureData.STANDING));
    }
    @Test void torsoAndHeadwearGeometryGroundTogetherAcrossScalesAndOrientations() {
        // Solid torso depth is 2px and solid head depth is 4px; thin outer layers
        // may penetrate the floor slightly as an accepted rendering adaptation.
        for (double scale : new double[]{.5, .9375, 1, 2})
            for (boolean faceUp : new boolean[]{false, true}) {
                double pivot = PronePoseMath.pivotOffset(.6).y * scale;
                double torsoContact = pivot - 2.0 / 16.0 * scale;
                double headCenter = PronePoseMath.headGroundingOffsetPixels(faceUp) / 16.0;
                double headFacingVertex = faceUp ? 4.0 / 16.0 : -4.0 / 16.0;
                double headContact = pivot + PronePoseMath.transform(Vec3.ZERO,
                        new Vec3(0, 0, headCenter + headFacingVertex), 0, faceUp).y * scale;
                assertEquals(0, torsoContact, 1e-9);
                assertEquals(0, headContact, 1e-9,
                        "headwear and torso contact at renderer scale " + scale + " faceUp=" + faceUp);
            }
        assertEquals(2.0 / 16.0, PronePoseMath.pivotOffset(.6).y, 1e-9);
        assertEquals(2.0F, PronePoseMath.headGroundingOffsetPixels(false));
        assertEquals(-2.0F, PronePoseMath.headGroundingOffsetPixels(true));
    }

    @Test void proneHeadwearContactsFloorAfterRendererAndEntityScalesInVanillaOrder() {
        for (double entityScale : new double[]{.5, 1, 2})
            for (double rendererModelScale : new double[]{.9375, 1})
                for (boolean faceUp : new boolean[]{false, true}) {
                    Matrix4f pose = new Matrix4f().scaling((float) entityScale)
                            .translate(0, (float) (PronePoseMath.pivotOffset(.6).y * rendererModelScale), 0)
                            .rotateX((float) Math.toRadians(PronePoseMath.PITCH_DEGREES))
                            .rotateY(faceUp ? (float) Math.PI : 0)
                            .scale(-1, -1, 1)
                            .scale((float) rendererModelScale, (float) rendererModelScale,
                                    (float) rendererModelScale);
                    float headOffset = PronePoseMath.headGroundingOffsetPixels(faceUp) / 16.0F;
                    float solidHeadDepth = 4.0F / 16.0F;
                    double lowestHeadwearPoint = Math.min(
                            pose.transformPosition(new Vector3f(0, 0, headOffset - solidHeadDepth)).y,
                            pose.transformPosition(new Vector3f(0, 0, headOffset + solidHeadDepth)).y);
                    double lowestTorsoPoint = Math.min(
                            pose.transformPosition(new Vector3f(0, 0, -2.0F / 16.0F)).y,
                            pose.transformPosition(new Vector3f(0, 0, 2.0F / 16.0F)).y);
                    assertEquals(0, lowestHeadwearPoint, 1e-6,
                            "floor contact with entityScale=" + entityScale + " modelScale="
                                    + rendererModelScale + " faceUp=" + faceUp);
                    assertEquals(0, lowestTorsoPoint, 1e-6,
                            "torso floor contact with entityScale=" + entityScale + " modelScale="
                                    + rendererModelScale + " faceUp=" + faceUp);
                }
    }
    @Test void cullingBoundsContainEveryTransformedModelCornerAtQuarterTurnsAndBothOrientations() {
        Vec3 position = new Vec3(11.25, 64, -8.5);
        for (double[] profile : new double[][]{{0.6, 1.8}, {.14, .5}}) {
            double width = profile[0], height = profile[1], half = width * .5;
            AABB standing = new AABB(position.x - half, position.y, position.z - half,
                    position.x + half, position.y + height, position.z + half);
            for (float yaw : new float[]{0, 90}) for (boolean faceUp : new boolean[]{false, true}) {
                AABB bounds = ProneRenderBounds.calculate(standing, position, width, height, yaw, faceUp);
                for (double x : new double[]{-half, half})
                    for (double y : new double[]{0, height})
                        for (double z : new double[]{-half, half}) {
                            Vec3 corner = ProneRenderBounds.transformCorner(position, width, height,
                                    yaw, faceUp, x / half, y / height, z / half);
                            assertTrue(containsInclusive(bounds, corner), () -> "culled render corner " + corner
                                    + " yaw=" + yaw + " faceUp=" + faceUp + " bounds=" + bounds);
                        }
            }
        }
    }

    @Test void cullingBoundsCoverRendererYawIndependentOfEntityHeadYaw() {
        Vec3 position = new Vec3(11.25, 64, -8.5);
        for (double[] profile : new double[][]{{.6, 1.8}, {.14, .5}}) {
            double width = profile[0], height = profile[1], half = width * .5;
            AABB standing = new AABB(position.x - half, position.y, position.z - half,
                    position.x + half, position.y + height, position.z + half);
            for (boolean faceUp : new boolean[]{false, true}) {
                // Entity YRot is 0 here; setupRotations may use body yaw or an interpolated prior yaw.
                AABB bounds = ProneRenderBounds.calculate(standing, position, width, height, 0, faceUp);
                for (float renderedBodyYaw : new float[]{35, 45, 75, 90})
                    for (double x : new double[]{-half, half})
                        for (double y : new double[]{0, height})
                            for (double z : new double[]{-half, half}) {
                                Vec3 corner = ProneRenderBounds.transformCorner(position, width, height,
                                        renderedBodyYaw, faceUp, x / half, y / height, z / half);
                                assertTrue(containsInclusive(bounds, corner), () ->
                                        "renderer yaw " + renderedBodyYaw + " corner " + corner
                                                + " outside entity-yaw culling bounds " + bounds);
                            }
            }
        }
    }

    private static boolean containsInclusive(AABB bounds, Vec3 point) {
        return point.x >= bounds.minX - BOUNDS_EPSILON && point.x <= bounds.maxX + BOUNDS_EPSILON
                && point.y >= bounds.minY - BOUNDS_EPSILON && point.y <= bounds.maxY + BOUNDS_EPSILON
                && point.z >= bounds.minZ - BOUNDS_EPSILON && point.z <= bounds.maxZ + BOUNDS_EPSILON;
    }
}
