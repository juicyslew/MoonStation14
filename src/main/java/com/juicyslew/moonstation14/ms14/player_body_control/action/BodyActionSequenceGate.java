package com.juicyslew.moonstation14.ms14.player_body_control.action;

/** Pure shared epoch, sequence, and per-tick admission state for body actions. */
public final class BodyActionSequenceGate {
    private long epoch;
    private long sequence;
    private long tick = Long.MIN_VALUE;
    private int count;

    public Result admit(long currentEpoch, long requestedEpoch, long nextSequence, long currentTick) {
        if (tick != currentTick) {
            tick = currentTick;
            count = 0;
        }
        if (count >= 8) return Result.RATE_LIMITED;
        count++;
        if (currentEpoch <= 0 || requestedEpoch != currentEpoch) return Result.WRONG_EPOCH;
        if (epoch != currentEpoch) {
            epoch = currentEpoch;
            sequence = 0;
        }
        if (nextSequence <= sequence) return Result.REPLAY;
        sequence = nextSequence;
        return Result.ADMITTED;
    }

    public enum Result { ADMITTED, WRONG_EPOCH, REPLAY, RATE_LIMITED }
}
