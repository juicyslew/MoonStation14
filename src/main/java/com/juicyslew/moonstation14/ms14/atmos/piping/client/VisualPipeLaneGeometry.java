package com.juicyslew.moonstation14.ms14.atmos.piping.client;

import net.minecraft.core.Direction;

/** Shared unit-block bounds for a visual pipe lane's center and directional arms. */
public final class VisualPipeLaneGeometry {
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) { }

    private VisualPipeLaneGeometry() { }

    public static Bounds center(int minimum) {
        float low = minimum / 16.0F;
        float high = (minimum + 2) / 16.0F;
        return new Bounds(low, low, low, high, high, high);
    }

    public static Bounds arm(int minimum, Direction direction) {
        Bounds center = center(minimum);
        return switch (direction) {
            case WEST -> new Bounds(0, center.minY(), center.minZ(), center.minX(), center.maxY(), center.maxZ());
            case EAST -> new Bounds(center.maxX(), center.minY(), center.minZ(), 1, center.maxY(), center.maxZ());
            case DOWN -> new Bounds(center.minX(), 0, center.minZ(), center.maxX(), center.minY(), center.maxZ());
            case UP -> new Bounds(center.minX(), center.maxY(), center.minZ(), center.maxX(), 1, center.maxZ());
            case NORTH -> new Bounds(center.minX(), center.minY(), 0, center.maxX(), center.maxY(), center.minZ());
            case SOUTH -> new Bounds(center.minX(), center.minY(), center.maxZ(), center.maxX(), center.maxY(), 1);
        };
    }
}
