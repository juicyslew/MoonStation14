package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyPickupRouteGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyPickupRouteGameTestEntry {
    private BodyPickupRouteGameTestEntry() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void firstHitDenialsAndWorldToHandConservation(GameTestHelper helper) {
        BodyPickupRouteGameTests.firstHitDenialsAndWorldToHandConservation(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nearestOverridesHintAndStaleState(GameTestHelper helper) {
        BodyPickupRouteGameTests.nearestOverridesHintAndStaleState(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tiedItemsLowFirst(GameTestHelper helper) {
        BodyPickupRouteGameTests.tiedItemsTransferLowerUuid(helper, true);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tiedItemsHighFirst(GameTestHelper helper) {
        BodyPickupRouteGameTests.tiedItemsTransferLowerUuid(helper, false);
    }
}
