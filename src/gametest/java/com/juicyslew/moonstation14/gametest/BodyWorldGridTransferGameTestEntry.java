package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyWorldGridTransferGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyWorldGridTransferGameTestEntry {
    private BodyWorldGridTransferGameTestEntry() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void storeRotateTakeAndDenyStaleRequests(GameTestHelper helper) {
        BodyWorldGridTransferGameTests.storeRotateTakeAndDenyStaleRequests(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void denialsLeaveBothOwnersUnchanged(GameTestHelper helper) {
        BodyWorldGridTransferGameTests.denialsLeaveBothOwnersUnchanged(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void storeCompensationKeepsOriginalRevisionStale(GameTestHelper helper) {
        BodyWorldGridTransferGameTests.storeCompensationKeepsOriginalRevisionStale(helper);
    }
}
