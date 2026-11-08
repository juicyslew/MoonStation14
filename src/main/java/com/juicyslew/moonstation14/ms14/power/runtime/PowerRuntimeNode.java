package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;

import java.util.List;

/** Narrow opt-in bridge for loaded block entities with an explicit typed power port. */
public interface PowerRuntimeNode {
    /** Ports must use this BE's own position; call {@link PowerRuntime#deviceChanged} after port changes. */
    List<DevicePort> powerRuntimePorts();

    /** Null when this node is not a supplier. Zero current and zero rating are valid inactive states. */
    default SupplierOffer powerSupplierOffer() { return null; }
    /** Interval-aware supplier offer; energy-backed suppliers can cap output to stored energy. */
    default SupplierOffer powerSupplierOffer(long elapsedTicks) { return powerSupplierOffer(); }

    /** Null when this node is not a genuine MV consumer; zero is valid. Demand changes call deviceChanged. */
    default Double mvPowerDemandWatts() { return null; }

    /** Null when this node is not a generic APC-tier receiver; demand changes call deviceChanged. */
    default Double apcPowerDemandWatts() { return null; }

    /** Stable placement/session identity used to bind fresh allocation observations. */
    default java.util.UUID apcPowerIdentity() { return null; }

    /** Receives actual allocation from this solve, including 0/unknown. */
    void receiveMvPowerAllocation(double actualWatts, boolean graphKnown, long sampleTick);

    /** Receives this solve's APC allocation, including zero/unknown. */
    default void receiveApcPowerAllocation(double actualWatts, boolean graphKnown, long sampleTick) { }

    /** Actual HV source delivery; elapsedTicks are ticks at 20 ticks/second. */
    default void receivePowerSupplyAllocation(double actualWatts, boolean graphKnown,
                                              long elapsedTicks, long sampleTick) { }

    record SupplierOffer(double currentWatts, double ratedMaximumWatts) {
        public SupplierOffer {
            if (!Double.isFinite(currentWatts) || currentWatts < 0
                    || !Double.isFinite(ratedMaximumWatts) || ratedMaximumWatts < 0)
                throw new IllegalArgumentException("Invalid supplier offer");
        }
    }
}

