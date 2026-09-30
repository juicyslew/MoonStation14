package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyPouchWorldRoundTripGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyPouchWorldRoundTripGameTestEntry {
    private BodyPouchWorldRoundTripGameTestEntry() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void worldHandWorldHandConservesFilledPouch(GameTestHelper helper) {
        BodyPouchWorldRoundTripGameTests.worldHandWorldHandConservesFilledPouch(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void occupiedPickupAndCanceledDropRetainSingleOwner(GameTestHelper helper) {
        BodyPouchWorldRoundTripGameTests.occupiedPickupAndCanceledDropRetainSingleOwner(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void worldAdmissionRejectsHiddenOwnership(GameTestHelper helper) {
        BodyPouchWorldRoundTripGameTests.worldAdmissionRejectsHiddenOwnership(helper);
    }
}
