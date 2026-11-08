package com.juicyslew.moonstation14.ms14.player_body_control;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RegisteredServerPostObservationWiringTest {
    private static final Path FIXTURE = Path.of("src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleRegisteredPlayerGameTests.java");
    private static final Path WAIT = Path.of("src/gametest/java/com/juicyslew/moonstation14/gametest/support/BoundedGameTestWait.java");

    @Test
    void movementFixtureWaitsForExactPostPublicationBeforeCheckingActionAcks() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve(FIXTURE))) root = root.getParent();
        assertTrue(root != null, "repository sources are available to wiring proof");
        String fixture = Files.readString(root.resolve(FIXTURE));
        String wait = Files.readString(root.resolve(WAIT));
        assertTrue(fixture.contains("snapshot.serverGameTick() == completedWorldTick"));
        assertTrue(fixture.contains("awaitServerPostSnapshot(helper, fixture, active.epoch(), body.getId(), 2, firstTick"));
        assertTrue(fixture.contains("awaitServerPostSnapshot(helper, fixture, active.epoch(), body.getId(), 2, completedHeldTick"));
        assertTrue(fixture.contains("awaitServerPostSnapshot(helper, fixture, active.epoch(), body.getId(), 3, completedReleaseTick"));
        assertTrue(fixture.contains("awaitServerPostSnapshot(helper, fixture, active.epoch(), body.getId(), 4, steeredTick"));
        assertTrue(wait.contains("catch (Exception | AssertionError failure)"));
        assertTrue(wait.contains("remove();") && wait.contains("safeDelay(helper, 1, () -> helper.testInfo.fail(failure))"));
        assertTrue(fixture.contains("installFixtureCleanup(helper, fixture)"));
        assertTrue(fixture.contains("installTraceFixtureCleanup(helper, fixture)"));
    }
}
