package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentationGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "storage", "loose-objects"})
public final class ItemPresentationGameTestEntry {
    private ItemPresentationGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void registeredSnapshotCodecsPreservePresentation(GameTestHelper helper) {
        ItemPresentationGameTests.registeredSnapshotCodecsPreservePresentation(helper);
    }
}
