package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.joml.Quaterniond;

/** Bounded test-only ordinal capture for the yaw perturbation experiment. */
final class YawPerturbationObservationWindow {
    static final int ROLLING_CAP = 240;
    static final int FIRST_AFTER_SEED_CAP = 120;
    static final int TOTAL_UNIQUE_CAP = 2400;
    static final double LATE_TILT_TRIGGER_DEGREES = 1d;
    enum Result { ACCEPTED, DUPLICATE, FAILED }
    record Dump(String header, List<ShuttleFixtureMotionService.AngularControlObservation> rows) { }

    private final UUID frameId;
    private final ArrayDeque<ShuttleFixtureMotionService.AngularControlObservation> rolling = new ArrayDeque<>(ROLLING_CAP);
    private final ArrayList<ShuttleFixtureMotionService.AngularControlObservation> firstAfterSeed = new ArrayList<>(FIRST_AFTER_SEED_CAP);
    private final ArrayList<ShuttleFixtureMotionService.AngularControlObservation> lateWindow = new ArrayList<>(TOTAL_UNIQUE_CAP);
    private final Set<Long> retainedOrdinals = new HashSet<>(TOTAL_UNIQUE_CAP);
    private long previousOrdinal;
    private long previousWorldGameTime;
    private boolean hasOrdinal, complete = true, overflow, dumped;
    private boolean hasWorldTime;
    private Long seedOrdinal, lateStartOrdinal;
    private Long expectedOrdinalAtDump;
    private String failure;

    YawPerturbationObservationWindow(UUID frameId) { this.frameId = java.util.Objects.requireNonNull(frameId); }

    Result accept(ShuttleFixtureMotionService.AngularControlObservation observation) {
        if (failure != null) return Result.FAILED;
        if (observation == null || !frameId.equals(observation.frameId())) return fail("frame identity mismatch");
        long ordinal = observation.controllerSubstepOrdinal();
        if (hasOrdinal && ordinal == previousOrdinal) return Result.DUPLICATE;
        if (ordinal <= 0 || (hasOrdinal && ordinal != previousOrdinal + 1)) return fail("controller ordinal discontinuity");
        long sourceWorldTime = observation.sourceWorldGameTime();
        if (hasWorldTime && (sourceWorldTime < previousWorldGameTime || sourceWorldTime > previousWorldGameTime + 1))
            return fail("source world-clock discontinuity");
        hasOrdinal = true;
        previousOrdinal = ordinal;
        hasWorldTime = true;
        previousWorldGameTime = sourceWorldTime;
        rolling.addLast(observation);
        if (rolling.size() > ROLLING_CAP) rolling.removeFirst();
        if (seedOrdinal == null || ordinal <= seedOrdinal) return Result.ACCEPTED;

        if (firstAfterSeed.size() < FIRST_AFTER_SEED_CAP) retain(firstAfterSeed, observation);
        double tilt;
        try {
            var q = observation.nativePreOrientation();
            tilt = YawPerturbationDiagnosticPlan.upTiltDegrees(new Quaterniond(q.x(), q.y(), q.z(), q.w()));
        } catch (RuntimeException invalid) { return fail("invalid observed orientation"); }
        if (lateStartOrdinal == null && tilt >= LATE_TILT_TRIGGER_DEGREES) {
            lateStartOrdinal = ordinal;
            for (var row : rolling) retain(lateWindow, row);
        } else if (lateStartOrdinal != null) {
            retain(lateWindow, observation);
        }
        return Result.ACCEPTED;
    }

    boolean markSeed(long latestOrdinal) {
        if (seedOrdinal != null || !hasOrdinal || latestOrdinal != previousOrdinal) {
            fail("seed ordinal does not match latest captured ordinal");
            return false;
        }
        seedOrdinal = latestOrdinal;
        return true;
    }

    void markExpectedOrdinal(long expectedOrdinal) {
        expectedOrdinalAtDump = expectedOrdinal;
        if (lastOrdinal() != expectedOrdinal) fail("terminal observation ordinal does not match controller callback count");
    }

    private void retain(List<ShuttleFixtureMotionService.AngularControlObservation> target,
                        ShuttleFixtureMotionService.AngularControlObservation row) {
        long ordinal = row.controllerSubstepOrdinal();
        if (retainedOrdinals.contains(ordinal)) return;
        if (retainedOrdinals.size() >= TOTAL_UNIQUE_CAP) { overflow = true; complete = false; return; }
        retainedOrdinals.add(ordinal);
        target.add(row);
    }

    private Result fail(String reason) { failure = reason; complete = false; return Result.FAILED; }
    void reject(String reason) { if (failure == null) fail(reason); }
    String failure() { return failure; }
    long lastOrdinal() { return hasOrdinal ? previousOrdinal : 0; }
    Long seedOrdinal() { return seedOrdinal; }
    Long lateStartOrdinal() { return lateStartOrdinal; }
    int rollingRows() { return rolling.size(); }
    int firstAfterSeedRows() { return firstAfterSeed.size(); }
    int lateRows() { return lateWindow.size(); }
    int retainedRows() { return retainedOrdinals.size(); }
    boolean complete() { return complete; }
    boolean overflow() { return overflow; }
    boolean dumped() { return dumped; }

    Dump dumpOnce(String phase) {
        if (dumped) return null;
        dumped = true;
        var rows = new ArrayList<ShuttleFixtureMotionService.AngularControlObservation>(retainedOrdinals.size());
        var dumpedOrdinals = new HashSet<Long>();
        rows.addAll(firstAfterSeed);
        for (var row : firstAfterSeed) dumpedOrdinals.add(row.controllerSubstepOrdinal());
        var tail = lateStartOrdinal == null ? rolling : lateWindow;
        for (var row : tail) if (dumpedOrdinals.add(row.controllerSubstepOrdinal())) rows.add(row);
        rows.sort(Comparator.comparingLong(ShuttleFixtureMotionService.AngularControlObservation::controllerSubstepOrdinal));
        String header = "phase="+phase+" source=normal-postphysics-pre-actuation frame="+frameId+" seedOrdinal="+seedOrdinal+" lateStartOrdinal="+lateStartOrdinal
                +" lastOrdinal="+lastOrdinal()+" rollingRows="+rollingRows()+" firstAfterSeedRows="+firstAfterSeed.size()
                +" lateRows="+lateWindow.size()+" uniqueRows="+rows.size()+" complete="+complete+" overflow="+overflow
                +" expectedOrdinalAtDump="+expectedOrdinalAtDump+" ordinalCoverageAtDump="+
                (expectedOrdinalAtDump != null && expectedOrdinalAtDump == lastOrdinal())+" failure="+failure;
        return new Dump(header, List.copyOf(rows));
    }
}
