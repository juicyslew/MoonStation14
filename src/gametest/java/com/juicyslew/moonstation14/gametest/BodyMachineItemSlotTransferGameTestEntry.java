package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransferGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"machine-slots", "hands"})
public final class BodyMachineItemSlotTransferGameTestEntry {
    private BodyMachineItemSlotTransferGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void insertTakePreserveWholeStackAndReinsertAfterOrdinaryOwnershipRelease(GameTestHelper helper) {
        BodyMachineItemSlotTransferGameTests.insertTakePreserveWholeStackAndReinsertAfterOrdinaryOwnershipRelease(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void fullHandsFallBackToCanonicalWorldAndRetireLedgerClaim(GameTestHelper helper) {
        BodyMachineItemSlotTransferGameTests.fullHandsFallBackToCanonicalWorldAndRetireLedgerClaim(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void rejectedWorldSpawnRestoresExactMachineOwner(GameTestHelper helper) {
        BodyMachineItemSlotTransferGameTests.rejectedWorldSpawnRestoresExactMachineOwner(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void acceptedWorldSpawnRetiresClaimWhenContextInvalidates(GameTestHelper helper) {
        BodyMachineItemSlotTransferGameTests.acceptedWorldSpawnRetiresClaimWhenContextInvalidates(helper);
    }
}
