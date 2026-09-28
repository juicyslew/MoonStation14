package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostIntentGate;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleGhostSessionControlTest {
    @Test
    void readyRequiresExactEpochUncommittedSessionAndStrictlyBeforeHundredTicks() {
        assertTrue(LifecycleGhostSessionControl.readyDecision(4, 4, false, 0, 100));
        assertTrue(LifecycleGhostSessionControl.readyDecision(4, 4, false, 99, 100));
        assertFalse(LifecycleGhostSessionControl.readyDecision(3, 4, false, 1, 100));
        assertFalse(LifecycleGhostSessionControl.readyDecision(4, 4, true, 1, 100));
        assertFalse(LifecycleGhostSessionControl.readyDecision(4, 4, false, 100, 100));
        assertFalse(LifecycleGhostSessionControl.readyDecision(4, 4, false, -1, 100));
    }

    @Test
    void corpseAndRuntimeGhostMustHaveDistinctHarnessIds() {
        MobHarnessId corpse = id(1);
        MobHarnessId ghost = id(2);
        assertTrue(LifecycleGhostSessionControl.distinctHarnessIds(corpse, ghost));
        assertFalse(LifecycleGhostSessionControl.distinctHarnessIds(corpse, corpse));
        assertFalse(LifecycleGhostSessionControl.distinctHarnessIds(null, ghost));
    }

    @Test
    void packetsHaveAnOwnerOnlyForTheExactConnectedSessionPlayer() {
        assertTrue(LifecycleGhostSessionControl.exactSessionOwnerMatches(true, true, true));
        assertFalse(LifecycleGhostSessionControl.exactSessionOwnerMatches(false, true, true));
        assertFalse(LifecycleGhostSessionControl.exactSessionOwnerMatches(true, false, true));
        assertFalse(LifecycleGhostSessionControl.exactSessionOwnerMatches(true, true, false));
    }

    @Test
    void intentsAreEpochBoundRateLimitedAndSequenceGatedAcrossTicks() {
        GhostControlPayloads.Intent first = intent(4, 1);
        assertTrue(LifecycleGhostSessionControl.intentMatchesSession(first, 4));
        assertFalse(LifecycleGhostSessionControl.intentMatchesSession(first, 5));
        assertFalse(LifecycleGhostSessionControl.intentMatchesSession(first, 0));
        assertFalse(LifecycleGhostSessionControl.intentRateAllows(0));
        assertTrue(LifecycleGhostSessionControl.intentRateAllows(8));
        assertFalse(LifecycleGhostSessionControl.intentRateAllows(9));

        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        assertTrue(gate.offer(first.sequence(), 10, first));
        assertFalse(gate.offer(intent(4, 2).sequence(), 10, intent(4, 2)));
        assertEquals(first, gate.take());
        assertFalse(gate.offer(first.sequence(), 11, first));
        GhostControlPayloads.Intent next = intent(4, 3);
        assertTrue(gate.offer(next.sequence(), 11, next));
        assertEquals(next, gate.take());
    }

    @Test
    void authoritativeSnapshotsMustMatchGhostEpochAndCurrentEntityId() {
        GhostControlPayloads.Snapshot ghost = new GhostControlPayloads.Snapshot(4, 27,
                MobHarnessKind.GHOST, 12, 3, 1, 2, 3, 90, 20,
                0, 0, 0, false, 1, 1, false);
        assertTrue(LifecycleGhostSessionControl.snapshotBoundToSession(ghost, 4, 27));
        assertFalse(LifecycleGhostSessionControl.snapshotBoundToSession(ghost, 5, 27));
        assertFalse(LifecycleGhostSessionControl.snapshotBoundToSession(ghost, 4, 28));
        GhostControlPayloads.Snapshot character = new GhostControlPayloads.Snapshot(4, 27,
                MobHarnessKind.CHARACTER, 12, 3, 1, 2, 3, 90, 20,
                0, 0, 0, false, 1, 1, false);
        assertFalse(LifecycleGhostSessionControl.snapshotBoundToSession(character, 4, 27));
    }

    @Test
    void validMotionToleratesOnlyFloatingPointBoundaryNoise() {
        assertSimulatedMotion(true, 4d, -60d, 0.6000000000000001, 0d);
        assertSimulatedMotion(true, 4d, -60d, 0d, 0.6000000000000001);
        assertSimulatedMotion(true, 4d, -60d, 0d, -0.6000000000000001);
        assertSimulatedMotion(false, 4d, -60d, 0.600001, 0d);

        MovementVector position = new MovementVector(4, -60, 0);
        assertFalse(LifecycleGhostSessionControl.validMotion(position,
                new Vec3(0, Double.NaN, 0), position));
        assertFalse(LifecycleGhostSessionControl.validMotion(position,
                Vec3.ZERO, new MovementVector(4.01, -60, 0)));
        MovementVector outsideWorld = new MovementVector(30_000_001, -60, 0);
        assertFalse(LifecycleGhostSessionControl.validMotion(outsideWorld, Vec3.ZERO, outsideWorld));
    }

    private static void assertSimulatedMotion(boolean expected, double beforeX, double beforeY,
                                              double requestedX, double requestedY) {
        double afterX = beforeX + requestedX;
        double afterY = beforeY + requestedY;
        MovementVector after = new MovementVector(afterX, afterY, 0);
        Vec3 displacement = new Vec3(afterX - beforeX, afterY - beforeY, 0);
        assertEquals(expected, LifecycleGhostSessionControl.validMotion(after, displacement, after));
    }

    private static GhostControlPayloads.Intent intent(long epoch, long sequence) {
        return new GhostControlPayloads.Intent(epoch, sequence, (short) 0, (short) 1000,
                (byte) 0, 0, 0, 0);
    }

    private static MobHarnessId id(long value) {
        return new MobHarnessId(new UUID(0, value));
    }
}
