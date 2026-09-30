package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.VanillaPlayerDataDiskProbeGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaPlayerDataDiskProbeGameTestEntry {
    private VanillaPlayerDataDiskProbeGameTestEntry() { }

    @GameTest(template = "empty")
    public static void absencePrimaryAndBackup(GameTestHelper helper) throws IOException {
        VanillaPlayerDataDiskProbeGameTests.absencePrimaryAndBackup(helper);
    }

    @GameTest(template = "empty")
    public static void unsafePathsAndSizeBounds(GameTestHelper helper) throws IOException {
        VanillaPlayerDataDiskProbeGameTests.unsafePathsAndSizeBounds(helper);
    }
}
