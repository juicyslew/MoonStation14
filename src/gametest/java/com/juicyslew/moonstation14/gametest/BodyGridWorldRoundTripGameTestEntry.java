package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGridWorldRoundTripGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyGridWorldRoundTripGameTestEntry {
    private BodyGridWorldRoundTripGameTestEntry() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void explicitGridBagWorldRoundTripConservesChildren(GameTestHelper helper) {
        BodyGridWorldRoundTripGameTests.explicitGridBagWorldRoundTripConservesChildren(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hostileGridPickupAndLegacyBeltRemainConserved(GameTestHelper helper) {
        BodyGridWorldRoundTripGameTests.hostileGridPickupAndLegacyBeltRemainConserved(helper);
    }
}
