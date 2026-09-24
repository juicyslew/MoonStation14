package com.juicyslew.moonstation14.ms14.status_effect;

/** The lifecycle event emitted by one status-effect tick. */
public enum StatusEffectTransition {
    NONE,
    ACTIVATED,
    EXPIRED,
    INVALIDATED;

    /** Compatibility spelling for callers describing an undefined prototype. */
    public static final StatusEffectTransition UNDEFINED = INVALIDATED;
}
