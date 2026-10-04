package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"loose-objects", "explosion"})
public final class LooseObjectEffectsGameTestEntry {
    private LooseObjectEffectsGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void jugEffects(GameTestHelper helper) { LooseObjectEffectsGameTests.jugEffects(helper); }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void horizontalFloorContactBreaksAndSpills(GameTestHelper helper) { LooseObjectEffectsGameTests.horizontalFloorContactBreaksAndSpills(helper); }
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void horizontalWallContactSpillsAtNearSideFloor(GameTestHelper helper) { LooseObjectEffectsGameTests.horizontalWallContactSpillsAtNearSideFloor(helper); }
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void fastAirborneJugDoesNotBreak(GameTestHelper helper) { LooseObjectEffectsGameTests.fastAirborneJugDoesNotBreak(helper); }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 60)
    public static void grenadeFuseSurvivesLooseEntity(GameTestHelper helper) {
        LooseObjectEffectsGameTests.grenadeFuseSurvivesLooseEntity(helper);
    }
}
