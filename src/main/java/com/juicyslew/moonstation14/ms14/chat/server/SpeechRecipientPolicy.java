package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;

/** Pure spatial and text-redaction rules for server-authored local speech. */
public final class SpeechRecipientPolicy {
    private SpeechRecipientPolicy() { }

    public enum Delivery { NONE, CLEAR, MUFFLED }

    /** World-relative bearing, 0 = +Z, 4 = +X, 8 = -Z, 12 = -X.
     * Slanted bearings use a 30-degree elevation threshold. No exact distance is encoded.
     * Directly above/below uses a vertical-only band and sector 0 as a placeholder. */
    public record Bearing(int azimuthSector, int verticalBand) { }

    /** Call only for an already classified, audible muffled recipient. */
    public static LocalSpeechPayload.DistanceTier distanceTier(double sx, double sy, double sz,
                                                                 double rx, double ry, double rz) {
        if (!LocalSpeechPolicy.validPosition(sx, sy, sz) || !LocalSpeechPolicy.validPosition(rx, ry, rz))
            throw new IllegalArgumentException("Invalid muffled distance position");
        double dx = sx - rx, dy = sy - ry, dz = sz - rz;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (!(distanceSquared > SpeechModePolicy.WHISPER_CLEAR_RANGE * SpeechModePolicy.WHISPER_CLEAR_RANGE)
                || distanceSquared > SpeechModePolicy.WHISPER_MUFFLED_RANGE * SpeechModePolicy.WHISPER_MUFFLED_RANGE)
            throw new IllegalArgumentException("Not in muffled whisper range");
        return distanceSquared <= 4.5 * 4.5
                ? LocalSpeechPayload.DistanceTier.NEAR : LocalSpeechPayload.DistanceTier.FAR;
    }

    public static Bearing bearing(double sx, double sy, double sz, double rx, double ry, double rz) {
        if (!LocalSpeechPolicy.validPosition(sx, sy, sz) || !LocalSpeechPolicy.validPosition(rx, ry, rz))
            throw new IllegalArgumentException("Invalid bearing position");
        double dx = sx - rx, dy = sy - ry, dz = sz - rz;
        double horizontal = Math.hypot(dx, dz);
        if (!Double.isFinite(horizontal) || !Double.isFinite(dy) || (horizontal == 0 && dy == 0))
            throw new IllegalArgumentException("Undefined bearing");
        if (horizontal == 0)
            return new Bearing(0, dy > 0 ? LocalSpeechPayload.BAND_UP : LocalSpeechPayload.BAND_DOWN);
        double angle = Math.atan2(dx, dz);
        int sector = (int) Math.floor((angle + Math.PI / 16) * (8 / Math.PI));
        sector = Math.floorMod(sector, LocalSpeechPayload.HORIZONTAL_SECTORS);
        int band = dy > horizontal / Math.sqrt(3) ? LocalSpeechPayload.BAND_HIGH
                : dy < -horizontal / Math.sqrt(3) ? LocalSpeechPayload.BAND_LOW
                : LocalSpeechPayload.BAND_LEVEL;
        return new Bearing(sector, band);
    }

    public static Delivery classify(SpeechModePolicy.Mode mode, boolean sender, boolean sameLevel,
                                    double sx, double sy, double sz, double rx, double ry, double rz) {
        if (!sameLevel || !LocalSpeechPolicy.validPosition(sx, sy, sz)
                || !LocalSpeechPolicy.validPosition(rx, ry, rz) || mode == null) return Delivery.NONE;
        if (mode != SpeechModePolicy.Mode.SAY && mode != SpeechModePolicy.Mode.SHOUT
                && mode != SpeechModePolicy.Mode.WHISPER) return Delivery.NONE;
        if (sender) return Delivery.CLEAR;
        double dx = sx - rx, dy = sy - ry, dz = sz - rz;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (mode == SpeechModePolicy.Mode.WHISPER) {
            if (distanceSquared <= SpeechModePolicy.WHISPER_CLEAR_RANGE * SpeechModePolicy.WHISPER_CLEAR_RANGE)
                return Delivery.CLEAR;
            return distanceSquared <= SpeechModePolicy.WHISPER_MUFFLED_RANGE * SpeechModePolicy.WHISPER_MUFFLED_RANGE
                    ? Delivery.MUFFLED : Delivery.NONE;
        }
        return distanceSquared <= SpeechModePolicy.SAY_RANGE * SpeechModePolicy.SAY_RANGE
                ? Delivery.CLEAR : Delivery.NONE;
    }

    /** Retains whitespace layout and at most one in five ASCII alphanumerics; masks everything else. */
    public static String muffle(String text) {
        if (!LocalSpeechPolicy.validText(text)) throw new IllegalArgumentException("Invalid speech text");
        StringBuilder result = new StringBuilder(text.length());
        int asciiAlphanumerics = 0;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (c < 128 && ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9'))) {
                asciiAlphanumerics++;
                result.append(asciiAlphanumerics % 5 == 0 ? (char) c : '·');
            } else if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                result.appendCodePoint(c);
            } else {
                result.append(c == '·' ? '•' : '·');
            }
        }
        return result.toString();
    }
}
