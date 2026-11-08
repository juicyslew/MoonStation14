package com.juicyslew.moonstation14.ms14.mobs;

import net.minecraft.world.phys.Vec3;

/** Pure per-NPC natural-flight phase and steering policy. World routing stays with AirNpcRoutePlanner. */
final class NpcFlightController {
    enum Phase { GROUND_WANDER, TAKEOFF, AIR_TRAVEL, LAND_APPROACH, LANDING }
    record State(Phase phase, long phaseStarted, long phaseDuration, Vec3 goal) {
        static State grounded(long now, long duration) { return new State(Phase.GROUND_WANDER, now, duration, null); }
        State transition(Phase next, long now, long duration, Vec3 nextGoal) {
            return new State(next, now, duration, nextGoal);
        }
    }
    private NpcFlightController() { }

    static long duration(int resolvedTicks, double randomUnit) {
        double bounded = Math.max(0, Math.min(Math.nextDown(1d), randomUnit));
        return Math.max(1, Math.round(resolvedTicks * (.75 + bounded * .5)));
    }

    static boolean expired(State state, long now) {
        return state != null && now - state.phaseStarted() >= state.phaseDuration();
    }

    /** Signed, eased vertical input; a negative value means descend while remaining airborne. */
    static double verticalWish(double currentY, double targetY, double verticalSpeedMultiplier) {
        if (!Double.isFinite(currentY) || !Double.isFinite(targetY) || !Double.isFinite(verticalSpeedMultiplier))
            return 0;
        return Math.max(-verticalSpeedMultiplier, Math.min(verticalSpeedMultiplier,
                (targetY - currentY) * .35));
    }

    static double easeVerticalWish(double previous, double desired, double maxChange) {
        if (!Double.isFinite(previous)) previous = 0d;
        if (!Double.isFinite(desired)) desired = 0d;
        double step = Double.isFinite(maxChange) ? Math.max(0d, maxChange) : 0d;
        return Math.max(previous - step, Math.min(previous + step, desired));
    }

    static boolean phaseLanding(Phase phase) { return phase == Phase.LANDING; }

    /** Phases with a committed vertical/landing maneuver must retain their route over ordinary task planning. */
    static boolean protectsCommittedManeuver(Phase phase) {
        return phase == Phase.TAKEOFF || phase == Phase.LAND_APPROACH || phase == Phase.LANDING;
    }

    static boolean takeoffCleared(double y, double targetY) { return Double.isFinite(y) && Double.isFinite(targetY) && y >= targetY - .35; }

    static double anchoredAltitude(double referenceY, double currentY, double randomUnit) {
        double roll = Double.isFinite(randomUnit) ? Math.max(0, Math.min(1, randomUnit)) : .5;
        double reference = Double.isFinite(referenceY) ? referenceY : currentY;
        return reference + (roll * 2 - 1) * 1.5;
    }

    static boolean nearLandingApproach(Vec3 position, Vec3 approach) {
        return position != null && approach != null && position.distanceTo(approach) < .8;
    }

    static boolean reached(Vec3 position, Vec3 waypoint) {
        return position.distanceToSqr(waypoint) <= .36;
    }

    static Vec3 lookAhead(Vec3 position, Vec3 waypoint, double distance) {
        Vec3 delta = waypoint.subtract(position);
        if (delta.lengthSqr() <= distance * distance || delta.lengthSqr() < 1.0e-8) return waypoint;
        return position.add(delta.normalize().scale(distance));
    }
}
