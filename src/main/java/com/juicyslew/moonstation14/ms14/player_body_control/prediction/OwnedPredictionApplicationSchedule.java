package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

/** One-shot client-tick authority application request for locally owned prediction. */
public final class OwnedPredictionApplicationSchedule {
    public enum Work { NONE, APPLY_AUTHORITY_ONLY, APPLY_AND_ALLOW_REPLAY }
    private Work pending = Work.NONE;
    private com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot snapshot;

    public record TickProgress(long consumedThrough, long presentedThrough, int completed) { }

    /** One shared planned-tick dispatch for replay reconstruction and incremental advancement. */
    public TickProgress dispatchTicks(
            java.util.List<com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostControlInputAccumulator.TickInput> ticks,
            long consumedThrough, long presentedThrough,
            boolean animationAllowed,
            java.util.function.BiPredicate<com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostControlInputAccumulator.TickInput, Boolean> apply) {
        long consumed = consumedThrough;
        long presented = presentedThrough;
        int completed = 0;
        int newestNew = -1;
        for (int i = 0; i < ticks.size(); i++) if (ticks.get(i).logicalTick() > consumedThrough) newestNew = i;
        for (int i = 0; i < ticks.size(); i++) {
            var tick = ticks.get(i);
            if (tick.logicalTick() <= consumed) continue;
            boolean animate = animationAllowed && i == newestNew && tick.logicalTick() > presented;
            if (!apply.test(tick, animate)) break;
            consumed = tick.logicalTick();
            if (animate) presented = consumed;
            completed++;
        }
        return new TickProgress(consumed, presented, completed);
    }

    /** Retains only a newer packet for the exact currently owned identity. */
    public boolean queue(com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot incoming,
                         long epoch, int entityId,
                         com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind kind) {
        if (incoming == null || incoming.epoch() != epoch || incoming.harnessEntityId() != entityId
                || incoming.harnessKind() != kind) return false;
        if (snapshot != null && (snapshot.epoch() != epoch || snapshot.harnessEntityId() != entityId
                || snapshot.harnessKind() != kind || incoming.serverGameTick() <= snapshot.serverGameTick())) return false;
        snapshot = incoming;
        return true;
    }

    public com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot takeSnapshot() {
        var result = snapshot;
        snapshot = null;
        return result;
    }

    /** Shared one-shot dispatch seam used by the client consumer and focused production-path tests. */
    public boolean dispatchQueued(java.util.function.Consumer<com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot> consumer) {
        var next = takeSnapshot();
        if (next == null) return false;
        consumer.accept(next);
        return true;
    }

    public void defer(boolean replayAllowed, boolean predictionStopped) {
        pending = replayAllowed && !predictionStopped ? Work.APPLY_AND_ALLOW_REPLAY : Work.APPLY_AUTHORITY_ONLY;
    }

    public Work take() {
        Work result = pending;
        pending = Work.NONE;
        return result;
    }

    public void clear() { pending = Work.NONE; snapshot = null; }
}
