package com.juicyslew.moonstation14.ms14.ui.client;

/** GUI-independent bounds and color calculations for a 0–100% charge meter. */
public final class MachineMeterMath {
    private MachineMeterMath() {}

    public static int percent(int permille) {
        return (Math.clamp(permille, 0, 1000) + 5) / 10;
    }

    public static int fillWidth(int width, float permille) {
        if (width <= 0 || Float.isNaN(permille)) return 0;
        return Math.clamp(Math.round(width * Math.clamp(permille, 0.0f, 1000.0f) / 1000.0f), 0, width);
    }

    /** Opaque warm-red → orange → green gradient, independent of texture assets. */
    public static int chargeColor(int permille) {
        int p = Math.clamp(permille, 0, 1000);
        if (p <= 500) return mix(0xffc34736, 0xffde9b3d, p, 500);
        return mix(0xffde9b3d, 0xff58b879, p - 500, 500);
    }

    private static int mix(int from, int to, int amount, int range) {
        int r = channel(from >> 16, to >> 16, amount, range);
        int g = channel(from >> 8, to >> 8, amount, range);
        int b = channel(from, to, amount, range);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int channel(int from, int to, int amount, int range) {
        return ((from & 255) * (range - amount) + (to & 255) * amount) / range;
    }
}
