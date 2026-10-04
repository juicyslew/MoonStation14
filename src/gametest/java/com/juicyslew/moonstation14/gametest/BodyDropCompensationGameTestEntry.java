package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.BodyDropCompensationGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands"})
public final class BodyDropCompensationGameTestEntry {
    private BodyDropCompensationGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void canceledSpawnRestoresOwnershipAndStalesRequest(GameTestHelper helper) {
        BodyDropCompensationGameTests.canceledSpawnRestoresOwnershipAndStalesRequest(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void successfulSpawnAndInvalidHandDoNotDuplicate(GameTestHelper helper) {
        BodyDropCompensationGameTests.successfulSpawnAndInvalidHandDoNotDuplicate(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void blockedForwardDropStaysNearSide(GameTestHelper helper) {
        BodyDropCompensationGameTests.blockedForwardDropStaysNearSide(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void blockedForwardAndUnsafeFeetFallbackPreservesConservation(GameTestHelper helper) {
        BodyDropCompensationGameTests.blockedForwardAndUnsafeFeetFallbackPreservesConservation(helper);
    }
}
