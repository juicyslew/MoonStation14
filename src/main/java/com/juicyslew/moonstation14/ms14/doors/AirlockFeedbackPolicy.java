package com.juicyslew.moonstation14.ms14.doors;

/** Audio-only admission policy: uncertainty and absent supply are silent. */
final class AirlockFeedbackPolicy {
    private AirlockFeedbackPolicy() { }

    static boolean deny(boolean supplyKnown, boolean powered, boolean quiet) {
        return supplyKnown && powered && !quiet;
    }
}

