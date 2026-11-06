package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Bounded client-side history of authoritative Sable frame snapshots (40 ticks at 20Hz). */
public final class AuthoritativeFrameTimeline {
    public static final int WINDOW_TICKS = 40;
    private final NavigableMap<Long, Sample> samples = new TreeMap<>();
    private UUID levelIdentity;
    private UUID frameId;
    private long newest = Long.MIN_VALUE;
    private boolean stopped;
    private boolean heldStatic;
    private long stoppedAt = Long.MIN_VALUE;
    private long revision;

    public enum Kind { EXACT, INTERPOLATED, PREDICTED, MISSING }

    public record Sample(long tick, Pose3d pose, Vec3 linearVelocity, Vec3 angularVelocity, boolean motionKnown) {
        public Sample(long tick, Pose3d pose, Vec3 linearVelocity, Vec3 angularVelocity) {
            this(tick, pose, linearVelocity, angularVelocity, true);
        }
        public Sample {
            pose = new Pose3d(Objects.requireNonNull(pose));
            linearVelocity = finite(Objects.requireNonNull(linearVelocity));
            angularVelocity = finite(Objects.requireNonNull(angularVelocity));
            validatePose(pose);
        }
        @Override public Pose3d pose() { return new Pose3d(pose); }
    }

    public record Result(Kind kind, Pose3d pose, Vec3 linearVelocity, Vec3 angularVelocity,
                         long sourceBefore, long sourceAfter, long ageTicks, boolean motionKnown) {
        public Result { pose = pose == null ? null : new Pose3d(pose); }
        @Override public Pose3d pose() { return pose == null ? null : new Pose3d(pose); }
        static Result missing() { return new Result(Kind.MISSING, null, Vec3.ZERO, Vec3.ZERO, -1, -1, -1, false); }
    }

    /** Establishes a distinct level/frame lifetime; old-world history can never leak across it. */
    public synchronized void bind(UUID level, UUID frame) {
        Objects.requireNonNull(level); Objects.requireNonNull(frame);
        if (!level.equals(levelIdentity) || !frame.equals(frameId)) {
            samples.clear(); newest = Long.MIN_VALUE; stopped = false; heldStatic = false; stoppedAt = Long.MIN_VALUE;
            revision++;
            levelIdentity = level; frameId = frame;
        }
    }

    /** Duplicate packet ticks are ignored; conflicting duplicates retain the first valid authority sample. */
    public synchronized boolean receive(UUID level, UUID frame, long tick, Pose3dc pose,
                                        Vec3 linear, Vec3 angular) {
        return receive(level, frame, tick, pose, linear, angular, true);
    }

    public synchronized boolean receive(UUID level, UUID frame, long tick, Pose3dc pose,
                                        Vec3 linear, Vec3 angular, boolean motionKnown) {
        Objects.requireNonNull(pose); Objects.requireNonNull(linear); Objects.requireNonNull(angular);
        bind(level, frame);
        if (tick < 0 || samples.containsKey(tick) || (stopped && tick <= stoppedAt)
                || (heldStatic && tick <= newest)) return false;
        if (tick > newest) { stopped = false; heldStatic = false; }
        Sample value = new Sample(tick, new Pose3d(pose), linear, angular, motionKnown);
        samples.put(tick, value);
        revision++;
        newest = Math.max(newest, tick);
        prune();
        return true;
    }

    /** Seeds the two server-authored start-tracking endpoints; moving seed velocities remain explicitly unknown. */
    public synchronized boolean seed(UUID level, UUID frame, long tick, Pose3dc lastPose, Pose3dc pose) {
        Objects.requireNonNull(lastPose); Objects.requireNonNull(pose); bind(level, frame);
        if (newest != Long.MIN_VALUE || tick < 0) return false;
        var lastPair = NativeFrameInterval.PosePair.capture(lastPose, lastPose);
        var currentPair = NativeFrameInterval.PosePair.capture(pose, pose);
        boolean stationary = NativeFrameInterval.roundoffEquivalent(lastPair, currentPair);
        if (tick > 0) samples.put(tick - 1, new Sample(tick - 1, new Pose3d(lastPose), Vec3.ZERO, Vec3.ZERO, stationary));
        samples.put(tick, new Sample(tick, new Pose3d(pose), Vec3.ZERO, Vec3.ZERO, stationary));
        newest = tick;
        revision++;
        heldStatic = stationary;
        prune();
        return true;
    }

    /** Stop packets invalidate velocity without fabricating a timestamped pose. */
    public synchronized void stop(UUID level, UUID frame, long knownTick) {
        bind(level, frame);
        if (knownTick < 0 || knownTick < newest) return;
        stopped = true; heldStatic = true; stoppedAt = knownTick; revision++;
    }

    public synchronized void remove(UUID level, UUID frame) {
        if (Objects.equals(levelIdentity, level) && Objects.equals(frameId, frame)) clear();
    }
    public synchronized void clear() { samples.clear(); newest = Long.MIN_VALUE; stopped = false; heldStatic = false; stoppedAt = Long.MIN_VALUE; levelIdentity = null; frameId = null; revision++; }
    public synchronized int size() { return samples.size(); }
    public synchronized long newestTick() { return newest; }
    public synchronized long revision() { return revision; }

