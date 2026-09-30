package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;

import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Session-only speech review and short-lived bubble policy. No account or player identity is stored. */
public final class LocalSpeechTranscript {
    public static final int MAX_HISTORY = 100;
    public static final int MAX_BUBBLES = 24;
    public static final int MAX_PER_SPEAKER = 4;
    public static final long BUBBLE_MILLIS = 4_000;
    private static final long MILLIS_PER_EXTRA_CHAR = 40;
    private static final int BASE_CHARS = 40;
    private static final int MAX_EXTRA_CHARS = 150;

    public record Line(int speakerId, String name, int rgb, String text, double x, double y, double z,
                       long receivedAt, LocalSpeechPayload.Mode mode, int azimuthSector, int verticalBand,
                       UUID speakerUuid) {
        public Line(int speakerId, String name, int rgb, String text, double x, double y, double z,
                    long receivedAt, LocalSpeechPayload.Mode mode, int azimuthSector, int verticalBand) {
            this(speakerId, name, rgb, text, x, y, z, receivedAt, mode, azimuthSector, verticalBand, null);
        }
        public Line(int speakerId, String name, int rgb, String text, double x, double y, double z,
                    long receivedAt, LocalSpeechPayload.Mode mode) {
            this(speakerId, name, rgb, text, x, y, z, receivedAt, mode,
                    LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING);
        }

        public String speechLabel() {
            return name + switch (mode) {
                case SAY -> ":";
                case WHISPER, W_MUFFLED -> " whispers:";
                case SHOUT -> " yells:";
            };
        }

        public boolean mentions(String localName) {
            return mode != LocalSpeechPayload.Mode.W_MUFFLED && LocalSpeechTranscript.mentions(text, localName);
        }
    }
    public record Bubble(Line line, Object dimension, UUID entityUuid, long expiresAt) { }
    public enum Placement { TRACKED, FALLBACK, ABSENT, SELF, WRONG_DIMENSION, EXPIRED, MISMATCH, OUT_OF_RANGE, OCCLUDED }

    private final ArrayDeque<Line> history = new ArrayDeque<>();
    private final ArrayDeque<Bubble> bubbles = new ArrayDeque<>();

    public void accept(int speakerId, String name, int rgb, String text, double x, double y, double z,
                        Object dimension, UUID entityUuid, long now) {
        accept(speakerId, name, rgb, text, x, y, z, dimension, entityUuid, now, LocalSpeechPayload.Mode.SAY);
    }

    public void accept(int speakerId, String name, int rgb, String text, double x, double y, double z,
                       Object dimension, UUID entityUuid, long now, LocalSpeechPayload.Mode mode) {
        accept(speakerId, name, rgb, text, x, y, z, dimension, entityUuid, now, mode,
                LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING);
    }

    /** Bearing is coarse and world-relative; only muffled lines retain it. */
    public void accept(int speakerId, String name, int rgb, String text, double x, double y, double z,
                       Object dimension, UUID entityUuid, long now, LocalSpeechPayload.Mode mode,
                       int azimuthSector, int verticalBand) {
        accept(speakerId, name, rgb, text, x, y, z, dimension, entityUuid, entityUuid, now, mode,
                azimuthSector, verticalBand);
    }

    /** The authored UUID is distinct from an entity that happened to be resolved at receipt. */
    public void accept(int speakerId, String name, int rgb, String text, double x, double y, double z,
                       Object dimension, UUID speakerUuid, UUID entityUuid, long now, LocalSpeechPayload.Mode mode,
                       int azimuthSector, int verticalBand) {
        boolean muffled = mode == LocalSpeechPayload.Mode.W_MUFFLED;
        // Muffled speech has a name but no entity anchor, durable color, or precise location.
        Line line = new Line(muffled ? 0 : speakerId,
                name,
                muffled ? LocalSpeechPayload.ANONYMOUS_RGB : rgb, text,
                muffled ? 0 : x, muffled ? 0 : y, muffled ? 0 : z, now, mode,
                muffled ? azimuthSector : LocalSpeechPayload.NO_BEARING,
                muffled ? verticalBand : LocalSpeechPayload.NO_BEARING,
                muffled || LocalSpeechPayload.NO_UUID.equals(speakerUuid) ? null : speakerUuid);
        history.addLast(line);
        while (history.size() > MAX_HISTORY) history.removeFirst();
        expire(dimension, now);
        if (muffled) return;
        int count = 0;
        for (Bubble bubble : bubbles) if (bubble.line().speakerId() == speakerId) count++;
        while (count >= MAX_PER_SPEAKER) {
            for (var iterator = bubbles.iterator(); iterator.hasNext();) {
                if (iterator.next().line().speakerId() == speakerId) {
                    iterator.remove();
                    count--;
                    break;
                }
            }
        }
        bubbles.addLast(new Bubble(line, dimension, entityUuid, now + bubbleLifetime(text)));
        while (bubbles.size() > MAX_BUBBLES) bubbles.removeFirst();
    }

