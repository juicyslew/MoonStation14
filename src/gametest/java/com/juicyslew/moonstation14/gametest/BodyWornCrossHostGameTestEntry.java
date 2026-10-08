package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.BodyWornCrossHostGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "storage", "equipment-regression"})
public final class BodyWornCrossHostGameTestEntry {
    private BodyWornCrossHostGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void moveBetweenWornHostsConservesChildrenAndRejectsStaleAndCollision(GameTestHelper helper) {
        BodyWornCrossHostGameTests.moveBetweenWornHostsConservesChildrenAndRejectsStaleAndCollision(helper);
    }
}
