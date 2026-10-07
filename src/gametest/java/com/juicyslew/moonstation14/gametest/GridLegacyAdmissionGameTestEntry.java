package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.GridLegacyAdmissionGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "storage"})
public final class GridLegacyAdmissionGameTestEntry {
    private GridLegacyAdmissionGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void rejectsHiddenOwnershipWithoutConsumingWorldSource(GameTestHelper helper) {
        GridLegacyAdmissionGameTests.rejectsHiddenOwnershipWithoutConsumingWorldSource(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void equippedLegacyBeltRetainsOversizedPickaxeOnTake(GameTestHelper helper) {
        GridLegacyAdmissionGameTests.equippedLegacyBeltRetainsOversizedPickaxeOnTake(helper);
    }
}
