package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.MachineSlotWorldOwnerGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"machine-slot-world-owner", "machine-slots"})
public final class MachineSlotWorldOwnerGameTestEntry {
    private MachineSlotWorldOwnerGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void canonicalReleaseFromNativeEntityOwner(GameTestHelper helper) {
        MachineSlotWorldOwnerGameTests.canonicalReleaseFromNativeEntityOwner(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void invalidOwnerAndRevisionsAreDenied(GameTestHelper helper) {
        MachineSlotWorldOwnerGameTests.invalidOwnerAndRevisionsAreDenied(helper);
    }
}
