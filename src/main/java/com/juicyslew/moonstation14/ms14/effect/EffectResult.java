package com.juicyslew.moonstation14.ms14.effect;

/** Outcome of attempting one effect. */
public enum EffectResult {
    APPLIED,
    SKIPPED_SCALE,
    SKIPPED_PROBABILITY,
    SKIPPED_CONDITION,
    SKIPPED_UNSUPPORTED,
    FAILED
}
