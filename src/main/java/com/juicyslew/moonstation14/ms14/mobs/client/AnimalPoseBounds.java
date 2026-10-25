package com.juicyslew.moonstation14.ms14.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import java.util.OptionalDouble;
import java.util.function.Predicate;

/** Pure measurement of visible cuboid bounds in a posed model, expressed in model pixels. */
public final class AnimalPoseBounds {
    private AnimalPoseBounds() { }

    /** Returns the greatest transformed Y coordinate (model Y points down), or empty without rendered cubes. */
    public static OptionalDouble maxVisibleY(ModelPart root) {
        return maxVisibleY(root, path -> true);
    }

    /** Returns greatest transformed Y among visible cubes whose visited path matches. */
    public static OptionalDouble maxVisibleY(ModelPart root, Predicate<String> pathFilter) {
        double[] maximum = {Double.NEGATIVE_INFINITY};
        PoseStack pose = new PoseStack();
        root.visit(pose, (matrix, path, index, cube) -> {
            if (!visiblePath(root, path.toString()) || cube == null || !pathFilter.test(path.toString())) return;
            float[] xs = {cube.minX, cube.maxX};
            float[] ys = {cube.minY, cube.maxY};
            float[] zs = {cube.minZ, cube.maxZ};
            for (float x : xs) for (float y : ys) for (float z : zs) {
                Vector3f point = matrix.pose().transformPosition(new Vector3f(x / 16F, y / 16F, z / 16F));
                maximum[0] = Math.max(maximum[0], point.y * 16F);
            }
        });
        return maximum[0] == Double.NEGATIVE_INFINITY ? OptionalDouble.empty() : OptionalDouble.of(maximum[0]);
    }

    private static boolean visiblePath(ModelPart root, String path) {
        String[] parts = path.split("/");
        ModelPart current = root;
        for (int i = 1; i < parts.length; i++) {
            if (!current.visible) return false;
            current = current.getChild(parts[i]);
        }
        return current.visible && !current.skipDraw;
    }
}
