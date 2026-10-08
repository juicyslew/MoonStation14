package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;

/** Pure medical-state gates for client-owned movement prediction. */
public final class MedicalPredictionPolicy {
    private MedicalPredictionPolicy() { }

    public record Decision(boolean suspended, boolean authorityBarrierRequired,
                           boolean recordHistory, boolean ownsTracker, boolean replaySnapshot) { }

    public static Decision decide(MobHarnessKind kind, MobStateComponent.State state,
                                  boolean wasSuspended, boolean historyBarrierApplied) {
        return decide(kind, state, wasSuspended, historyBarrierApplied, false);
    }

    public static Decision decide(MobHarnessKind kind, MobStateComponent.State state,
                                  boolean wasSuspended, boolean historyBarrierApplied, boolean externallyMoved) {
        boolean suspended = externallyMoved || kind == MobHarnessKind.CHARACTER
                && (state == MobStateComponent.State.CRITICAL || state == MobStateComponent.State.DEAD);
        // On recovery, clear any pre-incapacitation history before tracker ownership or replay resumes.
        boolean recovering = kind == MobHarnessKind.CHARACTER && wasSuspended && !suspended;
        boolean barrier = kind == MobHarnessKind.CHARACTER && (suspended && !wasSuspended || recovering);
        boolean historySafe = !recovering || historyBarrierApplied;
        boolean permitted = !suspended && historySafe;
        return new Decision(suspended, barrier, permitted, permitted, permitted);
    }
}
