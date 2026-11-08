package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import java.util.UUID;

/** Small production state kernel shared by the BE and focused cadence/lease tests. */
public final class AtmosMachinePowerState {
    private AtmosMachinePowerState() { }
    public static final PowerRuntimeNode.SupplierOffer ZERO_OFFER = new PowerRuntimeNode.SupplierOffer(0, 0);

    /** Transient TEG conversion energy awaiting exactly one supplier solve; never persisted. */
    public static final class TegEnergyBudget {
        private double joules;
        public void credit(double generatedJoules, boolean paidWorkCommitted) {
            if (paidWorkCommitted && Double.isFinite(generatedJoules) && generatedJoules > 0) {
                double sum = joules + generatedJoules;
                if (Double.isFinite(sum)) joules = sum;
            }
        }
        public double capWatts(double current, double rated, long elapsedTicks) {
            if (elapsedTicks < 1 || elapsedTicks > PowerRuntime.TICKS_PER_SOLVE
                    || !Double.isFinite(current) || current < 0 || !Double.isFinite(rated) || rated < current
                    || !Double.isFinite(joules) || joules <= 0) return 0;
            double cap = joules / (elapsedTicks / 20d);
            return Double.isFinite(cap) ? Math.min(cap, Math.min(current, rated)) : 0;
        }
        /** Consume once; accepted energy above the budget is invalid, and every solve discards surplus. */
        public boolean settle(double acceptedWatts, long elapsedTicks, boolean known) {
            double available = joules;
            joules = 0;
            if (!known || elapsedTicks < 1 || elapsedTicks > PowerRuntime.TICKS_PER_SOLVE
                    || !Double.isFinite(acceptedWatts) || acceptedWatts < 0) return false;
            double accepted = acceptedWatts * elapsedTicks / 20d;
            double tolerance = 8 * Math.ulp(Math.max(1d, available));
            return Double.isFinite(accepted) && accepted <= available + tolerance;
        }
        public void clear() { joules = 0; }
        public double pendingJoules() { return joules; }
    }

    /** SS14 pumps retain their component receiver draw while disabled or idle; enabled gates gas flow only. */
    public static Double apcDemand(AtmosMachineProfile profile, boolean configuredPowerEnabled) {
        if (profile == null || (profile.power() == null && profile.thermostat() == null)) return null;
        if (profile.pump() != null) return profile.configuredReceiverLoadWatts();
        // TEG assembly eligibility is deliberately left to its later runtime owner.
        if (profile.thermostat() != null || profile.teg() != null)
            return configuredPowerEnabled ? profile.configuredReceiverLoadWatts() : 0d;
        return profile.configuredReceiverLoadWatts();
    }

    /** sampleTick is the completing allocation callback and the authoritative freshness-clock value. */
    public record PaidInterval(UUID placementIdentity, long actualElapsedTicks, double actualJoules,
                               double allocationWatts, double requestedWatts, long sampleTick) { }

    public static final class PaidIntervalTracker {
        private UUID placementIdentity;
        private long anchorSampleTick = -1;
        private boolean anchorValid;
        private PaidInterval pending;

        /** Completes input from this solve's actual allocation, never the prior solve's cached watt value. */
        public void accept(UUID identity, double actualWatts, double requestedWatts, boolean graphKnown,
                           long sampleTick) {
            boolean valid = identity != null && graphKnown && Double.isFinite(actualWatts) && actualWatts > 0
                    && Double.isFinite(requestedWatts) && requestedWatts > 0 && actualWatts >= requestedWatts
                    && sampleTick >= 0;
            if (!valid) { clear(); return; }
            if (anchorValid && identity.equals(placementIdentity) && sampleTick > anchorSampleTick) {
                long elapsed = sampleTick - anchorSampleTick;
                if (elapsed <= PowerRuntime.TICKS_PER_SOLVE && pending == null) {
                    double joules = actualWatts * elapsed / 20d;
                    if (Double.isFinite(joules) && joules > 0)
                        pending = new PaidInterval(identity, elapsed, joules, actualWatts, requestedWatts,
                                sampleTick);
                } else if (elapsed > PowerRuntime.TICKS_PER_SOLVE) {
                    pending = null;
                }
            } else if (anchorValid && !identity.equals(placementIdentity)) {
                pending = null;
            }
            // A duplicate callback at the same sample tick cannot add time or change the accepted anchor.
            if (!anchorValid || !identity.equals(placementIdentity) || sampleTick > anchorSampleTick) {
                placementIdentity = identity;
                anchorSampleTick = sampleTick;
                anchorValid = true;
            }
        }

        public PaidInterval consume(long currentSampleTick) {
            PaidInterval result = pending;
            pending = null;
            if (result == null || currentSampleTick < result.sampleTick()
                    || currentSampleTick - result.sampleTick() > PowerRuntime.TICKS_PER_SOLVE) return null;
            return result;
        }

        public void clear() {
            placementIdentity = null;
            anchorSampleTick = -1; anchorValid = false; pending = null;
        }
    }

    /** Stale, disabled, or disconnected TEGs remain registered as zero-capacity sources, never as old positive offers. */
    public static PowerRuntimeNode.SupplierOffer offer(boolean tegProfile, boolean exactLoaded,
                                                        boolean enabled, boolean inputKnown,
                                                        double inputWatts, double inputDemand,
                                                        long inputSampleTick, long nowSampleTick,
                                                        PowerRuntimeNode.SupplierOffer published,
                                                        long publishedSampleTick) {
        if (!tegProfile) return null;
        boolean freshInput = inputSampleTick >= 0 && nowSampleTick >= inputSampleTick
                && nowSampleTick - inputSampleTick <= PowerRuntime.TICKS_PER_SOLVE;
        boolean freshOffer = published != null && publishedSampleTick >= 0 && nowSampleTick >= publishedSampleTick
                && nowSampleTick - publishedSampleTick <= PowerRuntime.TICKS_PER_SOLVE;
        if (!exactLoaded || !enabled || !inputKnown || !freshInput || !Double.isFinite(inputWatts)
                || !Double.isFinite(inputDemand) || inputDemand <= 0 || inputWatts < inputDemand || !freshOffer)
            return ZERO_OFFER;
        return published;
    }
}
