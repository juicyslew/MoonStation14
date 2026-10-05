package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseMotionChunkGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"loose-objects", "lifecycle"})
public final class LooseMotionChunkGameTestEntry {
    private LooseMotionChunkGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 10)
    public static void exclusiveSweepBoundary(GameTestHelper helper) {
        LooseMotionChunkGameTests.exclusiveSweepBoundary(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 10)
    public static void unloadedNeighborMotion(GameTestHelper helper) {
        LooseMotionChunkGameTests.unloadedNeighborMotion(helper);
    }
}
