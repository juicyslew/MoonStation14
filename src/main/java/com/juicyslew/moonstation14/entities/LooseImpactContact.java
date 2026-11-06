package com.juicyslew.moonstation14.entities;

/** Pure clipping predicate for distinguishing requested travel blocked by contact from position correction. */
final class LooseImpactContact {
    private LooseImpactContact() { }

    static boolean clippedAxis(double requested, double accepted, double epsilon) {
        return requested > 0 && accepted < requested - epsilon
                || requested < 0 && accepted > requested + epsilon;
    }

    static boolean clippedOwnTravel(double requestedX, double requestedY, double requestedZ,
                                    double acceptedX, double acceptedY, double acceptedZ, double epsilon) {
        return clippedAxis(requestedX, acceptedX, epsilon)
                || clippedAxis(requestedY, acceptedY, epsilon)
                || clippedAxis(requestedZ, acceptedZ, epsilon);
    }
}
