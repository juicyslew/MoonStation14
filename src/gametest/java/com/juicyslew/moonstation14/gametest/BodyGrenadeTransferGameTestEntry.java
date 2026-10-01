package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGrenadeTransferGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyGrenadeTransferGameTestEntry {
    private BodyGrenadeTransferGameTestEntry() { }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void transfersOnlyOwnedArmedGrenade(GameTestHelper helper) {
        BodyGrenadeTransferGameTests.transfersOnlyOwnedArmedGrenade(helper);
    }

    @GameTest(template = "empty")
    public static void canceledProjectileSpawnRestoresExactOwnership(GameTestHelper helper) {
        BodyGrenadeTransferGameTests.canceledProjectileSpawnRestoresExactOwnership(helper);
    }
}
