package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import java.util.UUID;

/** Shared fixed-time atmosphere-machine cadence and bounded accounting of actually paid power. */
public final class AtmosMachineCadence {
    private AtmosMachineCadence() { }
    public static final double DEFAULT_DEVICE_INTERVAL_SECONDS = 0.6;
    private static final double SERVER_TICKS_PER_SECOND = 20d;

    public record Settings(double deviceIntervalSeconds, double pumpSpeedup, double tileRadiationLossWatts) {
        public Settings {
            if (!Double.isFinite(deviceIntervalSeconds) || deviceIntervalSeconds <= 0
                    || !Double.isFinite(pumpSpeedup) || pumpSpeedup <= 0
                    || !Double.isFinite(tileRadiationLossWatts) || tileRadiationLossWatts < 0)
                throw new IllegalArgumentException("invalid atmosphere machine settings");
        }
        public long intervalTicks() { return Math.max(1, Math.round(deviceIntervalSeconds * SERVER_TICKS_PER_SECOND)); }
    }

    /** Fixed-grid due check. Anchoring on first observation prevents join-time catch-up. */
    public static final class Clock {
        private long anchorTick = -1;
        private long lastEventTick = -1;
        private boolean pending;
        public boolean due(long serverTick, Settings settings) {
            if (serverTick < 0) return false;
            if (anchorTick < 0 || serverTick < anchorTick) { reset(); anchorTick = serverTick; return false; }
            long frame = (serverTick - anchorTick) / settings.intervalTicks();
            if (frame == 0 || frame == lastEventTick) return pending;
            // Consume elapsed frames, but retain no more than one unpaid event.
            lastEventTick = frame;
            pending = true;
            return true;
        }
        public void completed() { pending = false; }
        public void reset() { anchorTick = -1; lastEventTick = -1; pending = false; }
    }

    /** Paid allocations fund fixed-cost device events; unspent credit is limited to one event. */
    public static final class PaidWorkBudget {
        private UUID identity;
        private double credit;
        private long latestGrantTick = -1;
        private long lastWorkTick = -1;
        private long grantWatermark = -1;

        public void accept(UUID owner, double actualJoules, long sampleTick, double eventCostJoules) {
            if (owner == null || !Double.isFinite(actualJoules) || actualJoules <= 0
                    || !Double.isFinite(eventCostJoules) || eventCostJoules <= 0 || sampleTick < 0) { unavailable(); return; }
            if (identity != null && !identity.equals(owner)) unavailable();
            if (sampleTick <= grantWatermark) return;
            if (latestGrantTick >= 0 && sampleTick - latestGrantTick > PowerRuntime.TICKS_PER_SOLVE) credit = 0;
            identity = owner;
            // Limit only the old carry. A fresh paid grant may fund multiple distinct events.
            credit = Math.min(eventCostJoules, credit) + actualJoules;
            latestGrantTick = sampleTick;
            grantWatermark = sampleTick;
        }

        public boolean canWork(UUID owner, long serverTick, long currentSampleTick, double eventCostJoules) {
            if (owner == null || !owner.equals(identity) || serverTick < 0 || latestGrantTick < 0
                    || currentSampleTick < latestGrantTick || currentSampleTick - latestGrantTick > PowerRuntime.TICKS_PER_SOLVE
                    || serverTick == lastWorkTick || !Double.isFinite(eventCostJoules) || eventCostJoules <= 0) {
                if (owner == null || (identity != null && !identity.equals(owner))) clear();
                return false;
            }
            return credit >= eventCostJoules;
        }

        /** Call only after the device operation is accepted; energy is never speculatively refunded. */
        public boolean commitWork(UUID owner, long serverTick, long currentSampleTick, double eventCostJoules) {
            if (!canWork(owner, serverTick, currentSampleTick, eventCostJoules)) return false;
            credit -= eventCostJoules;
            lastWorkTick = serverTick;
            return true;
        }
        public double remainingJoules() { return credit; }
        public void clear() { resetCredits(); }
        /** Clears offers/credits without allowing a consumed historical grant to be replayed. */
        private void resetCredits() { identity = null; credit = 0; latestGrantTick = -1; lastWorkTick = -1; }
        public void unavailable() { resetCredits(); }
    }
}
