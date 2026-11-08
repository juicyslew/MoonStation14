package com.juicyslew.moonstation14.gametest.support;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Boundary coverage for bounded readiness polling. */
public final class BoundedGameTestWaitGameTests {
    private BoundedGameTestWaitGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags({"fixture-readiness"})
    @PrefixGameTestTemplate(false)
    public static void readinessOnInclusiveDeadlineSucceeds(GameTestHelper helper) {
        final int maxWaitTicks = 8;
        final long startedAt = helper.getTick();
        final boolean[] timeoutDiagnosticRan = {false};
        final long[] observedReadyTick = {-1};
        BoundedGameTestWait.await(helper, maxWaitTicks, () -> {
            timeoutDiagnosticRan[0] = true;
            return "readiness should win at the inclusive deadline";
        }, () -> {
            long elapsed = helper.getTick() - startedAt;
            if (elapsed < maxWaitTicks) return false;
            observedReadyTick[0] = elapsed;
            return true;
        }, () -> {
            if (observedReadyTick[0] != maxWaitTicks)
                throw new AssertionError("readiness was not observed at exact deadline: elapsed=" + observedReadyTick[0]);
            if (timeoutDiagnosticRan[0]) throw new AssertionError("timeout diagnostic ran despite deadline readiness");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"fixture-readiness"})
    @PrefixGameTestTemplate(false)
    public static void immediatelyReadyConditionRunsCallback(GameTestHelper helper) {
        BoundedGameTestWait.await(helper, 4, () -> "immediate condition unexpectedly timed out",
                () -> true, () -> {
                    helper.succeed();
                });
    }
}
