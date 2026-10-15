package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;

/** Maps synchronized door state to authored, static native visual overlays. */
final class AirlockFramePlanner {
    record Plan(boolean maintenanceWires, boolean headerLamps, String headerOverlay, String readerOverlay) { }

    private AirlockFramePlanner() { }

    static Plan plan(AirlockState.DoorPhase phase, boolean powered, boolean boltsDown,
                     boolean boltLightsVisible, boolean panelVisible, boolean panelOpen) {
        boolean wires = panelVisible && panelOpen;
        String state = !powered ? null : boltsDown && boltLightsVisible ? "bolted_unlit" :
                phase == AirlockState.DoorPhase.DENYING ? "deny_unlit" :
                        phase == AirlockState.DoorPhase.CLOSED ? "closed_unlit" : "opening_unlit";
        String header = state == null ? null : state;
        String reader = state != null && (state.equals("opening_unlit") || state.equals("deny_unlit")) ? state : null;
        return new Plan(wires, powered, header, reader);
    }
}
