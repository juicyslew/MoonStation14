package com.juicyslew.moonstation14.ms14.singularity.containment;

import net.minecraft.core.BlockPos;

/** Converts discrete generator-anchor indices to the shared world-space center geometry. */
public final class FieldGeometry {
    private FieldGeometry() { }
    public static double center(int generatorIndex) { return generatorIndex + 0.5; }
    public static double[] center(BlockPos anchor) {
        return new double[]{center(anchor.getX()), center(anchor.getY()), center(anchor.getZ())};
    }
}
