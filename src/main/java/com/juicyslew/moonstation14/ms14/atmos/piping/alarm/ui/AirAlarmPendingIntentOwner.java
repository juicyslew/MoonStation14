package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import java.util.UUID;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode;

/** Per-screen authority for a setting intent; enqueue acknowledgements never count as application. */
public final class AirAlarmPendingIntentOwner {
    public enum Outcome { IDLE, PENDING, SUCCEEDED, REJECTED, CONFLICT, TIMED_OUT }
    private UUID device;
    private long baseRevision;
    private long deadlineTick;
    private long requestId;
    private AirAlarmControlRequest.Operation operation;
    private boolean desired;
    private int index;
    private double numeric;
    private Outcome outcome = Outcome.IDLE;

    public void begin(AirAlarmControlRequest request, long now, long timeoutTicks) {
        if (request == null || request.operation() == AirAlarmControlRequest.Operation.QUERY || timeoutTicks <= 0)
            throw new IllegalArgumentException("Expected bounded setting intent");
        device = request.deviceId(); requestId = request.requestId(); baseRevision = request.expectedRevision(); deadlineTick = now + timeoutTicks;
        operation = request.operation(); desired = request.desired(); index = request.enumIndex(); numeric = request.numericValue(); outcome = Outcome.PENDING;
    }
    public UUID device() { return outcome == Outcome.PENDING ? device : null; }
    public boolean pending() { return outcome == Outcome.PENDING; }
    /** Intended enabled state for presentation; this does not mutate authority. */
    public Boolean intendedEnabled(UUID target) {
        return pending() && operation == AirAlarmControlRequest.Operation.SET_ENABLED && device.equals(target) ? desired : null;
    }
    /** Shared admission and cadence policy for current and future settings views. */
    public boolean admitsSetting() { return !pending(); }
    public boolean shouldPoll(long tick, long cadence) {
        return pending() && cadence > 0 && tick > 0 && tick % cadence == 0 && tick + cadence < deadlineTick;
    }
    public Outcome outcome() { return outcome; }
    public Outcome acknowledge(boolean accepted) { if (pending() && !accepted) outcome = Outcome.REJECTED; return outcome; }
    public Outcome observe(AirAlarmControlSnapshot snapshot, long now) {
        if (!pending()) return outcome;
        if (now >= deadlineTick) { outcome = Outcome.TIMED_OUT; return outcome; }
        if (snapshot == null || !snapshot.accepted()) return outcome;
        if (operation == AirAlarmControlRequest.Operation.SET_ALARM_MODE || operation == AirAlarmControlRequest.Operation.SET_AUTO_MODE) {
            if (snapshot.controllerStateRevision() <= baseRevision) return outcome;
            boolean matches = operation == AirAlarmControlRequest.Operation.SET_AUTO_MODE
                    ? snapshot.autoMode() == desired : snapshot.currentMode() == AirAlarmMode.values()[index];
            if (!matches && snapshot.requestId() != requestId) return outcome;
            outcome = matches ? Outcome.SUCCEEDED : Outcome.CONFLICT;
            return outcome;
        }
        var d = snapshot.devices().stream().filter(x -> x.id().equals(device)).findFirst().orElse(null);
        if (d == null || d.revision() <= baseRevision) return outcome;
        boolean matches = switch (operation) {
            case SET_ENABLED -> d.enabled() == desired;
            case SET_SCRUBBER_MODE -> d.mode() == index;
            case SET_FILTER_GAS -> ((d.filterBits() & (1 << index)) != 0) == desired;
            case SET_EXTERNAL_PRESSURE_BOUND -> Double.compare(d.externalPressureBoundKPa(), numeric) == 0;
            case SET_VOLUME_RATE -> Double.compare(d.volumeRateM3PerSecond(), numeric) == 0;
            default -> false;
        };
        // An enabled-value mismatch in an unsolicited/earlier snapshot is not proof
        // that this request was rejected. Retain the presentation until the matching
        // acknowledgement rejects it, matching authority arrives, or it times out.
        if (operation == AirAlarmControlRequest.Operation.SET_ENABLED && !matches
                && snapshot.requestId() != requestId) return outcome;
        outcome = matches ? Outcome.SUCCEEDED : Outcome.CONFLICT;
        return outcome;
    }
}
