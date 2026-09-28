package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LoadedBodyResolver.*;
import static org.junit.jupiter.api.Assertions.*;

class LoadedBodyResolverTest {
    private static final UUID ACCOUNT = id(1);
    private static final UUID BODY = id(2);

    @Test void disabledGateReturnsBeforeLookup() {
        AtomicInteger calls = new AtomicInteger();
        var result = resolve(false, ignored -> { calls.incrementAndGet(); return LookupResult.missing(); },
                candidate -> true);
        assertEquals(Outcome.DISABLED, result.outcome());
        assertEquals(0, calls.get());
    }

    @Test void minecraftAdapterGatesMasterAndMovementBeforeLookupOrThreadAccess() {
        AtomicInteger calls = new AtomicInteger();
        var off = MinecraftLoadedBodyAdapter.observe(profile(SavedLifecycleProfile.State.OFFLINE), false, false,
                false, ignored -> { calls.incrementAndGet(); return LookupResult.missing(); }, c -> true);
        var conflict = MinecraftLoadedBodyAdapter.observe(profile(SavedLifecycleProfile.State.OFFLINE), true, true,
                false, ignored -> { calls.incrementAndGet(); return LookupResult.missing(); }, c -> true);
        assertEquals(Outcome.DISABLED, off.outcome());
        assertEquals(Outcome.DISABLED, conflict.outcome());
        assertEquals(0, calls.get());
    }

    @Test void publicMinecraftAdapterReturnsDisabledForNullInputsBeforeDereferenceWhenGateIsOff() {
        MindGhostStartupGate.onServerStopped();
        MovementStartupGate.onServerStopped();
        assertEquals(Outcome.DISABLED, MinecraftLoadedBodyAdapter.observe(null, null).outcome());
        assertEquals(Outcome.DISABLED,
                MinecraftLoadedBodyAdapter.observe(null, profile(SavedLifecycleProfile.State.OFFLINE)).outcome());
        assertEquals(Outcome.DISABLED, MinecraftLoadedBodyAdapter.observe(null, null).outcome());
    }

    @Test void injectedMinecraftAdapterRejectsNullProfileAndOffThreadBeforeLookup() {
        AtomicInteger calls = new AtomicInteger();
        LoadedBodyResolver.ReadOnlyLookup lookup = ignored -> {
            calls.incrementAndGet();
            return LookupResult.missing();
        };
        var nullProfile = MinecraftLoadedBodyAdapter.observe(null, true, false, true, lookup, c -> true);
        var offThread = MinecraftLoadedBodyAdapter.observe(null, true, false, false, lookup, c -> true);
        assertEquals(Outcome.RECOVERY_REQUIRED, nullProfile.outcome());
        assertEquals(Outcome.RECOVERY_REQUIRED, offThread.outcome());
        assertEquals(0, calls.get());
    }

    @Test void savedChunkCoordinateRejectsWorldAndIntegerOverflowCoordinates() {
        assertNull(MinecraftLoadedBodyAdapter.savedChunkCoordinate(30_000_001.0));
        assertNull(MinecraftLoadedBodyAdapter.savedChunkCoordinate(-30_000_001.0));
        assertNull(MinecraftLoadedBodyAdapter.savedChunkCoordinate(Double.MAX_VALUE));
        assertNull(MinecraftLoadedBodyAdapter.savedChunkCoordinate(-Double.MAX_VALUE));
        assertEquals(1_875_000, MinecraftLoadedBodyAdapter.savedChunkCoordinate(30_000_000.0));
        assertEquals(-1_875_000, MinecraftLoadedBodyAdapter.savedChunkCoordinate(-30_000_000.0));
    }

    @Test void minecraftAdapterRejectsOffThreadBeforeLookup() {
        AtomicInteger calls = new AtomicInteger();
        var result = MinecraftLoadedBodyAdapter.observe(profile(SavedLifecycleProfile.State.OFFLINE), true, false,
                false, ignored -> { calls.incrementAndGet(); return LookupResult.missing(); }, c -> true);
        assertEquals(Outcome.RECOVERY_REQUIRED, result.outcome());
        assertEquals(0, calls.get());
    }

    @Test void unloadedKnownBodyIsDeferredAndNeverConvertedToMissing() {
        var result = resolve(true, ignored -> LookupResult.unloaded(), candidate -> true);
        assertEquals(Outcome.DEFER_KNOWN_BODY, result.outcome());
    }

