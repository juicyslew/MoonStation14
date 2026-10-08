package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;

/** Pure mapping from the synchronized airlock view to exported atlas/panel presentation. */
final class AirlockFramePlanner {
    record Plan(String atlas, boolean maintenanceWires) { }

    private AirlockFramePlanner() { }

    static Plan plan(AirlockState.DoorPhase phase, boolean powered, boolean boltsDown,
                     boolean boltLightsVisible, boolean panelVisible, boolean panelOpen) {
        String atlas = atlas(phase, powered, boltsDown && boltLightsVisible);
        boolean wires = panelVisible && panelOpen && phase != AirlockState.DoorPhase.OPEN;
        if (wires) atlas = panelAtlas(atlas);
        return new Plan(atlas, wires);
    }

    private static String atlas(AirlockState.DoorPhase phase, boolean powered, boolean boltsVisible) {
        if (!powered) return "unpowered";
        if (boltsVisible) return "bolted";
        if (phase == AirlockState.DoorPhase.DENYING) return "denied";
        return switch (phase) {
            case OPEN, OPENING, OPENING_PASSABLE, CLOSING, CLOSING_PASSABLE -> "open";
            default -> "closed";
        };
    }

    private static String panelAtlas(String atlas) {
        return switch (atlas) {
            case "open" -> "open_panel";
            case "bolted" -> "bolted_panel";
            case "closed" -> "panel_open";
            default -> atlas;
        };
    }
}
