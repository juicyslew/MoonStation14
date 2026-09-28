package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Pure, bounded classification of a saved body identity. This class never loads or mutates entities. */
public final class LoadedBodyResolver {
    private LoadedBodyResolver() { }

    /** The caller must establish both startup gates before enabling a lookup. */
    public static Resolution resolve(SavedLifecycleProfile profile, boolean runtimeEnabled,
                                     ReadOnlyLookup lookup, Predicate<Candidate> eligibleCharacter) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(eligibleCharacter, "eligibleCharacter");
        if (!runtimeEnabled) return new Resolution(Outcome.DISABLED, null);
        if (profile.state() != SavedLifecycleProfile.State.OFFLINE) return recovery();

        LookupResult result = lookup.findExact(profile.bodyId());
        if (result == null) return recovery();
        return switch (result.kind()) {
            case UNLOADED -> new Resolution(Outcome.DEFER_KNOWN_BODY, null);
            case MISSING, AMBIGUOUS -> recovery();
            case LOADED -> classifyLoaded(profile, result.candidates(), eligibleCharacter);
        };
    }

    private static Resolution classifyLoaded(SavedLifecycleProfile profile, List<Candidate> candidates,
                                             Predicate<Candidate> eligibleCharacter) {
        if (candidates == null || candidates.size() != 1 || candidates.get(0) == null) return recovery();
        Candidate candidate = candidates.get(0);
        if (!profile.bodyId().equals(candidate.entityId())
                || !profile.mindId().equals(candidate.boundMindId())
                || !profile.dimension().equals(candidate.dimension())
                || !profile.accountId().equals(candidate.ownerAccountId())
                || !profile.profileKey().equals(candidate.ownerProfileKey())
                || !candidate.alive() || candidate.entity() == null) return recovery();
        try {
            if (!eligibleCharacter.test(candidate)) return recovery();
        } catch (RuntimeException validationFailure) {
            return recovery();
        }
        return new Resolution(Outcome.SAME_BODY_AVAILABLE, candidate);
    }

    private static Resolution recovery() { return new Resolution(Outcome.RECOVERY_REQUIRED, null); }

    /** Exact-ID, non-loading observation supplied by the caller. Null is never interpreted as missing. */
    @FunctionalInterface
    public interface ReadOnlyLookup {
        LookupResult findExact(UUID recordedBodyId);
    }

    public enum LookupKind { LOADED, UNLOADED, MISSING, AMBIGUOUS }
    public enum Outcome { DISABLED, SAME_BODY_AVAILABLE, DEFER_KNOWN_BODY, RECOVERY_REQUIRED }

    public record LookupResult(LookupKind kind, List<Candidate> candidates) {
        public LookupResult {
            Objects.requireNonNull(kind, "kind");
            candidates = candidates == null ? null : List.copyOf(candidates);
        }
        public static LookupResult loaded(Candidate candidate) {
            return new LookupResult(LookupKind.LOADED, List.of(candidate));
        }
        public static LookupResult unloaded() { return new LookupResult(LookupKind.UNLOADED, List.of()); }
        public static LookupResult missing() { return new LookupResult(LookupKind.MISSING, List.of()); }
        public static LookupResult ambiguous(List<Candidate> candidates) {
            return new LookupResult(LookupKind.AMBIGUOUS, candidates);
        }
    }

    /** Identity evidence; bound Mind evidence must come from the authoritative entity binding. */
    public record Candidate(UUID entityId, UUID boundMindId, String dimension, UUID ownerAccountId,
                            String ownerProfileKey, boolean alive, Object entity) { }

    public record Resolution(Outcome outcome, Candidate candidate) {
        public Resolution { Objects.requireNonNull(outcome, "outcome"); }
    }
}
