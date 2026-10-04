package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** Pure accounting gate for gas work paid for by one completed power-solver interval. */
public final class VentilationPaidInterval {
    public static final double RECEIVER_WATTS = 5.0;
    private static final double WATTS_EPSILON = 1.0e-9;

    /** Identity values are opaque block-entity identities; no game/runtime objects are accessed here. */
    public record ReceiverSignature(Object ventilationBE, Object apcBE, long breakerRevision,
                                    long topologyGeneration, boolean known, boolean ready,
                                    boolean breakerClosed, long tick) { }

    /** Meter values are observations, not inputs to tariff or delivery authorization. */
    public record IntervalResult(double deliveredWatts, long elapsedTicks,
                                 double sourceMeter, double batteryMeter) { }

    private final IdentityHashMap<Object, Long> lastAuthorizedEndTick = new IdentityHashMap<>();

    /**
     * Authorize gas work metered for the interval already billed by the solver.
     * An operation receives only this interval's meteredElapsed seconds; there is no future-time credit.
     */
    public synchronized Optional<WorkBudget> authorizeCompletedInterval(
            ReceiverSignature previous, ReceiverSignature current,
            double deliveredWatts, long elapsedTicks) {
        return authorizeCompletedInterval(previous, current, deliveredWatts, elapsedTicks, 0, 0);
    }

    public synchronized Optional<WorkBudget> authorizeCompletedInterval(
            ReceiverSignature previous, ReceiverSignature current,
            double deliveredWatts, long elapsedTicks, double sourceMeter, double batteryMeter) {
        if (previous == null || current == null || !sameReceiver(previous, current)
                || !previous.known() || !previous.ready() || !previous.breakerClosed()
                || !current.known() || !current.ready() || !current.breakerClosed()
                || previous.tick() < 0 || current.tick() < 0
                || elapsedTicks <= 0 || elapsedTicks > PowerRuntime.TICKS_PER_SOLVE
                || current.tick() <= previous.tick()
                || current.tick() - previous.tick() != elapsedTicks
                || !Double.isFinite(deliveredWatts)
                || Math.abs(deliveredWatts - RECEIVER_WATTS) > WATTS_EPSILON)
            return Optional.empty();

        if (!Double.isFinite(sourceMeter) || sourceMeter < 0
                || !Double.isFinite(batteryMeter) || batteryMeter < 0) return Optional.empty();
        Object receiver = current.ventilationBE();
        Long lastEnd = lastAuthorizedEndTick.get(receiver);
        if (lastEnd != null && previous.tick() < lastEnd) return Optional.empty();
        if (lastEnd == null && lastAuthorizedEndTick.size() >= PipeTopology.MAX_INPUT_NODES) return Optional.empty();
        lastAuthorizedEndTick.put(receiver, current.tick());
        return Optional.of(new WorkBudget(elapsedTicks / 20.0,
                new IntervalResult(deliveredWatts, elapsedTicks, sourceMeter, batteryMeter)));
    }

    /** Release receiver state when its block entity is removed. */
    public synchronized void forget(Object ventilationBE) {
        if (ventilationBE != null) lastAuthorizedEndTick.remove(ventilationBE);
    }

    /** Clear all bounded receiver state during teardown. */
    public synchronized void clear() { lastAuthorizedEndTick.clear(); }

    /** Number of currently tracked receiver identities. */
    public synchronized int trackedReceiverCount() { return lastAuthorizedEndTick.size(); }

    private static boolean sameReceiver(ReceiverSignature a, ReceiverSignature b) {
        return a.ventilationBE() != null && a.ventilationBE() == b.ventilationBE()
                && a.apcBE() != null && a.apcBE() == b.apcBE()
                && a.breakerRevision() == b.breakerRevision()
                && a.topologyGeneration() == b.topologyGeneration();
    }

    /** Single-use capability, limited to elapsed paid time. */
    public static final class WorkBudget {
        private final double elapsedSeconds;
        private final IntervalResult accounting;
        private boolean consumed;

        private WorkBudget(double elapsedSeconds, IntervalResult accounting) {
            this.elapsedSeconds = elapsedSeconds;
            this.accounting = accounting;
        }

        public synchronized Optional<IntervalResult> consume(double requestedSeconds) {
            if (consumed || !Double.isFinite(requestedSeconds) || requestedSeconds <= 0
                    || Double.compare(requestedSeconds, elapsedSeconds) != 0) return Optional.empty();
            consumed = true;
            return Optional.of(accounting);
        }

        public double elapsedSeconds() { return elapsedSeconds; }
        public double paidJoules() { return accounting.deliveredWatts() * accounting.elapsedTicks() / 20.0; }
    }
}
