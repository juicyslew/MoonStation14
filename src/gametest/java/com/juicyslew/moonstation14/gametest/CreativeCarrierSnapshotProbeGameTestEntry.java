package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierSnapshotProbeGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "lifecycle"})
public final class CreativeCarrierSnapshotProbeGameTestEntry {
    private CreativeCarrierSnapshotProbeGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void fakePlayerDeniedWithoutMutation(GameTestHelper helper) {
        CreativeCarrierSnapshotProbeGameTests.fakePlayerDeniedWithoutMutation(helper);
    }
}
