package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.Config;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereOwnershipTailRetryGameTests {
    private AtmosphereOwnershipTailRetryGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ownership_tail_retry", timeoutTicks = 240)
    public static void cancelledPartialClaimReclassifiesUncommittedTail(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService service = AtmosphereService.INSTANCE;
        boolean enabled = Config.ENABLE_ATMOSPHERICS.get();
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> vacuum =
                Config.parseAtmosphereVacuumDimensions(Config.ATMOSPHERE_VACUUM_DIMENSIONS.get());
        List<BlockPos> cells = new ArrayList<>();
        // Mutate an already-claimed seed, never an unclaimed tail cell: topologyChanged on an
        // unknown cell would independently enqueue a fresh search and mask retry-tail failures.
        BlockPos localChange = new BlockPos(1, 1, 1);
        BlockPos change = helper.absolutePos(localChange);
        try {
            service.configureAtServerStart(true, Set.of(level.dimension()));
            buildRoom(helper, cells);
            BlockPos seed = helper.absolutePos(new BlockPos(1, 1, 1));
            service.sample(level, seed); // enqueue ownership discovery

            int due = 0;
            int claimed;
            do {
                service.tick(level, (long) ++due * AtmosphereService.TICK_CADENCE);
                claimed = finiteCount(service, level, cells);
            } while (claimed < 128 && due < 20);
            require(claimed >= 128 && claimed < cells.size(),
                    "fixture must interrupt a partially committed large proof; claimed=" + claimed + "/" + cells.size());
            require(service.diagnosticCell(level, seed).status().equals("finite"),
                    "the topology edit must target an already-claimed seed");
            require(service.diagnosticCell(level, seed.above()).status().equals("finite")
                            && service.diagnosticCell(level, seed.east()).status().equals("finite"),
                    "nearby already-claimed cells must establish that retry cannot depend on an unknown edit seed");

            helper.setBlock(localChange, Blocks.STONE);
            service.topologyChanged(level, change);
            helper.setBlock(localChange, Blocks.AIR);
            service.topologyChanged(level, change);

            for (int i = 0; i < 100; i++) {
                service.tick(level, (long) ++due * AtmosphereService.TICK_CADENCE);
                if (finiteCount(service, level, cells) == cells.size()) break;
            }
            int finalFinite = finiteCount(service, level, cells);
            require(finalFinite == cells.size(), "fresh searches must eventually claim every safe tail cell; finite="
                    + finalFinite + "/" + cells.size() + ", timing=" + service.diagnosticTiming(level));
            helper.succeed();
        } finally {
            service.configureAtServerStart(enabled, vacuum);
        }
    }

    private static void buildRoom(GameTestHelper helper, List<BlockPos> cells) {
        for (int x = 0; x <= 11; x++) for (int z = 0; z <= 11; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 7, z), Blocks.STONE);
        }
        for (int y = 1; y <= 6; y++) {
            for (int z = 0; z <= 11; z++) {
                helper.setBlock(new BlockPos(0, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(11, y, z), Blocks.STONE);
            }
            for (int x = 0; x <= 11; x++) {
                helper.setBlock(new BlockPos(x, y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 11), Blocks.STONE);
            }
        }
        for (int x = 1; x <= 10; x++) for (int y = 1; y <= 6; y++) for (int z = 1; z <= 10; z++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            cells.add(helper.absolutePos(new BlockPos(x, y, z)));
        }
    }

    private static int finiteCount(AtmosphereService service, ServerLevel level, List<BlockPos> cells) {
        int count = 0;
        for (BlockPos pos : cells) if (service.diagnosticCell(level, pos).status().equals("finite")) count++;
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