    @Test void exactLoadedLivingOwnedCustomBodyIsAvailable() {
        Candidate expected = candidate(BODY, id(3), "minecraft:overworld", ACCOUNT, "main", true, new Object());
        var result = resolve(true, id -> {
            assertEquals(BODY, id);
            return LookupResult.loaded(expected);
        }, candidate -> candidate.entity() != null);
        assertEquals(Outcome.SAME_BODY_AVAILABLE, result.outcome());
        assertSame(expected, result.candidate());
    }

    @Test void onlyOfflineProfilesReachLookup() {
        for (SavedLifecycleProfile.State state : SavedLifecycleProfile.State.values()) {
            if (state == SavedLifecycleProfile.State.OFFLINE) continue;
            AtomicInteger calls = new AtomicInteger();
            var result = LoadedBodyResolver.resolve(profile(state), true,
                    ignored -> { calls.incrementAndGet(); return LookupResult.missing(); }, c -> true);
            assertEquals(Outcome.RECOVERY_REQUIRED, result.outcome(), state.name());
            assertEquals(0, calls.get(), state.name());
        }
    }

    @Test void ownershipDimensionIdentityAndAliveMismatchFailClosed() {
        assertRecovery(candidate(BODY, id(3), "minecraft:overworld", id(9), "main", true, new Object()), c -> true);
        assertRecovery(candidate(BODY, id(3), "minecraft:overworld", ACCOUNT, "other", true, new Object()), c -> true);
        assertRecovery(candidate(BODY, id(3), "minecraft:the_nether", ACCOUNT, "main", true, new Object()), c -> true);
        assertRecovery(candidate(id(8), id(3), "minecraft:overworld", ACCOUNT, "main", true, new Object()), c -> true);
        assertRecovery(candidate(BODY, id(3), "minecraft:overworld", ACCOUNT, "main", false, new Object()), c -> true);
    }

    @Test void missingOrWrongBoundMindAndNullEntityFailClosed() {
        assertRecovery(candidate(BODY, null, "minecraft:overworld", ACCOUNT, "main", true, new Object()), c -> true);
        assertRecovery(candidate(BODY, id(5), "minecraft:overworld", ACCOUNT, "main", true, new Object()), c -> true);
        assertRecovery(candidate(BODY, id(3), "minecraft:overworld", ACCOUNT, "main", true, null), c -> true);
    }

    @Test void ambiguousResultsAndOrdinaryMobWithoutCustomValidatorFailClosed() {
        Candidate candidate = candidate(BODY, id(3), "minecraft:overworld", ACCOUNT, "main", true, new Object());
        var ambiguous = resolve(true, ignored -> LookupResult.ambiguous(List.of(candidate, candidate)), c -> true);
        assertEquals(Outcome.RECOVERY_REQUIRED, ambiguous.outcome());
        assertRecovery(candidate, c -> false); // Vanilla Mob is not custom-character evidence by default.
    }

    @Test void missingIsExplicitAndNullLookupEvidenceIsNotInterpretedAsMissing() {
        assertEquals(Outcome.RECOVERY_REQUIRED,
                resolve(true, ignored -> LookupResult.missing(), c -> true).outcome());
        assertEquals(Outcome.RECOVERY_REQUIRED,
                resolve(true, ignored -> null, c -> true).outcome());
    }

    private static void assertRecovery(Candidate candidate, java.util.function.Predicate<Candidate> eligible) {
        assertEquals(Outcome.RECOVERY_REQUIRED,
                resolve(true, ignored -> LookupResult.loaded(candidate), eligible).outcome());
    }

    private static Resolution resolve(boolean enabled, ReadOnlyLookup lookup,
                                      java.util.function.Predicate<Candidate> eligible) {
        return LoadedBodyResolver.resolve(profile(SavedLifecycleProfile.State.OFFLINE), enabled, lookup, eligible);
    }

    private static Candidate candidate(UUID entityId, UUID mindId, String dimension, UUID owner, String profile,
                                       boolean alive, Object entity) {
        return new Candidate(entityId, mindId, dimension, owner, profile, alive, entity);
    }

    private static SavedLifecycleProfile profile(SavedLifecycleProfile.State state) {
        Long offlineSince = state == SavedLifecycleProfile.State.OFFLINE
                || state == SavedLifecycleProfile.State.DEAD_CLAIM
                || state == SavedLifecycleProfile.State.GHOST_OFFLINE ? Long.valueOf(123L) : null;
        return new SavedLifecycleProfile(1, ACCOUNT, "main", id(3), BODY, "minecraft:overworld",
                new SavedLifecycleProfile.Location(1, 2, 3), Map.of(), id(4), 0,
                state, 0, offlineSince);
    }

    private static UUID id(long value) { return new UUID(0, value); }
}
