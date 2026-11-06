package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated, test-only experiment for stock physical yaw feedback response to a small roll seed. */
@PrefixGameTestTemplate(false)
public final class ShuttleYawPerturbationCauseGameTests {
    private ShuttleYawPerturbationCauseGameTests() { }

    @GameTestTags("shuttle-yaw-perturbation-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = YawPerturbationDiagnosticPlan.TEST_TIMEOUT_TICKS)
    public static void nativeYawFeedbackAfterRollPerturbation(GameTestHelper helper) {
        SableOwnedBodyCarryGameTests.nativeYawFeedbackAfterRollPerturbation(helper);
    }
}
