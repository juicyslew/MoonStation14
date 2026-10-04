package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyWorldWorldGridTransferGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "storage"})
public final class BodyWorldWorldGridTransferGameTestEntry {
    private BodyWorldWorldGridTransferGameTestEntry() { }
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void movesBetweenTwoWorldHosts(GameTestHelper helper) { BodyWorldWorldGridTransferGameTests.movesBetweenTwoWorldHosts(helper); }
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void staleDuplicateCollisionAndValidationDenialsDoNotWrite(GameTestHelper helper) { BodyWorldWorldGridTransferGameTests.staleDuplicateCollisionAndValidationDenialsDoNotWrite(helper); }
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void revalidationAfterSourcePublicationCompensates(GameTestHelper helper) { BodyWorldWorldGridTransferGameTests.revalidationAfterSourcePublicationCompensates(helper); }
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void inPlaceCallbackMutationsAreNotOverwritten(GameTestHelper helper) { BodyWorldWorldGridTransferGameTests.inPlaceCallbackMutationsAreNotOverwritten(helper); }
}
