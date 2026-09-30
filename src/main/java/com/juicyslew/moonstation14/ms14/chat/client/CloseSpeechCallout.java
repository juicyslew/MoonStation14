package com.juicyslew.moonstation14.ms14.chat.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.List;

/** Screen-space geometry for a close speech callout. Points outside the view are rejected, not clamped. */
final class CloseSpeechCallout {
    private CloseSpeechCallout() { }

    record Point(float x, float y) { }
    /** Returns the left edge for independently centering measured text inside its panel. */
    static int centeredTextX(int panelX, int panelWidth, int textWidth) {
        return panelX + (panelWidth - textWidth) / 2;
    }

    record Rect(float x, float y, float width, float height) {
        boolean overlaps(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
        boolean overlapsAny(Rect reserved, List<Rect> previous) {
            return reserved != null && overlaps(reserved) || previous.stream().anyMatch(this::overlaps);
        }
    }

    static Point project(Matrix4f viewProjection, double x, double y, double z, int width, int height) {
        Point point = projectInFront(viewProjection, x, y, z, width, height);
        if (point == null || point.x() < 0 || point.x() >= width || point.y() >= height) return null;
        return point;
    }

    /** Projects a point in front of the camera without requiring it to be in the viewport. */
    static Point projectInFront(Matrix4f viewProjection, double x, double y, double z, int width, int height) {
        Vector4f clip = viewProjection.transform(new Vector4f((float)x, (float)y, (float)z, 1));
        if (!(clip.w > 0) || clip.z < -clip.w || clip.z > clip.w) return null;
        float sx = (clip.x / clip.w + 1) * width / 2;
        float sy = (1 - clip.y / clip.w) * height / 2;
        if (!Float.isFinite(sx) || !Float.isFinite(sy)) return null;
        return new Point(sx, sy);
    }

    static boolean onScreen(Point point, int width, int height) {
        return point != null && point.x() >= 0 && point.x() < width && point.y() >= 0 && point.y() < height;
    }

    /** Finds an on-screen point along a vertical body segment, projecting only (no visibility queries). */
    record SegmentPoint(Point point, double t) { }
    static SegmentPoint intersectSegment(Matrix4f viewProjection, double x0, double y0, double z0,
                                         double x1, double y1, double z1, int width, int height) {
        Point previous = projectInFront(viewProjection, x0, y0, z0, width, height);
        double previousT = 0;
        if (onScreen(previous, width, height)) return new SegmentPoint(previous, 0);
        for (int i = 1; i <= 32; i++) {
            double t = i / 32.0;
            Point point = projectInFront(viewProjection, x0 + (x1 - x0) * t,
                    y0 + (y1 - y0) * t, z0 + (z1 - z0) * t, width, height);
            if (onScreen(point, width, height)) {
                double low = previousT, high = t;
                double bestT = t;
                for (int step = 0; step < 10; step++) {
                    double middle = (low + high) * .5;
                    Point candidate = projectInFront(viewProjection, x0 + (x1 - x0) * middle,
                            y0 + (y1 - y0) * middle, z0 + (z1 - z0) * middle, width, height);
                    if (onScreen(candidate, width, height)) {
                        bestT = middle;
                        high = middle;
                    } else low = middle;
                }
                return new SegmentPoint(projectInFront(viewProjection, x0 + (x1 - x0) * bestT,
                        y0 + (y1 - y0) * bestT, z0 + (z1 - z0) * bestT, width, height), bestT);
            }
            previous = point;
            previousT = t;
        }
        return null;
    }

    /** Prefer the projected head, including an offscreen head; rescue only from a validated visible body point. */
    static Point chooseAnchor(Point head, Point highestVisibleBody, int guiHeight) {
        if (head != null) return head;
        return highestVisibleBody == null ? null : new Point(highestVisibleBody.x(),
                highestVisibleBody.y() - Math.max(1, Math.round(guiHeight * .04f)));
    }

    /** Fits a callout beside an already-visible anchor; bottom space reserves the input/chat strip. */
    static Rect layout(Point anchor, int width, int height, int viewportWidth, int viewportHeight,
                       int inputStrip, List<Rect> reserved) {
        if (anchor == null || width <= 0 || height <= 0 || viewportWidth < 1 || viewportHeight < 1) return null;
        int right = Math.max(6, viewportWidth - 6);
        int bottom = Math.max(6, viewportHeight - Math.max(24, inputStrip));
        int left = Math.min(6, right - width);
        int top = Math.min(6, bottom - height);
        if (right - left < width || bottom - top < height) return null;
        int[][] candidates = {{Math.round(anchor.x - width / 2), (int)anchor.y - height - 8},
                {(int)anchor.x + 12, (int)anchor.y - height / 2},
                {(int)anchor.x - width - 12, (int)anchor.y - height / 2},
                {(int)anchor.x - width - 12, (int)anchor.y + 8}};
        Rect fallback = null;
        for (int[] candidate : candidates) {
            int x = Math.max(left, Math.min(candidate[0], right - width));
            int y = Math.max(top, Math.min(candidate[1], bottom - height));
            Rect rect = new Rect(x, y, width, height);
            if (reserved != null && reserved.stream().anyMatch(rect::overlaps)) continue;
            if (fallback == null) fallback = rect;
            if (x == candidate[0] && y == candidate[1]) return rect;
        }
        return fallback;
    }
}
