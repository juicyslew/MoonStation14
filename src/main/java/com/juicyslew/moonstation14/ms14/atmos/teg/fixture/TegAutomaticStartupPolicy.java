package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

/** Pure, live-controller decision policy. Production readiness remains owned by ordinary phase actions. */
public final class TegAutomaticStartupPolicy {
    public static final String POWER_TOPOLOGY_WAIT_PREFIX = "Operating startup waiting for power topology:";
    private static final String POWER_TOPOLOGY_NOT_READY = "Operating startup refused before controls: power topology is not ready.";
    public static boolean retryablePowerWait(String result) {
        return result != null && (result.startsWith(POWER_TOPOLOGY_WAIT_PREFIX)
                || result.equals(POWER_TOPOLOGY_NOT_READY));
    }
    public static final long PREHEAT_BUDGET_TICKS = 2400;
    public static final long PHASE_BUDGET_TICKS = 600;
    public enum Attempt { START, START_HEATING, INITIALIZE, IGNITE, RUN, HEATER_OFF, COMPLETE, FAIL, WAIT }
    public record Decision(Attempt attempt, String failureReason) {
        public static Decision attempt(Attempt action) { return new Decision(action, ""); }
        public static Decision fail(String reason) { return new Decision(Attempt.FAIL, reason); }
    }
    private TegAutomaticStartupPolicy() {}

    public static Decision decide(TegFixtureSavedData.AutomaticStartupStatus state,
                                  TegFixtureSavedData.ContinuousPhase phase, boolean initialized,
                                  long nowTick, long armTick, long phaseStartedTick) {
        if (state == null || phase == null) return Decision.fail("invalid automatic startup state");
        if (state == TegFixtureSavedData.AutomaticStartupStatus.COMPLETE || state == TegFixtureSavedData.AutomaticStartupStatus.FAILED)
            return Decision.attempt(Attempt.WAIT);
        if (nowTick < armTick || armTick < 0) return Decision.fail("automatic startup clock rollback before preheat deadline");
        if (state == TegFixtureSavedData.AutomaticStartupStatus.WAITING_START)
            return before(nowTick, armTick, PREHEAT_BUDGET_TICKS, "START") ? Decision.attempt(Attempt.START) : Decision.fail("START preheat deadline (2400 ticks) expired");
        if (state == TegFixtureSavedData.AutomaticStartupStatus.PREPARING_COLD) {
            if (!initialized || phase != TegFixtureSavedData.ContinuousPhase.STARTUP)
                return Decision.fail("COLD_PREPARATION requires an initialized STARTUP fixture");
            if (phaseStartedTick < 0 || nowTick < phaseStartedTick)
                return Decision.fail("COLD_PREPARATION admission clock is invalid or in the future");
            if (!before(nowTick, armTick, PREHEAT_BUDGET_TICKS, "COLD_PREPARATION")) return Decision.fail("START preheat deadline (2400 ticks) expired");
            return nowTick - phaseStartedTick < 200 ? Decision.attempt(Attempt.WAIT) : Decision.attempt(Attempt.START_HEATING);
        }
        if (phase == TegFixtureSavedData.ContinuousPhase.BUILT)
            return before(nowTick, armTick, PREHEAT_BUDGET_TICKS, "BUILT")
                    ? (initialized ? Decision.fail("BUILT phase is already initialized") : Decision.attempt(Attempt.INITIALIZE))
                    : Decision.fail("BUILT preheat deadline (2400 ticks) expired");
        return switch (phase) {
            case BUILT -> throw new IllegalStateException("handled above");
            case STARTUP -> {
                if (!initialized) yield Decision.fail("STARTUP phase is not initialized");
                yield before(nowTick, armTick, PREHEAT_BUDGET_TICKS, "STARTUP") ? Decision.attempt(Attempt.IGNITE) : Decision.fail("STARTUP preheat deadline (2400 ticks) expired");
            }
            case IGNITION -> {
                yield phaseAttempt(nowTick, phaseStartedTick, Attempt.RUN, "IGNITION");
            }
            case RUNNING -> phaseAttempt(nowTick, phaseStartedTick, Attempt.HEATER_OFF, "RUNNING");
            case HEATER_OFF -> {
                if(phaseStartedTick<0||nowTick<phaseStartedTick) yield Decision.fail("HEATER_OFF phase clock rollback");
                yield nowTick-phaseStartedTick<=200 ? Decision.attempt(Attempt.COMPLETE) : Decision.fail("HEATER_OFF acknowledgement deadline (200 ticks) expired");
            }
            case NONE -> Decision.fail("automatic startup has no continuous operation phase");
        };
    }
    private static Decision phaseAttempt(long now, long started, Attempt attempt, String stage) {
        if (started < 0 || now < started) return Decision.fail(stage + " phase clock rollback");
        return now - started < PHASE_BUDGET_TICKS ? Decision.attempt(attempt) : Decision.fail(stage + " deadline (600 ticks) expired");
    }
    private static boolean before(long now, long anchor, long budget, String stage) {
        return now >= anchor && now - anchor < budget;
    }
}