    /** 4s for up to 40 visible code points, then 40ms per point, capped at 10s (190 points). */
    static long bubbleLifetime(String text) {
        String visible = text == null ? "" : text.strip();
        int chars = visible.codePointCount(0, visible.length());
        return BUBBLE_MILLIS + MILLIS_PER_EXTRA_CHAR * Math.min(MAX_EXTRA_CHARS, Math.max(0, chars - BASE_CHARS));
    }

    public void expire(Object dimension, long now) {
        bubbles.removeIf(bubble -> !Objects.equals(bubble.dimension(), dimension) || bubble.expiresAt() <= now);
    }

    /** Bind short-lived arrivals only when their actual source becomes tracked in this world. */
    public void bindPending(Object dimension, long now, Function<Line, UUID> resolver) {
        expire(dimension, now);
        int count = bubbles.size();
        for (int i = 0; i < count; i++) {
            Bubble bubble = bubbles.removeFirst();
            if (bubble.entityUuid() != null) {
                bubbles.addLast(bubble);
                continue;
            }
            UUID uuid = resolver.apply(bubble.line());
            if (uuid == null) bubbles.addLast(bubble);
            else if (uuid.equals(bubble.line().speakerUuid()))
                bubbles.addLast(new Bubble(bubble.line(), bubble.dimension(), uuid, bubble.expiresAt()));
            // A visible entity with this ID but another UUID is a permanent conflict.
        }
    }

    /** A tracked ID is never replaced by an unverified packet-position anchor. */
    public static Placement placement(Bubble bubble, Object dimension, long now, UUID trackedUuid,
                                      boolean nearAuthoredPosition, boolean self, double distanceSquared,
                                      boolean visible) {
        if (bubble.line().mode() == LocalSpeechPayload.Mode.W_MUFFLED) return Placement.MISMATCH;
        if (!Objects.equals(bubble.dimension(), dimension)) return Placement.WRONG_DIMENSION;
        if (now >= bubble.expiresAt()) return Placement.EXPIRED;
        if (trackedUuid != null && (!trackedUuid.equals(bubble.line().speakerUuid())
                || bubble.entityUuid() == null && !nearAuthoredPosition
                || bubble.entityUuid() != null && !bubble.entityUuid().equals(trackedUuid))) return Placement.MISMATCH;
        if (self) return Placement.SELF;
        if (trackedUuid == null && bubble.entityUuid() != null) return Placement.ABSENT;
        double maxDistance = trackedUuid == null && bubble.line().mode() != LocalSpeechPayload.Mode.SHOUT ? 15 : 24;
        if (distanceSquared > maxDistance * maxDistance) return Placement.OUT_OF_RANGE;
        if (!visible) return Placement.OCCLUDED;
        return trackedUuid == null ? Placement.FALLBACK : Placement.TRACKED;
    }

    /** A conflicting tracked ID permanently disqualifies this utterance from fallback. */
    public void discard(Bubble bubble) { bubbles.remove(bubble); }

    public List<Line> history() { return List.copyOf(history); }
    public List<Bubble> bubbles() { return bubbles.stream().filter(b -> b.entityUuid() != null).toList(); }
    public List<Bubble> candidates() { return List.copyOf(bubbles); }

    public void clear() { history.clear(); bubbles.clear(); }

    /** Match either full given/surname or a whole name word, never a substring of another word. */
    public static boolean mentions(String text, String localName) {
        return !mentionRanges(text, localName).isEmpty();
    }

    /** UTF-16 ranges of every whole-word occurrence, suitable for Component substring styling. */
    public static List<int[]> mentionRanges(String text, String localName) {
        List<int[]> ranges = new ArrayList<>();
        if (localName == null || localName.isBlank() || text == null) return ranges;
        for (String word : localName.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (word.codePointCount(0, word.length()) < 2) continue;
            for (int found = 0; found <= text.length() - word.length();) {
                int end = found + word.length();
                if (text.regionMatches(true, found, word, 0, word.length())
                        && (found == 0 || !Character.isLetterOrDigit(text.codePointBefore(found)))
                        && (end == text.length() || !Character.isLetterOrDigit(text.codePointAt(end))))
                    ranges.add(new int[]{found, end});
                found += Character.charCount(text.codePointAt(found));
            }
        }
        ranges.sort(java.util.Comparator.comparingInt(range -> range[0]));
        return ranges;
    }
}
