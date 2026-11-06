package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated selector entry for the experimental corner-cause native diagnostic. */
@PrefixGameTestTemplate(false)
public final class ShuttleCornerCauseDiagnosticGameTests {
    private ShuttleCornerCauseDiagnosticGameTests() { }

    @GameTestTags("shuttle-corner-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 1600)
    public static void cornerNeighborhoodNativeProofDiagnostic(GameTestHelper helper) {
        SableOwnedBodyCarryGameTests.cornerNeighborhoodNativeProofDiagnostic(helper);
    }
}
