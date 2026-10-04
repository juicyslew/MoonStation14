package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGridWorldRoundTripGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "storage"})
public final class BodyGridWorldRoundTripGameTestEntry {
    private BodyGridWorldRoundTripGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void explicitGridBagWorldRoundTripConservesChildren(GameTestHelper helper) {
        BodyGridWorldRoundTripGameTests.explicitGridBagWorldRoundTripConservesChildren(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void hostileGridPickupAndLegacyBeltRemainConserved(GameTestHelper helper) {
        BodyGridWorldRoundTripGameTests.hostileGridPickupAndLegacyBeltRemainConserved(helper);
    }
}
