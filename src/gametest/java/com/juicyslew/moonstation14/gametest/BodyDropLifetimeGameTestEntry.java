package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyDropLifetimeGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyDropLifetimeGameTestEntry {
    private BodyDropLifetimeGameTestEntry() { }

    @GameTest(template = "empty")
    public static void droppedHandItemSurvivesLifetimeAndRoundTrips(GameTestHelper helper) {
        BodyDropLifetimeGameTests.droppedHandItemSurvivesLifetimeAndRoundTrips(helper);
    }
}
