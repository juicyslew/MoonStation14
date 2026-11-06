package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import org.joml.Matrix3dc;
import org.joml.Matrix3d;
import org.joml.Quaterniondc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Pure impulse calculation. Native physics remains the authority for pose and motion integration. */
public final class ShuttleMotionMath {
    private ShuttleMotionMath() { }
    public record Vector(double x, double y, double z) { }
    public record Impulses(Vector linearLocal, Vector angularLocal) { }
    public record CommandAcceleration(Vector linearWorld, Vector angularWorld) { }
    public record ControlledImpulses(Impulses impulses, CommandAcceleration commanded) { }
    public record ControlLimits(double linear, double angular, double linearJerk, double angularJerk) {
        public ControlLimits {
            if (!(linear>0) || !(angular>0) || linearJerk<0 || angularJerk<0
                    || !Double.isFinite(linear+angular+linearJerk+angularJerk)) throw new IllegalArgumentException("invalid control limits");
        }
    }
    public static final ControlLimits LEGACY_LIMITS = new ControlLimits(2.0,.25,0,0);

    /** Exact one-shot counter-impulse in body coordinates; preserves orientation and applies no gravity compensation. */
    public static Impulses counterImpulse(Vector3dc worldLinearVelocity, Vector3dc worldAngularVelocity,
                                           Quaterniondc bodyToWorld, double mergedMass, Matrix3dc localInertia) {
        if (!finite(worldLinearVelocity) || !finite(worldAngularVelocity) || bodyToWorld == null
                || !Double.isFinite(mergedMass) || mergedMass <= 0 || !finite(localInertia)
                || !positiveDefinite(localInertia)) return null;
        double qlen=bodyToWorld.x()*bodyToWorld.x()+bodyToWorld.y()*bodyToWorld.y()
                +bodyToWorld.z()*bodyToWorld.z()+bodyToWorld.w()*bodyToWorld.w();
        if (!Double.isFinite(qlen) || qlen < 1e-24) return null;
        Quaterniond rotation=new Quaterniond(bodyToWorld).normalize();
        Vector3d linear=rotation.transformInverse(new Vector3d(worldLinearVelocity).negate()).mul(mergedMass);
        Vector3d omegaLocal=rotation.transformInverse(new Vector3d(worldAngularVelocity).negate());
        Vector3d angular=localInertia.transform(omegaLocal,new Vector3d());
        return finite(linear)&&finite(angular)?new Impulses(v(linear),v(angular)):null;
    }

    /**
     * Measures physical COM transport from successive native poses; uses SDK velocity only to bootstrap.
     * Rapier's island solver applies gravity/integration over internal iterations, so COM displacement is
     * the authoritative finite-interval transport measurement without assuming an iteration-dependent bias.
     */
    public static Vector3d measureTransportVelocity(Vector3dc previousPosition, double previousStepSeconds,
                                                      Vector3dc currentPosition, Vector3dc sdkVelocityFallback) {
        return finiteDifference(previousPosition, previousStepSeconds, currentPosition, sdkVelocityFallback);
    }

    /** Finite-interval uncontrolled angular acceleration; an absent/invalid baseline contributes zero. */
    public static Vector3d measureUncontrolledAngularAcceleration(Vector3dc previousWorldOmega,
                                                                    double completedStepSeconds,
                                                                    Vector3dc currentWorldOmega) {
        return finiteDifference(previousWorldOmega, completedStepSeconds, currentWorldOmega, new Vector3d());
    }

    /**
     * Forecasts next-interval angular acceleration from the previous interval's measured world alpha.
     * For the bounded fixture controller's body-attached external-torque hypothesis, transport it by
     * qCurrentPre * conjugate(qPreviousPost). This is a one-interval forecast, not a universal estimator:
     * world-fixed torques or rapidly varying external torque need not rotate with the body. Missing or
     * invalid orientation history returns a copy of the unchanged world measurement. Invalid alpha remains
     * invalid so the existing compute validation continues to reject it.
     */
    public static Vector3d forecastNextWorldAngularAcceleration(Vector3dc measuredWorldAlpha,
            Quaterniondc previousPostBodyToWorld, Quaterniondc currentPreBodyToWorld) {
        if (measuredWorldAlpha == null) return null;
        Vector3d rawCopy = new Vector3d(measuredWorldAlpha);
        if (!finite(measuredWorldAlpha)) return rawCopy;
        Quaterniond previous = normalizedOrientation(previousPostBodyToWorld);
        Quaterniond current = normalizedOrientation(currentPreBodyToWorld);
        if (previous == null || current == null) return rawCopy;
        Quaterniond intervalRotation = current.mul(previous.conjugate());
        double normSquared = intervalRotation.lengthSquared();
        if (!Double.isFinite(normSquared) || normSquared < 1e-24) return rawCopy;
        intervalRotation.normalize();
        return intervalRotation.transform(rawCopy);
    }

