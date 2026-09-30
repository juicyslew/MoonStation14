package com.juicyslew.moonstation14.ms14.hands.quarantine;

import org.junit.jupiter.api.Test;

import static com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeReconciliationPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class CreativeReconciliationPolicyTest {
    private static Evidence evidence(JournalEvidence journal, CreativeQuarantineRecord.Phase phase,
                                     IndexEvidence index, LiveObservation live,
                                     DiskObservation disk, DiskSource source) {
        return new Evidence(journal, phase, index, live, disk, source);
    }

    @Test
    void everyValidPhaseAndObservationPairIsInertIncludingCrashWindows() {
        for (CreativeQuarantineRecord.Phase phase : CreativeQuarantineRecord.Phase.values()) {
            for (LiveObservation live : LiveObservation.values()) {
                for (DiskObservation disk : DiskObservation.values()) {
                    Result result = classify(evidence(JournalEvidence.VALID, phase,
                            IndexEvidence.CONSISTENT, live, disk, DiskSource.PRIMARY));
                    assertFalse(result.authorized(), () -> phase + " / " + live + " / " + disk);
                    assertNotNull(result.reason());
                    Classification expected = live == LiveObservation.UNAVAILABLE
                            || disk == DiskObservation.UNAVAILABLE ? Classification.UNAVAILABLE
                            : live == LiveObservation.DIFFERENT || disk == DiskObservation.DIFFERENT
                            ? Classification.CONFLICT : Classification.CANDIDATE_AMBIGUOUS;
                    assertEquals(expected, result.classification(), () -> phase + " / " + live + " / " + disk);
                }
            }
            assertEquals(Reason.MATCHES_DO_NOT_PROVE_OWNERSHIP, classify(evidence(
                    JournalEvidence.VALID, phase, IndexEvidence.CONSISTENT,
                    LiveObservation.EXACT_SNAPSHOT, DiskObservation.EXACT_SUPPORTED_SLOTS,
                    DiskSource.PRIMARY)).reason());
            assertEquals(Reason.BOTH_EMPTY_NOT_BASELINE, classify(evidence(
                    JournalEvidence.VALID, phase, IndexEvidence.CONSISTENT,
                    LiveObservation.CANONICAL_EMPTY, DiskObservation.CANONICAL_EMPTY,
                    DiskSource.PRIMARY)).reason());
            assertEquals(Reason.CRASH_WINDOW_AMBIGUOUS, classify(evidence(
                    JournalEvidence.VALID, phase, IndexEvidence.CONSISTENT,
                    LiveObservation.EXACT_SNAPSHOT, DiskObservation.CANONICAL_EMPTY,
                    DiskSource.PRIMARY)).reason());
            assertEquals(Reason.CRASH_WINDOW_AMBIGUOUS, classify(evidence(
                    JournalEvidence.VALID, phase, IndexEvidence.CONSISTENT,
                    LiveObservation.CANONICAL_EMPTY, DiskObservation.EXACT_SUPPORTED_SLOTS,
                    DiskSource.PRIMARY)).reason());
        }
    }

    @Test
    void independentIndexAbsentConflictAndReadErrorNeverEstablishHighWater() {
        for (IndexEvidence index : IndexEvidence.values()) {
            Result result = classify(evidence(JournalEvidence.VALID,
                    CreativeQuarantineRecord.Phase.PARKED, index,
                    LiveObservation.EXACT_SNAPSHOT, DiskObservation.EXACT_SUPPORTED_SLOTS,
                    DiskSource.PRIMARY));
            assertFalse(result.authorized());
            assertEquals(switch (index) {
                case ABSENT -> Reason.INDEX_ABSENT;
                case CONSISTENT -> Reason.MATCHES_DO_NOT_PROVE_OWNERSHIP;
                case CONFLICT -> Reason.INDEX_CONFLICT;
                case READ_ERROR -> Reason.INDEX_READ_ERROR;
            }, result.reason());
        }
    }

    @Test
    void missingFallbackAndUnverifiedDiskDoNotBecomePrimaryEvidence() {
        var phase = CreativeQuarantineRecord.Phase.RESTORE_VERIFIED;
        assertEquals(Reason.DISK_MISSING, classify(evidence(JournalEvidence.VALID, phase,
                IndexEvidence.CONSISTENT, LiveObservation.EXACT_SNAPSHOT,
                DiskObservation.UNAVAILABLE, DiskSource.MISSING)).reason());
        for (DiskObservation disk : DiskObservation.values()) {
            Result unverified = classify(evidence(JournalEvidence.VALID, phase,
                    IndexEvidence.CONSISTENT, LiveObservation.EXACT_SNAPSHOT, disk,
                    DiskSource.UNVERIFIED));
            assertEquals(Classification.UNAVAILABLE, unverified.classification());
            assertEquals(Reason.DISK_UNVERIFIED, unverified.reason());
            assertFalse(unverified.authorized());
        }
        for (LiveObservation live : new LiveObservation[] {
                LiveObservation.EXACT_SNAPSHOT, LiveObservation.CANONICAL_EMPTY }) {
            for (DiskObservation disk : new DiskObservation[] {
                    DiskObservation.EXACT_SUPPORTED_SLOTS, DiskObservation.CANONICAL_EMPTY }) {
                Result fallback = classify(evidence(JournalEvidence.VALID, phase,
                        IndexEvidence.CONSISTENT, live, disk, DiskSource.DAT_OLD));
                assertEquals(Classification.CANDIDATE_AMBIGUOUS, fallback.classification());
                assertEquals(Reason.DISK_FALLBACK, fallback.reason());
                assertFalse(fallback.authorized());
            }
        }
    }

    @Test
    void absentOrUnreadableJournalCannotEstablishBaselineEvenWithBothCopiesEmpty() {
        for (JournalEvidence journal : new JournalEvidence[] {
                JournalEvidence.ABSENT, JournalEvidence.READ_ERROR }) {
            for (IndexEvidence index : IndexEvidence.values()) {
                Result result = classify(evidence(journal, null, index,
                        LiveObservation.CANONICAL_EMPTY, DiskObservation.CANONICAL_EMPTY,
                        DiskSource.PRIMARY));
                assertFalse(result.authorized());
                assertNotNull(result.reason());
                assertNotEquals("SAFE_BASELINE", result.classification().name());
            }
        }
        assertEquals(Reason.JOURNAL_ABSENT, classify(evidence(JournalEvidence.ABSENT, null,
                IndexEvidence.CONSISTENT, LiveObservation.CANONICAL_EMPTY,
                DiskObservation.CANONICAL_EMPTY, DiskSource.PRIMARY)).reason());
        assertEquals(Reason.JOURNAL_READ_ERROR, classify(evidence(JournalEvidence.READ_ERROR, null,
                IndexEvidence.CONSISTENT, LiveObservation.EXACT_SNAPSHOT,
                DiskObservation.EXACT_SUPPORTED_SLOTS, DiskSource.PRIMARY)).reason());
    }

    @Test
    void typedEvidenceRejectsImpossiblePhaseAndMissingDiskClaimsAndGrantingResults() {
        assertThrows(IllegalArgumentException.class, () -> evidence(JournalEvidence.VALID, null,
                IndexEvidence.ABSENT, LiveObservation.UNAVAILABLE, DiskObservation.UNAVAILABLE,
                DiskSource.MISSING));
        assertThrows(IllegalArgumentException.class, () -> evidence(JournalEvidence.ABSENT,
                CreativeQuarantineRecord.Phase.PREPARED, IndexEvidence.ABSENT,
                LiveObservation.UNAVAILABLE, DiskObservation.UNAVAILABLE, DiskSource.MISSING));
        assertThrows(IllegalArgumentException.class, () -> evidence(JournalEvidence.ABSENT, null,
                IndexEvidence.ABSENT, LiveObservation.UNAVAILABLE,
                DiskObservation.EXACT_SUPPORTED_SLOTS, DiskSource.MISSING));
        assertThrows(IllegalArgumentException.class, () -> new Result(
                Classification.CANDIDATE_AMBIGUOUS, Reason.MATCHES_DO_NOT_PROVE_OWNERSHIP, true));
        assertThrows(NullPointerException.class, () -> classify(null));
    }
}
