package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"gametest-polling"})
public final class NativeSequenceSchedulingGameTests {
    private NativeSequenceSchedulingGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 150)
    public static void repeatedWaitsAndTaskBridgesDoNotMutateActiveIterator(GameTestHelper helper) {
        int[] phase = {0};
        long start = helper.getTick();
        step(helper, phase, start);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 150)
    public static void overlappingNestedTimelinesDoNotMutateNativeIterators(GameTestHelper helper) {
        int[] completed = {0};
        long start = helper.getTick();
        Runnable[] timelines = new Runnable[2];
        for (int timeline = 0; timeline < timelines.length; timeline++) {
            int id = timeline;
            int[] phase = {0};
            timelines[timeline] = () -> nestedStep(helper, id, phase, completed, start);
            helper.runAfterDelay(1, timelines[timeline]);
        }
    }

    private static void nestedStep(GameTestHelper helper, int id, int[] phase, int[] completed, long start) {
        if (phase[0] == 64) {
            if (++completed[0] == 2) {
                if (helper.getTick() - start > 135) throw new GameTestAssertException("overlapping timelines exceeded bounded phase budget");
                helper.succeed();
            }
            return;
        }
        BoundedGameTestWait.safeDelay(helper, 1, () -> {
            if (helper.getTick() - start > 135) throw new GameTestAssertException("nested callback polling exceeded bounded phase budget");
            phase[0]++;
            BoundedGameTestWait.safeDelay(helper, 1, () -> nestedStep(helper, id, phase, completed, start));
        });
    }

    private static void step(GameTestHelper helper, int[] phase, long start) {
        if (phase[0] == 64) {
            // Each of 64 phases uses one readiness tick and one exact one-tick callback bridge.
            if (helper.getTick() - start > 130) throw new GameTestAssertException("polling exceeded 130-tick phase budget");
            helper.succeed();
            return;
        }
        int expected = phase[0];
        BoundedGameTestWait.await(helper, 2, () -> "readiness deadline at phase " + expected,
                () -> helper.getTick() > start + expected,
                () -> {
                    if (phase[0] != expected) throw new GameTestAssertException("duplicate/out-of-order callback");
                    phase[0]++;
                    BoundedGameTestWait.safeDelay(helper, 1, () -> step(helper, phase, start));
                });
    }
}