    private static Quaterniond normalizedOrientation(Quaterniondc source) {
        if (source == null || !Double.isFinite(source.x()) || !Double.isFinite(source.y())
                || !Double.isFinite(source.z()) || !Double.isFinite(source.w())) return null;
        double normSquared = source.x()*source.x()+source.y()*source.y()+source.z()*source.z()+source.w()*source.w();
        if (!Double.isFinite(normSquared) || normSquared < 1e-24) return null;
        return new Quaterniond(source).normalize();
    }

    private static Vector3d finiteDifference(Vector3dc previous, double seconds, Vector3dc current,
                                             Vector3dc fallback) {
        if (previous == null || !finite(previous) || !Double.isFinite(seconds) || seconds <= 0 || !finite(current))
            return finite(fallback) ? new Vector3d(fallback) : null;
        return new Vector3d(current).sub(previous).div(seconds);
    }

    public static Impulses compute(ShuttleMotionRequest request, Vector3dc worldLinearVelocity,
                                   Vector3dc worldAngularVelocity, Quaterniondc bodyToWorld,
                                   double mergedMass, Matrix3dc localInertia, Vector3dc worldGravity,
                                   double dtSeconds) {
        return compute(request, worldLinearVelocity, worldAngularVelocity, bodyToWorld, mergedMass,
                localInertia, worldGravity, dtSeconds, new Vector3d());
    }

    public static Impulses compute(ShuttleMotionRequest request, Vector3dc worldLinearVelocity,
                                   Vector3dc worldAngularVelocity, Quaterniondc bodyToWorld,
                                   double mergedMass, Matrix3dc localInertia, Vector3dc worldGravity,
                                   double dtSeconds, Vector3dc observedAngularAcceleration) {
        return compute(request, worldLinearVelocity, worldAngularVelocity, bodyToWorld, mergedMass,
                localInertia, worldGravity, dtSeconds, observedAngularAcceleration, 0.0);
    }

    /**
     * Computes bounded commanded angular acceleration plus feed-forward for native angular damping.
     * For Rapier's discrete damping recurrence omega'=(omega+delta)/(1+dt*d), the impulse produces
     * delta=dt*d*omega + dt*(1+dt*d)*a, so the post-damping rate advances by the commanded a*dt.
     */
    public static Impulses compute(ShuttleMotionRequest request, Vector3dc worldLinearVelocity,
                                   Vector3dc worldAngularVelocity, Quaterniondc bodyToWorld,
                                   double mergedMass, Matrix3dc localInertia, Vector3dc worldGravity,
                                    double dtSeconds, Vector3dc observedAngularAcceleration, double universalDamping) {
        return compute(request, worldLinearVelocity, worldLinearVelocity, worldAngularVelocity, bodyToWorld,
                mergedMass, localInertia, worldGravity, dtSeconds, observedAngularAcceleration, universalDamping);
    }

    /** Computes impulses using pose-derived COM transport for control and SDK velocity for drag feed-forward. */
    public static Impulses compute(ShuttleMotionRequest request, Vector3dc worldTransportVelocity,
                                   Vector3dc sdkWorldLinearVelocity, Vector3dc worldAngularVelocity,
                                   Quaterniondc bodyToWorld, double mergedMass, Matrix3dc localInertia,
                                   Vector3dc worldGravity, double dtSeconds,
                                   Vector3dc observedAngularAcceleration, double universalDamping) {
        ControlledImpulses computed=computeControlled(request, worldTransportVelocity, sdkWorldLinearVelocity, worldAngularVelocity,
                bodyToWorld, mergedMass, localInertia, worldGravity, dtSeconds, observedAngularAcceleration,
                universalDamping, LEGACY_LIMITS, null);
        return computed==null?null:computed.impulses();
    }

