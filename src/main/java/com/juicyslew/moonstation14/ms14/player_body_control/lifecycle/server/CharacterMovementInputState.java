package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostControlInputAccumulator;

/**
 * Reduces ordered CHARACTER full-tick input states to the held state and a pending jump edge.
 * SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91 keeps InputMoverComponent.HeldMoveButtons across
 * state changes and SharedMoverController.Input consumes them during movement. Unlike SS14's state
 * change messages, this Minecraft port sends one full-tick intent per client prediction step, so the
 * server FIFO reduces all currently admitted states in order before one normal physical step.
 * The shared accumulator owns held controls and jump-edge state; this adapter retains the exact
 * protocol intent metadata (including supported movement reference) for world-step dispatch.
 */
final class CharacterMovementInputState {
    private final GhostControlInputAccumulator controls = new GhostControlInputAccumulator();
    private GhostControlPayloads.Intent held;

    /** Reduces the gate's complete admitted prefix and returns its highest processed command. */
    GhostControlPayloads.Intent acceptQueued(com.juicyslew.moonstation14.ms14.player_body_control.server.GhostIntentGate<GhostControlPayloads.Intent> gate) {
        GhostControlPayloads.Intent latest = null;
        GhostControlPayloads.Intent next;
        while ((next = gate.takeQueued()) != null) {
            accept(next);
            latest = next;
        }
        return latest;
    }

    void accept(GhostControlPayloads.Intent next) {
        if (next == null) throw new NullPointerException("next");
        controls.accept(next.toControlState(), next.sequence());
        held = next;
    }

    GhostControlPayloads.Intent held() { return held; }
    boolean hasInput() { return held != null; }
    GhostControlInputAccumulator.TickInput consumeForTick(long tick) { return controls.consumeForTick(tick); }
    com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlState heldState() { return controls.heldState(); }
    boolean jumpForStep(GhostControlInputAccumulator.TickInput sampled) {
        return sampled != null && (sampled.effectiveState().buttons() & GhostControlPayloads.BUTTON_JUMP) != 0;
    }
    void clear() { held = null; controls.reset(); }
}
