package com.juicyslew.moonstation14.ms14.hands.quarantine;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure, immutable protocol state for account-owned Creative vanilla inventory quarantine.
 * This model records access-authorization intentions only; it does not implement quarantine,
 * persist data, or mutate an inventory.
 */
public final class QuarantineState {
    public enum Phase {
        CREATIVE_AVAILABLE,
        PREPARING,
        PARKED,
        RESTORING,
        RECOVERY_REQUIRED
    }

    public enum Intent {
        PARK,
        RESTORE
    }

    public enum Step {
        SNAPSHOT_PREPARED,
        CLEAR_AUTHORIZED,
        CLEAR_CONFIRMED,
        RESTORE_VERIFIED
    }

    public enum Access {
        CREATIVE_VANILLA,
        BODY_ONLY,
        NONE
    }

    public enum Rejection {
        STALE_GENERATION,
        STALE_TRANSITION,
        INVALID_TRANSITION,
        FORBIDDEN_STEP,
        MISSING_SNAPSHOT_PROOF,
        CLEAR_NOT_AUTHORIZED,
        UNRESOLVED_RECOVERY
    }

    public record Result(QuarantineState state, Rejection rejection, boolean duplicate) {
        public Result {
            Objects.requireNonNull(state, "state");
        }

        public boolean accepted() {
            return rejection == null;
        }
    }

    /** Bounded journal-shaped input. It contains no inventory or item data. */
    public record Journal(UUID accountId, long sessionGeneration, Phase phase,
                          UUID transitionId, Intent intent, boolean snapshotPrepared,
                          boolean clearAuthorized) {
        public Journal {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(phase, "phase");
            if (sessionGeneration < 0) {
                throw new IllegalArgumentException("Session generation must be nonnegative");
            }
            if ((transitionId == null) != (intent == null)) {
                throw new IllegalArgumentException("Transition ID and intent must be present together");
            }
        }
    }

    private final UUID accountId;
    private final long sessionGeneration;
    private final Phase phase;
    private final UUID transitionId;
    private final Intent intent;
    private final boolean snapshotPrepared;
    private final boolean clearAuthorized;
    private final Step lastStep;
    private final UUID lastTransitionId;
    private final Intent lastIntent;
    private final Step lastCompletedStep;

