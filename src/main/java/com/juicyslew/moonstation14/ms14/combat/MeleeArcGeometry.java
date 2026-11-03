package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import java.util.*;
import java.util.function.ToDoubleFunction;
import com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay;

/** Pure geometry shared by predicted and observer melee arc capture. */
public final class MeleeArcGeometry {
    public record Aim(Vec3 bodyPosition, Vec3 direction, Vec3 attackPoint) { }
    /** Immutable geometry snapshot for rendering/presentation; it is not hit admission. */
    public record StrikeCapture(Vec3 frozenBodyPosition, Vec3 eyeOrigin, Vec3 emissionOrigin, Vec3 fullDirection,
                                float facingYaw, double range, MeleeAttackRequest.Mode mode,
                                double angleDegrees, Vec3 centerEndpoint, List<Vec3> boundaryEndpoints, AABB sourceBounds) {
        public StrikeCapture { boundaryEndpoints = List.copyOf(boundaryEndpoints); }
        public StrikeCapture(Vec3 body, Vec3 eye, Vec3 emission, Vec3 direction, float yaw, double range,
                MeleeAttackRequest.Mode mode, double angle, Vec3 center, List<Vec3> edges) {
            this(body,eye,emission,direction,yaw,range,mode,angle,center,edges,null);
        }
    }
    public record Ray(Vec3 origin, Vec3 direction, double length) { }
    private MeleeArcGeometry() { }

