package com.juicyslew.moonstation14.ms14.explosion.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionPresentationTest {
    @Test
    void emissionFailureIsolatedAndLaterRecipientStillRuns() {
        boolean[] laterRecipient = {false};
        ExplosionPresentation.sendBestEffort(() -> { throw new IllegalStateException("packet failed"); });
        ExplosionPresentation.sendBestEffort(() -> laterRecipient[0] = true);
        assertTrue(laterRecipient[0]);
    }
}
