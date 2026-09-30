package com.juicyslew.moonstation14.ms14.hands.quarantine;

import java.util.Objects;

/**
 * Pure evidence triage for a future Creative playerdata reconciliation procedure. Inputs are
 * caller-reported facts, not proof of provenance, exclusive ownership, or a completed write.
 * In particular, playerdata does not persist the carried menu cursor from the journal snapshot.
 * No result authorizes access, clearing, restoring, or any other mutation.
 */
public final class CreativeReconciliationPolicy {
    private CreativeReconciliationPolicy() { }

    public enum JournalEvidence { ABSENT, VALID, READ_ERROR }
    public enum IndexEvidence { ABSENT, CONSISTENT, CONFLICT, READ_ERROR }
    public enum LiveObservation { EXACT_SNAPSHOT, CANONICAL_EMPTY, DIFFERENT, UNAVAILABLE }
    /** EXACT_SUPPORTED_SLOTS compares only persistent supported slots, never the cursor. */
    public enum DiskObservation { EXACT_SUPPORTED_SLOTS, CANONICAL_EMPTY, DIFFERENT, UNAVAILABLE }
    public enum DiskSource { PRIMARY, DAT_OLD, MISSING, UNVERIFIED }

    public enum Classification { CANDIDATE_AMBIGUOUS, CONFLICT, UNAVAILABLE }
    public enum Reason {
        JOURNAL_READ_ERROR,
        JOURNAL_ABSENT,
        INDEX_READ_ERROR,
        INDEX_CONFLICT,
        INDEX_ABSENT,
        DISK_MISSING,
        DISK_UNVERIFIED,
        DISK_FALLBACK,
        OBSERVATION_UNAVAILABLE,
        OBSERVATION_DIFFERENT,
        BOTH_EMPTY_NOT_BASELINE,
        MATCHES_DO_NOT_PROVE_OWNERSHIP,
        CRASH_WINDOW_AMBIGUOUS
    }

    public record Evidence(JournalEvidence journal, CreativeQuarantineRecord.Phase phase,
                           IndexEvidence index, LiveObservation live,
                           DiskObservation disk, DiskSource diskSource) {
        public Evidence {
            Objects.requireNonNull(journal, "journal");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(live, "live");
            Objects.requireNonNull(disk, "disk");
            Objects.requireNonNull(diskSource, "diskSource");
            if ((journal == JournalEvidence.VALID) != (phase != null))
                throw new IllegalArgumentException("Phase is required exactly when journal is valid");
            if (diskSource == DiskSource.MISSING && disk != DiskObservation.UNAVAILABLE)
                throw new IllegalArgumentException("Missing playerdata cannot have an observation");
        }
    }

    public record Result(Classification classification, Reason reason, boolean authorized) {
        public Result {
            Objects.requireNonNull(classification, "classification");
            Objects.requireNonNull(reason, "reason");
            if (authorized) throw new IllegalArgumentException("Evidence cannot authorize actions");
        }
    }

    public static Result classify(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.journal() == JournalEvidence.READ_ERROR)
            return result(Classification.UNAVAILABLE, Reason.JOURNAL_READ_ERROR);
        if (evidence.index() == IndexEvidence.READ_ERROR)
            return result(Classification.UNAVAILABLE, Reason.INDEX_READ_ERROR);
        if (evidence.index() == IndexEvidence.CONFLICT)
            return result(Classification.CONFLICT, Reason.INDEX_CONFLICT);
        if (evidence.diskSource() == DiskSource.MISSING)
            return result(Classification.UNAVAILABLE, Reason.DISK_MISSING);
        if (evidence.diskSource() == DiskSource.UNVERIFIED)
            return result(Classification.UNAVAILABLE, Reason.DISK_UNVERIFIED);
        if (evidence.live() == LiveObservation.UNAVAILABLE || evidence.disk() == DiskObservation.UNAVAILABLE)
            return result(Classification.UNAVAILABLE, Reason.OBSERVATION_UNAVAILABLE);
        if (evidence.live() == LiveObservation.DIFFERENT || evidence.disk() == DiskObservation.DIFFERENT)
            return result(Classification.CONFLICT, Reason.OBSERVATION_DIFFERENT);
        if (evidence.journal() == JournalEvidence.ABSENT)
            return result(Classification.CANDIDATE_AMBIGUOUS, Reason.JOURNAL_ABSENT);
        if (evidence.index() == IndexEvidence.ABSENT)
            return result(Classification.CANDIDATE_AMBIGUOUS, Reason.INDEX_ABSENT);
        if (evidence.diskSource() == DiskSource.DAT_OLD)
            return result(Classification.CANDIDATE_AMBIGUOUS, Reason.DISK_FALLBACK);
        if (evidence.live() == LiveObservation.CANONICAL_EMPTY
                && evidence.disk() == DiskObservation.CANONICAL_EMPTY)
            return result(Classification.CANDIDATE_AMBIGUOUS, Reason.BOTH_EMPTY_NOT_BASELINE);
        if (evidence.live() == LiveObservation.EXACT_SNAPSHOT
                && evidence.disk() == DiskObservation.EXACT_SUPPORTED_SLOTS)
            return result(Classification.CANDIDATE_AMBIGUOUS, Reason.MATCHES_DO_NOT_PROVE_OWNERSHIP);
        // Any phase can straddle a disk write, a live change, or a crash. Phase is intent,
        // including PARKED and RESTORE_VERIFIED; it is never a physical-operation proof.
        return result(Classification.CANDIDATE_AMBIGUOUS, Reason.CRASH_WINDOW_AMBIGUOUS);
    }

    private static Result result(Classification classification, Reason reason) {
        return new Result(classification, reason, false);
    }
}
