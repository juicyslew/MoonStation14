package com.juicyslew.moonstation14.ms14.player_body_control.client;

/** Session/revision admission shared by snapshot reconciliation and focused policy tests. */
public final class CharacterMovementRebasePolicy {
    private CharacterMovementRebasePolicy() { }

    public static Decision decide(long sessionRevision, long incomingRevision, boolean predictionStopped) {
        if (incomingRevision < sessionRevision) return Decision.REJECT;
        if (incomingRevision > sessionRevision) return Decision.REBASE;
        return predictionStopped ? Decision.BARRIER : Decision.NORMAL;
    }

    /** A stopped predictor resumes only from a newly accepted, known finite authority sample settling its failure. */
    public static Decision decide(long sessionRevision, long incomingRevision, boolean predictionStopped,
                                 long failedSequence, long acknowledgedSequence,
                                 boolean acceptedFreshSnapshot, boolean knownFiniteAuthority) {
        Decision revision = decide(sessionRevision, incomingRevision, predictionStopped);
        if (revision == Decision.REJECT || !acceptedFreshSnapshot || !knownFiniteAuthority) return Decision.REJECT;
        if (revision == Decision.REBASE) return Decision.REBASE;
        if (predictionStopped && failedSequence > 0 && acknowledgedSequence >= failedSequence)
            return Decision.RESUME_BARRIER;
        return revision;
    }

    public enum Decision { REJECT, REBASE, RESUME_BARRIER, BARRIER, NORMAL }
}
