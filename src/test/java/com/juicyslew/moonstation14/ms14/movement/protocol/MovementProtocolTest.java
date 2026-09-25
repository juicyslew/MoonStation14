package com.juicyslew.moonstation14.ms14.movement.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementProtocolTest {
    @Test
    void everyPayloadHasADistinctTypeAndEpochPayloadsRoundTrip() {
        assertEquals(MovementPayloads.Begin.TYPE, new MovementPayloads.Begin(1).type());
        assertEquals(MovementPayloads.Acknowledge.TYPE, new MovementPayloads.Acknowledge(1).type());
        assertEquals(MovementPayloads.Commit.TYPE, new MovementPayloads.Commit(1).type());
        assertEquals(MovementPayloads.Disable.TYPE, new MovementPayloads.Disable(1).type());
        assertEquals(MovementPayloads.DisableAcknowledge.TYPE, new MovementPayloads.DisableAcknowledge(1).type());
        assertEquals(MovementPayloads.ResumeVanilla.TYPE, new MovementPayloads.ResumeVanilla(1).type());
        assertEquals(MovementPayloads.ResumeAcknowledge.TYPE, new MovementPayloads.ResumeAcknowledge(1).type());
        assertEquals(MovementPayloads.Intent.TYPE,
                new MovementPayloads.Intent(1, 1, (short) 0, (short) 0, 0).type());
        assertEquals(MovementPayloads.Snapshot.TYPE,
                new MovementPayloads.Snapshot(1, 0, 0, 0, 0, 0, 0, 0, false).type());
        assertEquals(9, Set.of(MovementPayloads.Begin.TYPE, MovementPayloads.Acknowledge.TYPE,
                MovementPayloads.Commit.TYPE, MovementPayloads.Disable.TYPE,
                MovementPayloads.DisableAcknowledge.TYPE, MovementPayloads.ResumeVanilla.TYPE,
                MovementPayloads.ResumeAcknowledge.TYPE, MovementPayloads.Intent.TYPE,
                MovementPayloads.Snapshot.TYPE).size());

        assertEquals(new MovementPayloads.Begin(1), roundTrip(MovementPayloads.Begin.STREAM_CODEC,
                new MovementPayloads.Begin(1)));
        assertEquals(new MovementPayloads.Acknowledge(2), roundTrip(MovementPayloads.Acknowledge.STREAM_CODEC,
                new MovementPayloads.Acknowledge(2)));
        assertEquals(new MovementPayloads.Commit(3), roundTrip(MovementPayloads.Commit.STREAM_CODEC,
                new MovementPayloads.Commit(3)));
        assertEquals(new MovementPayloads.Disable(4), roundTrip(MovementPayloads.Disable.STREAM_CODEC,
                new MovementPayloads.Disable(4)));
        assertEquals(new MovementPayloads.DisableAcknowledge(5),
                roundTrip(MovementPayloads.DisableAcknowledge.STREAM_CODEC,
                        new MovementPayloads.DisableAcknowledge(5)));
        assertEquals(new MovementPayloads.ResumeVanilla(6),
                roundTrip(MovementPayloads.ResumeVanilla.STREAM_CODEC, new MovementPayloads.ResumeVanilla(6)));
        assertEquals(new MovementPayloads.ResumeAcknowledge(7),
                roundTrip(MovementPayloads.ResumeAcknowledge.STREAM_CODEC,
                        new MovementPayloads.ResumeAcknowledge(7)));
    }

    @Test
    void intentAndSnapshotHaveBoundedRoundTripWireForms() {
        MovementPayloads.Intent intent = new MovementPayloads.Intent(4, 12, (short) -1000, (short) 999,
                MovementPayloads.BUTTON_JUMP | MovementPayloads.BUTTON_SPRINT);
        assertEquals(intent, roundTrip(MovementPayloads.Intent.STREAM_CODEC, intent));

        MovementPayloads.Snapshot snapshot = new MovementPayloads.Snapshot(4, 12,
                1.25, 64, -3.5, 0.1, 0, -0.2, true);
        assertEquals(snapshot, roundTrip(MovementPayloads.Snapshot.STREAM_CODEC, snapshot));
        assertThrows(IllegalArgumentException.class,
                () -> new MovementPayloads.Intent(4, 1, (short) 1001, (short) 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MovementPayloads.Intent(4, 1, (short) 0, (short) 0, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new MovementPayloads.Snapshot(4, 0, Double.NaN, 0, 0, 0, 0, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new MovementPayloads.Snapshot(4, 0, 0, 0, 0, 30_000_001, 0, 0, false));
    }

    @Test
    void sessionStartsVanillaAndRequiresExplicitEligibleBeginAckCommit() {
        MovementSession session = new MovementSession();
        assertEquals(MovementSession.Phase.VANILLA, session.phase());
        assertTrue(session.vanillaOwnsMovement());
        assertFalse(session.customOwnsMovement());
        assertEquals(MovementSession.Result.REJECTED, session.begin(1, false, true, true));
        assertEquals(MovementSession.Result.REJECTED, session.begin(1, true, false, true));
        assertEquals(MovementSession.Result.REJECTED, session.begin(1, true, true, false));
        assertEquals(MovementSession.Result.ACCEPTED, session.begin(1, true, true, true));
        assertEquals(MovementSession.Phase.PENDING_BEGIN, session.phase());
        assertEquals(MovementSession.Result.REJECTED, session.commit(1, true));
        assertEquals(MovementSession.Result.REJECTED, session.acknowledge(2, true));
        assertEquals(MovementSession.Result.REJECTED, session.acknowledge(1, false));
        assertEquals(MovementSession.Result.ACCEPTED, session.acknowledge(1, true));
        assertEquals(MovementSession.Result.REJECTED, session.acknowledge(1, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.commit(1, true));
        assertTrue(session.customOwnsMovement());
        assertFalse(session.vanillaOwnsMovement());
    }

    @Test
    void onlyNextSequenceIsAcceptedAndOverflowDoesNotWrap() {
        MovementSession session = activeSession(9);
        assertEquals(MovementSession.Result.REJECTED, session.acceptIntent(8, 1, true));
        assertEquals(MovementSession.Result.REJECTED, session.acceptIntent(9, 0, true));
        assertEquals(MovementSession.Result.REJECTED, session.acceptIntent(9, 2, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.acceptIntent(9, 1, true));
        assertEquals(MovementSession.Result.REJECTED, session.acceptIntent(9, 1, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.acceptIntent(9, 2, true));
    }

    @Test
    void pendingEndRequiresOrderedVerifiedTeleportAndFinalResumeAcknowledgement() {
        MovementSession session = activeSession(2);
        assertEquals(MovementSession.Result.REJECTED, session.acknowledgeDisable(2, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.beginDisable(2, true));
        assertEquals(MovementSession.Phase.PENDING_END, session.phase());
        assertTrue(session.customOwnsMovement());
        assertFalse(session.vanillaOwnsMovement());
        assertEquals(MovementSession.Result.REJECTED, session.acceptIntent(2, 1, true));
        assertEquals(MovementSession.Result.REJECTED, session.acknowledgeTeleport(2, true));
        assertEquals(MovementSession.Result.REJECTED, session.authorizeResume(2, true));
        assertEquals(MovementSession.Result.REJECTED, session.finishDisable(2, true));
        assertEquals(MovementSession.Result.REJECTED, session.acknowledgeDisable(3, true));
        assertEquals(MovementSession.Result.REJECTED, session.acknowledgeDisable(2, false));
        assertEquals(MovementSession.Result.ACCEPTED, session.acknowledgeDisable(2, true));
        assertEquals(MovementSession.Result.REJECTED, session.acknowledgeDisable(2, true));
        assertEquals(MovementSession.Result.REJECTED, session.authorizeResume(2, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.acknowledgeTeleport(2, true));
        assertEquals(MovementSession.Result.REJECTED, session.acknowledgeTeleport(2, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.authorizeResume(2, true));
        assertEquals(MovementSession.Result.REJECTED, session.authorizeResume(2, true));
        assertEquals(MovementSession.Result.REJECTED, session.finishDisable(3, true));
        assertEquals(MovementSession.Result.REJECTED, session.finishDisable(2, false));
        assertTrue(session.customOwnsMovement());
        assertFalse(session.vanillaOwnsMovement());
        assertEquals(MovementSession.Result.ACCEPTED, session.finishDisable(2, true));
        assertEquals(MovementSession.Phase.VANILLA, session.phase());
        assertFalse(session.customOwnsMovement());
        assertTrue(session.vanillaOwnsMovement());
        assertEquals(MovementSession.Result.REJECTED, session.finishDisable(2, true));
    }

    @Test
    void resetInvalidatesEveryPendingEndStep() {
        MovementSession session = activeSession(3);
        assertEquals(MovementSession.Result.ACCEPTED, session.beginDisable(3, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.acknowledgeDisable(3, true));
        session.reset();
        assertEquals(MovementSession.Phase.VANILLA, session.phase());
        assertEquals(MovementSession.Result.REJECTED, session.acknowledgeTeleport(3, true));
        assertEquals(MovementSession.Result.REJECTED, session.authorizeResume(3, true));
        assertEquals(MovementSession.Result.REJECTED, session.finishDisable(3, true));
    }

    @Test
    void resetRejectsLateAckAndRequiresNewerEpochToReenroll() {
        MovementSession session = new MovementSession();
        assertEquals(MovementSession.Result.ACCEPTED, session.begin(5, true, true, true));
        session.reset();
        assertEquals(MovementSession.Phase.VANILLA, session.phase());
        assertEquals(MovementSession.Result.REJECTED, session.acknowledge(5, true));
        assertEquals(MovementSession.Result.REJECTED, session.begin(5, true, true, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.begin(6, true, true, true));
    }

    private static MovementSession activeSession(long epoch) {
        MovementSession session = new MovementSession();
        assertEquals(MovementSession.Result.ACCEPTED, session.begin(epoch, true, true, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.acknowledge(epoch, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.commit(epoch, true));
        return session;
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                   T value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
