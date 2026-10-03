package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyDropPlacementGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyDropPlacementGameTestEntry {
    private BodyDropPlacementGameTestEntry() { }

    @GameTest(template = "empty")
    public static void bodyDropSpawnsForwardWithNaturalGravity(GameTestHelper helper) {
        BodyDropPlacementGameTests.bodyDropSpawnsForwardWithNaturalGravity(helper);
    }

    @GameTest(template = "empty")
    public static void upwardSurfaceDropHasNoIntentionalVelocity(GameTestHelper helper) {
        BodyDropPlacementGameTests.upwardSurfaceDropHasNoIntentionalVelocity(helper);
    }

    @GameTest(template = "empty")
    public static void dropFitsUnderTwoBlockRoof(GameTestHelper helper) {
        BodyDropPlacementGameTests.dropFitsUnderTwoBlockRoof(helper);
    }

    @GameTest(template = "empty")
    public static void groundAimIsClampedWithoutLosingDrop(GameTestHelper helper) {
        BodyDropPlacementGameTests.groundAimIsClampedWithoutLosingDrop(helper);
    }

    @GameTest(template = "empty")
    public static void crowdingFallsBackWithoutDenyingPlacement(GameTestHelper helper) {
        BodyDropPlacementGameTests.crowdingFallsBackWithoutDenyingPlacement(helper);
    }

    @GameTest(template = "empty")
    public static void crowdedCarrierDropUsesFallback(GameTestHelper helper) {
        BodyDropPlacementGameTests.crowdedCarrierDropUsesFallback(helper);
    }

    @GameTest(template = "empty")
    public static void enclosedDropUsesLoadedBodyFallback(GameTestHelper helper) {
        BodyDropPlacementGameTests.enclosedDropUsesLoadedBodyFallback(helper);
    }
}
