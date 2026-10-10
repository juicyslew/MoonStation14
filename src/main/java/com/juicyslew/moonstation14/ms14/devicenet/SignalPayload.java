package com.juicyslew.moonstation14.ms14.devicenet;
import java.util.Objects;
public record SignalPayload(String portId, DeviceSignal signal) implements DevicePayload {
 public SignalPayload { Objects.requireNonNull(portId); Objects.requireNonNull(signal); }
}
