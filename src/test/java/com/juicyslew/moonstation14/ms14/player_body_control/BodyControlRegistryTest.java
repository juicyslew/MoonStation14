package com.juicyslew.moonstation14.ms14.player_body_control;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyControlRegistryTest {
    private static final TargetEligibility ELIGIBLE = target -> true;

    @Test
    void mindStartsAtomicallyOnRegisteredEligibleGhostAndRejectsInvalidOrDuplicateCreation() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(1);
        MobHarnessId ghost = harness(registry, 2, MobHarnessKind.GHOST);
        MobHarnessId character = harness(registry, 3, MobHarnessKind.CHARACTER);
        MobHarnessId unavailableGhost = harness(registry, 4, MobHarnessKind.GHOST);

        assertTrue(registry.createMind(session, new MobHarnessId(uuid(5)), ELIGIBLE).isEmpty());
        assertTrue(registry.createMind(session, character, ELIGIBLE).isEmpty());
        assertTrue(registry.createMind(session, unavailableGhost, target -> false).isEmpty());
        assertTrue(registry.mind(session).isEmpty());

        var initial = registry.createMind(session, ghost, ELIGIBLE).orElseThrow();
        assertEquals(ghost, initial.harnessId());
        assertEquals(1, initial.epoch());
        assertTrue(registry.authorizes(session, ghost, 1, ELIGIBLE));
        assertTrue(registry.createMind(session, ghost, ELIGIBLE).isEmpty());
        assertTrue(registry.createMind(uuid(6), ghost, ELIGIBLE).isEmpty());
        assertTrue(registry.mind(uuid(6)).isEmpty());
    }

    @Test
    void transfersGhostToCharacterAndBackWithoutReplacingTheMind() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(10);
        MobHarnessId ghost = harness(registry, 11, MobHarnessKind.GHOST);
        MobHarnessId character = harness(registry, 12, MobHarnessKind.CHARACTER);
        var initial = registry.createMind(session, ghost, ELIGIBLE).orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.CHANGED,
                registry.transfer(session, character, initial.epoch(), ELIGIBLE));
        var possessingCharacter = registry.mind(session).orElseThrow();
        assertEquals(initial.id(), possessingCharacter.id());
        assertEquals(character, possessingCharacter.harnessId());
        assertEquals(2, possessingCharacter.epoch());
        assertEquals(BodyControlRegistry.OperationResult.UNCHANGED,
                registry.transfer(session, character, possessingCharacter.epoch(), ELIGIBLE));
        assertEquals(possessingCharacter, registry.mind(session).orElseThrow());
        assertEquals(BodyControlRegistry.OperationResult.CHANGED,
                registry.transfer(session, ghost, possessingCharacter.epoch(), ELIGIBLE));
        assertEquals(initial.id(), registry.mind(session).orElseThrow().id());
        assertEquals(3, registry.mind(session).orElseThrow().epoch());
    }

    @Test
    void onlyOneMindCanOwnHarnessAndFailedRacePreservesBothBindings() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID first = uuid(20);
        UUID second = uuid(21);
        MobHarnessId firstGhost = harness(registry, 22, MobHarnessKind.GHOST);
        MobHarnessId secondGhost = harness(registry, 23, MobHarnessKind.GHOST);
        var firstMind = registry.createMind(first, firstGhost, ELIGIBLE).orElseThrow();
        var secondMind = registry.createMind(second, secondGhost, ELIGIBLE).orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.HARNESS_OWNED,
                registry.transfer(second, firstGhost, secondMind.epoch(), ELIGIBLE));
        assertEquals(secondGhost, registry.mind(second).orElseThrow().harnessId());
        assertTrue(registry.authorizes(first, firstGhost, firstMind.epoch(), ELIGIBLE));
    }

    @Test
    void wrongSessionUnknownHarnessAndIneligibleTargetsDoNotDestroyBinding() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(30);
        MobHarnessId ghost = harness(registry, 31, MobHarnessKind.GHOST);
        MobHarnessId character = harness(registry, 32, MobHarnessKind.CHARACTER);
        var mind = registry.createMind(session, ghost, ELIGIBLE).orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.UNKNOWN_SESSION,
                registry.transfer(uuid(33), character, mind.epoch(), ELIGIBLE));
        assertEquals(BodyControlRegistry.OperationResult.UNKNOWN_HARNESS,
                registry.transfer(session, new MobHarnessId(uuid(34)), mind.epoch(), ELIGIBLE));
        assertEquals(BodyControlRegistry.OperationResult.INELIGIBLE_TARGET,
                registry.transfer(session, character, mind.epoch(), target -> false));
        assertEquals(ghost, registry.mind(session).orElseThrow().harnessId());
        assertTrue(registry.authorizes(session, ghost, mind.epoch(), ELIGIBLE));
    }

    @Test
    void attachOnlyRecoversDetachedMindsToGhostAndVoluntaryReleaseIsDenied() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(40);
        MobHarnessId ghost = harness(registry, 41, MobHarnessKind.GHOST);
        MobHarnessId secondGhost = harness(registry, 42, MobHarnessKind.GHOST);
        MobHarnessId character = harness(registry, 43, MobHarnessKind.CHARACTER);
        var mind = registry.createMind(session, ghost, ELIGIBLE).orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.UNCHANGED,
                registry.attach(session, ghost, 1, ELIGIBLE));
        assertEquals(BodyControlRegistry.OperationResult.ATTACHED_HARNESS_REQUIRED,
                registry.attach(session, secondGhost, 1, ELIGIBLE));
        assertEquals(BodyControlRegistry.OperationResult.RELEASE_NOT_ALLOWED,
                registry.release(session, 1, BodyControlRegistry.ReleaseReason.VOLUNTARY));
        assertEquals(BodyControlRegistry.OperationResult.CHANGED,
                registry.release(session, 1, BodyControlRegistry.ReleaseReason.FAILURE));
        assertEquals(BodyControlRegistry.OperationResult.ATTACHED_HARNESS_REQUIRED,
                registry.transfer(session, ghost, 2, ELIGIBLE));
        assertEquals(BodyControlRegistry.OperationResult.INELIGIBLE_TARGET,
                registry.attach(session, character, 2, ELIGIBLE));
        assertEquals(BodyControlRegistry.OperationResult.CHANGED,
                registry.attach(session, secondGhost, 2, ELIGIBLE));
        assertEquals(mind.id(), registry.mind(session).orElseThrow().id());
        assertEquals(3, registry.mind(session).orElseThrow().epoch());
    }

    @Test
    void staleEpochCannotTransferOrAuthorizeAndEligibilityChangesRevokeBinding() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(50);
        MobHarnessId ghost = harness(registry, 51, MobHarnessKind.GHOST);
        MobHarnessId character = harness(registry, 52, MobHarnessKind.CHARACTER);
        registry.createMind(session, ghost, ELIGIBLE).orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.STALE_EPOCH,
                registry.transfer(session, character, -1, ELIGIBLE));
        assertFalse(registry.authorizes(session, ghost, -1, ELIGIBLE));
        assertTrue(registry.authorizes(session, ghost, 1, ELIGIBLE));
        assertFalse(registry.authorizes(session, ghost, 1, target -> false));
        assertNull(registry.mind(session).orElseThrow().harnessId());
        assertEquals(2, registry.mind(session).orElseThrow().epoch());
        assertFalse(registry.authorizes(session, ghost, 2, ELIGIBLE));
        assertFalse(registry.authorizes(session, null, 2, ELIGIBLE));
        assertFalse(registry.authorizes(null, ghost, 2, ELIGIBLE));
    }

    @Test
    void removingOwnedHarnessRevokesAuthorizationAndAdvancesEpoch() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(60);
        MobHarnessId ghost = harness(registry, 61, MobHarnessKind.GHOST);
        var mind = registry.createMind(session, ghost, ELIGIBLE).orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.CHANGED, registry.unregisterHarness(ghost));
        assertEquals(BodyControlRegistry.OperationResult.UNKNOWN_HARNESS, registry.unregisterHarness(ghost));
        assertEquals(2, registry.mind(session).orElseThrow().epoch());
        assertNull(registry.mind(session).orElseThrow().harnessId());
        assertFalse(registry.authorizes(session, ghost, mind.epoch(), ELIGIBLE));
        assertTrue(registry.registerHarness(new MobHarness(ghost, MobHarnessKind.GHOST)));
        assertFalse(registry.authorizes(session, ghost, mind.epoch(), ELIGIBLE));
        for (int i = 0; i < 100; i++) {
            MobHarnessId churned = new MobHarnessId(uuid(100 + i));
            assertTrue(registry.registerHarness(new MobHarness(churned, MobHarnessKind.GHOST)));
            assertEquals(BodyControlRegistry.OperationResult.CHANGED, registry.unregisterHarness(churned));
            assertTrue(registry.registerHarness(new MobHarness(churned, MobHarnessKind.GHOST)));
            assertEquals(BodyControlRegistry.OperationResult.CHANGED, registry.unregisterHarness(churned));
        }
    }

    @Test
    void sameTargetAttachRevalidatesAndRevokesInvalidOrNonGhostBinding() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(80);
        MobHarnessId ghost = harness(registry, 81, MobHarnessKind.GHOST);
        var mind = registry.createMind(session, ghost, ELIGIBLE).orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.INELIGIBLE_TARGET,
                registry.attach(session, ghost, mind.epoch(), target -> false));
        assertNull(registry.mind(session).orElseThrow().harnessId());
        assertEquals(mind.epoch() + 1, registry.mind(session).orElseThrow().epoch());

        MobHarnessId character = harness(registry, 82, MobHarnessKind.CHARACTER);
        assertEquals(BodyControlRegistry.OperationResult.INELIGIBLE_TARGET,
                registry.attach(session, character, registry.mind(session).orElseThrow().epoch(), ELIGIBLE));
        assertNull(registry.mind(session).orElseThrow().harnessId());
    }

    @Test
    void sameTargetAttachDoesNotDetachACharacterButInvalidGhostIsRevoked() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(83);
        MobHarnessId ghost = harness(registry, 84, MobHarnessKind.GHOST);
        MobHarnessId character = harness(registry, 85, MobHarnessKind.CHARACTER);
        var initial = registry.createMind(session, ghost, ELIGIBLE).orElseThrow();
        assertEquals(BodyControlRegistry.OperationResult.CHANGED,
                registry.transfer(session, character, initial.epoch(), ELIGIBLE));
        var possessingCharacter = registry.mind(session).orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.ATTACHED_HARNESS_REQUIRED,
                registry.attach(session, character, possessingCharacter.epoch(), ELIGIBLE));
        assertEquals(possessingCharacter, registry.mind(session).orElseThrow());
        assertTrue(registry.authorizes(session, character, possessingCharacter.epoch(), ELIGIBLE));

        assertEquals(BodyControlRegistry.OperationResult.CHANGED,
                registry.transfer(session, ghost, possessingCharacter.epoch(), ELIGIBLE));
        var possessingGhost = registry.mind(session).orElseThrow();
        assertEquals(BodyControlRegistry.OperationResult.INELIGIBLE_TARGET,
                registry.attach(session, ghost, possessingGhost.epoch(), target -> false));
        assertNull(registry.mind(session).orElseThrow().harnessId());
        assertEquals(possessingGhost.epoch() + 1, registry.mind(session).orElseThrow().epoch());
    }

    @Test
    void newMindForSameSessionUuidNeverReusesAnEarlierEpoch() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(90);
        MobHarnessId firstGhost = harness(registry, 91, MobHarnessKind.GHOST);
        var first = registry.createMind(session, firstGhost, ELIGIBLE).orElseThrow();
        assertEquals(BodyControlRegistry.OperationResult.CHANGED, registry.logout(session));

        MobHarnessId secondGhost = harness(registry, 92, MobHarnessKind.GHOST);
        var second = registry.createMind(session, secondGhost, ELIGIBLE).orElseThrow();
        assertNotEquals(first.epoch(), second.epoch());
        assertTrue(second.epoch() > first.epoch());
        assertFalse(registry.authorizes(session, secondGhost, first.epoch(), ELIGIBLE));
    }

    @Test
    void logoutCleansMindAndHarnessOwnershipAndNewSessionCanCreateAnotherMind() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(70);
        MobHarnessId ghost = harness(registry, 71, MobHarnessKind.GHOST);
        var mind = registry.createMind(session, ghost, ELIGIBLE).orElseThrow();

        assertEquals(BodyControlRegistry.OperationResult.CHANGED, registry.logout(session));
        assertTrue(registry.mind(session).isEmpty());
        assertFalse(registry.authorizes(session, ghost, mind.epoch(), ELIGIBLE));
        var otherMind = registry.createMind(uuid(72), ghost, ELIGIBLE).orElseThrow();
        assertNotEquals(mind.id(), otherMind.id());
        assertEquals(ghost, otherMind.harnessId());
    }

    @Test
    void logoutGenerationOverflowLeavesMindAndOwnershipUntouched() throws ReflectiveOperationException {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = uuid(73);
        MobHarnessId ghost = harness(registry, 74, MobHarnessKind.GHOST);
        var mind = registry.createMind(session, ghost, ELIGIBLE).orElseThrow();
        Field generation = BodyControlRegistry.class.getDeclaredField("generation");
        generation.setAccessible(true);
        generation.setLong(registry, Long.MAX_VALUE);

        assertThrows(ArithmeticException.class, () -> registry.logout(session));
        assertEquals(mind, registry.mind(session).orElseThrow());
        assertTrue(registry.authorizes(session, ghost, mind.epoch(), ELIGIBLE));
    }

    private static MobHarnessId harness(BodyControlRegistry registry, long id, MobHarnessKind kind) {
        MobHarnessId harnessId = new MobHarnessId(uuid(id));
        assertTrue(registry.registerHarness(new MobHarness(harnessId, kind)));
        return harnessId;
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
