package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyWorldGridTransferGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "storage"})
public final class BodyWorldGridTransferGameTestEntry {
    private BodyWorldGridTransferGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void storeRotateTakeAndDenyStaleRequests(GameTestHelper helper) {
        BodyWorldGridTransferGameTests.storeRotateTakeAndDenyStaleRequests(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void denialsLeaveBothOwnersUnchanged(GameTestHelper helper) {
        BodyWorldGridTransferGameTests.denialsLeaveBothOwnersUnchanged(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void storeCompensationKeepsOriginalRevisionStale(GameTestHelper helper) {
        BodyWorldGridTransferGameTests.storeCompensationKeepsOriginalRevisionStale(helper);
    }
}
