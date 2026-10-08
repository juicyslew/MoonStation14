package com.juicyslew.moonstation14.ms14.doors.ui;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;

/** Door controls and physical circuit continuity lamps. AI/log services remain unimplemented. */
public final class AirlockWireIndicators {
    public enum Light { OFF, ON, BLINK_SLOW }
    public record Status(Light power, Light bolts, Light boltLights, Light timing, Light safety,
                         Light aiInteract, Light log) { }

    private AirlockWireIndicators() { }

    public static Status resolve(AirlockState state, long currentTick) {
        if (state == null) return new Status(Light.OFF, Light.OFF, Light.OFF, Light.OFF, Light.OFF, Light.OFF, Light.OFF);
        boolean powered = state.powerAvailable();
        boolean powerWireCut = state.powerWireOneCut() || state.powerWireTwoCut();
        Light power = !state.powered() || state.receiverPowerDisabled() && !state.powerPulseLatched() ? Light.OFF
                : powerWireCut || state.powerPulseLatched() ? Light.BLINK_SLOW : Light.ON;
        Light bolts = powered && state.boltsDown() ? Light.ON : Light.OFF;
        Light boltLights = powered && state.boltLightsEnabled() ? Light.ON : Light.OFF;
        Light timing = !powered || state.autoCloseModifier() == 0.01 ? Light.OFF
                : state.autoCloseModifier() <= 0.5 ? Light.BLINK_SLOW : Light.ON;
        Light safety = powered && state.safetyActive(currentTick) ? Light.ON : Light.OFF;
        return new Status(power, bolts, boltLights, timing, safety,
                state.aiInteractWireCut() ? Light.OFF : Light.ON,
                state.logWireCut() || state.logCircuitDisabled() ? Light.OFF : Light.ON);
    }
}
