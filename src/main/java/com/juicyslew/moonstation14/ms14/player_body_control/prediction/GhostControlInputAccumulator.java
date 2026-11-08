package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import java.util.Objects;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlState;

/** Pure fold of admitted full-state controls; admission/security remains the caller's responsibility. */
public final class GhostControlInputAccumulator {
    private GhostControlState held = GhostControlState.neutral();
    private long sequence;
    private boolean jumpPulse;
    private boolean received;
    private long lastConsumedTick = -1;

    public void accept(GhostControlState state, long seq) {
        Objects.requireNonNull(state);
        if (seq <= sequence) throw new IllegalArgumentException("sequence must increase");
        if (!held.jumpHeld() && state.jumpHeld()) jumpPulse = true;
        held = state; sequence = seq; received = true;
    }
    public void seed(GhostControlState state, long acknowledgedSequence) {
        if (acknowledgedSequence < 0) throw new IllegalArgumentException("sequence must be nonnegative");
        held = Objects.requireNonNull(state); sequence = acknowledgedSequence; jumpPulse = false; received = true; lastConsumedTick = -1;
    }
    public TickInput consumeForTick(long logicalTick) {
        if (logicalTick < 0) throw new IllegalArgumentException("tick must be nonnegative");
        if (logicalTick <= lastConsumedTick) return null;
        lastConsumedTick = logicalTick;
        boolean pulse = jumpPulse; jumpPulse = false;
        GhostControlState effective = pulse ? new GhostControlState(held.wishX(), held.wishZ(), held.verticalWish(),
                held.buttons() | com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.BUTTON_JUMP, held.yaw(), held.pitch()) : held;
        return new TickInput(logicalTick, held, effective, sequence, received);
    }
    public void reset() { held = GhostControlState.neutral(); sequence = 0; jumpPulse = false; received = false; lastConsumedTick = -1; }
    public GhostControlState heldState() { return held; }
    public long latestSettledSequence() { return sequence; }
    public record TickInput(long logicalTick, GhostControlState heldState, GhostControlState effectiveState, long sequence, boolean receivedInput) { }
}
