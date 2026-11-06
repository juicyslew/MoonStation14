package com.juicyslew.moonstation14.ms14.movement;

import java.util.Optional;

/** Native geometry-only position correction proof; callers must establish the live collision context. */
public final class ContactPositionCorrectionEvidence {
    private final MovementVector requested;
    private final MovementVector boundedOwn;
    private final MovementVector geometryCorrection;
    private final Domain domain;

    private enum Domain { SUPPORTED_FLOOR, SIDE, AIRBORNE_SIDE_GEOMETRY, SUPPORTED_FLOOR_AND_SIDE, TRANSIENT_FLOOR_AND_SIDE, ASCENDING_FLOOR, CEILING_AND_SIDE }

    private ContactPositionCorrectionEvidence(MovementVector requested, MovementVector boundedOwn,
                                               MovementVector geometryCorrection, Domain domain) {
        this.requested = requested;
        this.boundedOwn = boundedOwn;
        this.geometryCorrection = geometryCorrection;
        this.domain = domain;
    }

    public static Optional<ContactPositionCorrectionEvidence> validateSupportedFloor(MovementVector request,
            MovementVector boundedOwn, MovementVector geometryCorrection) {
        if (request == null || boundedOwn == null || geometryCorrection == null || request.y() > 0d
                || Math.hypot(request.x(), request.z()) > NativeCollisionSlop.MAX_RESIDUAL
                || geometryCorrection.y() <= 0d || !boundedOwn.equals(MovementVector.ZERO)) return Optional.empty();
        return Optional.of(new ContactPositionCorrectionEvidence(request, boundedOwn, geometryCorrection,
                Domain.SUPPORTED_FLOOR));
    }

    public static Optional<ContactPositionCorrectionEvidence> validateSide(MovementVector request,
            MovementVector boundedOwn, MovementVector geometryCorrection, HorizontalContactEvidence contact) {
        if (request == null || boundedOwn == null || geometryCorrection == null
                || Math.abs(geometryCorrection.y()) > NativeCollisionSlop.MAX_RESIDUAL
                || (geometryCorrection.y() != 0d && boundedOwn.y() != request.y()))
            return Optional.empty();
        if (!hasContactOrNoHorizontalClipping(request, boundedOwn, contact)) return Optional.empty();
        return Optional.of(new ContactPositionCorrectionEvidence(request, boundedOwn, geometryCorrection, Domain.SIDE));
    }

    /** Position-only signed Y remainder backed by a successful complete airborne side-geometry proof. */
    public static Optional<ContactPositionCorrectionEvidence> validateAirborneSideGeometry(MovementVector request,
            MovementVector boundedOwn, MovementVector geometryCorrection, HorizontalContactEvidence contact) {
        if (!finite(request) || !finite(boundedOwn) || !finite(geometryCorrection)
                || boundedOwn.y() != request.y()
                || !hasContactOrNoHorizontalClipping(request, boundedOwn, contact)) return Optional.empty();
        return Optional.of(new ContactPositionCorrectionEvidence(request, boundedOwn, geometryCorrection,
                Domain.AIRBORNE_SIDE_GEOMETRY));
    }

    /** Joint native floor-Y and geometric side-plane evidence for one observed movement. */
    public static Optional<ContactPositionCorrectionEvidence> validateSupportedFloorAndSide(MovementVector request,
            MovementVector boundedOwn, MovementVector geometryCorrection, HorizontalContactEvidence contact) {
        if (request == null || boundedOwn == null || geometryCorrection == null
                || request.y() >= 0d || boundedOwn.y() > 0d || boundedOwn.y() < request.y()
                || geometryCorrection.y() < 0d
                || !hasContactOrNoHorizontalClipping(request, boundedOwn, contact)) return Optional.empty();
        return Optional.of(new ContactPositionCorrectionEvidence(request, boundedOwn, geometryCorrection,
                Domain.SUPPORTED_FLOOR_AND_SIDE));
    }

    /** Transient floor/side position proof; never grants grounded support and requires live clearance separately. */
    public static Optional<ContactPositionCorrectionEvidence> validateTransientFloorAndSide(MovementVector request,
            MovementVector boundedOwn, MovementVector geometryCorrection, HorizontalContactEvidence contact) {
        if (!finite(request) || !finite(boundedOwn) || !finite(geometryCorrection)
                || request.y() >= 0d || boundedOwn.y() < request.y() || boundedOwn.y() > 0d
                || Math.abs(geometryCorrection.y()) > NativeCollisionSlop.MAX_RESIDUAL
                || !hasContactOrNoHorizontalClipping(request, boundedOwn, contact)) return Optional.empty();
        return Optional.of(new ContactPositionCorrectionEvidence(request, boundedOwn, geometryCorrection,
                Domain.TRANSIENT_FLOOR_AND_SIDE));
    }

    /** Joint native ceiling and geometric side-plane evidence; never grants floor support. */
    public static Optional<ContactPositionCorrectionEvidence> validateCeilingAndSide(MovementVector request,
            MovementVector boundedOwn, MovementVector geometryCorrection, HorizontalContactEvidence contact) {
        if (!finite(request) || !finite(boundedOwn) || !finite(geometryCorrection)
                || geometryCorrection.y() > NativeCollisionSlop.MAX_RESIDUAL
                || (request.y() <= 0d ? boundedOwn.y() != request.y()
                    : boundedOwn.y() < 0d || boundedOwn.y() > request.y())
                || !hasContactOrNoHorizontalClipping(request, boundedOwn, contact)) return Optional.empty();
        return Optional.of(new ContactPositionCorrectionEvidence(request, boundedOwn, geometryCorrection,
                Domain.CEILING_AND_SIDE));
    }

    /** Ascending floor position correction; geometry evidence never grants grounded support. */
    public static Optional<ContactPositionCorrectionEvidence> validateAscendingFloor(MovementVector request,
            MovementVector boundedOwn, MovementVector geometryCorrection, HorizontalContactEvidence contact) {
        if (!finite(request) || !finite(boundedOwn) || !finite(geometryCorrection) || request.y() <= 0d
                || boundedOwn.y() != request.y() || geometryCorrection.y() < 0d
                || !hasContactOrNoHorizontalClipping(request, boundedOwn, contact)) return Optional.empty();
        return Optional.of(new ContactPositionCorrectionEvidence(request, boundedOwn, geometryCorrection,
                Domain.ASCENDING_FLOOR));
    }

    private static boolean finite(MovementVector v) {
        return v != null && Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }

    private static boolean hasContactOrNoHorizontalClipping(MovementVector request, MovementVector boundedOwn,
            HorizontalContactEvidence contact) {
        boolean exactContact = contact != null && contact.matches(request, boundedOwn);
        boolean noHorizontalClipping = request.x() == boundedOwn.x() && request.z() == boundedOwn.z();
        return exactContact || (contact == null && noHorizontalClipping);
    }

    public MovementVector geometryCorrection() { return geometryCorrection; }
    public MovementVector boundedOwnDisplacement() { return boundedOwn; }

    public boolean matches(MovementVector request, MovementVector actual, boolean resolvedOnGround) {
        return ((domain != Domain.SUPPORTED_FLOOR && domain != Domain.SUPPORTED_FLOOR_AND_SIDE) || resolvedOnGround)
                && (domain != Domain.ASCENDING_FLOOR || !resolvedOnGround)
                && (domain != Domain.AIRBORNE_SIDE_GEOMETRY || !resolvedOnGround)
                && (domain != Domain.TRANSIENT_FLOOR_AND_SIDE || !resolvedOnGround)
                && requested.equals(request) && boundedOwn.equals(actual);
    }
}
