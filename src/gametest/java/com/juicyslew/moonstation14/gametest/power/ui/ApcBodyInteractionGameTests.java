package com.juicyslew.moonstation14.gametest.power.ui;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"power", "interaction"})
@PrefixGameTestTemplate(false)
public final class ApcBodyInteractionGameTests {
    private ApcBodyInteractionGameTests() { }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear",
            batch = "apc_body_breaker", timeoutTicks = 40)
    public static void authorizedCoreTogglesOnlyWithinNativeRange(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(pos, ModBlocks.APC.get().defaultBlockState(), 3);
        PowerDeviceBlockEntity device = (PowerDeviceBlockEntity) level.getBlockEntity(pos);
        device.setEnergyJoules(0);
        long revision = device.breakerRevision();

        require(device.toggleBreakerAuthorized(true, 4), "authorized in-range body interaction must open breaker");
        require(!device.breakerClosed() && device.breakerRevision() == ++revision,
                "opening changes breaker state and revision once");
        require(device.energyJoules() == 0, "breaker interaction must not fabricate charge");

        require(!device.toggleBreakerAuthorized(false, 4), "unauthorized interaction must be rejected");
        require(!device.toggleBreakerAuthorized(true, 100), "out-of-range body interaction must be rejected");
        require(!device.breakerClosed() && device.breakerRevision() == revision,
                "rejected interactions leave breaker and revision unchanged");

        require(device.toggleBreakerAuthorized(true, 4), "second authorized interaction must reclose breaker");
        require(device.breakerClosed() && device.breakerRevision() == ++revision,
                "closing changes breaker state and revision once");
        require(device.energyJoules() == 0, "breaker closing must not fabricate charge");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
