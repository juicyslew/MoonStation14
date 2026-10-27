package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGrenadeHeldFuseGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "explosion"})
public final class BodyGrenadeHeldFuseGameTestEntry {
    private BodyGrenadeHeldFuseGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining", "merge-main-stage-two"})
    public static void heldFuseTicksAndExpiresOnce(GameTestHelper helper) {
        BodyGrenadeHeldFuseGameTests.heldFuseTicksAndExpiresOnce(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 10)
    public static void invalidAuthorityDoesNotPublishHeldFuse(GameTestHelper helper) {
        BodyGrenadeHeldFuseGameTests.invalidAuthorityDoesNotPublishHeldFuse(helper);
    }
}
