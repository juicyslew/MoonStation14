package com.juicyslew.moonstation14.ms14.slip;

import net.minecraft.world.phys.AABB;

/** Geometry shared by step-contact sources and their consumers. */
public final class ItemStepContactGeometry {
    private ItemStepContactGeometry() { }

    public static AABB feetBand(AABB bounds) {
        return new AABB(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX,
                bounds.minY + Math.min(.25d, Math.max(0d, bounds.getYsize())), bounds.maxZ);
    }

    public static double intersectionRatio(AABB target, AABB source) {
        double ta = target.getXsize() * target.getZsize(), sa = source.getXsize() * source.getZsize();
        if (ta <= 0 || sa <= 0 || target.maxY <= source.minY || target.minY >= source.maxY) return 0;
        double area = Math.max(0, Math.min(target.maxX, source.maxX)-Math.max(target.minX, source.minX))
                * Math.max(0, Math.min(target.maxZ, source.maxZ)-Math.max(target.minZ, source.minZ));
        return Math.max(area / ta, area / sa);
    }
}
