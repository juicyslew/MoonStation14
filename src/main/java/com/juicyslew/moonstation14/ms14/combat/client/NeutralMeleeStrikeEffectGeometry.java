package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.MeleeArcGeometry;
import com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest;
import com.juicyslew.moonstation14.ms14.combat.MeleeStrikeProfile;
import net.minecraft.world.phys.Vec3;

/** Entity-model-independent source and swept endpoint for a captured melee effect. */
public final class NeutralMeleeStrikeEffectGeometry {
    private NeutralMeleeStrikeEffectGeometry() { }

    /** Frozen world-space origin. Never follows a moving/posture-changing source entity. */
    public static Vec3 source(MeleeArcClient.StrikeView strike) {
        if (strike == null || strike.geometry() == null) return null;
        var g = strike.geometry();
        return g.emissionOrigin();
    }

    public static double travelFraction(MeleeStrikeProfile profile, double progress) {
        double bounded = Math.max(0, Math.min(profile.retractionStartFraction(), progress));
        return com.juicyslew.moonstation14.ms14.combat.MeleeStrikeTimeline
                .sample(profile, 1, bounded * profile.durationSeconds()).extension();
    }

    private static Vec3 fanEndpoint(MeleeArcGeometry.StrikeCapture capture, double normalizedSweepFraction,
                                    boolean swingLeft) {
        if (capture.mode() != MeleeAttackRequest.Mode.WIDE || capture.boundaryEndpoints().isEmpty())
            return capture.centerEndpoint();
        var points = capture.boundaryEndpoints();
        double slot = Math.max(0, Math.min(1, normalizedSweepFraction)) * (points.size() - 1);
        if (swingLeft) slot = points.size() - 1 - slot;
        int low = (int)Math.floor(slot), high = Math.min(points.size() - 1, low + 1);
        return points.get(low).lerp(points.get(high), slot - Math.floor(slot));
    }

    public static Vec3 selectedEndpoint(MeleeArcClient.StrikeView strike) {
        var capture = strike.geometry();
        if (capture.mode() != MeleeAttackRequest.Mode.WIDE) return capture.centerEndpoint();
        double fraction = com.juicyslew.moonstation14.ms14.combat.MeleeStrikeTimeline
                .wideSweepFraction(strike.profile(), strike.sample().progress());
        return fanEndpoint(capture, fraction, strike.swingLeft());
    }

    public static Vec3 motionEndpoint(MeleeArcClient.StrikeView strike) {
        return strike == null ? null : selectedEndpoint(strike);
    }

    /** Weapon tip follows the captured thrust extension; wide attacks retain their frozen fan sweep. */
    public static Vec3 weaponTipPoint(Vec3 origin, MeleeArcClient.StrikeView strike) {
        if (origin == null || strike == null) return null;
        Vec3 endpoint = motionEndpoint(strike);
        if (endpoint == null) return null;
        if (strike.geometry().mode() == MeleeAttackRequest.Mode.WIDE) return endpoint;
        return origin.lerp(endpoint, strike.sample().extension());
    }

    public static Vec3 capturedDirection(MeleeArcGeometry.StrikeCapture capture, Vec3 endpoint) {
        Vec3 ray = endpoint.subtract(capture.emissionOrigin());
        return ray.lengthSqr() < 1.0e-8 ? capture.fullDirection() : ray.normalize();
    }

    /** Minimum allowed rear support coordinate: whole captured-body support plus lunge envelope. */
    public static double rearClearance(MeleeArcGeometry.StrikeCapture capture, Vec3 direction) {
        if (capture == null || direction == null || direction.lengthSqr() < 1.0e-12) return Double.NaN;
        var box = capture.sourceBounds();
        Vec3 d = direction.normalize();
        Vec3 source = capture.emissionOrigin();
        if (box == null) return source.dot(d);
        double bodySupport=bodySupport(box,d);
        double margin=Math.min(.05, Math.sqrt(box.getXsize()*box.getXsize()+box.getYsize()*box.getYsize()+box.getZsize()*box.getZsize())*.025);
        return Math.max(source.dot(d),bodySupport) + com.juicyslew.moonstation14.ms14.combat.MeleeArcTimeline.MAX_LUNGE_DISTANCE
                + margin;
    }

    private static double bodySupport(net.minecraft.world.phys.AABB box, Vec3 d) {
        double bodySupport=Double.NEGATIVE_INFINITY;
        for(double x:new double[]{box.minX,box.maxX}) for(double y:new double[]{box.minY,box.maxY})
            for(double z:new double[]{box.minZ,box.maxZ}) bodySupport=Math.max(bodySupport,new Vec3(x,y,z).dot(d));
        return bodySupport;
    }

    /** Observer plane: bounds plus only the currently sampled lunge, never the padded emission origin. */
    public static double observerClearance(MeleeArcGeometry.StrikeCapture capture, Vec3 direction, Vec3 visualLunge) {
        if(capture==null || direction==null || direction.lengthSqr()<1e-12) return Double.NaN;
        Vec3 d=direction.normalize(); var box=capture.sourceBounds();
        if(box==null) return capture.emissionOrigin().dot(d);
        double margin=Math.min(.05,Math.sqrt(box.getXsize()*box.getXsize()+box.getYsize()*box.getYsize()+box.getZsize()*box.getZsize())*.025);
        return bodySupport(box,d)+margin+(visualLunge == null ? 0 : visualLunge.dot(d));
    }
}
