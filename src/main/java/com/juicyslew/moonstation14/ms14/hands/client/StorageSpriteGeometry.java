package com.juicyslew.moonstation14.ms14.hands.client;

/** Measured visible-pixel geometry for the native 32px storage pickaxe sprite. */
final class StorageSpriteGeometry {
    static final double CANVAS = 32;
    static final double NATIVE_CENTER = 16;
    static final double INTERIOR = 28;
    // Opaque pixel-corner bounds after the sprite's native -45 degree artwork rotation.
    private static final double MIN_X = -17 / Math.sqrt(2), MAX_X = 17 / Math.sqrt(2);
    private static final double MIN_Y = -15 / Math.sqrt(2), MAX_Y = 22 / Math.sqrt(2);

    record Fit(double scale, double offsetX, double offsetY, double minX, double minY, double maxX, double maxY) { }

    private StorageSpriteGeometry() { }

    /** Fit the already -45 degree artwork after a cardinal storage rotation. */
    static Fit fit(int degrees) {
        if (degrees % 90 != 0) throw new IllegalArgumentException("Storage rotation must be cardinal");
        double radians = Math.toRadians(degrees), c = Math.cos(radians), s = Math.sin(radians);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{MIN_X, MAX_X}) for (double y : new double[]{MIN_Y, MAX_Y}) {
            double rx = c * x - s * y;
            double ry = s * x + c * y;
            minX = Math.min(minX, rx); maxX = Math.max(maxX, rx);
            minY = Math.min(minY, ry); maxY = Math.max(maxY, ry);
        }
        double scale = INTERIOR / Math.max(maxX - minX, maxY - minY);
        double dx = -(minX + maxX) * scale / 2, dy = -(minY + maxY) * scale / 2;
        return new Fit(scale, dx, dy, dx + minX * scale, dy + minY * scale,
                dx + maxX * scale, dy + maxY * scale);
    }
}
