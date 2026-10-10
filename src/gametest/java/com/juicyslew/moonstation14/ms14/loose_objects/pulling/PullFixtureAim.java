package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared fixture geometry for selecting a visible living-body interaction point. */
final class PullFixtureAim {
    private PullFixtureAim() { }

    static void aimAtVisibleInteractionPoint(LivingEntity actor, LivingEntity target) {
        Vec3 eye = actor.getEyePosition();
        var bounds = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(target);
        double insetX = Math.min(.02, (bounds.maxX - bounds.minX) * .1);
        double insetY = Math.min(.02, (bounds.maxY - bounds.minY) * .1);
        double insetZ = Math.min(.02, (bounds.maxZ - bounds.minZ) * .1);
        Vec3 point = new Vec3(net.minecraft.util.Mth.clamp(eye.x, bounds.minX + insetX, bounds.maxX - insetX),
                net.minecraft.util.Mth.clamp(eye.y, bounds.minY + insetY, bounds.maxY - insetY),
                net.minecraft.util.Mth.clamp(eye.z, bounds.minZ + insetZ, bounds.maxZ - insetZ));
        aimAtPoint(actor, point);
    }

    static void aimAtPoint(LivingEntity actor, Vec3 point) {
        Vec3 eye = actor.getEyePosition();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        actor.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        actor.setYHeadRot(actor.getYRot());
        actor.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }
}
