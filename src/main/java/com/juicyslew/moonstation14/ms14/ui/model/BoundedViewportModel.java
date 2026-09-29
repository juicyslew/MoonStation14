package com.juicyslew.moonstation14.ms14.ui.model;

import java.util.List;

/** Bounded points for graphs/maps with clamped local pan/zoom; source data is authoritative. */
public final class BoundedViewportModel {
    public record Point(String id, double x, double y) implements ModelBounds.Identified {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("non-finite point");
        }
    }

    private final int cap;
    private final double width;
    private final double height;
    private List<Point> points = List.of();
    private long revision = -1;
    private double centerX;
    private double centerY;
    private double zoom = 1;

    public BoundedViewportModel(int cap, double width, double height) {
        ModelBounds.size(0, cap);
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("invalid viewport bounds");
        this.cap = cap;
        this.width = width;
        this.height = height;
        centerX = width / 2;
        centerY = height / 2;
    }

    public void snapshot(long revision, List<Point> points) {
        if (revision <= this.revision) return;
        ModelBounds.ids(points, cap);
        for (Point point : points) {
            if (point.x() < 0 || point.x() > width || point.y() < 0 || point.y() > height)
                throw new IllegalArgumentException("point outside bounds");
        }
        this.points = List.copyOf(points);
        this.revision = revision;
    }

    public void view(double x, double y, double zoom) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(zoom)) return;
        this.centerX = Math.max(0, Math.min(width, x));
        this.centerY = Math.max(0, Math.min(height, y));
        this.zoom = Math.max(1, Math.min(16, zoom));
    }

    public boolean visible(Point point) {
        return point.x() >= centerX - width / (2 * zoom) && point.x() <= centerX + width / (2 * zoom)
                && point.y() >= centerY - height / (2 * zoom) && point.y() <= centerY + height / (2 * zoom);
    }

    public int size() { return points.size(); }
    public Point at(int index) { return points.get(index); }
    public double centerX() { return centerX; }
    public double centerY() { return centerY; }
    public double zoom() { return zoom; }
    public long revision() { return revision; }
}
