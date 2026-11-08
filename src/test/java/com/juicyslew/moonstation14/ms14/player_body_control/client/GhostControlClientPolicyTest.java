package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GhostMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class GhostControlClientPolicyTest {
    @Test
    void canonicalMouseRoutingAndOwnedLookUseLiveCarrierSource() throws IOException {
        String mixin = source("src/main/java/com/juicyslew/moonstation14/mixin/client/MouseHandlerMindGhostLookMixin.java");
        String redirect = method(mixin, "private void moonstation14$turnObserverCamera");
        assertTrue(redirect.indexOf("isLocalGhostControl()") < redirect.indexOf("player.turn(yaw, pitch)"),
                "canonical branch must route mouse input to carrier before legacy observer classification");
        assertTrue(redirect.indexOf("player.turn(yaw, pitch)") < redirect.indexOf("ObserverClient.current()"));

        String control = source("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java");
        String intents = method(control, "public static void clientTick");
        String projection = method(control, "public static void projectOwnedGhostLookForRenderFrame");
        for (String path : new String[] { intents, projection }) {
            assertFalse(path.contains("ObserverClient.lookForGhost"), "owned paths must not consume stale raw observer look");
            assertTrue(path.contains("player.getYRot()"));
            assertTrue(path.contains("player.getXRot()"));
        }
        // Source-contract wiring check only; it does not exercise live mouse routing or prove runtime behavior.
    }

    private static String source(String relative) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path path = Path.of(relative);
        while (root != null && !Files.isRegularFile(root.resolve(path))) root = root.getParent();
        if (root == null) throw new IOException("Project source not found: " + relative);
        return Files.readString(root.resolve(path));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("Missing source method: " + signature);
        int end = source.indexOf("\n    }", start);
        if (end < 0) throw new AssertionError("Unterminated source method: " + signature);
        return source.substring(start, end);
    }

    @Test
    void characterDefaultsToFastWithoutModifierOrMinecraftSprint() {
        assertEquals(GhostControlPayloads.BUTTON_SPRINT,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, false, false, false));
    }

    @Test
    void characterShiftWalksEvenWhenMinecraftReportsSprinting() {
        assertEquals(GhostControlPayloads.BUTTON_SNEAK,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, false, true, true));
    }

    @Test
    void characterCtrlSprintDoesNotOverrideShiftWalkOrJump() {
        assertEquals(GhostControlPayloads.BUTTON_JUMP | GhostControlPayloads.BUTTON_SNEAK,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, true, true, true));
        assertEquals(GhostControlPayloads.BUTTON_SPRINT,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, false, false, true));
    }

    @Test
    void characterWithoutSprintFlagStillMovesFastWhenUnshifted() {
        assertEquals(GhostControlPayloads.BUTTON_JUMP | GhostControlPayloads.BUTTON_SPRINT,
                GhostControlClient.intentButtons(MobHarnessKind.CHARACTER, true, false, false));
    }

    @Test
    void ghostRetainsMinecraftSprintMappingRegardlessOfShift() {
        assertEquals(0, GhostControlClient.intentButtons(MobHarnessKind.GHOST, false, true, false));
        assertEquals(GhostControlPayloads.BUTTON_SPRINT,
                GhostControlClient.intentButtons(MobHarnessKind.GHOST, false, true, true));
        assertEquals(GhostControlPayloads.BUTTON_JUMP,
                GhostControlClient.intentButtons(MobHarnessKind.GHOST, true, false, false));
    }

    @Test
    void customRegisteredCharacterDoesNotDependOnLegacyHostMapping() {
        assertTrue(GhostControlClient.characterBodyRecognized(true, false));
    }

    @Test
    void legacyConfiguredMobStillRequiresItsMappedPolicy() {
        assertTrue(GhostControlClient.characterBodyRecognized(false, true));
        assertFalse(GhostControlClient.characterBodyRecognized(false, false));
    }

    @Test
    void deferredBeginSurvivesTransientSessionCleanupAndWaitsForOwner() {
        GhostControlPayloads.Begin begin = begin(4);
        GhostControlPayloads.Begin queued = GhostControlClient.ClientBeginPolicy.defer(null, begin, 3);

        assertSame(begin, GhostControlClient.ClientBeginPolicy.afterSessionClear(queued));
        assertFalse(GhostControlClient.ClientBeginPolicy.canAccept(queued, 3, false));
        assertTrue(GhostControlClient.ClientBeginPolicy.canAccept(queued, 3, true));
    }

    @Test
    void deferredBeginRequiresNewEpochAndLogoutDiscardsIt() {
        GhostControlPayloads.Begin begin = begin(4);
        assertNull(GhostControlClient.ClientBeginPolicy.defer(null, begin, 4));
        assertFalse(GhostControlClient.ClientBeginPolicy.canAccept(begin, 4, true));
        assertNull(GhostControlClient.ClientBeginPolicy.afterLogout());
    }

    @Test
    void beginIsAcceptedOnlyFromMatchingPayloadContext() {
        GhostControlPayloads.Begin begin = begin(4);
        assertTrue(GhostControlClient.ClientBeginPolicy.canAcceptPayload(begin, 3, true));
        assertFalse(GhostControlClient.ClientBeginPolicy.canAcceptPayload(begin, 3, false));
    }

    @Test
    void readyNeedsOnlyMatchingBeginEntityAndSessionNotObserverProjection() {
        // Observer snapshot, corpse entity, and prototype projection are deliberately absent here.
        assertTrue(GhostControlClient.ClientReadyPolicy.canSend(4, 4, true, true, true));
        assertFalse(GhostControlClient.ClientReadyPolicy.canSend(3, 4, true, true, true));
        assertFalse(GhostControlClient.ClientReadyPolicy.canSend(4, 4, false, true, true));
        assertFalse(GhostControlClient.ClientReadyPolicy.canSend(4, 4, true, true, false));
        assertFalse(GhostControlClient.ClientReadyPolicy.canSend(4, 4, true, false, true));
    }

    @Test
    void readyDiagnosticClassifiesMissingUnsupportedAndUnreadyCharacterSeparately() {
        assertEquals("ENTITY_NOT_TRACKED", GhostControlClient.ClientReadyPolicy.entityReason(
                false, MobHarnessKind.CHARACTER, false, false));
        assertEquals("UNSUPPORTED_KIND", GhostControlClient.ClientReadyPolicy.entityReason(
                true, MobHarnessKind.CHARACTER, false, false));
        assertEquals("CHARACTER_POLICY_UNAVAILABLE", GhostControlClient.ClientReadyPolicy.entityReason(
                true, MobHarnessKind.CHARACTER, true, false));
        assertEquals("UNSUPPORTED_KIND", GhostControlClient.ClientReadyPolicy.entityReason(true,
                MobHarnessKind.CHARACTER, GhostControlClient.ClientReadyPolicy.expectedCharacterEntityType(
                        com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity.class), false));
        assertFalse(GhostControlClient.ClientReadyPolicy.expectedCharacterEntityType(null));
        assertEquals("NOT_SPECTATOR", GhostControlClient.ClientReadyPolicy.reason(true, false, true, 4, 4));
        assertEquals("CONTEXT_MISMATCH", GhostControlClient.ClientReadyPolicy.reason(true, true, false, 4, 4));
    }

    @Test
    void readyDiagnosticObservationDeduplicatesIdenticalStateAndResetsForNewEpoch() {
        var first = new GhostControlClient.ClientReadyPolicy.Observation(4, MobHarnessKind.CHARACTER,
                7, "mob", true, false, true, true, 4, 4, 4, false, "CHARACTER_POLICY_UNAVAILABLE");
        var same = new GhostControlClient.ClientReadyPolicy.Observation(4, MobHarnessKind.CHARACTER,
                7, "mob", true, false, true, true, 4, 4, 4, false, "CHARACTER_POLICY_UNAVAILABLE");
        var nextEpoch = new GhostControlClient.ClientReadyPolicy.Observation(5, MobHarnessKind.CHARACTER,
                7, "mob", true, false, true, true, 5, 5, 5, false, "CHARACTER_POLICY_UNAVAILABLE");
        assertFalse(same.changedFrom(first));
        assertTrue(nextEpoch.changedFrom(first));
        assertTrue(first.changedFrom(null));
    }

    @Test
    void beginMovementRatesDriveSameMotorWithNoDefaultFallback() {
        var begin = new GhostControlPayloads.Begin(4, 7, MobHarnessKind.GHOST, 3, 7);
        GhostMovementMotor motor = new GhostMovementMotor(begin.ghostWalkSpeed(), begin.ghostSprintSpeed(), begin.ghostSprintSpeed());
        var walk = motor.tick(MovementVector.ZERO, 0, 1000, 0, false, 0,
                (position, displacement, grounded) -> new MovementCollisionResolver.CollisionResult(displacement, false));
        var sprint = motor.tick(MovementVector.ZERO, 0, 1000, 0, true, 0,
                (position, displacement, grounded) -> new MovementCollisionResolver.CollisionResult(displacement, false));
        assertEquals(3, walk.position().z() / .05, 1e-9);
        assertEquals(7, sprint.position().z() / .05, 1e-9);
        assertEquals(7, motor.verticalSpeedPerSecond());
    }

    @Test
    void characterBeginCarriesNoGhostMovementPolicy() {
        var begin = new GhostControlPayloads.Begin(4, 7, MobHarnessKind.CHARACTER);
        assertEquals(0, begin.ghostWalkSpeed());
        assertEquals(0, begin.ghostSprintSpeed());
    }

    private static GhostControlPayloads.Begin begin(long epoch) {
        return new GhostControlPayloads.Begin(epoch, 7, MobHarnessKind.GHOST);
    }
}