    /** Capture server/client visual geometry, clipping each ray independently against terrain. */
    public static StrikeCapture captureStrike(LivingEntity body, MeleeWeaponData weapon,
            MeleeAttackRequest.Mode mode, Entity nominatedEntity, MeleeTargetNomination.Block blockContact) {
        if (body == null || weapon == null || mode == null) return null;
        Vec3 eye = body.getEyePosition();
        Vec3 look = body.getLookAngle();
        float yaw = body.getYRot();
        double range = weapon.range();
        AABB source = body.getBoundingBox();
        AABB target = nominatedEntity == null ? null : nominatedEntity.getBoundingBox();
        return captureStrike(body.position(), source, eye, look, yaw, range, weapon.angleDegrees(), mode,
                weapon.wideOriginHeightRatio(),
                target, blockContact == null ? null : blockContact.hitpoint(), ray -> {
                    var hit = body.level().clip(new ClipContext(ray.origin(), ray.origin().add(ray.direction().scale(ray.length())),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
                    return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                            ? ray.origin().distanceTo(hit.getLocation()) : ray.length();
                });
    }

    /** Capture a trusted server strike toward its validated target without mutating entity look state. */
    public static StrikeCapture captureStrikeTowardTarget(LivingEntity body, MeleeWeaponData weapon, Entity target) {
        return captureToward(body, weapon, target == null ? null : target.getBoundingBox().getCenter(),
                target == null ? null : target.getBoundingBox());
    }

    /** Captures a trusted swing toward an empty point, with no nominated entity bounds. */
    public static StrikeCapture captureStrikeTowardPoint(LivingEntity body, MeleeWeaponData weapon, Vec3 point) {
        return captureToward(body, weapon, point, null);
    }

    private static StrikeCapture captureToward(LivingEntity body, MeleeWeaponData weapon, Vec3 point, AABB nominatedBounds) {
        if (body == null || weapon == null) return null;
        Vec3 eye = body.getEyePosition();
        Vec3 direction = point == null ? body.getLookAngle() : point.subtract(eye);
        if (direction.lengthSqr() < 1e-12) direction = body.getLookAngle();
        Vec3 look = direction;
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        var bounds = body.getBoundingBox();
        return captureStrike(body.position(), bounds, eye, look, yaw, weapon.range(), weapon.angleDegrees(),
                MeleeAttackRequest.Mode.LIGHT, weapon.wideOriginHeightRatio(), nominatedBounds, null, ray -> {
                    var hit = body.level().clip(new ClipContext(ray.origin(), ray.origin().add(ray.direction().scale(ray.length())),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
                    return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? ray.origin().distanceTo(hit.getLocation()) : ray.length();
                });
    }

    /** Pure, testable strike geometry. clipDistance returns the maximum visible distance for a ray. */
    public static StrikeCapture captureStrike(Vec3 bodyPosition, AABB sourceBounds, Vec3 eye, Vec3 look,
            float yaw, double range, double angle, MeleeAttackRequest.Mode mode, AABB nominatedBounds,
            Vec3 blockContact, ToDoubleFunction<Ray> clipDistance) {
        return captureStrike(bodyPosition, sourceBounds, eye, look, yaw, range, angle, mode, .6,
                nominatedBounds, blockContact, clipDistance);
    }

    public static StrikeCapture captureStrike(Vec3 bodyPosition, AABB sourceBounds, Vec3 eye, Vec3 look,
            float yaw, double range, double angle, MeleeAttackRequest.Mode mode, double wideOriginHeightRatio,
            AABB nominatedBounds, Vec3 blockContact, ToDoubleFunction<Ray> clipDistance) {
        if (!finite(bodyPosition) || !finite(eye) || !finite(look) || sourceBounds == null || mode == null
                || clipDistance == null || !Float.isFinite(yaw) || !Double.isFinite(range) || range <= 0
                || !Double.isFinite(angle) || angle < 0 || angle > 360 || look.lengthSqr() < 1e-12) return null;
        Vec3 direction = look.normalize();
        double reach = range;
        double diagonal = Math.sqrt(Math.pow(sourceBounds.getXsize(),2)+Math.pow(sourceBounds.getYsize(),2)+Math.pow(sourceBounds.getZsize(),2));
        double upper = reach + diagonal + eye.distanceTo(sourceBounds.getCenter());
        Vec3 wideOrigin = mode == MeleeAttackRequest.Mode.WIDE
                ? BodyInteractionRay.bodyHeightOrigin(sourceBounds, wideOriginHeightRatio) : eye;
        Vec3 center = mode == MeleeAttackRequest.Mode.WIDE
                ? clippedEndpoint(wideOrigin, direction, reach, clipDistance)
                : endpoint(eye, direction, reach, upper, sourceBounds, nominatedBounds, blockContact, clipDistance);
        double centerDistance = eye.distanceTo(center);
        boolean eyeInsideSource = sourceBounds.contains(eye);
        double exitDistance = 0;
        if (eyeInsideSource) {
            // Cast back from beyond the complete source box: the first box surface
            // encountered is the forward-facing exit for the captured attack axis.
            var exit = sourceBounds.clip(eye.add(direction.scale(upper)), eye);
            if (exit.isPresent()) exitDistance = eye.distanceTo(exit.get());
        }
        double epsilon = Math.min(1.0e-4, Math.max(1.0e-7, diagonal * 1.0e-5));
        double clearance = eyeInsideSource ? MeleeArcTimeline.MAX_LUNGE_DISTANCE + Math.min(.05, diagonal * .025) : 0;
        // Keep a meaningful stroke even when clipped by a nearby wall/contact.
        double emissionDistance = Math.min(Math.min(exitDistance + clearance, centerDistance * .60),
                Math.max(0, centerDistance - epsilon));
        Vec3 emission = mode == MeleeAttackRequest.Mode.WIDE ? wideOrigin : eye.add(direction.scale(emissionDistance));
        List<Vec3> edges = new ArrayList<>();
        if (mode == MeleeAttackRequest.Mode.WIDE) for (int i=0;i<=12;i++) {
            double a=Math.toRadians(-angle/2 + angle*i/12.0);
            Vec3 rayDir = BodyInteractionRay.headLocalFanDirection(look,yaw,a);
            edges.add(clippedEndpoint(wideOrigin, rayDir, reach, clipDistance));
        }
        return new StrikeCapture(bodyPosition,eye,emission,direction,yaw,reach,mode,angle,center,edges,sourceBounds);
    }

    private static Vec3 endpoint(Vec3 eye, Vec3 direction, double reach, double upper, AABB source,
            AABB nominated, Vec3 block, ToDoubleFunction<Ray> clip) {
        double low=0, high=upper;
        for(int i=0;i<20;i++) { double mid=(low+high)*.5; Vec3 point=eye.add(direction.scale(mid));
            double separation=CombatMeleeTargetProbe.BodyBounds.distance(source,new AABB(point,point));
            if(CombatMeleeTargetProbe.BodyBounds.withinRange(separation,(float)reach)) low=mid; else high=mid;
        }
        double distance=low;
        if(nominated!=null) { var surface=nominated.clip(eye,eye.add(direction.scale(distance)));
            if(surface.isPresent()) distance=Math.min(distance,eye.distanceTo(surface.get())); }
        if(block!=null) { double along=block.subtract(eye).dot(direction);
            if(along>=0 && along<=distance && eye.add(direction.scale(along)).distanceTo(block)<1e-4) distance=along; }
        double terrain=clip.applyAsDouble(new Ray(eye,direction,distance));
        if(Double.isFinite(terrain)) distance=Math.min(distance,Math.max(0,terrain));
        return eye.add(direction.scale(distance));
    }
    private static Vec3 clippedEndpoint(Vec3 eye, Vec3 direction, double range, ToDoubleFunction<Ray> clip) {
        double distance=clip.applyAsDouble(new Ray(eye,direction,range));
        if (!Double.isFinite(distance)) distance=range;
        return eye.add(direction.scale(Math.min(range,Math.max(0,distance))));
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }

    public static Aim capture(Vec3 bodyPosition, double bodyHeight, Vec3 look,
                              Vec3 nullableTargetPosition, double range) {
        Vec3 body = new Vec3(bodyPosition.x, bodyPosition.y, bodyPosition.z);
        Vec3 horizontalLook = horizontal(look);
        Vec3 direction = nullableTargetPosition == null ? horizontalLook
                : horizontal(new Vec3(nullableTargetPosition.x-body.x, nullableTargetPosition.y-body.y, nullableTargetPosition.z-body.z));
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1e-8) direction = horizontalLook;
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1e-8) return null;
        direction = direction.normalize();
        double max = Double.isFinite(range) ? Math.max(0, range-.2) : 0;
        double distance = max;
        if (nullableTargetPosition != null) {
            double d = body.subtract(nullableTargetPosition).horizontalDistance();
            if (Double.isFinite(d)) distance = Math.min(max, d);
        }
        return new Aim(body, direction, body.add(direction.scale(distance)).add(0, bodyHeight*.5, 0));
    }
    /** Preserve legacy horizontal aim when available; otherwise use a ground-plane yaw basis
     * while retaining the full-pitch strike's captured center endpoint for presentation. */
    public static Aim legacyAimWithStrikeFallback(Aim aim, StrikeCapture strike) {
        if (aim != null) return aim;
        if (strike == null || !finite(strike.frozenBodyPosition()) || !finite(strike.centerEndpoint())
                || !Float.isFinite(strike.facingYaw())) return null;
        double yaw = Math.toRadians(strike.facingYaw());
        Vec3 horizontalFacing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 direction = BodyInteractionRay.pitchedYawDirection(horizontalFacing, yaw);
        if (!finite(direction) || direction.lengthSqr() < 1e-12) return null;
        return new Aim(strike.frozenBodyPosition(), direction.normalize(), strike.centerEndpoint());
    }
    private static Vec3 horizontal(Vec3 v) { return new Vec3(v.x, 0, v.z); }
}
