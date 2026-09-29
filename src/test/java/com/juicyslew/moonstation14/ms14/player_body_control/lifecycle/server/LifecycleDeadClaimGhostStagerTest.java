package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleDeadClaimGhostStagerTest {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @TempDir Path directory;

    @AfterEach void resetGate() { MindGhostStartupGate.onServerStopped(); }

    @Test void defaultOffRejectsBeforePlayerServerOrWorldAccess() {
        MindGhostStartupGate.onServerStopped();
        var result = LifecycleDeadClaimGhostStager.stage(null, null);
        assertEquals(LifecycleDeadClaimGhostStager.Outcome.FAILED, result.outcome());
        assertNull(result.prepared());
        assertTrue(result.diagnostic().contains("gate is off"));
    }

    @Test void freshExactCurrentClaimIsReturnedForStaging() {
        var supplied = claim(ACCOUNT, 4);
        var current = claim(ACCOUNT, 4);
        assertSame(current, LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, supplied,
                account -> {
                    assertEquals(ACCOUNT, account);
                    return Optional.of(current);
                }).orElseThrow());
    }

    @Test void absentOrChangedCurrentClaimFailsClosed() {
        var supplied = claim(ACCOUNT, 4);
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, supplied,
                account -> Optional.empty()).isEmpty());
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, supplied,
                account -> Optional.of(claim(ACCOUNT, 5))).isEmpty());
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, supplied,
                account -> Optional.of(claim(UUID.randomUUID(), 4))).isEmpty());
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, supplied,
                account -> Optional.of(offline(ACCOUNT))).isEmpty());
    }

    @Test void suppliedClaimMustBelongToAccountAndBeDeadClaim() {
        var supplied = claim(ACCOUNT, 4);
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(UUID.randomUUID(), supplied,
                account -> fail("must not read a mismatched account")).isEmpty());
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, offline(ACCOUNT),
                account -> fail("must not read a non-claim")).isEmpty());
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, null,
                account -> fail("must not read a missing claim")).isEmpty());
    }

    @Test void corruptOrUnavailablePrimaryFailsClosed() {
        var supplied = claim(ACCOUNT, 4);
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, supplied,
                account -> { throw new IOException("corrupt primary"); }).isEmpty());
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, supplied,
                account -> { throw new IllegalStateException("invalid envelope"); }).isEmpty());
    }

    @Test void firstEnrollmentDeathClaimFromStartupNullEnvelopeValidatesOnlyAgainstCurrentPrimary() throws Exception {
        Path primary = directory.resolve("fresh-world-profiles.json");
        var store = new LifecycleProfileStore(primary);
        assertTrue(store.read().isEmpty());
        var context = new LifecycleServerContext(store, null);
        assertFalse(context.initialized());
        UUID mind = UUID.randomUUID();
        UUID bodyId = UUID.randomUUID();
        var location = new SavedLifecycleProfile.Location(12, 64, -8);
        var appearance = Map.of("model", "wide");

        assertTrue(context.reserveFirstProfile(ACCOUNT, "main", mind, bodyId,
                "minecraft:overworld", location, appearance));
        assertTrue(context.wasCreatedOnThisServer(ACCOUNT));
        var body = new MobHarness(new MobHarnessId(bodyId), MobHarnessKind.CHARACTER);
        assertTrue(context.ownership().registerHarness(body));
        assertTrue(context.stageFirstProfile(ACCOUNT, mind, bodyId, body.id()));
        var active = context.promoteFirstCharacter(ACCOUNT, mind, bodyId).orElseThrow();
        assertEquals(SavedLifecycleProfile.State.ACTIVE, store.read().orElseThrow().profiles().get(0).state());

        assertTrue(context.claimActiveDeath(ACCOUNT, "main", mind, bodyId, "minecraft:overworld",
                location, appearance, active.connectionGeneration(), candidate -> candidate == body));
        var supplied = context.currentDeadClaim(ACCOUNT).orElseThrow();
        assertEquals(SavedLifecycleProfile.State.DEAD_CLAIM, supplied.state());
        assertEquals(PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM,
                context.lifecycle().profile(ACCOUNT).orElseThrow().state());
        assertEquals(supplied, store.read().orElseThrow().profiles().get(0));
        assertFalse(context.initialized(), "first enrollment must not retroactively mark startup initialized");

        var current = LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, supplied,
                context::currentDeadClaim).orElseThrow();
        assertEquals(supplied, current);
        assertNotSame(supplied, current, "the staging authority must come from a fresh primary read");

        var stale = new SavedLifecycleProfile(supplied.schemaVersion(), supplied.accountId(), supplied.profileKey(),
                supplied.mindId(), supplied.bodyId(), supplied.dimension(), supplied.location(), supplied.appearance(),
                supplied.connectionEpoch(), supplied.connectionGeneration(), supplied.state(),
                supplied.revision() - 1, supplied.offlineSinceMillis());
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, stale,
                context::currentDeadClaim).isEmpty());
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, null,
                account -> fail("missing supplied claim must not read primary")).isEmpty());
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(UUID.randomUUID(), supplied,
                account -> fail("wrong account must not read primary")).isEmpty());
        assertTrue(context.currentDeadClaim(UUID.randomUUID()).isEmpty());

        // Corrupt evidence in this disposable store must never be treated as the earlier valid claim.
        Files.writeString(primary, "corrupt primary evidence");
        assertTrue(LifecycleDeadClaimGhostStager.validatedCurrentDeadClaim(ACCOUNT, supplied,
                context::currentDeadClaim).isEmpty());
        assertEquals(PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM,
                context.lifecycle().profile(ACCOUNT).orElseThrow().state());
        assertTrue(context.ownership().registeredHarness(body.id()).isPresent());
    }

    private static SavedLifecycleProfile claim(UUID account, long revision) {
        return new SavedLifecycleProfile(1, account, "main", UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"), "minecraft:overworld",
                new SavedLifecycleProfile.Location(0, 64, 0), Map.of(),
                UUID.fromString("00000000-0000-0000-0000-000000000004"), 1,
                SavedLifecycleProfile.State.DEAD_CLAIM, revision, 123L);
    }

    private static SavedLifecycleProfile offline(UUID account) {
        var claim = claim(account, 4);
        return new SavedLifecycleProfile(claim.schemaVersion(), claim.accountId(), claim.profileKey(),
                claim.mindId(), claim.bodyId(), claim.dimension(), claim.location(), claim.appearance(),
                claim.connectionEpoch(), claim.connectionGeneration(), SavedLifecycleProfile.State.OFFLINE,
                claim.revision(), claim.offlineSinceMillis());
    }
}
