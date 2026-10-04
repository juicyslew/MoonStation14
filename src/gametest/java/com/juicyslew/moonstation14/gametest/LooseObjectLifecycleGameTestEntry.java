package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"loose-objects", "lifecycle"})
public final class LooseObjectLifecycleGameTestEntry {
    private LooseObjectLifecycleGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    public static void looseItemLifecycle(GameTestHelper helper) {
        LooseObjectLifecycleGameTests.looseItemLifecycle(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "loose_objects_benchmark", timeoutTicks = 40)
    public static void looseObjectTickBenchmark(GameTestHelper helper) {
        LooseObjectLifecycleGameTests.looseObjectTickBenchmark(helper);
    }
}
