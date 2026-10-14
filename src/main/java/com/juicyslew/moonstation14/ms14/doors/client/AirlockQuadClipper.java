package com.juicyslew.moonstation14.ms14.doors.client;

import java.util.ArrayList;
import java.util.List;

/** Clips authored component quad vertices in canonical local door space. */
public final class AirlockQuadClipper {
    private static final float VERTEX_EPSILON = 1.0e-6f;
    public record Vertex(float x, float y, float z, float u, float v) {
        Vertex interpolate(Vertex other, float t) {
            return new Vertex(x + (other.x-x)*t, y + (other.y-y)*t, z + (other.z-z)*t,
                    u + (other.u-u)*t, v + (other.v-v)*t);
        }
    }
    private record Plane(int axis, float boundary, boolean keepGreater) { }
    private static final List<Plane> DOOR_VOLUME = List.of(
            new Plane(0,0,true), new Plane(0,1,false), new Plane(1,0,true), new Plane(1,2,false),
            new Plane(2,0,true), new Plane(2,1,false));

    private AirlockQuadClipper() { }

    /** Returns the clipped polygon (0, 3+ vertices); UVs are linearly interpolated at every cut. */
    public static List<Vertex> clip(List<Vertex> quad) {
        if (quad == null || quad.size() < 3) return List.of();
        boolean fullyInside = true;
        for (Vertex vertex : quad) {
            if (vertex.x() < 0 || vertex.x() > 1 || vertex.y() < 0 || vertex.y() > 2
                    || vertex.z() < 0 || vertex.z() > 1) {
                fullyInside = false;
                break;
            }
        }
        if (fullyInside) return isNonDegenerate(quad) ? quad : List.of();
        List<Vertex> polygon = List.copyOf(quad);
        for (Plane plane : DOOR_VOLUME) {
            if (polygon.isEmpty()) return List.of();
            ArrayList<Vertex> out = new ArrayList<>();
            Vertex previous = polygon.get(polygon.size()-1);
            float previousDistance = coordinate(previous, plane.axis()) - plane.boundary();
            boolean previousInside = plane.keepGreater() ? previousDistance >= 0 : previousDistance <= 0;
            for (Vertex current : polygon) {
                float currentDistance = coordinate(current, plane.axis()) - plane.boundary();
                boolean currentInside = plane.keepGreater() ? currentDistance >= 0 : currentDistance <= 0;
                if (currentInside != previousInside) {
                    float t = previousDistance / (previousDistance - currentDistance);
                    out.add(previous.interpolate(current, t));
                }
                if (currentInside) out.add(current);
                previous = current;
                previousDistance = currentDistance;
                previousInside = currentInside;
            }
            polygon = deduplicate(out);
            if (polygon.size() < 3) return List.of();
        }
        return isNonDegenerate(polygon) ? List.copyOf(polygon) : List.of();
    }

    private static List<Vertex> deduplicate(List<Vertex> vertices) {
        if (vertices.size() < 2) return List.copyOf(vertices);
        ArrayList<Vertex> unique = new ArrayList<>(vertices.size());
        for (Vertex vertex : vertices) {
            if (unique.isEmpty() || !sameVertex(unique.get(unique.size() - 1), vertex)) unique.add(vertex);
        }
        if (unique.size() > 1 && sameVertex(unique.get(0), unique.get(unique.size() - 1)))
            unique.remove(unique.size() - 1);
        return unique;
    }

    private static boolean sameVertex(Vertex a, Vertex b) {
        return Math.abs(a.x() - b.x()) <= VERTEX_EPSILON && Math.abs(a.y() - b.y()) <= VERTEX_EPSILON
                && Math.abs(a.z() - b.z()) <= VERTEX_EPSILON && Math.abs(a.u() - b.u()) <= VERTEX_EPSILON
                && Math.abs(a.v() - b.v()) <= VERTEX_EPSILON;
    }

    private static boolean isNonDegenerate(List<Vertex> vertices) {
        if (vertices.size() < 3) return false;
        Vertex origin = vertices.get(0);
        float areaX = 0, areaY = 0, areaZ = 0;
        for (int i = 1; i < vertices.size() - 1; i++) {
            Vertex b = vertices.get(i), c = vertices.get(i + 1);
            float abX = b.x() - origin.x(), abY = b.y() - origin.y(), abZ = b.z() - origin.z();
            float acX = c.x() - origin.x(), acY = c.y() - origin.y(), acZ = c.z() - origin.z();
            areaX += abY * acZ - abZ * acY;
            areaY += abZ * acX - abX * acZ;
            areaZ += abX * acY - abY * acX;
        }
        return areaX * areaX + areaY * areaY + areaZ * areaZ > VERTEX_EPSILON * VERTEX_EPSILON;
    }

    private static float coordinate(Vertex vertex, int axis) {
        return axis == 0 ? vertex.x() : axis == 1 ? vertex.y() : vertex.z();
    }
}

