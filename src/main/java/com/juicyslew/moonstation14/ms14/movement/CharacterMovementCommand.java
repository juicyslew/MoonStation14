package com.juicyslew.moonstation14.ms14.movement;

/** One tick of intent. Axes must be in [-1, 1]; in-range diagonal intent is normalized. */
public record CharacterMovementCommand(double wishX, double wishZ, boolean jumpRequested, boolean sprint) {
    public CharacterMovementCommand {
        if (!Double.isFinite(wishX) || !Double.isFinite(wishZ)) {
            throw new IllegalArgumentException("wish axes must be finite");
        }
        if (wishX < -1d || wishX > 1d || wishZ < -1d || wishZ > 1d) {
            throw new IllegalArgumentException("wish axes must be in [-1, 1]");
        }
        double x = wishX;
        double z = wishZ;
        double length = Math.hypot(x, z);
        if (length > 1d) {
            x /= length;
            z /= length;
        }
        wishX = x;
        wishZ = z;
    }

    public CharacterMovementCommand withStun(boolean stunned) {
        return stunned ? new CharacterMovementCommand(0d, 0d, false, sprint) : this;
    }
}
