package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyWorldWorldGridTransferGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyWorldWorldGridTransferGameTestEntry {
    private BodyWorldWorldGridTransferGameTestEntry() { }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void movesBetweenTwoWorldHosts(GameTestHelper helper) { BodyWorldWorldGridTransferGameTests.movesBetweenTwoWorldHosts(helper); }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void staleDuplicateCollisionAndValidationDenialsDoNotWrite(GameTestHelper helper) { BodyWorldWorldGridTransferGameTests.staleDuplicateCollisionAndValidationDenialsDoNotWrite(helper); }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void revalidationAfterSourcePublicationCompensates(GameTestHelper helper) { BodyWorldWorldGridTransferGameTests.revalidationAfterSourcePublicationCompensates(helper); }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inPlaceCallbackMutationsAreNotOverwritten(GameTestHelper helper) { BodyWorldWorldGridTransferGameTests.inPlaceCallbackMutationsAreNotOverwritten(helper); }
}
