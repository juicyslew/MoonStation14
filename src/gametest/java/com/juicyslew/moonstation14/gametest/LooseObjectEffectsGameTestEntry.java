package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LooseObjectEffectsGameTestEntry {
    private LooseObjectEffectsGameTestEntry() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void jugEffects(GameTestHelper helper) { LooseObjectEffectsGameTests.jugEffects(helper); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void horizontalFloorContactBreaksAndSpills(GameTestHelper helper) { LooseObjectEffectsGameTests.horizontalFloorContactBreaksAndSpills(helper); }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void horizontalWallContactSpillsAtNearSideFloor(GameTestHelper helper) { LooseObjectEffectsGameTests.horizontalWallContactSpillsAtNearSideFloor(helper); }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fastAirborneJugDoesNotBreak(GameTestHelper helper) { LooseObjectEffectsGameTests.fastAirborneJugDoesNotBreak(helper); }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void grenadeFuseSurvivesLooseEntity(GameTestHelper helper) {
        LooseObjectEffectsGameTests.grenadeFuseSurvivesLooseEntity(helper);
    }
}
