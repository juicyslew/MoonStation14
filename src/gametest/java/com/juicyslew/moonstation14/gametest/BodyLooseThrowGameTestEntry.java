package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrowGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "loose-objects"})
public final class BodyLooseThrowGameTestEntry {
    private BodyLooseThrowGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void wholeStackThrowAndStaleDenial(GameTestHelper helper) {
        BodyLooseThrowGameTests.throwsWholeStackAndDeniesStaleRequests(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void armedGrenadeComponentPreservation(GameTestHelper helper) {
        BodyLooseThrowGameTests.armedGrenadeComponentSurvivesLooseThrow(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void jugThrowUnderLowCeiling(GameTestHelper helper) {
        BodyLooseThrowGameTests.jugThrowFitsUnderTwoBlockCeiling(helper);
    }
}
