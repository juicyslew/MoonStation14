package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.BodyDropPlacementGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
@GameTestTags({"hands"})
public final class BodyDropPlacementGameTestEntry {
    private BodyDropPlacementGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void bodyDropSpawnsForwardWithNaturalGravity(GameTestHelper helper) {
        BodyDropPlacementGameTests.bodyDropSpawnsForwardWithNaturalGravity(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void containedRefusalDropsAtOwnerPosition(GameTestHelper helper) {
        BodyDropPlacementGameTests.containedRefusalDropsAtOwnerPosition(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", batch = "disposals", timeoutTicks = 30)
    public static void containedHandDropMovesWholeStackIntoTransitHolder(GameTestHelper helper) {
        BodyDropPlacementGameTests.containedHandDropMovesWholeStackIntoTransitHolder(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void upwardSurfaceDropHasNoIntentionalVelocity(GameTestHelper helper) {
        BodyDropPlacementGameTests.upwardSurfaceDropHasNoIntentionalVelocity(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void dropFitsUnderTwoBlockRoof(GameTestHelper helper) {
        BodyDropPlacementGameTests.dropFitsUnderTwoBlockRoof(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void groundAimIsClampedWithoutLosingDrop(GameTestHelper helper) {
        BodyDropPlacementGameTests.groundAimIsClampedWithoutLosingDrop(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void crowdingFallsBackWithoutDenyingPlacement(GameTestHelper helper) {
        BodyDropPlacementGameTests.crowdingFallsBackWithoutDenyingPlacement(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void crowdedCarrierDropUsesFallback(GameTestHelper helper) {
        BodyDropPlacementGameTests.crowdedCarrierDropUsesFallback(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void enclosedDropUsesLoadedBodyFallback(GameTestHelper helper) {
        BodyDropPlacementGameTests.enclosedDropUsesLoadedBodyFallback(helper);
    }
}
