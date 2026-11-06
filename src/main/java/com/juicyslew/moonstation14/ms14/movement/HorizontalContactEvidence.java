package com.juicyslew.moonstation14.ms14.movement;

import java.util.Optional;
import java.util.List;

/** Immutable adapter proof that one native side contact clipped this exact request horizontally. */
public final class HorizontalContactEvidence {
    private final MovementVector requested;
    private final MovementVector accepted;
    private final double normalX;
    private final double normalY;
    private final double normalZ;
    private final List<HorizontalContactEvidence> orderedPlanes;

    private HorizontalContactEvidence(MovementVector requested, MovementVector accepted,
                                      double normalX, double normalY, double normalZ) {
        this.requested = requested;
        this.accepted = accepted;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
        this.orderedPlanes = List.of();
    }

    private HorizontalContactEvidence(MovementVector requested, MovementVector accepted,
                                     List<HorizontalContactEvidence> orderedPlanes) {
        this.requested = requested;
        this.accepted = accepted;
        this.normalX = this.normalY = this.normalZ = 0d;
        this.orderedPlanes = List.copyOf(orderedPlanes);
    }

    /** Certifies an ordered sequence of individually bounded native contact projections. */
    public static Optional<HorizontalContactEvidence> validateOrdered(MovementVector requested,
            MovementVector accepted, int substeps, List<Projection> projections) {
        if (requested == null || accepted == null || substeps < 1 || substeps > 10 || projections == null || projections.isEmpty())
            return Optional.empty();
        MovementVector accumulated = MovementVector.ZERO;
        int previousStep = 0;
        double verticalResidualBudget = 0d;
        java.util.ArrayList<HorizontalContactEvidence> planes = new java.util.ArrayList<>();
        for (Projection projection : projections) {
            if (projection == null || projection.input() == null || projection.output() == null
                    || projection.substep() < previousStep || projection.substep() > substeps)
                return Optional.empty();
            int step = projection.substep();
            MovementVector expectedInput = new MovementVector(requested.x() * step / (double) substeps + accumulated.x(),
                    requested.y() * step / (double) substeps, requested.z() * step / (double) substeps + accumulated.z());
            double prefixY = requested.y() * step / (double) substeps;
            double minY = Math.min(0d, prefixY);
            double maxY = Math.max(0d, prefixY);
            if (!ulpNear(expectedInput.x(), projection.input().x()) || !ulpNear(expectedInput.z(), projection.input().z())
                    || !Double.isFinite(projection.input().y()) || projection.input().y() < minY
                    || projection.input().y() > maxY) return Optional.empty();
            MovementVector delta = projection.clipDelta();
            if (delta == null || !Double.isFinite(delta.x()) || !Double.isFinite(delta.y()) || !Double.isFinite(delta.z())
                    || delta.y() != 0d || !ulpNear(expectedInput.x() + delta.x(), projection.output().x())
                    || !ulpNear(expectedInput.z() + delta.z(), projection.output().z())
                    || projection.input().y() != projection.output().y()) return Optional.empty();
            var normal = normalizedNormal(projection.normalX(), projection.normalY(), projection.normalZ());
            if (normal == null) return Optional.empty();
            if (delta.x() != 0d || delta.z() != 0d) {
                double horizontalNormalSquared = normal.x()*normal.x() + normal.z()*normal.z();
                if (horizontalNormalSquared < normal.y()*normal.y()) {
                    // A verified vertical policy row can contribute only an existing sub-slop
                    // horizontal residue; it is not admitted as a side-contact plane.
                    if (!boundedProjection(projection.input(), delta, normal, horizontalNormalSquared)) return Optional.empty();
                    verticalResidualBudget += Math.hypot(delta.x(), delta.z());
                    if (!Double.isFinite(verticalResidualBudget)
                            || verticalResidualBudget > NativeCollisionSlop.MAX_RESIDUAL) return Optional.empty();
                } else {
                // Ordered replay is the only domain allowed to validate an intermediate with
                // sub-slop XZ when its full 3D prefix is a real side-plane approach.
                if (!(length3d(projection.input()) > NativeCollisionSlop.MAX_RESIDUAL)) return Optional.empty();
                var evidence = validatePlane(projection.input(), projection.output(), normal);
                if (evidence.isEmpty()) return Optional.empty();
                planes.add(evidence.get());
                }
            }
            accumulated = new MovementVector(accumulated.x() + delta.x(), 0, accumulated.z() + delta.z());
            previousStep = step;
        }
        MovementVector finalInput = new MovementVector(requested.x() + accumulated.x(), accepted.y(),
                requested.z() + accumulated.z());
        MovementVector normalized = NativeCollisionSlop.accepted(requested, finalInput);
        if (planes.isEmpty() || normalized.x() != accepted.x() || normalized.z() != accepted.z()) return Optional.empty();
        return Optional.of(new HorizontalContactEvidence(requested, accepted, planes));
    }

