package com.juicyslew.moonstation14.ms14.doors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirlockSignalConsumerTest {
    @Test
    void projectsEveryVisualPhaseToUpstreamLogicalStateAndSignal() {
        assertProjection(AirlockState.DoorPhase.CLOSED, AirlockState.DoorPhase.CLOSED, com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.LOW);
        assertProjection(AirlockState.DoorPhase.DENYING, AirlockState.DoorPhase.CLOSED, com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.LOW);
        assertProjection(AirlockState.DoorPhase.OPENING, AirlockState.DoorPhase.OPENING, com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.HIGH);
        assertProjection(AirlockState.DoorPhase.OPENING_PASSABLE, AirlockState.DoorPhase.OPENING, com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.HIGH);
        assertProjection(AirlockState.DoorPhase.OPEN, AirlockState.DoorPhase.OPEN, com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.HIGH);
        assertProjection(AirlockState.DoorPhase.CLOSING, AirlockState.DoorPhase.CLOSING, com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.HIGH);
        assertProjection(AirlockState.DoorPhase.CLOSING_PASSABLE, AirlockState.DoorPhase.CLOSING, com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.HIGH);
    }

    private static void assertProjection(AirlockState.DoorPhase visual, AirlockState.DoorPhase logical,
                                         com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal signal) {
        assertEquals(logical, AirlockSignalConsumer.logicalDoorState(visual));
        assertEquals(signal, AirlockSignalConsumer.doorStatus(visual));
    }
}
