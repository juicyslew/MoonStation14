package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyTargetVisibility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Read-only server admission probe for a client-nominated melee target. */
public final class CombatMeleeTargetProbe {
    private CombatMeleeTargetProbe() { }

    public enum Status { HIT, MISS, STALE }
    public record Result(Status status, UUID targetId, AABB bounds, Vec3 attackerPosition,
                         AABB attackerBounds, Vec3 attackerEye, Vec3 targetPosition, float weaponRange) { }

    public static Result probe(ServerLevel level, LivingEntity attacker, UUID nominatedTarget, float weaponRange) {
        if (level == null || attacker == null || level.isClientSide || !level.getServer().isSameThread()
                || attacker.level() != level || level.getEntity(attacker.getUUID()) != attacker
                || attacker.isRemoved() || !attacker.isAlive() || !attacker.isAddedToLevel())
            return new Result(Status.STALE, null, null, null, null, null, null, weaponRange);
        Vec3 origin = attacker.position();
        if (nominatedTarget == null || !Float.isFinite(weaponRange) || weaponRange <= 0)
            return miss();
        Entity target = level.getEntity(nominatedTarget);
        if (target == null || target.isRemoved() || !target.isAddedToLevel()
                || target.level() != level || !CommonMeleeTargetEligibility.damageable(target)) return miss();
        AABB bounds = target.getBoundingBox();
        Vec3 position = target.position();
        if (!finite(origin) || !finite(position) || !finite(bounds)) return miss();
        double distance = BodyBounds.distance(attacker.getBoundingBox(), bounds);
        if (!BodyBounds.withinRange(distance, weaponRange)) return miss();
        Vec3 eye = attacker.getEyePosition();
        Vec3 point = new Vec3(clamp(eye.x, bounds.minX, bounds.maxX), clamp(eye.y, bounds.minY, bounds.maxY), clamp(eye.z, bounds.minZ, bounds.maxZ));
        if (!BodyTargetVisibility.visible(level, attacker, eye, point)) return miss();
        return new Result(Status.HIT, target.getUUID(), bounds, origin, attacker.getBoundingBox(), eye, position, weaponRange);
    }

    /** Re-probes and rejects an admitted target if either participant moved or its identity changed. */
    public static boolean revalidate(ServerLevel level, LivingEntity attacker, Result snapshot) {
        if (snapshot == null || snapshot.status() != Status.HIT || snapshot.targetId() == null) return false;
        Result now = probe(level, attacker, snapshot.targetId(), snapshot.weaponRange());
        return now.status() == Status.HIT && snapshot.targetId().equals(now.targetId())
                && snapshot.bounds().equals(now.bounds()) && snapshot.attackerBounds().equals(now.attackerBounds())
                && snapshot.attackerPosition().equals(now.attackerPosition()) && snapshot.attackerEye().equals(now.attackerEye())
                && snapshot.targetPosition().equals(now.targetPosition());
    }

    private static Result miss() { return new Result(Status.MISS, null, null, null, null, null, null, Float.NaN); }
    private static double clamp(double n,double lo,double hi) { return Math.max(lo,Math.min(hi,n)); }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }
    private static boolean finite(AABB b) { return Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ)&&b.minX<=b.maxX&&b.minY<=b.maxY&&b.minZ<=b.maxZ; }

    /** Minecraft adaptation: Euclidean 3D separation of physical AABBs, rather than SS14's 2D nearest-edge distance. */
    public static final class BodyBounds {
        private BodyBounds() { }
        public static double distance(AABB a, AABB b) {
            if (a == null || b == null || !finite(a) || !finite(b)) return Double.NaN;
            double x=Math.max(0,Math.max(a.minX-b.maxX,b.minX-a.maxX));
            double y=Math.max(0,Math.max(a.minY-b.maxY,b.minY-a.maxY));
            double z=Math.max(0,Math.max(a.minZ-b.maxZ,b.minZ-a.maxZ));
            return Math.sqrt(x*x+y*y+z*z);
        }
        public static boolean withinRange(double distance, float resolvedWeaponRange) {
            return Double.isFinite(distance) && Double.isFinite(resolvedWeaponRange)
                    && resolvedWeaponRange > 0 && distance <= resolvedWeaponRange;
        }
    }
}
