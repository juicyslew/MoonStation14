package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Pure, captured-world-space crescent geometry for a disembodied melee slash. */
public final class MeleeStrikeRibbonGeometry {
    private static final int SEGMENTS = 14;
    private static final double ARC = Math.PI * .65;

    private MeleeStrikeRibbonGeometry() { }

    /** Returns independent strip quads (four vertices each), bounded by the captured tip plane. */
    public static List<Vec3> build(Vec3 origin, Vec3 tip, Vec3 clippedEndpoint,
                                   double frozenYaw, double rollRadians, double styleWidth) {
        return build(origin, tip, clippedEndpoint, frozenYaw, rollRadians, styleWidth, false);
    }

    public static List<Vec3> build(Vec3 origin, Vec3 tip, Vec3 clippedEndpoint,
                                   double frozenYaw, double rollRadians, double styleWidth,
                                   boolean counterTilt) {
        return build(origin, tip, clippedEndpoint, frozenYaw, rollRadians, styleWidth, counterTilt, styleWidth);
    }
    public static List<Vec3> build(Vec3 origin, Vec3 tip, Vec3 clippedEndpoint,
                                   double frozenYaw, double rollRadians, double styleWidth,
                                   boolean counterTilt, double bandWidth) {
        Vec3 travel = tip.subtract(origin);
        double traveledLength = travel.length();
        Vec3 clippedRay = clippedEndpoint.subtract(origin);
        double clippedLength = clippedRay.length();
        if (traveledLength < 1.0e-8 || clippedLength < 1.0e-8 || styleWidth <= 0) return List.of();
        Vec3 axis = travel.scale(1.0 / traveledLength);

        double yaw = Math.toRadians(frozenYaw);
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        right = right.subtract(axis.scale(right.dot(axis)));
        if (right.lengthSqr() < 1.0e-8) {
            Vec3 fallback = Math.abs(axis.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            right = fallback.subtract(axis.scale(fallback.dot(axis)));
        }
        right = right.normalize();
        Vec3 up = axis.cross(right).normalize();
        Vec3 rolledRight = right.scale(Math.cos(rollRadians)).add(up.scale(Math.sin(rollRadians))).normalize();
        Vec3 rolledUp = up.scale(Math.cos(rollRadians)).subtract(right.scale(Math.sin(rollRadians))).normalize();
        Vec3 planeUp = rolledUp.add(axis.scale(counterTilt ? -1 : 1)).normalize();

        // Transverse profile belongs to the resolved style, not the axial wall clip.
        // Only axial stations are clipped below, preserving a full-width contact effect.
        double radius = Math.min(styleWidth * 3, Math.max(styleWidth * 1.5, clippedLength * .75));
        double band = Math.min(styleWidth, bandWidth);
        if (radius < 1.0e-8 || band < 1.0e-8) return List.of();
        ArrayList<Vec3> vertices = new ArrayList<>(SEGMENTS * 4);
        for (int i = 0; i < SEGMENTS; i++) {
            double t0 = (double)i / SEGMENTS, t1 = (double)(i + 1) / SEGMENTS;
            Vec3 outer0 = point(tip, rolledRight, planeUp, radius, t0);
            Vec3 inner0 = point(tip, rolledRight, planeUp, radius - band * Math.sin(Math.PI * t0), t0);
            Vec3 inner1 = point(tip, rolledRight, planeUp, radius - band * Math.sin(Math.PI * t1), t1);
            Vec3 outer1 = point(tip, rolledRight, planeUp, radius, t1);
            vertices.add(outer0); vertices.add(inner0); vertices.add(inner1); vertices.add(outer1);
        }
        double overshoot = vertices.stream().mapToDouble(v -> v.subtract(tip).dot(axis)).max().orElse(0);
        if (overshoot > 0) {
            Vec3 correction = axis.scale(overshoot);
            vertices.replaceAll(v -> v.subtract(correction));
        }
        vertices.replaceAll(v -> {
            double station = v.subtract(origin).dot(axis);
            double boundedStation = Math.max(0, Math.min(traveledLength, station));
            return v.add(axis.scale(boundedStation - station));
        });
        return List.copyOf(vertices);
    }

    private static Vec3 point(Vec3 center, Vec3 right, Vec3 up,
                              double radius, double t) {
        double angle = -ARC + 2 * ARC * t;
        return center.add(right.scale(radius * Math.cos(angle)))
                .add(up.scale(radius * Math.sin(angle)));
    }
}
