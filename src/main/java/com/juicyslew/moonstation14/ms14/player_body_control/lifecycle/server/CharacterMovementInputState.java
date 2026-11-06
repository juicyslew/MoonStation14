package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;

/**
 * Reduces ordered CHARACTER full-tick input states to the held state and a pending jump edge.
 * SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91 keeps InputMoverComponent.HeldMoveButtons across
 * state changes and SharedMoverController.Input consumes them during movement. Unlike SS14's state
 * change messages, this Minecraft port sends one full-tick intent per client prediction step, so the
 * server FIFO reduces all currently admitted states in order before one normal physical step;
 * a pending press latch preserves a press/release tap in that batch without replaying physics steps.
 */
final class CharacterMovementInputState {
    private GhostControlPayloads.Intent held;
    private boolean pendingJumpPress;

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
        boolean jumpDown = (next.buttons() & GhostControlPayloads.BUTTON_JUMP) != 0;
        boolean wasJumpDown = held != null && (held.buttons() & GhostControlPayloads.BUTTON_JUMP) != 0;
        if (jumpDown && !wasJumpDown) pendingJumpPress = true;
        held = next;
    }

    GhostControlPayloads.Intent held() { return held; }
    boolean hasInput() { return held != null; }
    boolean jumpForStep() {
        return held != null && ((held.buttons() & GhostControlPayloads.BUTTON_JUMP) != 0 || pendingJumpPress);
    }
    void afterStep() { pendingJumpPress = false; }
    void clear() { held = null; pendingJumpPress = false; }
}
