package com.juicyslew.moonstation14.ms14.ui.model.animation;

import java.util.Objects;

/** Immutable, bounded row-major sheet. Frames for each direction are consecutive in direction order. */
public final class SpriteSheet {
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_FRAMES = 256;
    private static final int MAX_DURATION_MS = 86_400_000;

    private final String state;
    private final int width, height, frameWidth, frameHeight, directions, columns;
    private final int[][] durations;
    private final long[] cycles;
    private final int[] offsets;

    public SpriteSheet(String state, int width, int height, int frameWidth, int frameHeight,
                       int[][] durations) {
        this.state = Objects.requireNonNull(state, "state");
        if (state.isBlank() || state.length() > 128 || width < 1 || height < 1
                || width > MAX_DIMENSION || height > MAX_DIMENSION || frameWidth < 1 || frameHeight < 1
                || width % frameWidth != 0 || height % frameHeight != 0)
            throw new IllegalArgumentException("Invalid sprite sheet geometry or state");
        this.width = width;
        this.height = height;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        columns = width / frameWidth;
        Objects.requireNonNull(durations, "durations");
        if (durations.length < 1 || durations.length > 8)
            throw new IllegalArgumentException("Directions must be between 1 and 8");
        directions = durations.length;
        this.durations = new int[directions][];
        cycles = new long[directions];
        offsets = new int[directions];
        int count = 0;
        for (int d = 0; d < directions; d++) {
            int[] times = Objects.requireNonNull(durations[d], "direction durations");
            if (times.length < 1 || times.length > MAX_FRAMES || count + times.length > MAX_FRAMES)
                throw new IllegalArgumentException("Too many frames");
            offsets[d] = count;
            count += times.length;
            this.durations[d] = times.clone();
            for (int time : times) {
                if (time < 1 || time > MAX_DURATION_MS)
                    throw new IllegalArgumentException("Frame duration out of bounds");
                cycles[d] += time;
            }
        }
        if ((long) columns * (height / frameHeight) != count)
            throw new IllegalArgumentException("Sheet cells must match frame count");
    }

    public String state() { return state; }
    public int width() { return width; }
    public int height() { return height; }
    public int frameWidth() { return frameWidth; }
    public int frameHeight() { return frameHeight; }
    public int directions() { return directions; }
    public int frameCount(int direction) { return durations[checkDirection(direction)].length; }
    public long cycleMillis(int direction) { return cycles[checkDirection(direction)]; }

    /** At an exact frame boundary, selects the next frame. Negative elapsed values wrap as well. */
    public int frameAt(int direction, long elapsedMillis) {
        int d = checkDirection(direction);
        long offset = Math.floorMod(elapsedMillis, cycles[d]);
        for (int i = 0; i < durations[d].length; i++) {
            if (offset < durations[d][i]) return i;
            offset -= durations[d][i];
        }
        throw new AssertionError("Unreachable frame");
    }

    public int u(int direction, int frame) { return cell(direction, frame) % columns * frameWidth; }
    public int v(int direction, int frame) { return cell(direction, frame) / columns * frameHeight; }

    private int cell(int direction, int frame) {
        int d = checkDirection(direction);
        if (frame < 0 || frame >= durations[d].length) throw new IllegalArgumentException("Invalid frame");
        return offsets[d] + frame;
    }

    private int checkDirection(int direction) {
        if (direction < 0 || direction >= directions)
            throw new IllegalArgumentException("Invalid direction");
        return direction;
    }
}
