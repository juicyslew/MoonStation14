package com.juicyslew.moonstation14.ms14.radiation;

/** Consumer for accumulated radiation rate and elapsed exposure duration. */
@FunctionalInterface
public interface RadiationDoseReceiver {
    void receive(double radsPerSecond, double durationSeconds);
}

