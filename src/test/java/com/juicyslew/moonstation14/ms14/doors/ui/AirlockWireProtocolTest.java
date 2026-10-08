package com.juicyslew.moonstation14.ms14.doors.ui;

import org.junit.jupiter.api.Test;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AirlockWireProtocolTest {
    @Test void replayAndExactSessionAdmissionRejectStaleOrForeignCommands() {
        UUID session = UUID.randomUUID();
        var replay = new AirlockWireMenuService.Replay(8);
        var fresh = new AirlockWireRequest(3, session, 9, 2, 1, AirlockWireRequest.Operation.CUT);
        var stale = new AirlockWireRequest(3, session, 8, 2, 1, AirlockWireRequest.Operation.CUT);
        var foreign = new AirlockWireRequest(3, UUID.randomUUID(), 10, 2, 1, AirlockWireRequest.Operation.CUT);
        assertTrue(AirlockWireMenuService.admit(3, session, replay, fresh));
        assertFalse(AirlockWireMenuService.admit(3, session, replay, stale));
        assertFalse(AirlockWireMenuService.admit(3, session, replay, foreign));
        assertFalse(AirlockWireMenuService.admit(4, session, replay, fresh));
    }

    @Test void requestConstrainsOperationsAndRowSelectors() {
        UUID session = UUID.randomUUID();
        assertDoesNotThrow(() -> new AirlockWireRequest(0, session, 0, 0, -1, AirlockWireRequest.Operation.QUERY));
        assertDoesNotThrow(() -> new AirlockWireRequest(0, session, 1, 0, 7, AirlockWireRequest.Operation.PULSE));
        assertThrows(IllegalArgumentException.class, () -> new AirlockWireRequest(0, session, 0, 0, 8, AirlockWireRequest.Operation.CUT));
        assertThrows(IllegalArgumentException.class, () -> new AirlockWireRequest(0, session, 0, 0, -1, AirlockWireRequest.Operation.MEND));
    }

    @Test void clientRowHasNoFunctionalWireIdentity() {
        assertEquals(java.util.List.of("id", "argb", "letter", "cut"),
                java.util.Arrays.stream(AirlockWireSnapshot.Row.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }
    @Test void authoritativeStunReasonSurvivesSnapshotCodec() {
        var source=new AirlockWireSnapshot(1,UUID.randomUUID(),9,false,AirlockWireSnapshot.Rejection.STUNNED,3,true,
                AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.OFF,AirlockWireIndicators.Light.ON,
                AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.ON,
                AirlockWireIndicators.Light.ON,true,true,java.util.List.of());
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        AirlockWireSnapshot.STREAM_CODEC.encode(buf,source);
        assertEquals(source,AirlockWireSnapshot.STREAM_CODEC.decode(buf));
    }

    @Test void rootRevisionAndLiveToolIntentAreIndependentCommandGates() {
        assertTrue(AirlockWireCommandPolicy.revisionMatches(4, 4));
        assertFalse(AirlockWireCommandPolicy.revisionMatches(5, 4));
        assertFalse(AirlockWireCommandPolicy.revisionMatches(4, -1));
        assertTrue(AirlockWireCommandPolicy.toolAllows(AirlockWireRequest.Operation.MEND, true, false, true));
        assertFalse(AirlockWireCommandPolicy.toolAllows(AirlockWireRequest.Operation.CUT, false, true, false));
        assertTrue(AirlockWireCommandPolicy.toolAllows(AirlockWireRequest.Operation.PULSE, false, true, false));
        assertFalse(AirlockWireCommandPolicy.toolAllows(AirlockWireRequest.Operation.PULSE, false, true, true));
        assertEquals(AirlockWireRequest.Operation.MEND, AirlockWireCommandPolicy.resolveClick(true, true, true));
        assertEquals(AirlockWireRequest.Operation.CUT, AirlockWireCommandPolicy.resolveClick(true, true, false));
        assertEquals(AirlockWireRequest.Operation.PULSE, AirlockWireCommandPolicy.resolveClick(false, true, false));
        assertNull(AirlockWireCommandPolicy.resolveClick(false, true, true));
    }
}
