package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.devicenet.control.WallSignalControlBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** End-to-end checks against resolved wall-control prototypes and the live per-level runtime. */
@GameTestTags("device-network")
@PrefixGameTestTemplate(false)
public final class DeviceWorkflowGameTests {
    private DeviceWorkflowGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=80)
    public static void placedButtonUsesResolvedPrototypeAndRegistersLiveEndpoint(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos.south(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.SIGNAL_BUTTON.get());
        helper.runAfterDelay(1, () -> {
            BlockPos absolutePos = helper.absolutePos(pos);
            if (!(helper.getLevel().getBlockEntity(absolutePos) instanceof WallSignalControlBlockEntity button))
                throw new GameTestAssertException("placed button creates its native endpoint block entity");
            var composition = button.composition();
            if (composition.sources().stream().noneMatch(port -> port.id().equals("Pressed")))
                throw new GameTestAssertException("button source port is inherited/resolved from its prototype");
            ServerLevel level = helper.getLevel();
            DeviceEndpoint registered = DeviceNetworkRuntime.forLevel(level).transport().lookup(button.identity());
            if (registered == null || !registered.identity().equals(button.identity())
                    || !registered.position().equals(absolutePos))
                throw new GameTestAssertException("native block-entity load registers the current endpoint identity and position");
            helper.succeed();
        });
    }

}
