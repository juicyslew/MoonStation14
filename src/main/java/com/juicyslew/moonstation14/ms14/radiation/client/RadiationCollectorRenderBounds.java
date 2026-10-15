package com.juicyslew.moonstation14.ms14.radiation.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Local-unit union of the accepted collector shell and panel swept through its full 0..90 degree clip. */
final class RadiationCollectorRenderBounds {
    // Covers shell (including z=2.84 lower_controls), pressure overlay (z=2.83),
    // and panel element sweep about [8,10,11.5]/16, rounded outward to tenths of a pixel.
    static final AABB LOCAL = new AABB(1.0 / 16, 0, 2.8 / 16,
            15.0 / 16, 20.0 / 16, 21.0 / 16);

    private RadiationCollectorRenderBounds() { }

    static AABB at(int x, int y, int z) {
        return LOCAL.move(new BlockPos(x, y, z));
    }
}
