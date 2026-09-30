package com.juicyslew.moonstation14.ms14.chat.server;

import java.util.WeakHashMap;

/** Pure admission and spatial delivery decisions; no fallback speaker identity. */
public final class LocalSpeechPolicy {
    public static final int MAX_TEXT_LENGTH = 256;
    public static final double RANGE = 15.0;
    public static final long SPEECH_COOLDOWN_NANOS = 1_000_000_000L;
    private LocalSpeechPolicy() { }

    public enum Route { VANILLA, DROP, LOCAL }

    /** One admitted attempt per cooldown, including attempts later dropped by routing checks.
     * Weak player keys prevent stale connections from accumulating if logout is missed.
     */
    public static final class AttemptLimiter<K> {
        private final WeakHashMap<K, Long> lastAdmitted = new WeakHashMap<>();

        public boolean admit(K player, long nowNanos) {
            if (player == null) return false;
            Long previous = lastAdmitted.get(player);
            if (previous != null) {
                long elapsed = nowNanos - previous;
                if (elapsed >= 0 && elapsed < SPEECH_COOLDOWN_NANOS) return false;
            }
            lastAdmitted.put(player, nowNanos);
            return true;
        }

        public void remove(K player) { lastAdmitted.remove(player); }

        public void clear() { lastAdmitted.clear(); }
    }

    public static Route route(boolean anyCommitted, boolean exactCharacterBody, boolean canSpeak,
                              boolean savedIdentity, String rawText) {
        if (!anyCommitted) return Route.VANILLA;
        return exactCharacterBody && canSpeak && savedIdentity && validText(rawText) ? Route.LOCAL : Route.DROP;
    }

    /** Preserve the authenticated raw string verbatim, including literal formatting characters. */
    public static boolean validText(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_TEXT_LENGTH;
    }

    public static boolean validPosition(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= 30_000_000 && Math.abs(z) <= 30_000_000;
    }

    public static boolean recipient(boolean sender, boolean sameLevel, double sx, double sy, double sz,
                                    double rx, double ry, double rz) {
        if (!sameLevel || !validPosition(sx, sy, sz) || !validPosition(rx, ry, rz)) return false;
        if (sender) return true;
        double dx = sx - rx, dy = sy - ry, dz = sz - rz;
        return dx * dx + dy * dy + dz * dz <= RANGE * RANGE;
    }
}
