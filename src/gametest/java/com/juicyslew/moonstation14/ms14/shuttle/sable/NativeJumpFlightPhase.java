package com.juicyslew.moonstation14.ms14.shuttle.sable;

import java.util.UUID;

/** Small observation policy for the rotating-jump fixture; it does not predict or alter motion. */
public final class NativeJumpFlightPhase {
    /** Landing-specific evidence gate; unlike the idle correction certificate, horizontal travel is valid. */
    static boolean landingWitnessAdmissible(boolean fresh, boolean complete, boolean trackedFrame,
            boolean grounded, boolean floorContact, boolean upwardFloorNormal,
            com.juicyslew.moonstation14.ms14.movement.MovementVector request,
            com.juicyslew.moonstation14.ms14.movement.MovementVector measured,
            double nativeX, double nativeY, double nativeZ,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows, UUID frame,
            java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownDeckFloor) {
        double eps = com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL;
        if (!fresh || !complete || !trackedFrame || !grounded || !floorContact || !upwardFloorNormal
                || request == null || request.y() > 0 || measured == null || rows == null || rows.isEmpty()
                || frame == null || knownDeckFloor == null
                || !Double.isFinite(nativeX) || !Double.isFinite(nativeY) || !Double.isFinite(nativeZ)
                || Math.abs(nativeX - measured.x()) > eps + Math.ulp(measured.x())
                || Math.abs(nativeY - measured.y()) > eps + Math.ulp(measured.y())
                || Math.abs(nativeZ - measured.z()) > eps + Math.ulp(measured.z())) return false;
        return rows.stream().allMatch(row -> row != null && frame.equals(row.frameId())
                && row.verticalCollision() && Double.isFinite(row.responseY()) && row.responseY() >= 0
                && knownDeckFloor.test(row));
    }

    private final UUID launchFrame;
    private boolean departureConsumed;
    private int observations;
    private int risingSamples;
    private int fallingSamples;
    private boolean sdkMotionChanged;
    private boolean landed;

    public NativeJumpFlightPhase(UUID launchFrame) { this.launchFrame = java.util.Objects.requireNonNull(launchFrame); }

    public boolean observeDeparture(boolean fresh, boolean airborne, UUID beforeFrame, UUID afterFrame) {
        if (departureConsumed || !fresh || !airborne || !launchFrame.equals(beforeFrame) || afterFrame != null) return false;
        departureConsumed = true;
        return true;
    }

    public void observeAir(boolean fresh, boolean airborne, boolean contactFree, double incomingY, double outgoingY, boolean sdkChanged) {
        if (!departureConsumed || !fresh || !airborne || !contactFree || observations >= 12
                || !Double.isFinite(incomingY) || !Double.isFinite(outgoingY))
            throw new IllegalStateException("invalid airborne jump observation");
        observations++;
        if (outgoingY > 0) risingSamples++;
        if (incomingY < 0 && outgoingY < 0) fallingSamples++;
        sdkMotionChanged |= sdkChanged;
    }

    public boolean airProofComplete() { return risingSamples >= 2 && fallingSamples >= 2 && sdkMotionChanged; }
    public void observeLanding(boolean fresh, boolean knownLaunchDeckFloor) {
        if (!departureConsumed || !fresh || !knownLaunchDeckFloor || !airProofComplete() || landed || observations >= 12)
            throw new IllegalStateException("landing lacks fresh known-floor contact or completed air proof");
        landed = true;
        observations++;
    }
    public void observeGrounded(boolean fresh, boolean knownLaunchDeckFloor) {
        if (!landed || !fresh || !knownLaunchDeckFloor || observations >= 12)
            throw new IllegalStateException("invalid post-landing observation");
        observations++;
    }
    public int observations() { return observations; }
    public boolean departureConsumed() { return departureConsumed; }
}
