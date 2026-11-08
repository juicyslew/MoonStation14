package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags("reactor-models")
@PrefixGameTestTemplate(false)
public final class RadiationCollectorAppearanceGameTests {
    private RadiationCollectorAppearanceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30)
    public static void liveCollectorPublishesAndClientConsumesMinimalAppearance(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof RadiationCollectorBlockEntity collector))
            throw new GameTestAssertException("collector block entity created");

        RadiationCollectorBlockEntity.serverTick(level, pos, level.getBlockState(pos), collector);
        var initialView = collector.visualSnapshot();
        if (!collector.toggleEnabled()) throw new GameTestAssertException("authoritative enabled toggle accepted");
        var serverView = collector.visualSnapshot();
        if (serverView.enabled() == initialView.enabled() || !serverView.transitioning())
            throw new GameTestAssertException("live visual projection publishes enabled target and active clip phase");
        if (!serverView.tankPresent()) throw new GameTestAssertException("fresh prototype initialization publishes starter tank presence");
        var updateTag = collector.getUpdateTag(level.registryAccess());
        if (updateTag.contains("tankSlot") || updateTag.contains("identity"))
            throw new GameTestAssertException("client update excludes private tank and owner identity");
        if (!updateTag.getString("VisualProfile").equals(serverView.profileId().toString()))
            throw new GameTestAssertException("resolved collector profile is part of appearance snapshot");

        var clientConsumer = new RadiationCollectorBlockEntity(pos, collector.getBlockState());
        clientConsumer.handleUpdateTag(updateTag, level.registryAccess());
        if (!clientConsumer.visualSnapshot().equals(serverView))
            throw new GameTestAssertException("native updateTag consumer receives the authoritative derived appearance");
        if (collector.getUpdatePacket() == null)
            throw new GameTestAssertException("native block entity update packet available for late join/chunk tracking");
        helper.succeed();
    }
}
