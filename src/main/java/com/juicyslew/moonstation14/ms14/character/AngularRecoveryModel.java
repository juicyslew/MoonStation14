package com.juicyslew.moonstation14.ms14.character;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Pure model: orientation is an inertial world-frame basis; angular velocity is world-frame rad/s. */
public final class AngularRecoveryModel {
    private AngularRecoveryModel() { }
    public record Vec3(double x, double y, double z) {
        public Vec3 { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("non-finite vector"); }
        Vector3d j() { return new Vector3d(x, y, z); }
        static Vec3 of(Vector3d v) { return new Vec3(v.x, v.y, v.z); }
        public double length() { return j().length(); }
        public double lengthSquared() { return x*x + y*y + z*z; }
        public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }
        public Vec3 normalized() { Vector3d v = j(); if (v.lengthSquared() < 1e-24) return new Vec3(0, 1, 0); return of(v.normalize()); }
    }
    public record Quat(double w, double x, double y, double z) {
        public Quat { if (!Double.isFinite(w) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || w*w+x*x+y*y+z*z < 1e-24) throw new IllegalArgumentException("invalid quaternion"); }
        Quaterniond j() { return new Quaterniond(x, y, z, w).normalize(); }
        static Quat of(Quaterniond q) { q.normalize(); return new Quat(q.w, q.x, q.y, q.z); }
        public Vec3 rotate(Vec3 v) { return Vec3.of(j().transform(v.j())); }
        public static Quat fromAxisAngle(Vec3 axis, double angle) { if (!Double.isFinite(angle) || axis.lengthSquared() < 1e-24) throw new IllegalArgumentException("invalid axis angle"); return of(new Quaterniond().fromAxisAngleRad(axis.j().normalize(), angle)); }
        public Quat multiply(Quat b) { return of(j().mul(b.j())); }
    }
    public record State(Quat orientation, Vec3 worldAngularVelocity, double airborneSeconds, boolean recoveryActive) {
        public State { if (orientation == null || worldAngularVelocity == null || !Double.isFinite(airborneSeconds) || airborneSeconds < 0) throw new IllegalArgumentException("invalid state"); }
    }
    public record Landing(boolean bad, double tiltDegrees, double offAxisAngularSpeed) { }
    /** Result of one physical move, including the pre-settlement state used for impact assessment. */
    public record MoveStep(State nextState, State impactState) { }
    public static MoveStep afterMove(State prior, boolean wasGrounded, boolean grounded,
                                     Quat departureOrientation, Vec3 sampledOmega, Vec3 up,
                                     double dt, double grace, double recovery) {
        if (wasGrounded && grounded) return new MoveStep(prior, null);
        if (wasGrounded) return new MoveStep(departure(departureOrientation, sampledOmega), null);
        State airborne = prior == null ? departure(new Quat(1,0,0,0), new Vec3(0,0,0)) : prior;
        State advanced = step(airborne, up, dt, grace, recovery);
        return grounded ? new MoveStep(landed(advanced, up), advanced) : new MoveStep(advanced, null);
    }
    public static State departure(Quat q, Vec3 omega) { return new State(q, omega, 0, false); }

    private static double envelope(double t, double grace, double recovery) {
        double u = Math.max(0, Math.min(1, (t - grace) / recovery));
        return 1 - 3*u*u + 2*u*u*u;
    }
    /** Integral of the spin envelope from time zero through t. */
    private static double integral(double t, double grace, double recovery) {
        if (t <= grace) return t;
        double u = Math.max(0, Math.min(1, (t - grace) / recovery));
        return grace + recovery * (u - u*u*u + .5*u*u*u*u);
    }
    private static Vector3d unit(Vec3 v) {
        if (v.length() < 1e-12) throw new IllegalArgumentException("gravity up must be non-zero");
        return v.j().normalize();
    }
    private static Vector3d between(Vector3d from, Vector3d to) {
        Vector3d axis = new Vector3d(from).cross(to);
        if (axis.lengthSquared() < 1e-20 && from.dot(to) < 0)
            axis = Math.abs(from.x) < .8 ? new Vector3d(from).cross(1, 0, 0) : new Vector3d(from).cross(0, 0, 1);
        return axis;
    }
    public static State step(State s, Vec3 gravityUp, double dt, double grace, double recovery) {
        if (s == null || !Double.isFinite(dt) || dt <= 0 || !Double.isFinite(grace) || grace < 0 || !Double.isFinite(recovery) || recovery <= 0 || !Double.isFinite(grace + recovery))
            throw new IllegalArgumentException("invalid timestep/policy");
        Vector3d up = unit(gravityUp);
        double old = s.airborneSeconds(), now = old + dt;
        if (!Double.isFinite(now)) throw new IllegalArgumentException("airborne time overflow");
        Quaterniond q = s.orientation().j();
        Vector3d omegaVector = s.worldAngularVelocity().j();
        double oldG = envelope(old, grace, recovery), newG = envelope(now, grace, recovery);
        double weightedTime = oldG == 0 ? 0 : (integral(now, grace, recovery) - integral(old, grace, recovery)) / oldG;
        double angle = omegaVector.length() * weightedTime;
        if (angle > 0) q = new Quaterniond().fromAxisAngleRad(omegaVector.normalize(), angle).mul(q).normalize();

        Vector3d bodyUp = q.transform(new Vector3d(0, 1, 0));
        if (now >= grace && old < grace + recovery) {
            double oldU = Math.max(0, Math.min(1, (old - grace) / recovery));
            double newU = Math.max(0, Math.min(1, (now - grace) / recovery));
            double fraction = (newU - oldU) / Math.max(1e-15, 1 - oldU);
            Vector3d axis = between(bodyUp, up);
            double error = Math.acos(Math.max(-1, Math.min(1, bodyUp.dot(up))));
            if (axis.lengthSquared() > 1e-20 && error > 0)
                q = new Quaterniond().fromAxisAngleRad(axis.normalize(), error * fraction).mul(q).normalize();
        } else if (old >= grace + recovery) {
            Vector3d axis = between(bodyUp, up);
            double error = Math.acos(Math.max(-1, Math.min(1, bodyUp.dot(up))));
            double correction = Math.min(error, Math.PI * dt / recovery);
            if (axis.lengthSquared() > 1e-20 && correction > 0)
                q = new Quaterniond().fromAxisAngleRad(axis.normalize(), correction).mul(q).normalize();
        }
        Vec3 omega = newG == 0 ? new Vec3(0, 0, 0) : s.worldAngularVelocity().scale(newG / oldG);
        return new State(Quat.of(q), omega, now, now >= grace);
    }
    public static Landing landing(Quat q, Vec3 omega, Vec3 up, double tiltLimit, double spinLimit) {
        if (q == null || omega == null || !Double.isFinite(tiltLimit) || tiltLimit < 0 || !Double.isFinite(spinLimit) || spinLimit < 0) throw new IllegalArgumentException("invalid landing input");
        Vector3d u = unit(up);
        double tilt = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, q.rotate(new Vec3(0, 1, 0)).j().normalize().dot(u)))));
        Vector3d w = omega.j(); w.fma(-w.dot(u), u);
        return new Landing(tilt > tiltLimit || w.length() > spinLimit, tilt, w.length());
    }
    /** Upright while retaining the settled world-frame heading; no north-facing reset. */
    public static State landed(State state, Vec3 up) {
        if (state == null) throw new IllegalArgumentException("landing state required");
        Vector3d target = unit(up);
        Quaterniond q = state.orientation().j();
        Vector3d bodyUp = q.transform(new Vector3d(0, 1, 0));
        Vector3d axis = between(bodyUp, target);
        double angle = Math.acos(Math.max(-1, Math.min(1, bodyUp.dot(target))));
        if (axis.lengthSquared() > 1e-20 && angle > 0)
            q = new Quaterniond().fromAxisAngleRad(axis.normalize(), angle).mul(q).normalize();
        return new State(Quat.of(q), new Vec3(0, 0, 0), 0, false);
    }
}
