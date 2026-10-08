package com.juicyslew.moonstation14.ms14.doors.ui;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.Light.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AirlockWireIndicatorsTest {
    @Test void initialPoweredIndicatorsAreIndependentOfVisibleDoorBoltLamp() {
        var state = AirlockState.initial(UUID.randomUUID(), true, true).withPower(true);
        var status = AirlockWireIndicators.resolve(state, 0);
        assertEquals(ON, status.power());
        assertEquals(OFF, status.bolts());
        assertEquals(ON, status.boltLights());
        assertEquals(ON, status.timing());
        assertEquals(ON, status.safety());
        // The named bolt-wire status describes the circuit, not the active door overlay.
        org.junit.jupiter.api.Assertions.assertEquals(false, state.boltLightsVisible());
    }

    @Test void powerWireFeedbackAndPowerOffWrapperMatchSourceBehavior() {
        var powered = AirlockState.initial(UUID.randomUUID(), true, true).withPower(true);
        var oneCut = powered.applyWire(AirlockState.Wire.POWER_ONE, AirlockState.WireAction.CUT, 0);
        assertEquals(BLINK_SLOW, AirlockWireIndicators.resolve(oneCut, 0).power());
        var bothCut = oneCut.applyWire(AirlockState.Wire.POWER_TWO, AirlockState.WireAction.CUT, 0);
        assertEquals(OFF, AirlockWireIndicators.resolve(bothCut, 0).power());
        assertEquals(new AirlockWireIndicators.Status(OFF, OFF, OFF, OFF, OFF, ON, ON),
                AirlockWireIndicators.resolve(powered.withPower(false), 0));
        var pulsed = powered.applyWire(AirlockState.Wire.POWER_ONE, AirlockState.WireAction.PULSE, 0);
        assertEquals(BLINK_SLOW, AirlockWireIndicators.resolve(pulsed, 0).power());
    }

    @Test void timingAndSafetyWireStatusesTrackResolvedLiveEffects() {
        var powered = AirlockState.initial(UUID.randomUUID(), true, true).withPower(true);
        var timingCut = powered.applyWire(AirlockState.Wire.TIMING, AirlockState.WireAction.CUT, 0);
        assertEquals(OFF, AirlockWireIndicators.resolve(timingCut, 0).timing());
        var timingPulse = powered.applyWire(AirlockState.Wire.TIMING, AirlockState.WireAction.PULSE, 0);
        assertEquals(BLINK_SLOW, AirlockWireIndicators.resolve(timingPulse, 0).timing());
        var safetyCut = powered.applyWire(AirlockState.Wire.SAFETY, AirlockState.WireAction.CUT, 0);
        var safetyPulse = powered.applyWire(AirlockState.Wire.SAFETY, AirlockState.WireAction.PULSE, 0);
        assertEquals(OFF, AirlockWireIndicators.resolve(safetyCut, 0).safety());
        assertEquals(OFF, AirlockWireIndicators.resolve(safetyPulse, 0).safety());
        assertEquals(ON, AirlockWireIndicators.resolve(powered, 0).safety());
    }

    @Test void aiAndLogLightsReportOnlyPhysicalCircuitContinuity() {
        var initial = AirlockState.initial(UUID.randomUUID(), true, true);
        assertEquals(ON, AirlockWireIndicators.resolve(initial, 0).aiInteract());
        assertEquals(ON, AirlockWireIndicators.resolve(initial, 0).log());
        var aiCut = initial.applyWire(AirlockState.Wire.AI_INTERACT, AirlockState.WireAction.CUT, 0);
        assertEquals(OFF, AirlockWireIndicators.resolve(aiCut, 0).aiInteract());
        assertEquals(ON, AirlockWireIndicators.resolve(aiCut, 0).log());
        var logCut = aiCut.applyWire(AirlockState.Wire.LOG, AirlockState.WireAction.CUT, 0);
        assertEquals(OFF, AirlockWireIndicators.resolve(logCut, 0).log());
        assertEquals(ON, AirlockWireIndicators.resolve(logCut.applyWire(AirlockState.Wire.LOG, AirlockState.WireAction.MEND, 0), 0).log());
        assertEquals(OFF, AirlockWireIndicators.resolve(aiCut.applyWire(AirlockState.Wire.AI_INTERACT, AirlockState.WireAction.PULSE, 0), 0).aiInteract());
    }
}
