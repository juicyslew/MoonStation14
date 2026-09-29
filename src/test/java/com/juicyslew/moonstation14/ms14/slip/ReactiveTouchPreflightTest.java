package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectDuration;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveTouchPreflightTest {
    @Test
    void preflightUsesRegisteredHandlersAndRejectsUnsupportedKnockdownProjections() {
        assertTrue(ReactiveTouchSystem.supportsTouchPayload(
                new EffectData.ModifyBleed(EffectCommonData.DEFAULT, 1f)));
        assertTrue(ReactiveTouchSystem.supportsTouchPayload(
                new EffectData.ModifyBloodLevel(EffectCommonData.DEFAULT, 1f)));

        EffectData.ModifyKnockdown crawling = knockdown(StatusEffectOperation.ADD, true, false, 2f);
        EffectData.ModifyKnockdown dropping = knockdown(StatusEffectOperation.UPDATE, false, true, 2f);
        assertFalse(ReactiveTouchSystem.supportsTouchPayload(crawling));
        assertFalse(ReactiveTouchSystem.supportsTouchPayload(dropping));

        assertTrue(ReactiveTouchSystem.supportsTouchPayload(
                knockdown(StatusEffectOperation.REMOVE, true, true, 2f)));
        assertTrue(ReactiveTouchSystem.supportsTouchPayload(
                knockdown(StatusEffectOperation.ADD, true, true, 0f)));
        assertFalse(ReactiveTouchSystem.supportsTouchPayload(permanentKnockdown(
                StatusEffectOperation.ADD, true, false)));
        assertFalse(ReactiveTouchSystem.supportsTouchPayload(permanentKnockdown(
                StatusEffectOperation.UPDATE, false, true)));
        assertTrue(ReactiveTouchSystem.supportsTouchPayload(
                new EffectData.Jitter(EffectCommonData.DEFAULT, 1f, 1f, 1f, false)));
    }

    private static EffectData.ModifyKnockdown knockdown(StatusEffectOperation operation,
                                                         boolean crawling, boolean drop, float duration) {
        return new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT, StatusEffectDuration.finite(duration),
                operation, 0f, crawling, drop);
    }

    private static EffectData.ModifyKnockdown permanentKnockdown(StatusEffectOperation operation,
                                                                  boolean crawling, boolean drop) {
        return new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT, StatusEffectDuration.permanent(),
                operation, 0f, crawling, drop);
    }
}
