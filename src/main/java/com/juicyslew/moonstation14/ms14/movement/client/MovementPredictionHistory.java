package com.juicyslew.moonstation14.ms14.movement.client;

/**
 * Bounded end-of-tick position history used to preserve pending displacement without replaying movement.
 * The fixed 64-entry ring bounds retained memory and acknowledgement scans; this is not a measured budget.
 */
final class MovementPredictionHistory {
    static final int CAPACITY = 64;

    private final Entry[] entries = new Entry[CAPACITY];
    private int start;
    private int size;
    private long lastRecordedSequence;

    record Position(double x, double y, double z) {
        Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("position must be finite");
        }

        Position plus(Position other) { return new Position(x + other.x, y + other.y, z + other.z); }
        Position minus(Position other) { return new Position(x - other.x, y - other.y, z - other.z); }
        double length() { return Math.sqrt(x * x + y * y + z * z); }
    }

    record Entry(long sequence, Position position, boolean onGround) { }
    record Acknowledgement(Entry predictedEnd, boolean hasPending) { }

    /** Add only after the corresponding client motor tick completed successfully. */
    void record(long sequence, Position position, boolean onGround) {
        if (sequence <= 0) throw new IllegalArgumentException("sequence must be positive");
        if (sequence <= lastRecordedSequence)
            throw new IllegalArgumentException("prediction sequence must increase");
        if (size == CAPACITY) {
            entries[start] = null;
            start = (start + 1) % CAPACITY;
            size--;
        }
        entries[(start + size) % CAPACITY] = new Entry(sequence, position, onGround);
        size++;
        lastRecordedSequence = sequence;
    }

    /** Finds the acknowledged end state and removes all acknowledged (or older) samples. */
    Acknowledgement acknowledge(long sequence) {
        Entry matched = null;
        for (int i = 0; i < size; i++) {
            Entry entry = entries[(start + i) % CAPACITY];
            if (entry.sequence() == sequence) matched = entry;
            if (entry.sequence() > sequence) break;
        }
        while (size > 0 && entries[start].sequence() <= sequence) {
            entries[start] = null;
            start = (start + 1) % CAPACITY;
            size--;
        }
        return new Acknowledgement(matched, size > 0);
    }

    /** Return a bounded projected position, or null to require an authoritative snap. */
    static Position project(Position authoritative, Position current, boolean serverGround,
                            Entry acknowledged, boolean currentGround, boolean hasPending,
                            double maximumCorrection) {
        if (acknowledged == null || !hasPending || !serverGround || !acknowledged.onGround() || !currentGround)
            return null;
        Position correction = authoritative.minus(acknowledged.position());
        double tolerance = precisionTolerance(authoritative, acknowledged.position(), current);
        if (correction.length() > maximumCorrection + tolerance) return null;
        return authoritative.plus(current.minus(acknowledged.position()));
    }

    static boolean effectivelyEqual(Position first, Position second) {
        double tolerance = precisionTolerance(first, second);
        return Math.abs(first.x() - second.x()) <= tolerance
                && Math.abs(first.y() - second.y()) <= tolerance
                && Math.abs(first.z() - second.z()) <= tolerance;
    }

    int size() { return size; }

    void clear() {
        for (int i = 0; i < size; i++) entries[(start + i) % CAPACITY] = null;
        start = 0;
        size = 0;
        lastRecordedSequence = 0;
    }

    private static double precisionTolerance(Position... positions) {
        double tolerance = 0d;
        for (Position position : positions) {
            tolerance = Math.max(tolerance, Math.ulp(position.x()));
            tolerance = Math.max(tolerance, Math.ulp(position.y()));
            tolerance = Math.max(tolerance, Math.ulp(position.z()));
        }
        return tolerance * 4d;
    }
}
