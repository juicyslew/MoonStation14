package com.juicyslew.moonstation14.ms14.player_body_control;

/** Runtime supplied validation; it must check that the target is configured, loaded, and alive. */
@FunctionalInterface
public interface TargetEligibility {
    boolean isEligible(MobHarness target);
}
