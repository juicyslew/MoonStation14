package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestProgressObserver;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"gametest-progress"})
public final class GameTestProgressGameTests {
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "gametest-progress")
    public static void nativeTickerHookTracksAndAdvances(GameTestHelper helper) {
        if (!GameTestProgressObserver.isObserved(helper.testInfo)) {
            helper.fail("GameTest ticker progress hook did not observe this native test");
            return;
        }
        long initialTick = helper.getTick();
        helper.runAfterDelay(3, () -> {
            if (helper.getTick() < initialTick + 3) {
                helper.fail("native GameTest helper tick did not advance while observed");
                return;
            }
            helper.succeed();
        });
    }
}
