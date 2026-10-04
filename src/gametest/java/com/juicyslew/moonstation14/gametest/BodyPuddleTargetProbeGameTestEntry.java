package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyPuddleTargetProbeGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyPuddleTargetProbeGameTestEntry {
    private BodyPuddleTargetProbeGameTestEntry() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void thinPuddleMargin(GameTestHelper helper) {
        BodyPuddleTargetProbeGameTests.thinPuddleMarginCanBeSelectedAndStarted(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wallOcclusion(GameTestHelper helper) {
        BodyPuddleTargetProbeGameTests.wallOccludesPuddleProbeAndDrinkStart(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void maxReach(GameTestHelper helper) {
        BodyPuddleTargetProbeGameTests.actualCenterBeyondFiveBlocksIsOutOfReach(helper);
    }
}
