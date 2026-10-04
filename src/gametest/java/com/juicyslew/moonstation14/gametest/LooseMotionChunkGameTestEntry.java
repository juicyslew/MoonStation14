package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseMotionChunkGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LooseMotionChunkGameTestEntry {
    private LooseMotionChunkGameTestEntry() { }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void exclusiveSweepBoundary(GameTestHelper helper) {
        LooseMotionChunkGameTests.exclusiveSweepBoundary(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void unloadedNeighborMotion(GameTestHelper helper) {
        LooseMotionChunkGameTests.unloadedNeighborMotion(helper);
    }
}
