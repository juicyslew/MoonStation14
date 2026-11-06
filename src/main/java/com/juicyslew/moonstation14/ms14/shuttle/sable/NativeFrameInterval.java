package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Matrix4d;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Immutable SDK-matrix fingerprints for one body-local native pose interval. */
public final class NativeFrameInterval {
    // bakeIntoMatrix/COM rebasing performs a bounded handful of IEEE-754 operations
    // (rotation products/sums and matrix translation composition). This is numerical
    // representation slack, not a world-position or gameplay movement tolerance.
    private static final int MATRIX_ROUNDOFF_ULPS = 8;
    public record PoseValue(double[] matrix) {
        public PoseValue {
            if (matrix == null || matrix.length != 16) throw new IllegalArgumentException("pose matrix must have 16 values");
            matrix = matrix.clone();
            for (double value : matrix) if (!Double.isFinite(value)) throw new IllegalArgumentException("pose matrix must be finite");
        }
        @Override public double[] matrix() { return matrix.clone(); }
        @Override public boolean equals(Object other) { return other instanceof PoseValue p && Arrays.equals(matrix, p.matrix); }
        @Override public int hashCode() { return Arrays.hashCode(matrix); }
        @Override public String toString() { return "PoseValue[matrix=" + Arrays.toString(matrix) + "]"; }
        public static PoseValue capture(Pose3dc pose) {
            Matrix4d baked = new Pose3d(Objects.requireNonNull(pose)).bakeIntoMatrix(new Matrix4d());
            double[] values = new double[16];
            baked.get(values);
            return new PoseValue(values);
        }
    }
    public static final class PosePair {
        private final PoseValue last;
        private final PoseValue logical;
        private final double lastTranslationOperandScale;
        private final double logicalTranslationOperandScale;
        public PosePair(PoseValue last, PoseValue logical) {
            this(last, logical, matrixScale(last), matrixScale(logical));
        }
        private PosePair(PoseValue last, PoseValue logical, double lastScale, double logicalScale) {
            this.last = Objects.requireNonNull(last); this.logical = Objects.requireNonNull(logical);
            this.lastTranslationOperandScale = lastScale; this.logicalTranslationOperandScale = logicalScale;
        }
        public PoseValue last() { return last; }
        public PoseValue logical() { return logical; }
        public static PosePair capture(Pose3dc last, Pose3dc logical) {
            return new PosePair(PoseValue.capture(last), PoseValue.capture(logical), translationOperandScale(last), translationOperandScale(logical));
        }
        private static double translationOperandScale(Pose3dc pose) {
            return Math.max(1.0, Math.max(maxAbs(pose.position().x(), pose.position().y(), pose.position().z()),
                    maxAbs(pose.rotationPoint().x(), pose.rotationPoint().y(), pose.rotationPoint().z())));
        }
        private static double maxAbs(double x, double y, double z) { return Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))); }
        private static double matrixScale(PoseValue value) {
            double max = 1;
            for (double v : value.matrix) max = Math.max(max, Math.abs(v));
            return max;
        }
        @Override public boolean equals(Object other) { return other instanceof PosePair p && last.equals(p.last) && logical.equals(p.logical); }
        @Override public int hashCode() { return Objects.hash(last, logical); }
        @Override public String toString() { return "PosePair[last=" + last + ", logical=" + logical + "]"; }
    }

    private UUID frame;
    private PosePair poses;
    private long epoch;

    /** Per-controlled-body policy: true marks the interval consumed, independent of intent count. */
    public boolean consumeIfChanged(long bodyEpoch, UUID frameId, PosePair current) {
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(current, "current");
        boolean sameAuthority = epoch == bodyEpoch && frameId.equals(frame);
        boolean changed = !sameAuthority || poses == null || !pairsRoundoffEquivalent(current, poses);
        // Keep the consumed representation as the baseline on equivalent rebakes. Replacing
        // it would let arbitrarily small real changes disappear indefinitely.
        if (changed) { epoch = bodyEpoch; frame = frameId; poses = current; }
        return changed;
    }
    private static boolean pairsRoundoffEquivalent(PosePair a, PosePair b) {
        return matrixEquivalent(a.last.matrix, b.last.matrix, Math.max(a.lastTranslationOperandScale, b.lastTranslationOperandScale))
                && matrixEquivalent(a.logical.matrix, b.logical.matrix, Math.max(a.logicalTranslationOperandScale, b.logicalTranslationOperandScale));
    }
    /** SDK transform equivalence for timeline seed poses, including COM/pivot bake roundoff. */
    public static boolean roundoffEquivalent(PosePair a, PosePair b) {
        Objects.requireNonNull(a); Objects.requireNonNull(b);
        return pairsRoundoffEquivalent(a, b);
    }
    private static boolean matrixEquivalent(double[] a, double[] b, double operandScale) {
        for (int i = 0; i < a.length; i++) {
            if (Double.doubleToLongBits(a[i]) == Double.doubleToLongBits(b[i])) continue;
            // Translation (column-major indices 12..14) is a sum of position and
            // rotated pivot operands. Bound cancellation error by those operands,
            // not by ulp(result), which collapses near a zero world coordinate.
            double budget = MATRIX_ROUNDOFF_ULPS * (i >= 12 && i <= 14
                    ? Math.ulp(operandScale) : Math.max(Math.ulp(a[i]), Math.ulp(b[i])));
            if (Math.abs(a[i] - b[i]) > budget) return false;
        }
        return true;
    }
    public void reset() { frame = null; poses = null; epoch = 0; }
}
