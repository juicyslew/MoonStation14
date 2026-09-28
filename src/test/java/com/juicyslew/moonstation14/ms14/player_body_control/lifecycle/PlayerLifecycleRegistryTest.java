package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle;

import com.juicyslew.moonstation14.ms14.player_body_control.*;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerLifecycleRegistryTest {
    private static final TargetEligibility ELIGIBLE = mob -> true;
    @TempDir Path directory;
    private int restoreStoreNumber;

    @Test void freshRegistryStagesAndDurablyActivatesDeadClaimOnGhostWithoutChangingCorpseRow() throws Exception {
        UUID account = uuid(1800), mind = uuid(1801), epoch = uuid(1802);
        MobHarnessId corpse = harnessId(1803), ghost = harnessId(1804);
        SavedLifecycleProfile dead = saved(account, "dead", mind, corpse.value(), epoch, 2,
                SavedLifecycleProfile.State.DEAD_CLAIM);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("dead-ghost-restart.json"));
        store.compareAndSwap(-1, epoch, 2, java.util.List.of(dead));
        BodyControlRegistry ownership = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(ownership);
        harness(ownership, 1804, MobHarnessKind.GHOST);

        var staged = store.withCurrentPrimary(primary -> lifecycle.stageDeadClaimGhost(primary, dead, ghost, ELIGIBLE))
                .orElseThrow().orElseThrow();
        assertEquals(new MindId(mind), ownership.mind(account).orElseThrow().id());
        assertEquals(ghost, ownership.mind(account).orElseThrow().harnessId());
        assertFalse(staged.active());
        assertFalse(lifecycle.authorizes(account, staged.connectionGeneration(), ghost));
        assertEquals(dead, store.read().orElseThrow().profiles().get(0));

        var activated = store.withCurrentPrimary(primary -> lifecycle.activateDeadClaimGhostDurably(
                primary, dead, ghost, true, ELIGIBLE)).orElseThrow().orElseThrow();
        SavedLifecycleProfile persisted = store.read().orElseThrow().profiles().get(0);
        assertEquals(SavedLifecycleProfile.State.DEAD_CLAIM, persisted.state());
        assertEquals(corpse.value(), persisted.bodyId());
        assertEquals(dead.offlineSinceMillis(), persisted.offlineSinceMillis());
        assertTrue(persisted.connectionGeneration() > dead.connectionGeneration());
        assertTrue(persisted.revision() > dead.revision());
        assertTrue(activated.active());
        assertTrue(lifecycle.authorizes(account, activated.connectionGeneration(), ghost, ELIGIBLE));
    }

    @Test void deadClaimGhostRejectsInvalidTargetAndUnauthenticatedActivationWithoutPartialAuthority() throws Exception {
        UUID account = uuid(1810), epoch = uuid(1811);
        MobHarnessId corpse = harnessId(1812), ghost = harnessId(1813);
        SavedLifecycleProfile dead = saved(account, "dead", uuid(1814), corpse.value(), epoch, 0,
                SavedLifecycleProfile.State.DEAD_CLAIM);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("dead-ghost-reject.json"));
        store.compareAndSwap(-1, epoch, 1, java.util.List.of(dead));
        BodyControlRegistry ownership = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(ownership);
        harness(ownership, 1813, MobHarnessKind.CHARACTER);
        assertTrue(store.withCurrentPrimary(primary -> lifecycle.stageDeadClaimGhost(primary, dead, ghost, ELIGIBLE))
                .orElseThrow().isEmpty());
        harness(ownership, 1815, MobHarnessKind.GHOST);
        MobHarnessId validGhost = harnessId(1815);
        var staged = store.withCurrentPrimary(primary -> lifecycle.stageDeadClaimGhost(
                primary, dead, validGhost, ELIGIBLE)).orElseThrow().orElseThrow();
        assertTrue(store.withCurrentPrimary(primary -> lifecycle.activateDeadClaimGhostDurably(
                primary, dead, validGhost, false, ELIGIBLE)).orElseThrow().isEmpty());
        assertFalse(staged.active());
        assertFalse(lifecycle.authorizes(account, staged.connectionGeneration(), validGhost));
    }

    @Test void deadClaimGhostRejectsStaleRowAndGhostAlreadyOwnedByAnotherMind() throws Exception {
        UUID account = uuid(1816), mind = uuid(1817), epoch = uuid(1818), otherAccount = uuid(1819);
        MobHarnessId corpse = harnessId(1826), ghost = harnessId(1827);
        SavedLifecycleProfile dead = saved(account, "dead-exact", mind, corpse.value(), epoch, 1,
                SavedLifecycleProfile.State.DEAD_CLAIM);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("dead-ghost-exactness.json"));
        store.compareAndSwap(-1, epoch, 2, java.util.List.of(dead));
        BodyControlRegistry ownership = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(ownership);
        harness(ownership, 1827, MobHarnessKind.GHOST);

        SavedLifecycleProfile stale = new SavedLifecycleProfile(dead.schemaVersion(), dead.accountId(), dead.profileKey(),
                dead.mindId(), dead.bodyId(), dead.dimension(), dead.location(), dead.appearance(), dead.connectionEpoch(),
                dead.connectionGeneration(), dead.state(), dead.revision() + 1, dead.offlineSinceMillis());
        assertTrue(store.withCurrentPrimary(primary -> lifecycle.stageDeadClaimGhost(primary, stale, ghost, ELIGIBLE))
                .orElseThrow().isEmpty());
        var otherGhost = harnessId(1828);
        assertTrue(lifecycle.registerHarness(new MobHarness(otherGhost, MobHarnessKind.GHOST)));
        ownership.createMind(otherAccount, otherGhost, ELIGIBLE).orElseThrow();
        assertTrue(store.withCurrentPrimary(primary -> lifecycle.stageDeadClaimGhost(primary, dead, otherGhost, ELIGIBLE))
                .orElseThrow().isEmpty());
        assertTrue(lifecycle.profile(account).isEmpty());
        assertEquals(dead, store.read().orElseThrow().profiles().get(0));
    }

    @Test void durableGhostLogoutReturnsSameMindToCorpseWithoutChangingRecordAndCanStageNewGhost() throws Exception {
        UUID account = uuid(1820), mindId = uuid(1821), epoch = uuid(1822);
        MobHarnessId corpse = harnessId(1823), firstGhost = harnessId(1824), nextGhost = harnessId(1825);
        SavedLifecycleProfile dead = saved(account, "logout", mindId, corpse.value(), epoch, 3,
                SavedLifecycleProfile.State.DEAD_CLAIM);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("dead-ghost-logout.json"));
        store.compareAndSwap(-1, epoch, 3, java.util.List.of(dead));
        BodyControlRegistry ownership = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(ownership);
        harness(ownership, 1823, MobHarnessKind.CHARACTER);
        harness(ownership, 1824, MobHarnessKind.GHOST);
        harness(ownership, 1825, MobHarnessKind.GHOST);

        var staged = store.withCurrentPrimary(primary -> lifecycle.stageDeadClaimGhost(primary, dead, firstGhost, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var active = store.withCurrentPrimary(primary -> lifecycle.activateDeadClaimGhostDurably(
                primary, dead, firstGhost, true, ELIGIBLE)).orElseThrow().orElseThrow();
        SavedLifecycleProfile durableBeforeLogout = store.read().orElseThrow().profiles().get(0);
        assertTrue(active.connectionGeneration() > staged.connectionGeneration());
        assertTrue(lifecycle.returnGhostToDeadClaim(account, active.mindId(), firstGhost, corpse,
                active.connectionGeneration() - 1).isEmpty());
        assertTrue(lifecycle.returnGhostToDeadClaim(account, active.mindId(), nextGhost, corpse,
                active.connectionGeneration()).isEmpty());
        assertTrue(lifecycle.authorizes(account, active.connectionGeneration(), firstGhost, ELIGIBLE));

        var returned = lifecycle.returnGhostToDeadClaim(account, active.mindId(), firstGhost, corpse,
                active.connectionGeneration()).orElseThrow();
        assertEquals(active.mindId(), returned.mindId());
        assertEquals(corpse, returned.bodyId());
        assertEquals(active.connectionGeneration(), returned.connectionGeneration(), "logout must not allocate an epoch");
        assertEquals(PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM, returned.state());
        assertFalse(returned.active());
        assertTrue(returned.deadClaim());
        assertFalse(lifecycle.authorizes(account, active.connectionGeneration(), firstGhost, ELIGIBLE));
        assertFalse(ownership.authorizes(account, corpse, active.connectionGeneration(), ELIGIBLE));
        assertEquals(active.mindId(), ownership.mind(account).orElseThrow().id());
        assertEquals(corpse, ownership.mind(account).orElseThrow().harnessId());
        assertTrue(ownership.registeredHarness(corpse).isPresent());
        assertEquals(durableBeforeLogout, store.read().orElseThrow().profiles().get(0));
        assertTrue(lifecycle.create(uuid(1826), "another", new MindId(uuid(1827)), corpse, ELIGIBLE).isEmpty(),
                "the returning Mind retains exclusive corpse ownership");

        var stagedAgain = store.withCurrentPrimary(primary -> lifecycle.stageDeadClaimGhost(
                primary, durableBeforeLogout, nextGhost, ELIGIBLE)).orElseThrow().orElseThrow();
        var activatedAgain = store.withCurrentPrimary(primary -> lifecycle.activateDeadClaimGhostDurably(
                primary, durableBeforeLogout, nextGhost, true, ELIGIBLE)).orElseThrow().orElseThrow();
        assertEquals(active.mindId(), activatedAgain.mindId());
        assertEquals(nextGhost, activatedAgain.bodyId());
        assertTrue(activatedAgain.connectionGeneration() > active.connectionGeneration());
        assertFalse(lifecycle.authorizes(account, active.connectionGeneration(), firstGhost, ELIGIBLE));
        assertTrue(lifecycle.authorizes(account, activatedAgain.connectionGeneration(), nextGhost, ELIGIBLE));
        assertTrue(ownership.registeredHarness(corpse).isPresent());
        assertEquals(corpse.value(), store.read().orElseThrow().profiles().get(0).bodyId());
    }

    @Test void missingActiveGhostFailsClosedToRecoveryInsteadOfRestoringCorpseClaim() throws Exception {
        UUID account = uuid(1830), mindId = uuid(1831), epoch = uuid(1832);
        MobHarnessId corpse = harnessId(1833), ghost = harnessId(1834);
        SavedLifecycleProfile dead = saved(account, "missing-ghost", mindId, corpse.value(), epoch, 0,
                SavedLifecycleProfile.State.DEAD_CLAIM);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("dead-ghost-missing.json"));
        store.compareAndSwap(-1, epoch, 0, java.util.List.of(dead));
        BodyControlRegistry ownership = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(ownership);
        harness(ownership, 1833, MobHarnessKind.CHARACTER);
        harness(ownership, 1834, MobHarnessKind.GHOST);
        store.withCurrentPrimary(primary -> lifecycle.stageDeadClaimGhost(primary, dead, ghost, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var active = store.withCurrentPrimary(primary -> lifecycle.activateDeadClaimGhostDurably(
                primary, dead, ghost, true, ELIGIBLE)).orElseThrow().orElseThrow();
        ownership.unregisterHarness(ghost);

        assertTrue(lifecycle.returnGhostToDeadClaim(account, active.mindId(), ghost, corpse,
                active.connectionGeneration()).isEmpty());
        var recovery = lifecycle.profile(account).orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.RECOVERY_REQUIRED, recovery.state());
        assertFalse(recovery.active());
        assertEquals(ghost, recovery.bodyId(), "do not manufacture a corpse attachment after ghost loss");
        assertFalse(lifecycle.authorizes(account, active.connectionGeneration(), ghost, ELIGIBLE));
        assertEquals(SavedLifecycleProfile.State.DEAD_CLAIM, store.read().orElseThrow().profiles().get(0).state());
        assertTrue(ownership.registeredHarness(corpse).isPresent());
    }

    @Test void sharedRegistryEnforcesExclusiveMindBodyOwnershipAndRejectedClaimsAreAtomic() {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        UUID account = uuid(1);
        MobHarness body = harness(shared, 2, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 3, MobHarnessKind.GHOST);
        MindId stableMind = new MindId(uuid(4));
        var debugMind = shared.createMind(uuid(5), ghost.id(), ELIGIBLE).orElseThrow();

        assertTrue(lifecycle.create(account, "profile", stableMind, body.id(), ELIGIBLE).isPresent());
        var created = lifecycle.profile(account).orElseThrow();
        assertTrue(lifecycle.create(uuid(6), "other", new MindId(uuid(7)), body.id(), ELIGIBLE).isEmpty());
        assertTrue(lifecycle.create(account, "another-profile", new MindId(uuid(9)), harnessId(10), ELIGIBLE).isEmpty());
        assertTrue(lifecycle.create(uuid(8), "other", stableMind, harnessId(11), ELIGIBLE).isEmpty());
        assertTrue(lifecycle.create(uuid(8), "other", new MindId(uuid(12)), ghost.id(), ELIGIBLE).isEmpty());
        assertTrue(lifecycle.create(uuid(8), "other", new MindId(uuid(13)), harnessId(14), ELIGIBLE).isEmpty());
        assertTrue(shared.createCharacterMind(uuid(5), new MindId(uuid(15)), body.id(), ELIGIBLE).isEmpty());
        assertEquals(created, lifecycle.profile(account).orElseThrow());
        assertEquals(debugMind, shared.mind(uuid(5)).orElseThrow());
        assertFalse(lifecycle.authorizes(account, created.connectionGeneration(), ghost.id()));
        assertTrue(lifecycle.authorizes(account, created.connectionGeneration(), body.id()));
    }

    @Test void firstEnrollmentStagesOnlyExactCurrentPreparingClaimAndNeverAuthorizes() throws Exception {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        var body = harness(shared, 900, MobHarnessKind.CHARACTER);
        var ghost = harness(shared, 901, MobHarnessKind.GHOST);
        UUID account = uuid(902), mind = uuid(903), epoch = uuid(904);
        SavedLifecycleProfile preparing = saved(account, "first", mind, body.id().value(), epoch, 0,
                SavedLifecycleProfile.State.PREPARING);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("first-enrollment.json"));
        store.compareAndSwap(-1, epoch, 0, java.util.List.of(preparing));

        var staged = store.withCurrentPrimary(primary -> lifecycle.stageFirstCharacter(primary, preparing, ELIGIBLE))
                .orElseThrow().orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.PREPARING, staged.state());
        assertFalse(staged.active());
        assertFalse(lifecycle.authorizes(account, staged.connectionGeneration(), body.id()));
        assertFalse(shared.authorizes(account, body.id(), staged.connectionGeneration(), ELIGIBLE));
        assertTrue(shared.mind(account).isPresent());
        assertEquals(body.id(), shared.mind(account).orElseThrow().harnessId());
        assertTrue(shared.createCharacterMind(uuid(905), new MindId(uuid(906)), ghost.id(), ELIGIBLE).isEmpty());
    }

    @Test void firstEnrollmentRejectsWrongReservationAndIneligibleOrOwnedBodyWithoutStaging() throws Exception {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        var ownedBody = harness(shared, 910, MobHarnessKind.CHARACTER);
        var ghost = harness(shared, 911, MobHarnessKind.GHOST);
        var owner = lifecycle.create(uuid(912), "m1", new MindId(uuid(913)), ownedBody.id(), ELIGIBLE).orElseThrow();
        UUID account = uuid(914), epoch = uuid(915);
        SavedLifecycleProfile preparing = saved(account, "new", uuid(916), ownedBody.id().value(), epoch, 0,
                SavedLifecycleProfile.State.PREPARING);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("first-enrollment-reject.json"));
        store.compareAndSwap(-1, epoch, 0, java.util.List.of(preparing));
        SavedLifecycleProfile mismatched = saved(account, "other", uuid(916), ghost.id().value(), epoch, 0,
                SavedLifecycleProfile.State.PREPARING);
        store.withCurrentPrimary(primary -> {
            assertTrue(lifecycle.stageFirstCharacter(primary, mismatched, ELIGIBLE).isEmpty());
            assertTrue(lifecycle.stageFirstCharacter(primary, preparing, target -> false).isEmpty());
            assertTrue(lifecycle.stageFirstCharacter(primary, preparing, ELIGIBLE).isEmpty(),
                    "a body already owned by an M1 Mind cannot be staged");
            return null;
        });
        assertTrue(shared.mind(account).isEmpty());
        assertEquals(owner, lifecycle.profile(uuid(912)).orElseThrow());
    }

    @Test void firstEnrollmentDurablePromotionActivatesExactStagedMindAndBodyWithNewGeneration() throws Exception {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        var body = harness(shared, 920, MobHarnessKind.CHARACTER);
        UUID account = uuid(921), mindId = uuid(922), epoch = uuid(923);
        var preparing = saved(account, "promote", mindId, body.id().value(), epoch, 0,
                SavedLifecycleProfile.State.PREPARING);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("promotion.json"));
        store.compareAndSwap(-1, epoch, 0, java.util.List.of(preparing));
        var staged = store.withCurrentPrimary(primary -> lifecycle.stageFirstCharacter(primary, preparing, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var priorMind = shared.mind(account).orElseThrow();
        assertFalse(lifecycle.authorizes(account, staged.connectionGeneration(), body.id()));

        var promoted = store.withCurrentPrimary(primary -> lifecycle.promoteFirstCharacterDurably(
                primary, preparing)).orElseThrow().orElseThrow();
        var durable = store.read().orElseThrow().profiles().get(0);
        var activeMind = shared.mind(account).orElseThrow();
        assertEquals(staged.mindId(), promoted.mindId());
        assertEquals(body.id(), promoted.bodyId());
        assertEquals(priorMind.id(), activeMind.id());
        assertEquals(priorMind.harnessId(), activeMind.harnessId());
        assertTrue(promoted.connectionGeneration() > staged.connectionGeneration());
        assertEquals(promoted.connectionGeneration(), activeMind.epoch());
        assertEquals(SavedLifecycleProfile.State.ACTIVE, durable.state());
        assertEquals(1, durable.revision());
        assertTrue(lifecycle.authorizes(account, promoted.connectionGeneration(), body.id()));
    }

    @Test void promotionRejectsWrongKindOwnerMismatchAndExpiredOrMismatchedProofWithoutCas() throws Exception {
        UUID epoch = uuid(934), account = uuid(935);
        var store = new LifecycleProfileStore(directory.resolve("promotion-reject.json"));
        var bodyShared = new BodyControlRegistry();
        var lifecycle = new PlayerLifecycleRegistry(bodyShared);
        var character = harness(bodyShared, 936, MobHarnessKind.CHARACTER);
        var ghost = harness(bodyShared, 937, MobHarnessKind.GHOST);
        var preparing = saved(account, "reject", uuid(938), character.id().value(), epoch, 0,
                SavedLifecycleProfile.State.PREPARING);
        store.compareAndSwap(-1, epoch, 0, java.util.List.of(preparing));
        var staged = store.withCurrentPrimary(primary -> lifecycle.stageFirstCharacter(primary, preparing, ELIGIBLE))
                .orElseThrow().orElseThrow();

        assertTrue(store.withCurrentPrimary(primary -> lifecycle.promoteFirstCharacterDurably(primary,
                saved(account, "mismatch", uuid(938), character.id().value(), epoch, 0,
                        SavedLifecycleProfile.State.PREPARING))).orElseThrow().isEmpty());
        LifecycleProfileStore.CurrentPrimary[] expired = new LifecycleProfileStore.CurrentPrimary[1];
        store.withCurrentPrimary(primary -> { expired[0] = primary; return null; });
        assertTrue(lifecycle.promoteFirstCharacterDurably(expired[0], preparing).isEmpty());
        assertTrue(lifecycle.profile(account).orElseThrow().equals(staged));
        assertEquals(0, store.read().orElseThrow().storeRevision());
        assertTrue(bodyShared.registeredHarness(ghost.id()).isPresent());

        // Registered ghost bodies and bodies claimed by another Mind cannot be staged/promoted.
        UUID ghostAccount = uuid(939);
        var wrongKind = saved(ghostAccount, "wrong-kind", uuid(940), ghost.id().value(), epoch, 0,
                SavedLifecycleProfile.State.PREPARING);
        var wrongKindStore = new LifecycleProfileStore(directory.resolve("promotion-wrong-kind.json"));
        wrongKindStore.compareAndSwap(-1, epoch, 0, java.util.List.of(wrongKind));
        assertTrue(wrongKindStore.withCurrentPrimary(primary -> lifecycle.stageFirstCharacter(primary,
                wrongKind, ELIGIBLE)).orElseThrow().isEmpty());
        UUID otherAccount = uuid(941);
        var ownedBody = harness(bodyShared, 942, MobHarnessKind.CHARACTER);
        lifecycle.create(uuid(943), "owner", new MindId(uuid(944)), ownedBody.id(), ELIGIBLE).orElseThrow();
        var ownedReservation = saved(otherAccount, "owned", uuid(945), ownedBody.id().value(), epoch, 0,
                SavedLifecycleProfile.State.PREPARING);
        var ownedStore = new LifecycleProfileStore(directory.resolve("promotion-owned.json"));
        ownedStore.compareAndSwap(-1, epoch, 0, java.util.List.of(ownedReservation));
        assertTrue(ownedStore.withCurrentPrimary(primary -> lifecycle.stageFirstCharacter(primary,
                ownedReservation, ELIGIBLE)).orElseThrow().isEmpty());

        assertThrows(NullPointerException.class, () -> store.withCurrentPrimary(primary -> {
            primary.promotePreparingToActive(null, 1, java.util.List.of());
            return null;
        }));
        assertEquals(0, store.read().orElseThrow().storeRevision(), "a forged/missing proof cannot promote the reservation");
    }

    @Test void promotionLostRegisteredBodyFailsBeforeCas() throws Exception {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        var body = harness(shared, 930, MobHarnessKind.CHARACTER);
        UUID account = uuid(931), epoch = uuid(932);
        var preparing = saved(account, "post-cas", uuid(933), body.id().value(), epoch, 0,
                SavedLifecycleProfile.State.PREPARING);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("promotion-post-cas.json"));
        store.compareAndSwap(-1, epoch, 0, java.util.List.of(preparing));
        var staged = store.withCurrentPrimary(primary -> lifecycle.stageFirstCharacter(primary, preparing, ELIGIBLE))
                .orElseThrow().orElseThrow();
        shared.unregisterHarness(body.id());
        var result = store.withCurrentPrimary(primary -> lifecycle.promoteFirstCharacterDurably(primary, preparing))
                .orElseThrow();
        assertTrue(result.isEmpty());
        assertEquals(SavedLifecycleProfile.State.PREPARING, store.read().orElseThrow().profiles().get(0).state());
        assertEquals(PlayerLifecycleRegistry.LifecycleState.PREPARING, lifecycle.profile(account).orElseThrow().state());
        assertFalse(lifecycle.authorizes(account, staged.connectionGeneration(), body.id()));
        assertFalse(shared.authorizes(account, body.id(), staged.connectionGeneration(), ELIGIBLE));
    }

    @Test void conventionalProfileKeyIsScopedToAccount() {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness firstBody = harness(shared, 160, MobHarnessKind.CHARACTER);
        MobHarness secondBody = harness(shared, 161, MobHarnessKind.CHARACTER);

        var first = lifecycle.create(uuid(162), "main", new MindId(uuid(163)), firstBody.id(), ELIGIBLE).orElseThrow();
        var second = lifecycle.create(uuid(164), "main", new MindId(uuid(165)), secondBody.id(), ELIGIBLE).orElseThrow();

        assertEquals("main", first.profileId());
        assertEquals("main", second.profileId());
        assertNotEquals(first.mindId(), second.mindId());
        assertNotEquals(first.bodyId(), second.bodyId());
        assertTrue(lifecycle.create(uuid(162), "secondary", new MindId(uuid(166)), harnessId(167), ELIGIBLE).isEmpty());
    }

    @Test void disconnectRetainsMindAndBodyReconnectsWithNewGenerationAndRejectsStaleGeneration() {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        UUID account = uuid(20);
        MobHarness body = harness(shared, 21, MobHarnessKind.CHARACTER);
        var initial = lifecycle.create(account, "profile", new MindId(uuid(22)), body.id(), ELIGIBLE).orElseThrow();
        assertEquals(PlayerLifecycleRegistry.Transition.CHANGED, lifecycle.disconnect(account, initial.connectionGeneration()));
        var offline = lifecycle.profile(account).orElseThrow();
        assertEquals(initial.mindId(), offline.mindId());
        assertEquals(body.id(), offline.bodyId());
        assertFalse(lifecycle.authorizes(account, initial.connectionGeneration(), body.id()));
        assertTrue(lifecycle.reconnect(account, initial.connectionGeneration(), true, null, ELIGIBLE).isEmpty());
        var reconnected = lifecycle.reconnect(account, offline.connectionGeneration(), true, null, ELIGIBLE).orElseThrow();
        assertEquals(initial.mindId(), reconnected.mindId());
        assertTrue(reconnected.connectionGeneration() > initial.connectionGeneration());
        assertFalse(lifecycle.authorizes(account, initial.connectionGeneration(), body.id()));
        assertTrue(lifecycle.authorizes(account, reconnected.connectionGeneration(), body.id()));
    }

    @Test void connectedActualDeathMovesSameMindToFreshGhostButLeavesCorpseRegistered() {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        UUID account = uuid(30);
        MobHarness corpse = harness(shared, 31, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 32, MobHarnessKind.GHOST);
        var initial = lifecycle.create(account, "profile", new MindId(uuid(33)), corpse.id(), ELIGIBLE).orElseThrow();
        assertTrue(lifecycle.markActualDeath(account, initial.connectionGeneration(), ghost.id(), ELIGIBLE, mob -> false).isEmpty());
        assertTrue(lifecycle.authorizes(account, initial.connectionGeneration(), corpse.id()));
        var dead = lifecycle.markActualDeath(account, initial.connectionGeneration(), ghost.id(), ELIGIBLE, mob -> true).orElseThrow();
        assertEquals(initial.mindId(), dead.mindId());
        assertEquals(ghost.id(), dead.bodyId());
        assertEquals(PlayerLifecycleRegistry.LifecycleState.GHOST, dead.state());
        assertTrue(dead.deadClaim());
        assertTrue(shared.registeredHarness(corpse.id()).isPresent());
        assertFalse(lifecycle.authorizes(account, initial.connectionGeneration(), corpse.id()));
        assertFalse(lifecycle.authorizes(account, initial.connectionGeneration(), ghost.id()));
        assertTrue(lifecycle.authorizes(account, dead.connectionGeneration(), ghost.id()));
        assertTrue(lifecycle.markActualDeath(account, initial.connectionGeneration(), ghost.id(), ELIGIBLE, mob -> true).isEmpty());
        assertTrue(lifecycle.markActualDeath(account, dead.connectionGeneration(), harnessId(34), ELIGIBLE, mob -> true).isEmpty());
        assertTrue(lifecycle.disconnect(account, dead.connectionGeneration()) == PlayerLifecycleRegistry.Transition.CHANGED);
        var ghostOffline = lifecycle.profile(account).orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.GHOST_OFFLINE, ghostOffline.state());
        assertTrue(lifecycle.reconnect(account, ghostOffline.connectionGeneration(), true,
                harnessId(35), ELIGIBLE).isEmpty());
        assertTrue(lifecycle.reconnect(account, ghostOffline.connectionGeneration(), true,
                null, ELIGIBLE).isEmpty());
    }

    @Test void offlineDeathClaimDoesNotPossessGhostUntilReconnectAndRejectsAliveDeathProof() {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        UUID account = uuid(40);
        MobHarness corpse = harness(shared, 41, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 42, MobHarnessKind.GHOST);
        var initial = lifecycle.create(account, "profile", new MindId(uuid(43)), corpse.id(), ELIGIBLE).orElseThrow();
        lifecycle.disconnect(account, initial.connectionGeneration());
        var offline = lifecycle.profile(account).orElseThrow();
        assertTrue(lifecycle.markActualDeath(account, offline.connectionGeneration(), null, ELIGIBLE, mob -> false).isEmpty());
        var claim = lifecycle.markActualDeath(account, offline.connectionGeneration(), null, ELIGIBLE, mob -> true).orElseThrow();
        assertEquals(corpse.id(), claim.bodyId());
        assertFalse(claim.active());
        assertEquals(offline.connectionGeneration(), claim.connectionGeneration());
        assertFalse(lifecycle.authorizes(account, claim.connectionGeneration(), corpse.id()));
        assertTrue(shared.registeredHarness(corpse.id()).isPresent());
        var reconnect = lifecycle.reconnect(account, claim.connectionGeneration(), true, ghost.id(), ELIGIBLE).orElseThrow();
        assertEquals(initial.mindId(), reconnect.mindId());
        assertEquals(ghost.id(), reconnect.bodyId());
        assertTrue(reconnect.active());
        assertEquals(PlayerLifecycleRegistry.LifecycleState.GHOST, reconnect.state());
        assertTrue(reconnect.deadClaim());
        assertFalse(lifecycle.authorizes(account, initial.connectionGeneration(), ghost.id()));
        assertTrue(lifecycle.authorizes(account, reconnect.connectionGeneration(), ghost.id()));
        assertFalse(lifecycle.authorizes(account, reconnect.connectionGeneration(), corpse.id()));
        assertTrue(shared.registeredHarness(corpse.id()).isPresent());
    }

    @Test void allRejectedInitialCallsLeaveNoClaimOrBodyGenerationMutation() {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = new MobHarness(harnessId(50), MobHarnessKind.CHARACTER);
        assertTrue(lifecycle.create(uuid(51), "p", new MindId(uuid(52)), body.id(), ELIGIBLE).isEmpty());
        assertTrue(lifecycle.registerHarness(body));
        assertTrue(lifecycle.create(uuid(51), "p", new MindId(uuid(52)), body.id(), mob -> false).isEmpty());
        assertTrue(lifecycle.profile(uuid(51)).isEmpty());
        var created = lifecycle.create(uuid(51), "p", new MindId(uuid(52)), body.id(), ELIGIBLE).orElseThrow();
        assertEquals(1, created.connectionGeneration());
    }

    @Test void sharedAuthorityRemovalAndEligibilityLossInvalidateProfileAuthorization() {
        for (String mutation : new String[] {"unregister", "eligibility"}) {
            BodyControlRegistry shared = new BodyControlRegistry();
            PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
            UUID account = uuid(100 + mutation.hashCode());
            MobHarness body = harness(shared, 110, MobHarnessKind.CHARACTER);
            var created = lifecycle.create(account, "profile-" + mutation, new MindId(uuid(111)), body.id(), ELIGIBLE).orElseThrow();
            if (mutation.equals("unregister")) shared.unregisterHarness(body.id());
            assertFalse(lifecycle.authorizes(account, created.connectionGeneration(), body.id(),
                    mutation.equals("eligibility") ? target -> false : ELIGIBLE), mutation);
        }
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        UUID account = uuid(109);
        MobHarness body = harness(shared, 110, MobHarnessKind.CHARACTER);
        var created = lifecycle.create(account, "protected", new MindId(uuid(111)), body.id(), ELIGIBLE).orElseThrow();
        assertEquals(BodyControlRegistry.OperationResult.ATTACHED_HARNESS_REQUIRED,
                shared.release(account, created.connectionGeneration(), BodyControlRegistry.ReleaseReason.FAILURE));
        assertEquals(BodyControlRegistry.OperationResult.ATTACHED_HARNESS_REQUIRED, shared.logout(account));
        assertTrue(lifecycle.authorizes(account, created.connectionGeneration(), body.id()));
    }

    @Test void reconnectRequiresEligibleLivingCharacterAndOfflineDeathCannotOrdinarilyRebind() {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        UUID account = uuid(120);
        MobHarness body = harness(shared, 121, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 122, MobHarnessKind.GHOST);
        var initial = lifecycle.create(account, "reconnect", new MindId(uuid(123)), body.id(), ELIGIBLE).orElseThrow();
        lifecycle.disconnect(account, initial.connectionGeneration());
        var offline = lifecycle.profile(account).orElseThrow();
        assertTrue(lifecycle.reconnect(account, offline.connectionGeneration(), true, null, target -> false).isEmpty());
        assertEquals(offline, lifecycle.profile(account).orElseThrow());
        assertTrue(lifecycle.markActualDeath(account, offline.connectionGeneration(), null, ELIGIBLE, target -> true).isPresent());
        assertTrue(lifecycle.reconnect(account, offline.connectionGeneration(), true, null, ELIGIBLE).isEmpty());
        assertTrue(lifecycle.reconnect(account, offline.connectionGeneration(), true, body.id(), ELIGIBLE).isEmpty());
        assertTrue(lifecycle.reconnect(account, offline.connectionGeneration(), true, ghost.id(), ELIGIBLE).isPresent());
    }

    @Test void debugMutationPathsCannotTakeOverLifecycleOwnedMind() {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        UUID account = uuid(130);
        MobHarness body = harness(shared, 131, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 132, MobHarnessKind.GHOST);
        var created = lifecycle.create(account, "owned", new MindId(uuid(133)), body.id(), ELIGIBLE).orElseThrow();
        assertEquals(BodyControlRegistry.OperationResult.ATTACHED_HARNESS_REQUIRED,
                shared.transfer(account, ghost.id(), created.connectionGeneration(), ELIGIBLE));
        assertEquals(BodyControlRegistry.OperationResult.ATTACHED_HARNESS_REQUIRED,
                shared.attach(account, ghost.id(), created.connectionGeneration(), ELIGIBLE));
        assertEquals(BodyControlRegistry.OperationResult.ATTACHED_HARNESS_REQUIRED,
                shared.release(account, created.connectionGeneration(), BodyControlRegistry.ReleaseReason.FAILURE));
        assertEquals(BodyControlRegistry.OperationResult.ATTACHED_HARNESS_REQUIRED, shared.logout(account));
        assertTrue(lifecycle.authorizes(account, created.connectionGeneration(), body.id()));
        assertFalse(shared.authorizes(account, body.id(), created.connectionGeneration(), target -> false));
        assertEquals(created, lifecycle.profile(account).orElseThrow());
        assertTrue(lifecycle.authorizes(account, created.connectionGeneration(), body.id()));
    }

    @Test void restoredReconnectPersistsGenerationBeforeAuthorizingSameMindAndBody() throws Exception {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        UUID account = uuid(180), mind = uuid(181), bodyUuid = uuid(182), epoch = uuid(183);
        MobHarness body = harness(shared, 182, MobHarnessKind.CHARACTER);
        SavedLifecycleProfile saved = saved(account, "persisted", mind, bodyUuid, epoch, 4,
                SavedLifecycleProfile.State.OFFLINE);
        var store = new LifecycleProfileStore(directory.resolve("durable-reconnect.json"));
        store.compareAndSwap(-1, epoch, 12, java.util.List.of(saved));
        var restored = store.withCurrentPrimary(proof -> lifecycle.restoreOffline(proof, saved, ELIGIBLE))
                .orElseThrow().orElseThrow();
        assertFalse(restored.active());
        assertEquals(PlayerLifecycleRegistry.LifecycleState.OFFLINE, restored.state());
        assertEquals(13, restored.connectionGeneration());
        assertEquals(mind, restored.mindId().value());
        assertEquals(body.id(), restored.bodyId());
        assertFalse(lifecycle.authorizes(account, restored.connectionGeneration(), body.id()));
        assertTrue(lifecycle.reconnect(account, restored.connectionGeneration(), true, null, ELIGIBLE).isEmpty(),
                "restored records cannot use the in-memory-only reconnect route");
        LifecycleProfileStore.CurrentPrimary[] expired = new LifecycleProfileStore.CurrentPrimary[1];
        var connected = store.withCurrentPrimary(proof -> {
            expired[0] = proof;
            return lifecycle.reconnectLivingDurably(proof, saved, true, ELIGIBLE);
        }).orElseThrow().orElseThrow();
        assertEquals(restored.mindId(), connected.mindId());
        assertEquals(restored.bodyId(), connected.bodyId());
        assertTrue(connected.connectionGeneration() > 12);
        var persisted = store.read().orElseThrow();
        assertEquals(1, persisted.storeRevision());
        assertEquals(connected.connectionGeneration(), persisted.generationCounter());
        assertEquals(SavedLifecycleProfile.State.ACTIVE, persisted.profiles().get(0).state());
        assertEquals(1, persisted.profiles().get(0).revision());
        assertNull(persisted.profiles().get(0).offlineSinceMillis());
        assertTrue(lifecycle.reconnectLivingDurably(expired[0], saved, true, ELIGIBLE).isEmpty());
        assertFalse(lifecycle.authorizes(account, restored.connectionGeneration(), body.id()));
        assertTrue(lifecycle.authorizes(account, connected.connectionGeneration(), body.id()));
    }

    @Test void durableReconnectRejectsStaleInvalidAndDeadClaimsWithoutGhostFallback() throws Exception {
        UUID epoch = uuid(184), account = uuid(185), mind = uuid(186), bodyUuid = uuid(187);
        SavedLifecycleProfile saved = saved(account, "durable", mind, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = harness(shared, 187, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 188, MobHarnessKind.GHOST);
        var store = new LifecycleProfileStore(directory.resolve("durable-reject.json"));
        store.compareAndSwap(-1, epoch, 4, java.util.List.of(saved));
        var restored = store.withCurrentPrimary(proof -> lifecycle.restoreOffline(proof, saved, ELIGIBLE))
                .orElseThrow().orElseThrow();
        store.withCurrentPrimary(proof -> {
            assertTrue(lifecycle.reconnectLivingDurably(proof,
                    saved(account, "durable", mind, bodyUuid, epoch, 2, SavedLifecycleProfile.State.OFFLINE),
                    true, ELIGIBLE).isEmpty());
            assertTrue(lifecycle.reconnectLivingDurably(proof, saved, false, ELIGIBLE).isEmpty());
            assertTrue(lifecycle.reconnectLivingDurably(proof, saved, true, target -> false).isEmpty());
            return null;
        });
        assertTrue(lifecycle.markActualDeath(account, restored.connectionGeneration(), null, ELIGIBLE,
                target -> true).isEmpty());
        assertEquals(restored, lifecycle.profile(account).orElseThrow());
        assertTrue(lifecycle.reconnect(account, restored.connectionGeneration(), true, ghost.id(), ELIGIBLE).isEmpty(),
                "restored offline claims cannot use the in-memory-only death-to-ghost path");
        assertFalse(lifecycle.authorizes(account, restored.connectionGeneration(), ghost.id()));
        var persisted = store.read().orElseThrow();
        assertEquals(0, persisted.storeRevision());
        assertEquals(SavedLifecycleProfile.State.OFFLINE, persisted.profiles().get(0).state());
        assertEquals(body.id(), restored.bodyId());
    }

    @Test void durableReconnectRejectsLostOwnedBodyWithoutCasOrAuthorization() throws Exception {
        UUID epoch = uuid(189), account = uuid(188), mind = uuid(187), bodyUuid = uuid(186);
        SavedLifecycleProfile saved = saved(account, "lost-body", mind, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = harness(shared, 186, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 185, MobHarnessKind.GHOST);
        var store = new LifecycleProfileStore(directory.resolve("durable-lost-body.json"));
        store.compareAndSwap(-1, epoch, 4, java.util.List.of(saved));
        var restored = store.withCurrentPrimary(proof -> lifecycle.restoreOffline(proof, saved, ELIGIBLE))
                .orElseThrow().orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.CHANGED, shared.unregisterHarness(body.id()));
        assertTrue(store.withCurrentPrimary(proof -> lifecycle.reconnectLivingDurably(proof, saved, true, ELIGIBLE))
                .orElseThrow().isEmpty());

        var persisted = store.read().orElseThrow();
        assertEquals(0, persisted.storeRevision());
        assertEquals(SavedLifecycleProfile.State.OFFLINE, persisted.profiles().get(0).state());
        assertEquals(restored, lifecycle.profile(account).orElseThrow());
        assertFalse(lifecycle.authorizes(account, restored.connectionGeneration(), body.id()));
        assertFalse(lifecycle.authorizes(account, restored.connectionGeneration(), ghost.id()));
    }

    @Test void durableReconnectCommitEligibilityFailureLeavesActiveRecordRecoveryRequiredAndMindBlocked() throws Exception {
        UUID epoch = uuid(194), account = uuid(195), mind = uuid(196), bodyUuid = uuid(197);
        SavedLifecycleProfile offline = saved(account, "reconnect-commit-failure", mind, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = harness(shared, 197, MobHarnessKind.CHARACTER);
        var store = new LifecycleProfileStore(directory.resolve("durable-reconnect-commit-failure.json"));
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline));
        var restored = store.withCurrentPrimary(primary -> lifecycle.restoreOffline(primary, offline, ELIGIBLE))
                .orElseThrow().orElseThrow();
        int[] eligibilityCalls = {0};
        TargetEligibility throwsAtCommit = target -> {
            if (++eligibilityCalls[0] == 2) throw new IllegalStateException("commit eligibility failed");
            return true;
        };

        assertTrue(store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(
                primary, offline, true, throwsAtCommit)).orElseThrow().isEmpty());
        assertEquals(2, eligibilityCalls[0], "eligibility succeeds at preview and throws at commit");

        var persisted = store.read().orElseThrow();
        SavedLifecycleProfile savedActive = persisted.profiles().get(0);
        assertEquals(1, persisted.storeRevision());
        assertEquals(SavedLifecycleProfile.State.ACTIVE, savedActive.state());
        assertEquals(1, savedActive.revision());
        assertNull(savedActive.offlineSinceMillis());
        var recovery = lifecycle.profile(account).orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.RECOVERY_REQUIRED, recovery.state());
        assertFalse(recovery.active());
        assertEquals(savedActive.connectionGeneration(), recovery.connectionGeneration());
        assertEquals(restored.mindId(), recovery.mindId());
        assertEquals(body.id(), recovery.bodyId());
        assertEquals(restored.mindId(), shared.mind(account).orElseThrow().id());
        assertEquals(body.id(), shared.mind(account).orElseThrow().harnessId());
        assertFalse(lifecycle.authorizes(account, restored.connectionGeneration(), body.id()));
        assertFalse(lifecycle.authorizes(account, recovery.connectionGeneration(), body.id()));
        assertTrue(store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(
                primary, offline, true, ELIGIBLE)).orElseThrow().isEmpty());
        assertTrue(lifecycle.reconnect(account, recovery.connectionGeneration(), true, null, ELIGIBLE).isEmpty());
    }

    @Test void activeDurableSessionCanBeSuspendedForRecoveryOnlyWithExactAuthorizedSession() throws Exception {
        UUID epoch = uuid(198), account = uuid(199), mindId = uuid(200), bodyUuid = uuid(201);
        SavedLifecycleProfile offline = saved(account, "controller-recovery", mindId, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = harness(shared, 201, MobHarnessKind.CHARACTER);
        MobHarness otherBody = harness(shared, 202, MobHarnessKind.CHARACTER);
        var store = new LifecycleProfileStore(directory.resolve("controller-recovery.json"));
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline));

        var restored = store.withCurrentPrimary(primary -> lifecycle.restoreOffline(primary, offline, ELIGIBLE))
                .orElseThrow().orElseThrow();
        assertFalse(lifecycle.suspendActiveSessionForRecovery(account, restored.mindId(), body.id(),
                restored.connectionGeneration()));
        var active = store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(
                primary, offline, true, ELIGIBLE)).orElseThrow().orElseThrow();
        assertTrue(lifecycle.authorizes(account, active.connectionGeneration(), body.id()));
        var mindBefore = shared.mind(account).orElseThrow();

        assertFalse(lifecycle.suspendActiveSessionForRecovery(uuid(203), active.mindId(), body.id(),
                active.connectionGeneration()));
        assertFalse(lifecycle.suspendActiveSessionForRecovery(account, new MindId(uuid(204)), body.id(),
                active.connectionGeneration()));
        assertFalse(lifecycle.suspendActiveSessionForRecovery(account, active.mindId(), otherBody.id(),
                active.connectionGeneration()));
        assertFalse(lifecycle.suspendActiveSessionForRecovery(account, active.mindId(), body.id(),
                active.connectionGeneration() - 1));
        assertTrue(lifecycle.authorizes(account, active.connectionGeneration(), body.id()),
                "rejected/stale requests leave the active exact session authorized");

        assertTrue(lifecycle.suspendActiveSessionForRecovery(account, active.mindId(), body.id(),
                active.connectionGeneration()));
        assertFalse(lifecycle.suspendActiveSessionForRecovery(account, active.mindId(), body.id(),
                active.connectionGeneration()), "suspending an already-revoked session is not a second success");
        var recovery = lifecycle.profile(account).orElseThrow();
        var suspendedMind = shared.mind(account).orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.RECOVERY_REQUIRED, recovery.state());
        assertFalse(recovery.active());
        assertEquals(active.connectionGeneration(), recovery.connectionGeneration());
        assertEquals(mindBefore.id(), suspendedMind.id());
        assertEquals(body.id(), suspendedMind.harnessId());
        assertEquals(active.connectionGeneration(), suspendedMind.epoch());
        assertTrue(shared.registeredHarness(body.id()).isPresent(), "body claim remains loaded/registered");
        assertFalse(lifecycle.authorizes(account, active.connectionGeneration(), body.id()));
        assertFalse(shared.authorizes(account, body.id(), active.connectionGeneration(), ELIGIBLE));
        assertTrue(lifecycle.reconnect(account, active.connectionGeneration(), true, null, ELIGIBLE).isEmpty(),
                "in-memory reconnect cannot bypass recovery for a durable profile");
        assertFalse(lifecycle.authorizes(account, active.connectionGeneration(), body.id()),
                "the old packet epoch remains revoked");
        assertEquals(SavedLifecycleProfile.State.ACTIVE, store.read().orElseThrow().profiles().get(0).state());
        assertEquals(active.connectionGeneration(), store.read().orElseThrow().profiles().get(0).connectionGeneration());
    }

    @Test void durableDisconnectPersistsOfflineBeforeRetiringSameMindAndBody() throws Exception {
        UUID epoch = uuid(220), account = uuid(221), mind = uuid(222), bodyUuid = uuid(223);
        SavedLifecycleProfile offline = saved(account, "durable-disconnect", mind, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = harness(shared, 223, MobHarnessKind.CHARACTER);
        var store = new LifecycleProfileStore(directory.resolve("durable-disconnect.json"));
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline));
        var restored = store.withCurrentPrimary(proof -> lifecycle.restoreOffline(proof, offline, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var connected = store.withCurrentPrimary(proof -> lifecycle.reconnectLivingDurably(proof, offline, true, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var active = store.read().orElseThrow().profiles().get(0);
        assertEquals(SavedLifecycleProfile.State.ACTIVE, active.state());
        assertEquals(restored.mindId(), connected.mindId());
        assertEquals(body.id(), connected.bodyId());
        assertEquals(PlayerLifecycleRegistry.Transition.STALE_GENERATION,
                lifecycle.disconnect(account, connected.connectionGeneration()),
                "the in-memory path cannot bypass durable OFFLINE persistence");

        assertTrue(store.withCurrentPrimary(proof -> lifecycle.disconnectDurably(proof, active,
                connected.connectionGeneration() - 1, 9876)).orElseThrow().isEmpty(), "stale epoch is a no-op");
        assertEquals(1, store.read().orElseThrow().storeRevision());
        LifecycleProfileStore.CurrentPrimary[] expired = new LifecycleProfileStore.CurrentPrimary[1];
        var disconnected = store.withCurrentPrimary(proof -> {
            expired[0] = proof;
            return lifecycle.disconnectDurably(proof, active, connected.connectionGeneration(), 9876);
        }).orElseThrow().orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.OFFLINE, disconnected.state());
        assertFalse(disconnected.active());
        assertEquals(connected.mindId(), disconnected.mindId());
        assertEquals(body.id(), disconnected.bodyId());
        assertTrue(disconnected.connectionGeneration() > connected.connectionGeneration());
        assertFalse(lifecycle.authorizes(account, connected.connectionGeneration(), body.id()));
        assertFalse(lifecycle.authorizes(account, disconnected.connectionGeneration(), body.id()));

        var persisted = store.read().orElseThrow();
        SavedLifecycleProfile savedOffline = persisted.profiles().get(0);
        assertEquals(2, persisted.storeRevision());
        assertEquals(SavedLifecycleProfile.State.OFFLINE, savedOffline.state());
        assertEquals(disconnected.connectionGeneration(), savedOffline.connectionGeneration());
        assertEquals(2, savedOffline.revision());
        assertEquals(9876L, savedOffline.offlineSinceMillis());
        assertEquals(active.mindId(), savedOffline.mindId());
        assertEquals(active.bodyId(), savedOffline.bodyId());
        assertEquals(active.appearance(), savedOffline.appearance());
        assertEquals(active.location(), savedOffline.location());
        assertTrue(lifecycle.disconnectDurably(expired[0], active, connected.connectionGeneration(), 9999).isEmpty());
        assertTrue(store.withCurrentPrimary(proof -> lifecycle.disconnectDurably(proof, active,
                connected.connectionGeneration(), 9999)).orElseThrow().isEmpty(), "duplicate disconnect is a no-op");
        assertEquals(savedOffline, store.read().orElseThrow().profiles().get(0));
        assertEquals(PlayerLifecycleRegistry.Transition.STALE_GENERATION,
                lifecycle.disconnect(account, disconnected.connectionGeneration()));
    }

    @Test void durableDisconnectCommitEligibilityFailureRevokesSessionAndRequiresRecovery() throws Exception {
        UUID epoch = uuid(224), account = uuid(225), mind = uuid(226), bodyUuid = uuid(227);
        SavedLifecycleProfile offline = saved(account, "disconnect-commit-failure", mind, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = harness(shared, 227, MobHarnessKind.CHARACTER);
        var store = new LifecycleProfileStore(directory.resolve("durable-disconnect-commit-failure.json"));
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline));
        store.withCurrentPrimary(proof -> lifecycle.restoreOffline(proof, offline, ELIGIBLE)).orElseThrow().orElseThrow();
        var connected = store.withCurrentPrimary(proof -> lifecycle.reconnectLivingDurably(proof, offline, true, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var active = store.read().orElseThrow().profiles().get(0);
        int[] eligibilityCalls = {0};
        TargetEligibility throwsAtCommit = target -> {
            if (++eligibilityCalls[0] == 2) throw new IllegalStateException("commit eligibility failed");
            return true;
        };

        assertTrue(store.withCurrentPrimary(proof -> lifecycle.disconnectDurably(proof, active,
                connected.connectionGeneration(), 9877, throwsAtCommit)).orElseThrow().isEmpty());

        var persisted = store.read().orElseThrow();
        assertEquals(SavedLifecycleProfile.State.OFFLINE, persisted.profiles().get(0).state());
        assertEquals(2, persisted.storeRevision());
        var recovery = lifecycle.profile(account).orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.RECOVERY_REQUIRED, recovery.state());
        assertFalse(recovery.active());
        assertFalse(lifecycle.authorizes(account, connected.connectionGeneration(), body.id()));
        assertTrue(shared.mind(account).isPresent(), "the same Mind remains retained for recovery");
        assertTrue(store.withCurrentPrimary(proof -> lifecycle.disconnectDurably(proof,
                persisted.profiles().get(0), recovery.connectionGeneration(), 9999)).orElseThrow().isEmpty());
        assertTrue(lifecycle.reconnect(account, recovery.connectionGeneration(), true, null, ELIGIBLE).isEmpty());
        assertFalse(lifecycle.authorizes(account, connected.connectionGeneration(), body.id()));
        assertEquals(SavedLifecycleProfile.State.OFFLINE, store.read().orElseThrow().profiles().get(0).state());
    }

    @Test void restoredOfflineDeathPersistsDeadClaimBeforeRetainingDisconnectedCorpseMind() throws Exception {
        UUID epoch = uuid(950), account = uuid(951), mind = uuid(952), bodyUuid = uuid(953);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness corpse = harness(shared, 953, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 954, MobHarnessKind.GHOST);
        LifecycleProfileStore store = new LifecycleProfileStore(directory.resolve("durable-death-claim.json"));
        SavedLifecycleProfile offline0 = saved(account, "death", mind, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline0));
        var restored = store.withCurrentPrimary(primary -> lifecycle.restoreOffline(primary, offline0, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var active = store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(primary, offline0, true, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var activeSaved = store.read().orElseThrow().profiles().get(0);
        var disconnected = store.withCurrentPrimary(primary -> lifecycle.disconnectDurably(primary, activeSaved,
                active.connectionGeneration(), 1234)).orElseThrow().orElseThrow();
        SavedLifecycleProfile savedOffline = store.read().orElseThrow().profiles().get(0);

        assertTrue(store.withCurrentPrimary(primary -> lifecycle.markOfflineDeathDurably(primary, savedOffline,
                disconnected.connectionGeneration(), body -> false)).orElseThrow().isEmpty());
        assertEquals(SavedLifecycleProfile.State.OFFLINE, store.read().orElseThrow().profiles().get(0).state());
        assertEquals(disconnected, lifecycle.profile(account).orElseThrow());

        var deathClaim = store.withCurrentPrimary(primary -> lifecycle.markOfflineDeathDurably(primary, savedOffline,
                disconnected.connectionGeneration(), body -> body == corpse)).orElseThrow().orElseThrow();
        SavedLifecycleProfile persisted = store.read().orElseThrow().profiles().get(0);
        assertEquals(PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM, deathClaim.state());
        assertFalse(deathClaim.active());
        assertTrue(deathClaim.deadClaim());
        assertEquals(corpse.id(), deathClaim.bodyId());
        assertTrue(deathClaim.connectionGeneration() > disconnected.connectionGeneration());
        assertEquals(SavedLifecycleProfile.State.DEAD_CLAIM, persisted.state());
        assertEquals(deathClaim.connectionGeneration(), persisted.connectionGeneration());
        assertEquals(savedOffline.revision() + 1, persisted.revision());
        assertEquals(savedOffline.offlineSinceMillis(), persisted.offlineSinceMillis());
        assertEquals(savedOffline.dimension(), persisted.dimension());
        assertEquals(savedOffline.location(), persisted.location());
        assertEquals(savedOffline.appearance(), persisted.appearance());
        assertEquals(3, store.read().orElseThrow().storeRevision());
        assertEquals(corpse.id(), shared.mind(account).orElseThrow().harnessId());
        assertEquals(deathClaim.connectionGeneration(), shared.mind(account).orElseThrow().epoch());
        assertTrue(shared.registeredHarness(corpse.id()).isPresent());
        assertFalse(lifecycle.authorizes(account, deathClaim.connectionGeneration(), corpse.id()));
        assertFalse(lifecycle.authorizes(account, deathClaim.connectionGeneration(), ghost.id()));
        assertTrue(lifecycle.markActualDeath(account, deathClaim.connectionGeneration(), null, ELIGIBLE,
                body -> true).isEmpty(), "restored profiles cannot take the in-memory-only death route");
        assertTrue(store.withCurrentPrimary(primary -> lifecycle.markOfflineDeathDurably(primary, savedOffline,
                deathClaim.connectionGeneration(), body -> true)).orElseThrow().isEmpty(), "duplicate death is rejected");
        assertEquals(SavedLifecycleProfile.State.DEAD_CLAIM, store.read().orElseThrow().profiles().get(0).state());
        assertEquals(restored.mindId(), deathClaim.mindId());
    }

    @Test void durableDeathRejectsStaleAccountEpochAndMissingCorpseWithoutWriting() throws Exception {
        UUID epoch = uuid(960), account = uuid(961), mind = uuid(962), bodyUuid = uuid(963);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness corpse = harness(shared, 963, MobHarnessKind.CHARACTER);
        var offline = saved(account, "death-stale", mind, bodyUuid, epoch, 1, SavedLifecycleProfile.State.OFFLINE);
        var store = new LifecycleProfileStore(directory.resolve("durable-death-stale.json"));
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline));
        var restored = store.withCurrentPrimary(primary -> lifecycle.restoreOffline(primary, offline, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var active = store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(primary, offline, true, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var activeSaved = store.read().orElseThrow().profiles().get(0);
        var disconnected = store.withCurrentPrimary(primary -> lifecycle.disconnectDurably(primary, activeSaved,
                active.connectionGeneration(), 2222)).orElseThrow().orElseThrow();
        var currentOffline = store.read().orElseThrow().profiles().get(0);
        store.withCurrentPrimary(primary -> {
            assertTrue(lifecycle.markOfflineDeathDurably(primary, currentOffline,
                    disconnected.connectionGeneration() - 1, body -> true).isEmpty());
            assertTrue(lifecycle.markOfflineDeathDurably(primary,
                    saved(uuid(999), "death-stale", mind, bodyUuid, epoch, currentOffline.connectionGeneration(),
                            SavedLifecycleProfile.State.OFFLINE), disconnected.connectionGeneration(), body -> true).isEmpty());
            assertTrue(lifecycle.markOfflineDeathDurably(primary, currentOffline,
                    disconnected.connectionGeneration(), body -> false).isEmpty());
            return null;
        });
        assertEquals(2, store.read().orElseThrow().storeRevision());
        assertEquals(SavedLifecycleProfile.State.OFFLINE, store.read().orElseThrow().profiles().get(0).state());
        assertEquals(disconnected, lifecycle.profile(account).orElseThrow());
        assertFalse(lifecycle.authorizes(account, restored.connectionGeneration(), corpse.id()));
    }

    @Test void durableDeathStoreCasFailureLeavesOfflineMemoryAndMindUnclaimed() throws Exception {
        UUID epoch = uuid(970), account = uuid(971), mind = uuid(972), bodyUuid = uuid(973);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness corpse = harness(shared, 973, MobHarnessKind.CHARACTER);
        var offline = saved(account, "death-cas", mind, bodyUuid, epoch, 1, SavedLifecycleProfile.State.OFFLINE);
        Path path = directory.resolve("durable-death-cas.json");
        var store = new LifecycleProfileStore(path);
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline));
        var restored = store.withCurrentPrimary(primary -> lifecycle.restoreOffline(primary, offline, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var active = store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(primary, offline, true, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var activeSaved = store.read().orElseThrow().profiles().get(0);
        var disconnected = store.withCurrentPrimary(primary -> lifecycle.disconnectDurably(primary, activeSaved,
                active.connectionGeneration(), 3333)).orElseThrow().orElseThrow();
        var currentOffline = store.read().orElseThrow().profiles().get(0);
        Path orphan = path.resolveSibling(path.getFileName() + ".tmp");

        assertThrows(java.io.IOException.class, () -> store.withCurrentPrimary(primary ->
                lifecycle.markOfflineDeathDurably(primary, currentOffline, disconnected.connectionGeneration(), body -> {
                    try { java.nio.file.Files.createFile(orphan); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return true;
                })));
        java.nio.file.Files.delete(orphan);
        assertEquals(SavedLifecycleProfile.State.OFFLINE, store.read().orElseThrow().profiles().get(0).state());
        assertEquals(disconnected, lifecycle.profile(account).orElseThrow());
        assertFalse(disconnected.deadClaim());
        assertEquals(corpse.id(), shared.mind(account).orElseThrow().harnessId());
        assertEquals(disconnected.connectionGeneration(), shared.mind(account).orElseThrow().epoch());
        assertFalse(lifecycle.authorizes(account, disconnected.connectionGeneration(), corpse.id()));
        assertTrue(restored.connectionGeneration() < disconnected.connectionGeneration());
    }

    @Test void connectedDurableDeathCasClaimsCorpseBeforeSuspendingMindWithoutGhost() throws Exception {
        UUID epoch = uuid(980), account = uuid(981), mind = uuid(982), bodyUuid = uuid(983);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness corpse = harness(shared, 983, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 984, MobHarnessKind.GHOST);
        SavedLifecycleProfile offline = saved(account, "connected-death", mind, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        var store = new LifecycleProfileStore(directory.resolve("connected-death.json"));
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline));
        var restored = store.withCurrentPrimary(primary -> lifecycle.restoreOffline(primary, offline, ELIGIBLE))
                .orElseThrow().orElseThrow();
        var active = store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(
                primary, offline, true, ELIGIBLE)).orElseThrow().orElseThrow();
        SavedLifecycleProfile activeSaved = store.read().orElseThrow().profiles().get(0);
        SavedLifecycleProfile updatedBody = new SavedLifecycleProfile(activeSaved.schemaVersion(), account,
                activeSaved.profileKey(), mind, bodyUuid, "moonstation14:station",
                new SavedLifecycleProfile.Location(12.5, 64, -7.25), java.util.Map.of("look", "corpse"),
                activeSaved.connectionEpoch(), activeSaved.connectionGeneration(), SavedLifecycleProfile.State.ACTIVE,
                activeSaved.revision(), null);

        assertTrue(lifecycle.markActualDeath(account, active.connectionGeneration(), ghost.id(), ELIGIBLE,
                body -> true).isEmpty(), "durable ACTIVE cannot bypass the store CAS with the M1 path");
        var claim = store.withCurrentPrimary(primary -> lifecycle.markActiveDeathDurably(primary, activeSaved,
                updatedBody, active.connectionGeneration(), 9876, body -> body == corpse))
                .orElseThrow().orElseThrow();
        SavedLifecycleProfile persisted = store.read().orElseThrow().profiles().get(0);
        assertEquals(SavedLifecycleProfile.State.DEAD_CLAIM, persisted.state());
        assertEquals(2, persisted.revision());
        assertEquals(9876L, persisted.offlineSinceMillis());
        assertEquals(updatedBody.dimension(), persisted.dimension());
        assertEquals(updatedBody.location(), persisted.location());
        assertEquals(updatedBody.appearance(), persisted.appearance());
        assertTrue(claim.connectionGeneration() > active.connectionGeneration());
        assertEquals(PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM, claim.state());
        assertFalse(claim.active());
        assertTrue(claim.deadClaim());
        assertEquals(corpse.id(), claim.bodyId());
        assertEquals(corpse.id(), shared.mind(account).orElseThrow().harnessId());
        assertEquals(claim.connectionGeneration(), shared.mind(account).orElseThrow().epoch());
        assertTrue(shared.registeredHarness(corpse.id()).isPresent());
        assertFalse(lifecycle.authorizes(account, claim.connectionGeneration(), corpse.id()));
        assertFalse(lifecycle.authorizes(account, claim.connectionGeneration(), ghost.id()));
        assertTrue(lifecycle.reconnect(account, claim.connectionGeneration(), true, null, ELIGIBLE).isEmpty());
    }

    @Test void connectedDurableDeathRejectsFalseThrowingAndStaleEvidenceWithoutMutation() throws Exception {
        UUID epoch = uuid(985), account = uuid(986), mind = uuid(987), bodyUuid = uuid(988);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness corpse = harness(shared, 988, MobHarnessKind.CHARACTER);
        var offline = saved(account, "connected-death-reject", mind, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        var store = new LifecycleProfileStore(directory.resolve("connected-death-reject.json"));
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline));
        store.withCurrentPrimary(primary -> lifecycle.restoreOffline(primary, offline, ELIGIBLE)).orElseThrow().orElseThrow();
        var active = store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(
                primary, offline, true, ELIGIBLE)).orElseThrow().orElseThrow();
        SavedLifecycleProfile activeSaved = store.read().orElseThrow().profiles().get(0);
        store.withCurrentPrimary(primary -> {
            assertTrue(lifecycle.markActiveDeathDurably(primary, activeSaved, activeSaved,
                    active.connectionGeneration() - 1, 123, body -> true).isEmpty());
            assertTrue(lifecycle.markActiveDeathDurably(primary, activeSaved, activeSaved,
                    active.connectionGeneration(), 123, body -> false).isEmpty());
            assertTrue(lifecycle.markActiveDeathDurably(primary, activeSaved, activeSaved,
                    active.connectionGeneration(), 123, body -> { throw new IllegalStateException("untrusted proof"); }).isEmpty());
            return null;
        });
        assertEquals(1, store.read().orElseThrow().storeRevision());
        assertEquals(SavedLifecycleProfile.State.ACTIVE, store.read().orElseThrow().profiles().get(0).state());
        assertEquals(active, lifecycle.profile(account).orElseThrow());
        assertTrue(lifecycle.authorizes(account, active.connectionGeneration(), corpse.id()));
        assertEquals(active.connectionGeneration(), shared.mind(account).orElseThrow().epoch());
    }

    @Test void connectedDurableDeathCasFailureLeavesActiveAuthorityUnchanged() throws Exception {
        UUID epoch = uuid(989), account = uuid(990), mind = uuid(991), bodyUuid = uuid(992);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness corpse = harness(shared, 992, MobHarnessKind.CHARACTER);
        var offline = saved(account, "connected-death-cas", mind, bodyUuid, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        Path path = directory.resolve("connected-death-cas.json");
        var store = new LifecycleProfileStore(path);
        store.compareAndSwap(-1, epoch, 5, java.util.List.of(offline));
        store.withCurrentPrimary(primary -> lifecycle.restoreOffline(primary, offline, ELIGIBLE)).orElseThrow().orElseThrow();
        var active = store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(
                primary, offline, true, ELIGIBLE)).orElseThrow().orElseThrow();
        SavedLifecycleProfile activeSaved = store.read().orElseThrow().profiles().get(0);
        Path orphan = path.resolveSibling(path.getFileName() + ".tmp");
        assertThrows(java.io.IOException.class, () -> store.withCurrentPrimary(primary ->
                lifecycle.markActiveDeathDurably(primary, activeSaved, activeSaved,
                        active.connectionGeneration(), 123, body -> {
                            try { java.nio.file.Files.createFile(orphan); }
                            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                            return true;
                        })));
        java.nio.file.Files.delete(orphan);
        assertEquals(SavedLifecycleProfile.State.ACTIVE, store.read().orElseThrow().profiles().get(0).state());
        assertEquals(active, lifecycle.profile(account).orElseThrow());
        assertTrue(lifecycle.authorizes(account, active.connectionGeneration(), corpse.id()));
        assertEquals(corpse.id(), shared.mind(account).orElseThrow().harnessId());
        assertEquals(active.connectionGeneration(), shared.mind(account).orElseThrow().epoch());
    }

    @Test void restoreRejectsInvalidSavedProfilesWithoutPartialMutation() {
        UUID epoch = uuid(190), account = uuid(191), mind = uuid(192), bodyId = uuid(193);
        SavedLifecycleProfile offline = saved(account, "restore", mind, bodyId, epoch, 2,
                SavedLifecycleProfile.State.OFFLINE);
        for (SavedLifecycleProfile.State state : new SavedLifecycleProfile.State[] {
                SavedLifecycleProfile.State.ACTIVE, SavedLifecycleProfile.State.GHOST,
                SavedLifecycleProfile.State.GHOST_OFFLINE, SavedLifecycleProfile.State.DEAD_CLAIM }) {
            BodyControlRegistry shared = new BodyControlRegistry();
            PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
            MobHarness body = new MobHarness(new MobHarnessId(bodyId), MobHarnessKind.CHARACTER);
            assertTrue(lifecycle.registerHarness(body));
            SavedLifecycleProfile unsupported = saved(account, "restore", mind, bodyId, epoch, 2, state);
            assertTrue(restore(lifecycle, envelope(epoch, 9, unsupported), unsupported, ELIGIBLE).isEmpty());
            assertTrue(lifecycle.profile(account).isEmpty());
            assertFalse(lifecycle.authorizes(account, 10, body.id()));
            assertTrue(lifecycle.create(account, "restore", new MindId(mind), body.id(), ELIGIBLE).isPresent());
        }

        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness character = new MobHarness(new MobHarnessId(bodyId), MobHarnessKind.CHARACTER);
        assertTrue(lifecycle.registerHarness(character));
        SavedLifecycleProfile otherRecord = saved(uuid(194), "other", uuid(195), uuid(196), epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        assertTrue(restore(lifecycle, envelope(epoch, 9, otherRecord), offline, ELIGIBLE).isEmpty());
        assertTrue(restore(lifecycle, envelope(epoch, 9, offline), offline, target -> false).isEmpty());
        assertTrue(lifecycle.profile(account).isEmpty());
        assertTrue(lifecycle.create(account, "restore", new MindId(mind), character.id(), ELIGIBLE).isPresent());

        BodyControlRegistry unregistered = new BodyControlRegistry();
        PlayerLifecycleRegistry noBody = new PlayerLifecycleRegistry(unregistered);
        assertTrue(restore(noBody, envelope(epoch, 9, offline), offline, ELIGIBLE).isEmpty());
        assertTrue(noBody.profile(account).isEmpty());
        assertTrue(noBody.registerHarness(character));
        assertTrue(noBody.create(account, "restore", new MindId(mind), character.id(), ELIGIBLE).isPresent());

        BodyControlRegistry ghostShared = new BodyControlRegistry();
        PlayerLifecycleRegistry ghostLifecycle = new PlayerLifecycleRegistry(ghostShared);
        MobHarness ghost = harness(ghostShared, 193, MobHarnessKind.GHOST);
        assertTrue(restore(ghostLifecycle, envelope(epoch, 9, offline), offline, ELIGIBLE).isEmpty());
        assertTrue(ghostLifecycle.profile(account).isEmpty());
    }

    @Test void restoreRejectsDuplicateOwnersAndGenerationOverflowAtomically() {
        UUID epoch = uuid(200), account = uuid(201), mind = uuid(202), bodyId = uuid(203);
        SavedLifecycleProfile saved = saved(account, "restore", mind, bodyId, epoch, 2,
                SavedLifecycleProfile.State.OFFLINE);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = harness(shared, 203, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 204, MobHarnessKind.GHOST);
        var debug = shared.createMind(uuid(205), ghost.id(), ELIGIBLE).orElseThrow();
        assertEquals(BodyControlRegistry.OperationResult.CHANGED,
                shared.transfer(uuid(205), body.id(), debug.epoch(), ELIGIBLE));
        assertTrue(restore(lifecycle, envelope(epoch, 5, saved), saved, ELIGIBLE).isEmpty());
        assertTrue(lifecycle.profile(account).isEmpty());
        assertTrue(shared.mind(uuid(205)).isPresent());

        BodyControlRegistry overflowShared = new BodyControlRegistry();
        PlayerLifecycleRegistry overflowLifecycle = new PlayerLifecycleRegistry(overflowShared);
        MobHarness overflowBody = harness(overflowShared, 203, MobHarnessKind.CHARACTER);
        assertTrue(restore(overflowLifecycle, envelope(epoch, Long.MAX_VALUE, saved), saved, ELIGIBLE).isEmpty());
        assertTrue(overflowLifecycle.profile(account).isEmpty());
        assertEquals(1, overflowLifecycle.create(account, "restore", new MindId(mind), overflowBody.id(), ELIGIBLE)
                .orElseThrow().connectionGeneration());
    }

    @Test void restoreRequiresCurrentPrimaryMembershipAndRejectsArbitraryOrExpiredProofWithoutMutation() throws Exception {
        UUID epoch = uuid(210), account = uuid(211), mind = uuid(212), bodyId = uuid(213);
        SavedLifecycleProfile oldPrimaryRecord = saved(account, "proof", mind, bodyId, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        SavedLifecycleProfile currentRecord = saved(account, "proof", mind, bodyId, epoch, 2,
                SavedLifecycleProfile.State.OFFLINE);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = harness(shared, 213, MobHarnessKind.CHARACTER);
        var store = new LifecycleProfileStore(directory.resolve("proof-current.json"));
        store.compareAndSwap(-1, epoch, 1, java.util.List.of(oldPrimaryRecord));
        store.compareAndSwap(0, epoch, 2, java.util.List.of(currentRecord));
        LifecycleProfileStore.CurrentPrimary[] expired = new LifecycleProfileStore.CurrentPrimary[1];

        var oldRecordResult = store.withCurrentPrimary(proof -> {
            expired[0] = proof;
            return lifecycle.restoreOffline(proof, oldPrimaryRecord, ELIGIBLE);
        }).orElseThrow();
        assertTrue(oldRecordResult.isEmpty(), "same-epoch backup membership is not current-primary proof");
        assertTrue(lifecycle.profile(account).isEmpty());

        assertTrue(lifecycle.restoreOffline(expired[0], currentRecord, ELIGIBLE).isEmpty());
        assertTrue(lifecycle.profile(account).isEmpty());
        assertTrue(lifecycle.create(account, "proof", new MindId(mind), body.id(), ELIGIBLE).isPresent(),
                "rejected proof must not publish a Mind or account claim");
    }

    @Test void directLifecycleRegistryCallsAndSecondFacadeCannotBypassProfileAuthority() {
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        UUID account = uuid(140);
        MobHarness body = harness(shared, 141, MobHarnessKind.CHARACTER);
        MobHarness ghost = harness(shared, 142, MobHarnessKind.GHOST);
        var created = lifecycle.create(account, "protected", new MindId(uuid(143)), body.id(), ELIGIBLE).orElseThrow();
        assertThrows(IllegalStateException.class, () -> new PlayerLifecycleRegistry(shared));

        assertTrue(shared.createCharacterMind(account, created.mindId(), body.id(), ELIGIBLE).isEmpty());
        assertTrue(shared.createCharacterMind(account, created.mindId(), body.id(), ELIGIBLE, null).isEmpty());
        assertTrue(shared.disconnectLifecycle(account, created.connectionGeneration()).isEmpty());
        assertTrue(shared.disconnectLifecycle(account, created.connectionGeneration(), null).isEmpty());
        assertTrue(shared.reconnectLifecycle(account, created.connectionGeneration(), ELIGIBLE).isEmpty());
        assertTrue(shared.transferActualDeath(account, created.connectionGeneration(), ghost.id(), ELIGIBLE,
                mob -> true).isEmpty());
        assertFalse(shared.claimOfflineDeath(account, created.connectionGeneration()));
        assertTrue(shared.reconnectDeadClaim(account, created.connectionGeneration(), ghost.id(), ELIGIBLE).isEmpty());
        assertEquals(created, lifecycle.profile(account).orElseThrow());
        assertTrue(lifecycle.authorizes(account, created.connectionGeneration(), body.id()));

        var offline = lifecycle.disconnect(account, created.connectionGeneration());
        assertEquals(PlayerLifecycleRegistry.Transition.CHANGED, offline);
        var disconnected = lifecycle.profile(account).orElseThrow();
        assertTrue(shared.reconnectDeadClaim(account, disconnected.connectionGeneration(), ghost.id(), ELIGIBLE).isEmpty());
        assertFalse(shared.claimOfflineDeath(account, disconnected.connectionGeneration()));
        assertEquals(disconnected, lifecycle.profile(account).orElseThrow());
        assertEquals(PlayerLifecycleRegistry.LifecycleState.OFFLINE, disconnected.state());
        var rebound = lifecycle.reconnect(account, disconnected.connectionGeneration(), true, null, ELIGIBLE).orElseThrow();
        assertEquals(disconnected.connectionGeneration() + 1, rebound.connectionGeneration());
    }

    @Test void cleanDisconnectPersistsAuthoritativeLocationAndBothAppearanceOptionsBeforeRevokingInput() throws Exception {
        UUID epoch = uuid(214), account = uuid(215), mind = uuid(216), bodyId = uuid(217);
        BodyControlRegistry shared = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(shared);
        MobHarness body = harness(shared, 217, MobHarnessKind.CHARACTER);
        var store = new LifecycleProfileStore(directory.resolve("clean-disconnect.json"));
        SavedLifecycleProfile offline = saved(account, "clean", mind, bodyId, epoch, 1,
                SavedLifecycleProfile.State.OFFLINE);
        store.compareAndSwap(-1, epoch, 4, java.util.List.of(offline));
        store.withCurrentPrimary(primary -> lifecycle.restoreOffline(primary, offline, ELIGIBLE)).orElseThrow().orElseThrow();
        var active = store.withCurrentPrimary(primary -> lifecycle.reconnectLivingDurably(primary, offline, true, ELIGIBLE))
                .orElseThrow().orElseThrow();
        SavedLifecycleProfile oldActive = store.read().orElseThrow().profiles().get(0);
        SavedLifecycleProfile updated = new SavedLifecycleProfile(oldActive.schemaVersion(), account, "clean", mind,
                bodyId, "moonstation14:station", new SavedLifecycleProfile.Location(14.5, 70, -2.25),
                java.util.Map.of("appearance", "ALEX", "bodyShape", "SLIM"), oldActive.connectionEpoch(),
                oldActive.connectionGeneration(), SavedLifecycleProfile.State.ACTIVE, oldActive.revision(), null);

        var disconnected = store.withCurrentPrimary(primary -> lifecycle.disconnectDurably(primary, oldActive,
                updated, active.connectionGeneration(), 99, ELIGIBLE)).orElseThrow().orElseThrow();
        SavedLifecycleProfile persisted = store.read().orElseThrow().profiles().get(0);
        assertEquals(SavedLifecycleProfile.State.OFFLINE, persisted.state());
        assertEquals(updated.dimension(), persisted.dimension());
        assertEquals(updated.location(), persisted.location());
        assertEquals(updated.appearance(), persisted.appearance());
        assertFalse(lifecycle.authorizes(account, active.connectionGeneration(), body.id()),
                "old controller input authorization is revoked after clean logout");
        assertFalse(disconnected.active());
        assertEquals(body.id(), shared.mind(account).orElseThrow().harnessId(), "the same body remains claimed");
        assertTrue(store.withCurrentPrimary(primary -> lifecycle.disconnectDurably(primary, oldActive, updated,
                active.connectionGeneration(), 100, ELIGIBLE)).orElseThrow().isEmpty(), "duplicate transition is harmless");
    }

    private static MobHarness harness(BodyControlRegistry registry, long id, MobHarnessKind kind) {
        MobHarness mob = new MobHarness(harnessId(id), kind);
        assertTrue(registry.registerHarness(mob));
        return mob;
    }
    private static MobHarnessId harnessId(long value) { return new MobHarnessId(uuid(value)); }
    private static UUID uuid(long value) { return new UUID(0, value); }
    private static SavedLifecycleProfile saved(UUID account, String profile, UUID mind, UUID body, UUID epoch,
            long generation, SavedLifecycleProfile.State state) {
        Long offlineSince = state == SavedLifecycleProfile.State.OFFLINE
                || state == SavedLifecycleProfile.State.GHOST_OFFLINE
                || state == SavedLifecycleProfile.State.DEAD_CLAIM ? Long.valueOf(1) : null;
        return new SavedLifecycleProfile(1, account, profile, mind, body, "overworld",
                new SavedLifecycleProfile.Location(0, 0, 0), java.util.Map.of(), epoch, generation,
                state, 0, offlineSince);
    }
    private static LifecycleStoreEnvelope envelope(UUID epoch, long counter, SavedLifecycleProfile profile) {
        return new LifecycleStoreEnvelope(1, 0, epoch, counter, java.util.List.of(profile));
    }

    private java.util.Optional<PlayerLifecycleRegistry.Snapshot> restore(PlayerLifecycleRegistry lifecycle,
            LifecycleStoreEnvelope envelope, SavedLifecycleProfile saved, TargetEligibility eligibility) {
        var store = new LifecycleProfileStore(directory.resolve("restore-" + restoreStoreNumber++ + ".json"));
        try {
            store.compareAndSwap(-1, envelope.epochToken(), envelope.generationCounter(), envelope.profiles());
            return store.withCurrentPrimary(proof -> lifecycle.restoreOffline(proof, saved, eligibility)).orElseThrow();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
