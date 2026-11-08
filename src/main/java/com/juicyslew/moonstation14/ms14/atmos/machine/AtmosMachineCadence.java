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
        public long intervalTicks() { return AtmosMachineCadence.intervalTicks(deviceIntervalSeconds); }
    }

    public static long intervalTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) throw new IllegalArgumentException("interval seconds must be finite and positive");
        return Math.max(1, Math.round(seconds * SERVER_TICKS_PER_SECOND));
    }

    /** Fixed-grid due check. Anchoring on first observation prevents join-time catch-up. */
    public static final class Clock {
        private long anchorTick = -1;
        private long lastEventTick = -1;
        private boolean pending;
        public boolean due(long serverTick, Settings settings) {
            return due(serverTick, settings.intervalTicks());
        }
        public boolean due(long serverTick, long intervalTicks) {
            if (intervalTicks <= 0) throw new IllegalArgumentException("interval ticks must be positive");
            if (serverTick < 0) return false;
            if (anchorTick < 0 || serverTick < anchorTick) { reset(); anchorTick = serverTick; return false; }
            long frame = (serverTick - anchorTick) / intervalTicks;
            if (frame == 0 || frame == lastEventTick) return pending;
            // Consume elapsed frames, but retain no more than one unpaid event.
            lastEventTick = frame;
            pending = true;
            return true;
        }
        /** Start a new fixed grid only after the caller establishes its readiness condition. */
        public void anchor(long serverTick) {
            reset();
            if (serverTick >= 0) anchorTick = serverTick;
        }
        public boolean anchored() { return anchorTick >= 0; }
        public void completed() { pending = false; }
        public void reset() { anchorTick = -1; lastEventTick = -1; pending = false; }
    }

    /** Paid allocations fund fixed-cost events; owners select the old-credit cap explicitly. */
    public static final class PaidWorkBudget {
        private UUID identity;
        private double credit;
        private long latestGrantTick = -1;
        private long lastWorkTick = -1;
        private long grantWatermark = -1;

        public boolean accept(UUID owner, double actualJoules, long sampleTick, double eventCostJoules) {
            return accept(owner, actualJoules, sampleTick, eventCostJoules, eventCostJoules);
        }

        /** Unary devices may retain at most one billing quantum plus one event; other owners retain legacy cap. */
        public boolean accept(UUID owner, double actualJoules, long sampleTick,
                              double eventCostJoules, double maximumOldCarryJoules) {
            if (owner == null || !Double.isFinite(actualJoules) || actualJoules <= 0
                    || !Double.isFinite(eventCostJoules) || eventCostJoules <= 0
                    || !Double.isFinite(maximumOldCarryJoules) || maximumOldCarryJoules < 0
                    || sampleTick < 0) { unavailable(); return false; }
            if (identity != null && !identity.equals(owner)) unavailable();
            if (sampleTick <= grantWatermark) return false;
            if (latestGrantTick >= 0 && sampleTick - latestGrantTick > PowerRuntime.TICKS_PER_SOLVE) credit = 0;
            double nextCredit = Math.min(maximumOldCarryJoules, credit) + actualJoules;
            if (!Double.isFinite(nextCredit)) { unavailable(); return false; }
            identity = owner;
            // Limit only old carry. Fresh paid grants remain fully credited; the cap cannot mint energy.
            credit = nextCredit;
            latestGrantTick = sampleTick;
            grantWatermark = sampleTick;
            return true;
        }

        public boolean canWork(UUID owner, long serverTick, long currentPowerTick, double eventCostJoules) {
            if (owner == null || !owner.equals(identity) || serverTick < 0 || latestGrantTick < 0
                    || currentPowerTick < latestGrantTick || currentPowerTick - latestGrantTick > PowerRuntime.TICKS_PER_SOLVE
                    || serverTick == lastWorkTick || !Double.isFinite(eventCostJoules) || eventCostJoules <= 0) {
                if (owner == null || (identity != null && !identity.equals(owner))) clear();
                return false;
            }
            return credit >= eventCostJoules;
        }

        /** Call only after the device operation is accepted; energy is never speculatively refunded. */
        public boolean commitWork(UUID owner, long serverTick, long currentPowerTick, double eventCostJoules) {
            if (!canWork(owner, serverTick, currentPowerTick, eventCostJoules)) return false;
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
