package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyDropCompensationGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyDropCompensationGameTestEntry {
    private BodyDropCompensationGameTestEntry() { }

    @GameTest(template = "empty")
    public static void canceledSpawnRestoresOwnershipAndStalesRequest(GameTestHelper helper) {
        BodyDropCompensationGameTests.canceledSpawnRestoresOwnershipAndStalesRequest(helper);
    }

    @GameTest(template = "empty")
    public static void successfulSpawnAndInvalidHandDoNotDuplicate(GameTestHelper helper) {
        BodyDropCompensationGameTests.successfulSpawnAndInvalidHandDoNotDuplicate(helper);
    }

    @GameTest(template = "empty")
    public static void blockedForwardDropFallsBackToFeet(GameTestHelper helper) {
        BodyDropCompensationGameTests.blockedForwardDropFallsBackToFeet(helper);
    }
}
