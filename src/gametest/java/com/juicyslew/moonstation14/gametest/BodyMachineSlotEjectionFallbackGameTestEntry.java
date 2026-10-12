package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.BodyMachineSlotEjectionFallbackGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"machine-slots", "chemmaster", "hands"})
public final class BodyMachineSlotEjectionFallbackGameTestEntry {
    private BodyMachineSlotEjectionFallbackGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void activeOccupiedFallsBackToOtherEligibleHand(GameTestHelper helper) {
        BodyMachineSlotEjectionFallbackGameTests.activeOccupiedFallsBackToOtherEligibleHand(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void bothHandsFullUseCanonicalWorldRelease(GameTestHelper helper) {
        BodyMachineSlotEjectionFallbackGameTests.bothHandsFullUseCanonicalWorldRelease(helper);
    }
}
