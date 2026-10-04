package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrowGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyLooseThrowGameTestEntry {
    private BodyLooseThrowGameTestEntry() { }

    @GameTest(template = "empty")
    public static void wholeStackThrowAndStaleDenial(GameTestHelper helper) {
        BodyLooseThrowGameTests.throwsWholeStackAndDeniesStaleRequests(helper);
    }

    @GameTest(template = "empty")
    public static void armedGrenadeComponentPreservation(GameTestHelper helper) {
        BodyLooseThrowGameTests.armedGrenadeComponentSurvivesLooseThrow(helper);
    }

    @GameTest(template = "empty")
    public static void jugThrowUnderLowCeiling(GameTestHelper helper) {
        BodyLooseThrowGameTests.jugThrowFitsUnderTwoBlockCeiling(helper);
    }
}
