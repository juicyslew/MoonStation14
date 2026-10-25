package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.mobs.client.AnimalModelAdapters;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** CPU-only measurements of the actual baked exported rigs; no client is started. */
class FocusedAnimalPresentationGeometryTest {
    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) { }

    private static Map<String, Bounds> measure(String model) {
        ModelPart root = AnimalModelAdapters.resolve(model).definition().bakeRoot();
        float[] bounds = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            var matrix = pose.pose();
            float[] xs = {cube.minX, cube.maxX}, ys = {cube.minY, cube.maxY}, zs = {cube.minZ, cube.maxZ};
            for (float x : xs) for (float y : ys) for (float z : zs) {
                var point = matrix.transformPosition(new org.joml.Vector3f(x / 16F, y / 16F, z / 16F));
                bounds[0] = Math.min(bounds[0], point.x * 16F); bounds[1] = Math.min(bounds[1], point.y * 16F);
                bounds[2] = Math.min(bounds[2], point.z * 16F); bounds[3] = Math.max(bounds[3], point.x * 16F);
                bounds[4] = Math.max(bounds[4], point.y * 16F); bounds[5] = Math.max(bounds[5], point.z * 16F);
            }
        });
        return Map.of("all", new Bounds(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]));
    }

    @Test void printsActualBakedAnimalBoundsAndFlyerGroundClearance() {
        for (String id : new String[]{"gorilla", "bat", "bee", "butterfly", "parrot"}) {
            Bounds b = measure(id).get("all");
            System.out.printf("GEOMETRY %s bounds=[%.3f,%.3f,%.3f..%.3f,%.3f,%.3f] width=%.3f height=%.3f feetGap=%.3f%n",
                    id, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                    b.maxX-b.minX, b.maxY-b.minY, (24F-b.maxY)/16F);
            assertTrue(Float.isFinite(b.maxY), id);
        }
    }
}
