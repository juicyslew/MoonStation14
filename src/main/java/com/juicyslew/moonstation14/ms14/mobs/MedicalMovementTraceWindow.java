package com.juicyslew.moonstation14.ms14.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded medical movement samples; scope is exact level and body object identity. */
public final class MedicalMovementTraceWindow {
    public static final int MAX_ROWS = 160;
    private final Object level;
    private final Object body;
    private final List<Row> rows = new ArrayList<>(MAX_ROWS);
    private boolean closed;
    private boolean truncated;

    public MedicalMovementTraceWindow(Object level, Object body) {
        this.level = Objects.requireNonNull(level);
        this.body = Objects.requireNonNull(body);
    }

    public boolean matches(Object candidateLevel, Object candidateBody) {
        return level == candidateLevel && body == candidateBody;
    }

    /** Invalid numeric samples are retained and explicitly marked, never normalized to zero. */
    public boolean append(long serverTick, long worldTick, Point position, Point velocity, String details) {
        if (closed || rows.size() == MAX_ROWS) { truncated = true; return false; }
        boolean finite = position != null && velocity != null && position.finite() && velocity.finite();
        rows.add(new Row(serverTick, worldTick, position, velocity,
                details == null ? "details=null" : details, finite));
        return true;
    }

    public Snapshot close(String stopReason) {
        closed = true;
        return new Snapshot(List.copyOf(rows), stopReason == null ? "unknown" : stopReason,
                rows.size(), rows.isEmpty() ? null : rows.get(0).worldTick,
                rows.isEmpty() ? null : rows.get(rows.size() - 1).worldTick, truncated);
    }

    public boolean closed() { return closed; }
    public int size() { return rows.size(); }
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public String text() { return Double.toString(x) + ',' + y + ',' + z; }
    }
    public record Row(long serverTick, long worldTick, Point position, Point velocity,
                      String details, boolean finite) { }
    public record Snapshot(List<Row> rows, String stopReason, int rowCount,
                           Long firstWorldTick, Long lastWorldTick, boolean truncated) { }
}
