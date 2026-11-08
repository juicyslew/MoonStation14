package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CombatModeClientPolicyTest {
    @Test void requestAndAcknowledgementStreamCodecsRoundTrip() {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        var request = new CombatModeRequest(4, 5, true);
        CombatModeRequest.STREAM_CODEC.encode(buffer, request);
        assertEquals(request, CombatModeRequest.STREAM_CODEC.decode(buffer));
        var withBody = new CombatModeRequestResult(CombatModeRequestResult.Status.ACCEPTED, UUID.randomUUID(), 4, 5, true);
        CombatModeRequestResult.STREAM_CODEC.encode(buffer, withBody);
        assertEquals(withBody, CombatModeRequestResult.STREAM_CODEC.decode(buffer));
        var withoutBody = new CombatModeRequestResult(CombatModeRequestResult.Status.NO_AUTHORITY, null, 4, 5, false);
        CombatModeRequestResult.STREAM_CODEC.encode(buffer, withoutBody);
        assertEquals(withoutBody, CombatModeRequestResult.STREAM_CODEC.decode(buffer));
    }

    @Test void predictsAndOnlyAcceptsMatchingAcknowledgement() {
        var policy = new CombatModeClientPolicy(); var body = UUID.randomUUID();
        policy.bind(body, 3, false);
        var request = policy.toggle(10);
        assertTrue(policy.mode()); assertTrue(policy.pending());
        assertFalse(policy.acknowledge(new CombatModeRequestResult(CombatModeRequestResult.Status.ACCEPTED, body, 3, request.sequence()-1, false)));
        assertTrue(policy.acknowledge(new CombatModeRequestResult(CombatModeRequestResult.Status.ACCEPTED, body, 3, request.sequence(), true)));
        assertTrue(policy.mode()); assertFalse(policy.pending());
    }

    @Test void bodyHandoffAndTimeoutDiscardPredictionWithoutRetry() {
        var policy = new CombatModeClientPolicy(); var body = UUID.randomUUID();
        policy.bind(body, 1, false); policy.toggle(2);
        policy.bind(UUID.randomUUID(), 2, false);
        assertFalse(policy.mode()); assertFalse(policy.pending());
        assertTrue(policy.toggle(3).desiredMode());
        policy.bind(body, 1, false); policy.toggle(4);
        assertTrue(policy.timeout(20, 10)); assertFalse(policy.mode()); assertFalse(policy.pending());
        assertNotNull(policy.toggle(21)); // a new explicit user action is allowed; timeout itself did not retry.
    }

    @Test void freshBindingResetAttachmentAndTimeoutKeepContextsAndPredictionSafe() {
        var policy = new CombatModeClientPolicy(); var first = UUID.randomUUID(); var second = UUID.randomUUID();
        policy.bind(first, 10, false);
        var old = policy.toggle(1);
        policy.attachmentUpdate(first, 10, true); // authoritative update cannot erase pending prediction
        assertTrue(policy.mode()); assertTrue(policy.pending());
        policy.bind(second, 11, false);
        var fresh = policy.toggle(2);
        assertTrue(fresh.sequence() > old.sequence());
        assertFalse(policy.acknowledge(new CombatModeRequestResult(CombatModeRequestResult.Status.ACCEPTED,
                first, 10, old.sequence(), true)));
        assertTrue(policy.timeout(102, 100));
        assertFalse(policy.mode()); assertFalse(policy.pending());
        policy.bind(first, 10, true);
        var afterReset = policy.toggle(103);
        assertTrue(afterReset.sequence() > fresh.sequence());
        policy.clear();
        assertFalse(policy.acknowledge(new CombatModeRequestResult(CombatModeRequestResult.Status.ACCEPTED,
                first, 10, afterReset.sequence(), false)));
    }
}
