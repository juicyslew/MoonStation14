package com.juicyslew.moonstation14.ms14.examine.scene;

import com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared client preflight/server authorization for virtual scene targets. */
public final class CelestialSceneExamination {
    private CelestialSceneExamination() { }
    public static boolean visible(Level level, Entity body, CelestialSceneData profile, double time, boolean critical) {
        if (level == null || body == null || body.level() != level || body.isRemoved()
                || !CelestialSceneGeometry.available(level, profile, critical)) return false;
        Vec3 direction = CelestialSceneGeometry.visibleDirection(profile, time);
        if (direction == null) return false;
        return rayVisibility(level, body.getEyePosition(), direction).visible();
    }
    /** World occlusion distance is the shared visible-world cap, never virtual sky draw distance. */
    public static ExamineTargetResolver.VisibilityResult rayVisibility(Level level, Vec3 worldOrigin, Vec3 direction) {
        if (level == null || worldOrigin == null || direction == null || direction.lengthSqr() < 1e-12)
            return new ExamineTargetResolver.VisibilityResult(false, null, "invalid_direction");
        Vec3 endpoint = rayEndpoint(worldOrigin, direction);
        return ExamineTargetResolver.visibilityAlongRay(level, worldOrigin, endpoint, true, false);
    }
    public static Vec3 rayEndpoint(Vec3 worldOrigin, Vec3 direction) {
        return worldOrigin.add(direction.normalize()
                .scale(com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.MAX_VISIBLE_RANGE));
    }
}
