package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyPickupRouteGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags("hands")
public final class BodyPickupRouteGameTestEntry {
    private BodyPickupRouteGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void firstHitDenialsAndWorldToHandConservation(GameTestHelper helper) {
        BodyPickupRouteGameTests.firstHitDenialsAndWorldToHandConservation(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void nearestOverridesHintAndStaleState(GameTestHelper helper) {
        BodyPickupRouteGameTests.nearestOverridesHintAndStaleState(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void tiedItemsLowFirst(GameTestHelper helper) {
        BodyPickupRouteGameTests.tiedItemsTransferLowerUuid(helper, true);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void tiedItemsHighFirst(GameTestHelper helper) {
        BodyPickupRouteGameTests.tiedItemsTransferLowerUuid(helper, false);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void coincidentItemsTransferToBothHands(GameTestHelper helper) {
        BodyPickupRouteGameTests.coincidentItemsTransferToBothHands(helper);
    }
}
