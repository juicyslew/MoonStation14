package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldCrossPublicationGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "storage", "equipment-regression"})
public final class BodyWorldCrossPublicationGameTestEntry {
    private BodyWorldCrossPublicationGameTestEntry() { }
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void transfersBothDirectionsAndPreservesBothOwners(GameTestHelper helper) {
        BodyWorldCrossPublicationGameTests.transfersBothDirectionsAndPreservesBothOwners(helper);
    }
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void blockedFitAndPublisherFailureConserveChild(GameTestHelper helper) {
        BodyWorldCrossPublicationGameTests.blockedFitAndPublisherFailureConserveChild(helper);
    }
}
