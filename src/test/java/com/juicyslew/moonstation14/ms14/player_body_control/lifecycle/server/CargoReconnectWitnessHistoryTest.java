package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CargoReconnectWitnessHistoryTest {
    @Test
    void firstPerJugWitnessAndGlobalFailureStayLatchedWhileRecentRingWraps() {
        var history = new CargoReconnectWitnessHistory();
        UUID jug = UUID.randomUUID();
        UUID oldEntity = UUID.randomUUID();
        UUID reloadedEntity = UUID.randomUUID();
        var first = new CargoReconnectWitnessHistory.Witness(17, "OFFLINE", jug, oldEntity, null, "original#1", null,
                "jug/water=500", "jug/empty", "missing", "(1,2,3)", "frame=abc/support=false", "prior=full");
        var later = new CargoReconnectWitnessHistory.Witness(22, "REATTACHED", jug, oldEntity, reloadedEntity, "original#1", "reloaded#2",
                "jug/water=500", "jug/water=500", "world-entity:" + jug, "(2,3,4)", "frame=abc/support=true", "prior=empty");

        history.departure(first);
        history.departure(later);
        history.cargoFailure(first);
        history.cargoFailure(later);
        for (int i = 0; i < CargoReconnectWitnessHistory.SAMPLE_LIMIT + 5; i++) history.sample("tick=" + i);

        assertSame(first, history.firstFailure());
        assertSame(first, history.departure(jug));
        assertSame(first, history.cargoFailure(jug));
        assertEquals("OFFLINE", history.firstFailure().phase());
        assertEquals(oldEntity, history.firstFailure().originalEntityUuid());
        assertNull(history.firstFailure().currentEntityUuid());
        assertEquals(CargoReconnectWitnessHistory.SAMPLE_LIMIT, history.recent().size());
        assertEquals("tick=5", history.recent().get(0));
        assertEquals("tick=36", history.recent().get(31));
        assertEquals("jug/water=500", history.firstFailure().baselineStack());
        assertEquals("prior=full", history.firstFailure().priorSample());
    }
}
