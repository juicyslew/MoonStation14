package com.juicyslew.moonstation14.gametest.support;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;

/** Aligns fixture observations away from scheduled reagent metabolism ticks. */
public final class MetabolismObservationWindow {
    private MetabolismObservationWindow() { }

    static boolean isQuiet(long start, int entityId, int firstRelevantOffset, int lastRelevantOffset) {
        if (firstRelevantOffset < 0 || lastRelevantOffset < firstRelevantOffset
                || lastRelevantOffset - firstRelevantOffset >= 20)
            throw new IllegalArgumentException("observation window must fit within one 20-tick metabolism interval");
        var metabolism = com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM;
        for (int offset = firstRelevantOffset; offset <= lastRelevantOffset; offset++)
            if (metabolism.isDue(start + offset, entityId)) return false;
        return true;
    }

    public static void runWhenQuiet(GameTestHelper helper, LivingEntity body, int firstRelevantOffset,
                                    int lastRelevantOffset, Runnable action) {
        long start = helper.getLevel().getGameTime();
        if (!isQuiet(start, body.getId(), firstRelevantOffset, lastRelevantOffset)) {
            helper.runAfterDelay(1, () -> runWhenQuiet(helper, body, firstRelevantOffset, lastRelevantOffset, action));
            return;
        }
        action.run();
    }
}
