package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

/** One-shot client-tick authority application request for locally owned prediction. */
public final class OwnedPredictionApplicationSchedule {
    public enum Work { NONE, APPLY_AUTHORITY_ONLY, APPLY_AND_ALLOW_REPLAY }
    private Work pending = Work.NONE;

    public void defer(boolean replayAllowed, boolean predictionStopped) {
        pending = replayAllowed && !predictionStopped ? Work.APPLY_AND_ALLOW_REPLAY : Work.APPLY_AUTHORITY_ONLY;
    }

    public Work take() {
        Work result = pending;
        pending = Work.NONE;
        return result;
    }

    public void clear() { pending = Work.NONE; }
}
