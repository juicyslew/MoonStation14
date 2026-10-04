package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LoosePullMotionGameTestEntry {
    private LoosePullMotionGameTestEntry() { }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void pullMovesHorizontallyWithoutChangingSource(GameTestHelper helper) {
        com.juicyslew.moonstation14.ms14.loose_objects.pulling.LoosePullMotionGameTests
                .pullMovesHorizontallyWithoutChangingSource(helper);
    }
}
