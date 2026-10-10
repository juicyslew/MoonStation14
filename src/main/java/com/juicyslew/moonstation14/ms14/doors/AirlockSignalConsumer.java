package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal;

/** Pure command decoding for the authoritative airlock device-network adapter. */
public final class AirlockSignalConsumer {
    public enum Command { OPEN, CLOSE, TOGGLE, AUTO_CLOSE_HOLD, BOLT_TOGGLE, BOLT_DOWN, BOLT_UP }

    private AirlockSignalConsumer() { }

    /** Null means the signal is not a valid invocation for this input. */
    public static Command command(String port, DeviceSignal signal) {
        if (port == null || signal == null) return null;
        return switch (port) {
            case "Open" -> signal == DeviceSignal.HIGH || signal == DeviceSignal.MOMENTARY ? Command.OPEN : null;
            case "Close" -> signal == DeviceSignal.HIGH || signal == DeviceSignal.MOMENTARY ? Command.CLOSE : null;
            case "Toggle" -> signal == DeviceSignal.HIGH || signal == DeviceSignal.MOMENTARY ? Command.TOGGLE : null;
            case "AutoClose" -> Command.AUTO_CLOSE_HOLD;
            case "DoorBolt" -> switch (signal) {
                case MOMENTARY -> Command.BOLT_TOGGLE;
                case HIGH -> Command.BOLT_DOWN;
                case LOW -> Command.BOLT_UP;
                case UNKNOWN -> null;
            };
            default -> null;
        };
    }

    /** Upstream logical door state; animation-only phases and denial visuals do not change it. */
    public static AirlockState.DoorPhase logicalDoorState(AirlockState.DoorPhase phase) {
        if (phase == null) return null;
        return switch (phase) {
            case CLOSED, DENYING -> AirlockState.DoorPhase.CLOSED;
            case OPENING, OPENING_PASSABLE -> AirlockState.DoorPhase.OPENING;
            case OPEN -> AirlockState.DoorPhase.OPEN;
            case CLOSING, CLOSING_PASSABLE -> AirlockState.DoorPhase.CLOSING;
        };
    }

    /** DoorStatus is low only for logical Closed; UNKNOWN is never emitted. */
    public static DeviceSignal doorStatus(AirlockState.DoorPhase phase) {
        AirlockState.DoorPhase logical = logicalDoorState(phase);
        if (logical == null) return DeviceSignal.UNKNOWN;
        return logical == AirlockState.DoorPhase.CLOSED ? DeviceSignal.LOW : DeviceSignal.HIGH;
    }

}