    private record Normal(double x, double y, double z) { }

    private static Normal normalizedNormal(double normalX, double normalY, double normalZ) {
        if (!Double.isFinite(normalX) || !Double.isFinite(normalY) || !Double.isFinite(normalZ)) return null;
        double length = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (!Double.isFinite(length) || Math.abs(length - 1d) > NativeCollisionSlop.MAX_RESIDUAL) return null;
        return new Normal(normalX / length, normalY / length, normalZ / length);
    }

    /** Bounds an already-recorded vertical-policy horizontal remainder; it never creates a side plane. */
    private static boolean boundedProjection(MovementVector input, MovementVector delta, Normal normal,
                                              double horizontalNormalSquared) {
        if (!(horizontalNormalSquared > 0d)) return false;
        double into = input.x() * normal.x() + input.y() * normal.y() + input.z() * normal.z();
        double lambda = (delta.x() * normal.x() + delta.z() * normal.z()) / horizontalNormalSquared;
        double maxClip = Math.max(0d, -into) + NativeCollisionSlop.MAX_RESIDUAL;
        double residualX = delta.x() - lambda * normal.x();
        double residualZ = delta.z() - lambda * normal.z();
        return Double.isFinite(into) && into < 0d && Double.isFinite(lambda)
                && lambda >= 0d && lambda <= maxClip
                && Double.isFinite(residualX) && Double.isFinite(residualZ)
                && Math.abs(residualX) <= NativeCollisionSlop.MAX_RESIDUAL
                && Math.abs(residualZ) <= NativeCollisionSlop.MAX_RESIDUAL;
    }

    private static boolean ulpNear(double a, double b) {
        return Double.isFinite(a) && Double.isFinite(b)
                && Math.abs(a - b) <= 4d * Math.max(Math.ulp(a), Math.ulp(b));
    }

    public record Projection(int substep, MovementVector input, MovementVector output, MovementVector clipDelta,
                             double normalX, double normalY, double normalZ) { }

    /** Builds evidence only when observed movement is a bounded projection onto the supplied contact plane. */
    public static Optional<HorizontalContactEvidence> validate(MovementVector requested, MovementVector accepted,
                                                                double normalX, double normalZ) {
        return validate(requested, accepted, normalX, 0d, normalZ);
    }

    /** Full native unit normal; this certificate authorizes horizontal response only. */
    public static Optional<HorizontalContactEvidence> validate(MovementVector requested, MovementVector accepted,
                                                                double normalX, double normalY, double normalZ) {
        if (requested == null || accepted == null) return Optional.empty();
        Normal normalized = normalizedNormal(normalX, normalY, normalZ);
        if (normalized == null) return Optional.empty();
        normalX = normalized.x(); normalY = normalized.y(); normalZ = normalized.z();
        double requestedLength = Math.hypot(requested.x(), requested.z());
        if (!(requestedLength > NativeCollisionSlop.MAX_RESIDUAL)) return Optional.empty();
        return validatePlane(requested, accepted, normalized);
    }

