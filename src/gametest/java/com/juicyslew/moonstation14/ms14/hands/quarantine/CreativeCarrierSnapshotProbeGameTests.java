package com.juicyslew.moonstation14.ms14.hands.quarantine;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** A fake player can exercise denial, not genuine connected-player acceptance. */
public final class CreativeCarrierSnapshotProbeGameTests {
    private CreativeCarrierSnapshotProbeGameTests() { }

    public static void fakePlayerDeniedWithoutMutation(GameTestHelper helper) {
        var fake = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack original = fake.getInventory().items.get(0);
        int originalCount = original.getCount();
        int originalSelected = fake.getInventory().selected;
        var originalMenu = fake.containerMenu;
        var originalMode = fake.gameMode.getGameModeForPlayer();
        if (CreativeCarrierSnapshotProbe.capture(fake).isPresent()
                || fake.getInventory().items.get(0) != original || original.getCount() != originalCount
                || fake.getInventory().selected != originalSelected || fake.containerMenu != originalMenu
                || fake.gameMode.getGameModeForPlayer() != originalMode)
            throw new AssertionError("fake carrier accepted or mutated");
        helper.succeed();
    }
}
