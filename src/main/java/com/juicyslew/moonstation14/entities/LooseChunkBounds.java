package com.juicyslew.moonstation14.entities;

import net.minecraft.world.phys.AABB;

/** Chunk-coordinate rectangle covered by a world-space box. */
public record LooseChunkBounds(int minX, int maxX, int minZ, int maxZ) {
    public static LooseChunkBounds from(AABB box) {
        return new LooseChunkBounds(section(box.minX, box.maxX), sectionMax(box.minX, box.maxX),
                section(box.minZ, box.maxZ), sectionMax(box.minZ, box.maxZ));
    }

    private static int section(double min, double max) { return (int) Math.floor(min) >> 4; }
    private static int sectionMax(double min, double max) {
        return (int) Math.floor(max > min ? Math.nextDown(max) : min) >> 4;
    }
}

