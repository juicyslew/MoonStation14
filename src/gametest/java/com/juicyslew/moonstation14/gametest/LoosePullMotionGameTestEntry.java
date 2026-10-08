package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"loose-objects", "movement", "merge-main-regression"})
public final class LoosePullMotionGameTestEntry {
    private LoosePullMotionGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void pullMovesHorizontallyWithoutChangingSource(GameTestHelper helper) {
        com.juicyslew.moonstation14.ms14.loose_objects.pulling.LoosePullMotionGameTests
                .pullMovesHorizontallyWithoutChangingSource(helper);
    }
}
