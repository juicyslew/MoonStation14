package com.juicyslew.moonstation14.ms14.combat.client;

/** Pure camera-eye recoil policy adapted from SS14 CameraRecoilSystem. */
public final class GunCameraRecoilPolicy {
    private static final double MAX_MAGNITUDE = 1.0;
    private static final double SNAP_THRESHOLD = 0.005;
    private GunCameraRecoilPolicy() { }

    public record Offset(double x, double y, double z) {
        public Offset(double x, double y) { this(x, y, 0); }
        public double magnitude() { return Math.sqrt(x*x + y*y + z*z); }
        public static final Offset ZERO = new Offset(0, 0, 0);
    }
    /** elapsedSeconds is time since the most recent kick. */
    public record State(double elapsedSeconds, Offset kick) {
        public static final State ZERO = new State(0, Offset.ZERO);
        public State {
            if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0 || kick == null
                    || !Double.isFinite(kick.x()) || !Double.isFinite(kick.y()) || !Double.isFinite(kick.z()))
                throw new IllegalArgumentException("invalid camera recoil state");
        }
    }

    /** A shot directed from the camera towards its target kicks oppositely, at half-unit base strength. */
    public static State kick(State state, double fromX, double fromY, double toX, double toY,
                             double scalar, double screenShakeIntensity) {
        return kick(state, fromX, fromY, 0, toX, toY, 0, scalar, screenShakeIntensity);
    }
    public static State kick(State state, double fromX, double fromY, double fromZ,
                             double toX, double toY, double toZ, double scalar, double screenShakeIntensity) {
        if (state == null || !Double.isFinite(scalar) || scalar < 0 || !Double.isFinite(screenShakeIntensity))
            throw new IllegalArgumentException("invalid recoil input");
        if (screenShakeIntensity == 0 || scalar == 0) return state;
        double dx = fromX - toX, dy = fromY - toY, dz = fromZ - toZ;
        double length = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (length == 0) return state;
        double scale = .5 * scalar * screenShakeIntensity;
        double damp = Math.max(0, 1 - state.kick().magnitude());
        double x = state.kick().x() + dx / length * scale * damp;
        double y = state.kick().y() + dy / length * scale * damp;
        double z = state.kick().z() + dz / length * scale * damp;
        double magnitude = Math.sqrt(x*x + y*y + z*z);
        if (magnitude > MAX_MAGNITUDE) { x /= magnitude; y /= magnitude; z /= magnitude; }
        return new State(0, new Offset(x, y, z));
    }

    public static State advance(State state, double deltaSeconds) {
        if (state == null || !Double.isFinite(deltaSeconds) || deltaSeconds < 0)
            throw new IllegalArgumentException("invalid recoil frame time");
        Offset kick = state.kick();
        double magnitude = kick.magnitude();
        if (magnitude <= SNAP_THRESHOLD) return new State(state.elapsedSeconds() + deltaSeconds, Offset.ZERO);
        double elapsed = state.elapsedSeconds() + deltaSeconds;
        double rate = .1 + (30 - .1) * Math.min(1, elapsed / 4.0);
        double amount = rate * deltaSeconds;
        double x = kick.x() - kick.x() / magnitude * amount;
        double y = kick.y() - kick.y() / magnitude * amount;
        double z = kick.z() - kick.z() / magnitude * amount;
        if (Math.signum(x) != Math.signum(kick.x())) x = 0;
        if (Math.signum(y) != Math.signum(kick.y())) y = 0;
        if (Math.signum(z) != Math.signum(kick.z())) z = 0;
        return new State(elapsed, new Offset(x, y, z));
    }
}