    private QuarantineState(UUID accountId, long sessionGeneration, Phase phase,
                            UUID transitionId, Intent intent, boolean snapshotPrepared,
                            boolean clearAuthorized, Step lastStep, UUID lastTransitionId,
                            Intent lastIntent, Step lastCompletedStep) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.sessionGeneration = sessionGeneration;
        this.phase = Objects.requireNonNull(phase, "phase");
        this.transitionId = transitionId;
        this.intent = intent;
        this.snapshotPrepared = snapshotPrepared;
        this.clearAuthorized = clearAuthorized;
        this.lastStep = lastStep;
        this.lastTransitionId = lastTransitionId;
        this.lastIntent = lastIntent;
        this.lastCompletedStep = lastCompletedStep;
    }

    public static QuarantineState create(UUID accountId, long sessionGeneration) {
        if (sessionGeneration < 0) {
            throw new IllegalArgumentException("Session generation must be nonnegative");
        }
        return new QuarantineState(accountId, sessionGeneration, Phase.CREATIVE_AVAILABLE,
                null, null, false, false, null, null, null, null);
    }

    /**
     * Reopening any journal is conservative: access is revoked until an external recovery
     * procedure resolves ownership. In particular, no crash stage grants either owner.
     */
    public static QuarantineState recover(Journal journal) {
        Objects.requireNonNull(journal, "journal");
        return new QuarantineState(journal.accountId(), journal.sessionGeneration(),
                Phase.RECOVERY_REQUIRED, journal.transitionId(), journal.intent(),
                journal.snapshotPrepared(), journal.clearAuthorized(), null,
                journal.transitionId(), journal.intent(), null);
    }

    public UUID accountId() { return accountId; }
    public long sessionGeneration() { return sessionGeneration; }
    public Phase phase() { return phase; }
    public UUID transitionId() { return transitionId; }
    public Intent intent() { return intent; }
    public boolean snapshotPrepared() { return snapshotPrepared; }
    public boolean clearAuthorized() { return clearAuthorized; }

    /**
     * Records intended access: the body may use hands/equipment/item-owned storage only,
     * never the carrier's vanilla inventory. In-flight and recovery states fail closed.
     */
    public Access access() {
        return switch (phase) {
            case CREATIVE_AVAILABLE -> Access.CREATIVE_VANILLA;
            case PARKED -> Access.BODY_ONLY;
            case PREPARING, RESTORING, RECOVERY_REQUIRED -> Access.NONE;
        };
    }

    /** Account ownership is deliberately retained in every phase, including recovery. */
    public boolean accountOwnershipRetained() {
        return true;
    }

    public Journal journal() {
        return new Journal(accountId, sessionGeneration, phase, transitionId, intent,
                snapshotPrepared, clearAuthorized);
    }

    /** Begin an explicit park or restore intent. */
    public Result begin(UUID requestedTransitionId, long generation, Intent requestedIntent) {
        if (generation != sessionGeneration) return rejected(Rejection.STALE_GENERATION);
        if (requestedTransitionId == null || requestedIntent == null) {
            return rejected(Rejection.INVALID_TRANSITION);
        }
        if (phase == Phase.RECOVERY_REQUIRED) return rejected(Rejection.UNRESOLVED_RECOVERY);

        if (requestedTransitionId.equals(transitionId) && requestedIntent == intent) {
            return accepted(this, true);
        }
        if (requestedTransitionId.equals(lastTransitionId) && requestedIntent == lastIntent
                && lastCompletedStep != null) {
            return accepted(this, true);
        }

        if (transitionId != null) return rejected(Rejection.INVALID_TRANSITION);
        Phase next;
        if (requestedIntent == Intent.PARK && phase == Phase.CREATIVE_AVAILABLE) {
            next = Phase.PREPARING;
        } else if (requestedIntent == Intent.RESTORE && phase == Phase.PARKED) {
            next = Phase.RESTORING;
        } else {
            return rejected(Rejection.INVALID_TRANSITION);
        }
        return accepted(new QuarantineState(accountId, sessionGeneration, next,
                requestedTransitionId, requestedIntent, false, false, null,
                lastTransitionId, lastIntent, lastCompletedStep), false);
    }

    /** Record an explicit durability/verification protocol step. */
    public Result confirm(UUID requestedTransitionId, long generation, Step step) {
        if (generation != sessionGeneration) return rejected(Rejection.STALE_GENERATION);
        if (phase == Phase.RECOVERY_REQUIRED) return rejected(Rejection.UNRESOLVED_RECOVERY);
        if (step == null || requestedTransitionId == null) return rejected(Rejection.INVALID_TRANSITION);
        if (requestedTransitionId.equals(transitionId) && step == lastStep) {
            return accepted(this, true);
        }
        if (requestedTransitionId.equals(transitionId) && intent == Intent.PARK
                && ((step == Step.SNAPSHOT_PREPARED && snapshotPrepared)
                || (step == Step.CLEAR_AUTHORIZED && clearAuthorized))) {
            return accepted(this, true);
        }
        if (requestedTransitionId.equals(lastTransitionId) && step == lastCompletedStep) {
            return accepted(this, true);
        }
        if (!requestedTransitionId.equals(transitionId)) return rejected(Rejection.STALE_TRANSITION);

        if (phase == Phase.PREPARING && intent == Intent.PARK) {
            if (step == Step.SNAPSHOT_PREPARED && !snapshotPrepared) {
                return accepted(copy(phase, true, false, step, transitionId, intent, null), false);
            }
            if (step == Step.CLEAR_AUTHORIZED && snapshotPrepared && !clearAuthorized) {
                return accepted(copy(phase, true, true, step, transitionId, intent, null), false);
            }
            if (step == Step.CLEAR_AUTHORIZED && !snapshotPrepared) {
                return rejected(Rejection.MISSING_SNAPSHOT_PROOF);
            }
            if (step == Step.CLEAR_CONFIRMED && !clearAuthorized) {
                return rejected(Rejection.CLEAR_NOT_AUTHORIZED);
            }
            if (step == Step.CLEAR_CONFIRMED) {
                return accepted(copy(Phase.PARKED, true, true, step, null, null,
                        transitionId, intent, step), false);
            }
        }
        if (phase == Phase.RESTORING && intent == Intent.RESTORE
                && step == Step.RESTORE_VERIFIED) {
            return accepted(copy(Phase.CREATIVE_AVAILABLE, false, false, step, null, null,
                    transitionId, intent, step), false);
        }
        return rejected(Rejection.FORBIDDEN_STEP);
    }

    /** Session replacement preserves stable account ownership; in-flight work must recover first. */
    public Result reconnect(long newGeneration) {
        if (newGeneration <= sessionGeneration) return rejected(Rejection.STALE_GENERATION);
        if (phase == Phase.RECOVERY_REQUIRED || transitionId != null) {
            return rejected(Rejection.UNRESOLVED_RECOVERY);
        }
        return accepted(new QuarantineState(accountId, newGeneration, phase, null, null,
                false, false, null, lastTransitionId, lastIntent, lastCompletedStep), false);
    }

    private QuarantineState copy(Phase nextPhase, boolean prepared, boolean authorized,
                                 Step nextLastStep, UUID nextTransitionId, Intent nextIntent,
                                 UUID nextLastTransitionId, Intent nextLastIntent,
                                 Step nextCompletedStep) {
        return new QuarantineState(accountId, sessionGeneration, nextPhase, nextTransitionId,
                nextIntent, prepared, authorized, nextLastStep, nextLastTransitionId,
                nextLastIntent, nextCompletedStep);
    }

    private QuarantineState copy(Phase nextPhase, boolean prepared, boolean authorized,
                                 Step nextLastStep, UUID nextTransitionId, Intent nextIntent,
                                 Step nextCompletedStep) {
        return copy(nextPhase, prepared, authorized, nextLastStep, nextTransitionId, nextIntent,
                lastTransitionId, lastIntent, nextCompletedStep);
    }

    private Result rejected(Rejection reason) { return new Result(this, reason, false); }
    private static Result accepted(QuarantineState state, boolean duplicate) {
        return new Result(state, null, duplicate);
    }
}
