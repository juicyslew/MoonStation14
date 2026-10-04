package com.juicyslew.moonstation14.ms14.disposal.domain;

import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import java.util.Objects;

/** Tick-clocked unit scheduling state, independent of item storage, power blocks and gas transactions. */
public final class DisposalUnitTimingKernel {
    public enum State { READY, PRESSURIZING, FLUSHED }
    public enum FlushResult { NONE, FLUSH_REQUESTED, MISSING_ENTRY_CANCELLED }
    /** Runtime save boundary; payload ownership remains with the unit/holder adapter. */
    public record StateSnapshot(long logicalTick, boolean engaged, boolean powered, boolean anchored,
                                long pressureDeadlineTick, Long nextFlushTick, long visualDeadlineTick,
                                Long lastExitAttemptTick) { }
    private final DisposalEntityData.Unit config;
    private final long flushDelay, recharge;
    private long now, nextPressurized, visualDeadline;
    private Long lastExitAttempt;
    private Long nextFlush;
    private int contents;
    private boolean powered = true, anchored = true, engaged;
    private State storedState = State.READY;

    public DisposalUnitTimingKernel(DisposalEntityData.Unit config, long initialTick) {
        this.config = config; now = initialTick;
        flushDelay = ticks(config.flushedVisualSeconds()); recharge = ticks(config.rechargeSeconds());
        nextPressurized = initialTick;
        visualDeadline = initialTick;
    }
    public static long ticks(double seconds) { return Math.round(seconds * 20.0); }
    public void insert(int count) {
        if (count < 0) throw new IllegalArgumentException("negative insertion");
        contents += count;
        if (count > 0) scheduleAutomatic();
    }
    public boolean exitAttemptAllowed() {
        if (lastExitAttempt != null && now < lastExitAttempt + ticks(config.exitAttemptCooldownSeconds())) return false;
        lastExitAttempt = now; return true;
    }
    public void manualEngage() {
        engaged = true;
        if (!canFlush()) return;
        scheduleEarlier(now + ticks(config.manualFlushSeconds()));
    }
    public void disengage() { engaged = false; if (contents == 0) nextFlush = null; }
    public void setPowered(boolean value) {
        if (powered == value) return;
        powered = value;
        if (!powered) nextFlush = null;
        else if (engaged) manualEngage();
    }
    public void setAnchored(boolean value) { anchored = value; }

    /** Mirrors SharedDisposalUnitSystem.Update: while charging, only state changes; otherwise timed flush precedes ready transition. */
    public FlushResult advanceTo(long tick, boolean sameCellEntryTrunkPresent) {
        if (tick < now) throw new IllegalArgumentException("game tick must be monotonic");
        now = tick;
        State observed = state();
        if (nextPressurized > now) { updateState(observed); return FlushResult.NONE; }
        FlushResult result = FlushResult.NONE;
        if (nextFlush != null && nextFlush < now && canFlush()) {
            // SS14 advances the next deadline before checking for an entry trunk.
            nextFlush = nextFlush + ticks(config.automaticEngageSeconds());
            if (!sameCellEntryTrunkPresent) {
                engaged = false;
                return FlushResult.MISSING_ENTRY_CANCELLED;
            }
            contents = 0; // ownership transfer to the adapter/holder is requested, not gas sampled here.
            nextPressurized = now + recharge;
            visualDeadline = now + flushDelay;
            engaged = false; nextFlush = null;
            return FlushResult.FLUSH_REQUESTED;
        }
        updateState(observed);
        return result;
    }
    private void updateState(State next) {
        if (storedState == next) return;
        storedState = next;
        if (next == State.READY) {
            nextPressurized = now;
            if (engaged) nextFlush = now + ticks(config.manualFlushSeconds());
            else if (contents > 0) nextFlush = now + ticks(config.automaticEngageSeconds());
            else nextFlush = null;
        }
    }
    private boolean canFlush() { return anchored && powered && state() == State.READY; }
    private State state() {
        if (now < visualDeadline) return State.FLUSHED;
        if (nextPressurized > now) return State.PRESSURIZING;
        return State.READY;
    }
    private void scheduleAutomatic() {
        if (!powered && contents == 0) return;
        scheduleEarlier(now + ticks(config.automaticEngageSeconds()));
    }
    private void scheduleEarlier(long requested) { nextFlush = nextFlush == null ? requested : Math.min(nextFlush, requested); }
    public State currentState() { return state(); }
    public int contents() { return contents; }
    public boolean engaged() { return engaged; }
    public Long nextFlushTick() { return nextFlush; }
    public long nextPressurizedTick() { return nextPressurized; }
    public long nowTick() { return now; }
    public StateSnapshot snapshot() {
        return new StateSnapshot(now, engaged, powered, anchored, nextPressurized, nextFlush, visualDeadline, lastExitAttempt);
    }

    /** Restores only after validating the complete snapshot; no partially-restored instance escapes on failure. */
    public static DisposalUnitTimingKernel restore(DisposalEntityData.Unit config, StateSnapshot snapshot) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.logicalTick() < 0 || snapshot.pressureDeadlineTick() < 0 || snapshot.visualDeadlineTick() < 0
                || snapshot.nextFlushTick() != null && snapshot.nextFlushTick() < 0
                || snapshot.lastExitAttemptTick() != null && (snapshot.lastExitAttemptTick() < 0
                || snapshot.lastExitAttemptTick() > snapshot.logicalTick()))
            throw new IllegalArgumentException("invalid disposal unit timing snapshot");
        DisposalUnitTimingKernel restored = new DisposalUnitTimingKernel(config, snapshot.logicalTick());
        restored.engaged = snapshot.engaged();
        restored.powered = snapshot.powered();
        restored.anchored = snapshot.anchored();
        restored.nextPressurized = snapshot.pressureDeadlineTick();
        restored.nextFlush = snapshot.nextFlushTick();
        restored.visualDeadline = snapshot.visualDeadlineTick();
        restored.lastExitAttempt = snapshot.lastExitAttemptTick();
        restored.storedState = restored.state();
        return restored;
    }
}