    private static Optional<HorizontalContactEvidence> validatePlane(MovementVector requested,
            MovementVector accepted, Normal normal) {
        double normalX=normal.x(), normalY=normal.y(), normalZ=normal.z();
        double horizontalNormalSquared = normalX * normalX + normalZ * normalZ;
        double into = requested.x() * normalX + requested.y() * normalY + requested.z() * normalZ;
        if (horizontalNormalSquared < normalY * normalY
                // Keep the certificate in the side-plane domain: horizontal normal energy must
                // dominate vertical energy, preventing near-floor normals from amplifying XZ corrections.
                || !Double.isFinite(into) || !(into < 0d))
            return Optional.empty();

        double clippedX = accepted.x() - requested.x();
        double clippedZ = accepted.z() - requested.z();
        double lambda = (clippedX * normalX + clippedZ * normalZ) / horizontalNormalSquared;
        double maxClip = Math.max(0d, -into) + NativeCollisionSlop.MAX_RESIDUAL;
        double tangentResidualX = clippedX - lambda * normalX;
        double tangentResidualZ = clippedZ - lambda * normalZ;
        if (!Double.isFinite(lambda) || !Double.isFinite(maxClip)
                || lambda < -NativeCollisionSlop.MAX_RESIDUAL || lambda > maxClip
                || !Double.isFinite(tangentResidualX) || !Double.isFinite(tangentResidualZ)
                || Math.abs(tangentResidualX) > NativeCollisionSlop.MAX_RESIDUAL
                || Math.abs(tangentResidualZ) > NativeCollisionSlop.MAX_RESIDUAL) return Optional.empty();
        return Optional.of(new HorizontalContactEvidence(requested, accepted, normalX, normalY, normalZ));
    }

    private static double length3d(MovementVector vector) {
        if (vector == null || !Double.isFinite(vector.x()) || !Double.isFinite(vector.y()) || !Double.isFinite(vector.z()))
            return Double.NaN;
        return Math.hypot(Math.hypot(vector.x(),vector.y()),vector.z());
    }

    public boolean matches(MovementVector requestedMove, MovementVector acceptedMove) {
        return requested.equals(requestedMove) && accepted.equals(acceptedMove);
    }

    /** Removes only velocity directed into this validated plane; preserves its horizontal tangent. */
    public MovementVector projectVelocityTangent(MovementVector velocity) {
        if (!orderedPlanes.isEmpty()) {
            MovementVector result = velocity;
            for (var plane : orderedPlanes) result = plane.projectVelocityTangent(result);
            return result;
        }
        double into = velocity.x() * normalX + velocity.y() * normalY + velocity.z() * normalZ;
        if (into >= 0d) return velocity;
        double horizontalNormalSquared = normalX * normalX + normalZ * normalZ;
        double x = velocity.x() - into * normalX / horizontalNormalSquared;
        double z = velocity.z() - into * normalZ / horizontalNormalSquared;
        if (!Double.isFinite(into) || !Double.isFinite(x) || !Double.isFinite(z)) return velocity;
        return new MovementVector(x, velocity.y(), z);
    }

    public double normalX() { return orderedPlanes.isEmpty() ? normalX : orderedPlanes.get(0).normalX(); }
    public double normalY() { return orderedPlanes.isEmpty() ? normalY : orderedPlanes.get(0).normalY(); }
    public double normalZ() { return orderedPlanes.isEmpty() ? normalZ : orderedPlanes.get(0).normalZ(); }

    @Override
    public String toString() {
        return "HorizontalContactEvidence[requested=" + requested + ", accepted=" + accepted
                + ", normal=(" + normalX + "," + normalY + "," + normalZ + ")]";
    }
}