    public static ControlledImpulses computeControlled(ShuttleMotionRequest request, Vector3dc worldTransportVelocity,
            Vector3dc sdkWorldLinearVelocity, Vector3dc worldAngularVelocity, Quaterniondc bodyToWorld,
            double mergedMass, Matrix3dc localInertia, Vector3dc worldGravity, double dtSeconds,
            Vector3dc observedAngularAcceleration, double universalDamping, ControlLimits limits,
            CommandAcceleration priorCommand) {
        if (request == null || !finite(worldTransportVelocity) || !finite(worldAngularVelocity)
                || !finite(sdkWorldLinearVelocity) || !finite(worldGravity) || !finite(observedAngularAcceleration)
                || bodyToWorld == null || !Double.isFinite(bodyToWorld.x())
                || !Double.isFinite(bodyToWorld.y()) || !Double.isFinite(bodyToWorld.z())
                || !Double.isFinite(bodyToWorld.w()) || !Double.isFinite(mergedMass) || mergedMass <= 0
                || !Double.isFinite(dtSeconds) || dtSeconds <= 0 || !Double.isFinite(universalDamping)
                || universalDamping < 0 || !finite(localInertia) || limits == null) return null;
        double qlen = bodyToWorld.x()*bodyToWorld.x()+bodyToWorld.y()*bodyToWorld.y()
                +bodyToWorld.z()*bodyToWorld.z()+bodyToWorld.w()*bodyToWorld.w();
        if (!Double.isFinite(qlen) || qlen < 1e-24 || !positiveDefinite(localInertia)) return null;
        Quaterniond rotation = new Quaterniond(bodyToWorld).normalize();

        Vector3d linearAccel = new Vector3d(request.targetX(), request.targetY(), request.targetZ())
                .sub(worldTransportVelocity).mul(2.0);
        clamp(linearAccel, limits.linear());
        if (limits.linearJerk()>0 && priorCommand!=null) {
            limitJerk(linearAccel,priorCommand.linearWorld(),limits.linearJerk()*dtSeconds);
            if (length(priorCommand.linearWorld())<=limits.linear()+1e-12) clamp(linearAccel,limits.linear());
        }
        Vector3d commandedLinear=new Vector3d(linearAccel);
        // Linear control targets COM transport, so damping is an additive J/m feed-forward term;
        // do not scale the bounded transport acceleration by (1 + dt*d) as angular control does.
        linearAccel.sub(worldGravity).add(new Vector3d(sdkWorldLinearVelocity).mul(universalDamping));
        rotation.transformInverse(linearAccel);
        linearAccel.mul(mergedMass * dtSeconds);

        // Budget is an acceleration-equivalent bound on I^-1 * torque, not a torque bound.
        Vector3d feedbackAccel = new Vector3d(-2.0 * worldAngularVelocity.x(), 0,
                -2.0 * worldAngularVelocity.z()).sub(observedAngularAcceleration.x(), 0,
                observedAngularAcceleration.z());
        // Legacy overload retains precisely its historical clamp and arithmetic path.
        clamp(feedbackAccel, limits.angular());
        double yawBudget = Math.sqrt(Math.max(0.0, limits.angular() * limits.angular() - feedbackAccel.lengthSquared()));
        double yawAccel = 2.0 * (request.targetYawRate() - worldAngularVelocity.y());
        yawAccel = Math.max(-yawBudget, Math.min(yawBudget, yawAccel));
        feedbackAccel.y = yawAccel;
        if (limits.angularJerk()>0 && priorCommand!=null) {
            limitJerk(feedbackAccel,priorCommand.angularWorld(),limits.angularJerk()*dtSeconds);
            if (length(priorCommand.angularWorld())<=limits.angular()+1e-12) clamp(feedbackAccel,limits.angular());
        }
        Vector3d commandedAngular=new Vector3d(feedbackAccel);
        // This feed-forward is outside the bounded commanded acceleration budget; it offsets
        // only native damping, without changing the requested net acceleration cap.
        feedbackAccel.mul(1.0 + dtSeconds * universalDamping)
                .add(new Vector3d(worldAngularVelocity).mul(universalDamping));
        rotation.transformInverse(feedbackAccel);
        if (!finite(feedbackAccel)) return null;
        Vector3d torqueImpulse = localInertia.transform(feedbackAccel, new Vector3d()).mul(dtSeconds);
        if (!finite(linearAccel) || !finite(torqueImpulse)) return null;
        return new ControlledImpulses(new Impulses(v(linearAccel), v(torqueImpulse)),
                new CommandAcceleration(v(commandedLinear),v(commandedAngular)));
    }

    private static void limitJerk(Vector3d value, Vector source, double maxDelta) {
        Vector3d delta=new Vector3d(value).sub(source.x(),source.y(),source.z());
        clamp(delta,maxDelta);
        value.set(source.x(),source.y(),source.z()).add(delta);
    }
    private static double length(Vector value) { return Math.sqrt(value.x()*value.x()+value.y()*value.y()+value.z()*value.z()); }

    private static void clamp(Vector3d v, double max) { double len=v.length(); if (len > max) v.mul(max/len); }
    private static Vector v(Vector3dc v) { return new Vector(v.x(),v.y(),v.z()); }
    private static boolean finite(Vector3dc v) { return v != null && Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z()); }
    private static boolean finite(Matrix3dc m) {
        if (m == null) return false;
        for (int r=0;r<3;r++) for (int c=0;c<3;c++) if (!Double.isFinite(m.get(r,c))) return false;
        return true;
    }
    private static boolean positiveDefinite(Matrix3dc m) {
        double a=m.m00(), b=m.m01(), c=m.m02(), d=m.m10(), e=m.m11(), f=m.m12(), g=m.m20(), h=m.m21(), i=m.m22();
        double scale=Math.max(Math.max(Math.abs(a),Math.abs(e)),Math.max(Math.abs(i),
                Math.max(Math.max(Math.abs(b),Math.abs(c)),Math.max(Math.max(Math.abs(d),Math.abs(f)),Math.max(Math.abs(g),Math.abs(h))))));
        // Inertia is a symmetric physical tensor. Allow only double-roundoff-scale disagreement
        // from rotation/merge arithmetic: 16 ULPs or 1e-12 relative, whichever is larger.
        double symmetryTolerance=Math.max(16*Math.ulp(scale),scale*1e-12);
        if (Math.abs(b-d)>symmetryTolerance || Math.abs(c-g)>symmetryTolerance
                || Math.abs(f-h)>symmetryTolerance) return false;
        return a>0 && a*e-d*b>0 && a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g)>0;
    }
}