    public synchronized Result sample(UUID frame, long tick) {
        if (!Objects.equals(frameId, frame) || samples.isEmpty() || tick < 0
                || tick < newest - WINDOW_TICKS || (!heldStatic && tick > newest + WINDOW_TICKS)) return Result.missing();
        Sample exact = samples.get(tick);
        if (exact != null) return result(Kind.EXACT, exact.pose(), exact.linearVelocity(), exact.angularVelocity(), tick, tick, 0, exact.motionKnown());
        var lower = samples.floorEntry(tick); var upper = samples.ceilingEntry(tick);
        if (lower != null && upper != null) {
            long span = upper.getKey() - lower.getKey();
            if (span <= 0 || span > WINDOW_TICKS) return Result.missing();
            double alpha = (double)(tick - lower.getKey()) / span;
            Pose3d pose = lower.getValue().pose().lerp(upper.getValue().pose(), alpha);
            Sample a = lower.getValue(), b = upper.getValue();
            return result(Kind.INTERPOLATED, pose, lerp(a.linearVelocity(), b.linearVelocity(), alpha), lerp(a.angularVelocity(), b.angularVelocity(), alpha), a.tick(), b.tick(), Math.min(tick-a.tick(), b.tick()-tick), a.motionKnown() && b.motionKnown());
        }
        if (upper == null && lower != null && tick > lower.getKey()) {
            if (heldStatic) return result(Kind.PREDICTED, lower.getValue().pose(), Vec3.ZERO, Vec3.ZERO, lower.getKey(), lower.getKey(), tick-lower.getKey(), true);
            if (tick-lower.getKey() <= WINDOW_TICKS && lower.getValue().motionKnown()) return predict(lower.getValue(), tick);
        }
        return Result.missing();
    }

    /** Resolves both endpoints using this same authoritative history, for an owned native interval. */
    public NativeFrameInterval.PosePair sampleInterval(UUID frame, long from, long to) {
        Result a = sample(frame, from), b = sample(frame, to);
        return a.kind() == Kind.MISSING || b.kind() == Kind.MISSING ? null : NativeFrameInterval.PosePair.capture(a.pose(), b.pose());
    }

    private Result predict(Sample s, long tick) {
        double seconds = (tick - s.tick()) / 20.0;
        Pose3d p = s.pose();
        p.position().add(s.linearVelocity().x * seconds, s.linearVelocity().y * seconds, s.linearVelocity().z * seconds);
        Vector3d omega = new Vector3d(s.angularVelocity().x, s.angularVelocity().y, s.angularVelocity().z);
        double angle = omega.length() * seconds;
        if (angle != 0) {
            omega.normalize();
            Quaterniond orientation = new Quaterniond().rotationAxis(angle, omega.x, omega.y, omega.z)
                    .mul(p.orientation());
            p.orientation().set(orientation);
        }
        return result(Kind.PREDICTED, p, s.linearVelocity(), s.angularVelocity(), s.tick(), s.tick(), tick-s.tick(), true);
    }
    private void prune() { long cutoff = newest - WINDOW_TICKS; var prior = samples.lowerEntry(cutoff); samples.headMap(cutoff, false).clear(); if (prior != null) samples.put(prior.getKey(), prior.getValue()); while (samples.size() > WINDOW_TICKS + 2) samples.pollFirstEntry(); }
    private static Result result(Kind k, Pose3dc p, Vec3 v, Vec3 w, long a, long b, long age) { return result(k,p,v,w,a,b,age,true); }
    private static Result result(Kind k, Pose3dc p, Vec3 v, Vec3 w, long a, long b, long age, boolean motionKnown) { return new Result(k, new Pose3d(p), v, w, a, b, age, motionKnown); }
    private static Vec3 lerp(Vec3 a, Vec3 b, double t) { return a.lerp(b, t); }
    private static Vec3 finite(Vec3 v) { if (!Double.isFinite(v.x)||!Double.isFinite(v.y)||!Double.isFinite(v.z)) throw new IllegalArgumentException("non-finite velocity"); return v; }
    private static void validatePose(Pose3dc p) {
        var v = p.position(); var q = p.orientation(); var r = p.rotationPoint(); var s = p.scale();
        if (!Double.isFinite(v.x())||!Double.isFinite(v.y())||!Double.isFinite(v.z())||!Double.isFinite(r.x())||!Double.isFinite(r.y())||!Double.isFinite(r.z())||!Double.isFinite(s.x())||!Double.isFinite(s.y())||!Double.isFinite(s.z())||!Double.isFinite(q.x())||!Double.isFinite(q.y())||!Double.isFinite(q.z())||!Double.isFinite(q.w())||Math.abs(s.x()-s.y())>1e-9||Math.abs(s.x()-s.z())>1e-9) throw new IllegalArgumentException("invalid or unsupported frame pose");
    }
}
