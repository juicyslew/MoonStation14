package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyPouchWorldRoundTripGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "storage"})
public final class BodyPouchWorldRoundTripGameTestEntry {
    private BodyPouchWorldRoundTripGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void worldHandWorldHandConservesFilledPouch(GameTestHelper helper) {
        BodyPouchWorldRoundTripGameTests.worldHandWorldHandConservesFilledPouch(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void occupiedPickupAndCanceledDropRetainSingleOwner(GameTestHelper helper) {
        BodyPouchWorldRoundTripGameTests.occupiedPickupAndCanceledDropRetainSingleOwner(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void worldAdmissionRejectsHiddenOwnership(GameTestHelper helper) {
        BodyPouchWorldRoundTripGameTests.worldAdmissionRejectsHiddenOwnership(helper);
    }
}
