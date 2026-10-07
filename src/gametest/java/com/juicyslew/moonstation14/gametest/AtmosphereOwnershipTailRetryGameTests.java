package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere"})
public final class AtmosphereOwnershipTailRetryGameTests {
    private AtmosphereOwnershipTailRetryGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_ownership_tail_retry", timeoutTicks = 240, templateNamespace = "moonstation14")
    public static void topologyEditAfterCompletedProofReclassifiesRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService service = AtmosphereService.INSTANCE;
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> vacuum = service.vacuumDimensionsForTest();
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> spaces = service.spaceDimensionsForTest();
        List<BlockPos> cells = new ArrayList<>();
        // Mutate an already-claimed seed, never an unclaimed tail cell: topologyChanged on an
        // unknown cell would independently enqueue a fresh search and mask retry-tail failures.
        BlockPos localChange = new BlockPos(1, 1, 1);
        BlockPos change = helper.absolutePos(localChange);
        try {
            service.configureAtServerStart(Set.of(level.dimension()), spaces);
            buildRoom(helper, cells);
            BlockPos seed = helper.absolutePos(new BlockPos(1, 1, 1));
            service.activateCellAndNeighbors(level, seed); // Activate a loaded seed; ownership remains strictly UNKNOWN until proved.

            int due = 0;
            int claimed;
            do {
                service.tick(level, ++due);
                claimed = finiteCount(service, level, cells);
            } while (claimed < cells.size() && due < 480);
            require(claimed == cells.size(),
                    "bounded ownership work must eventually complete the room proof; claimed="
                            + claimed + "/" + cells.size() + ", callbacks=" + due
                            + ", timing=" + service.diagnosticTiming(level));
            require(service.diagnosticCell(level, seed).status().equals("finite"),
                    "the topology edit must target an already-claimed seed");
            require(service.diagnosticCell(level, seed.above()).status().equals("finite")
                            && service.diagnosticCell(level, seed.east()).status().equals("finite"),
                    "nearby already-claimed cells must establish that retry cannot depend on an unknown edit seed");

            helper.setBlock(localChange, Blocks.STONE);
            service.topologyChanged(level, change);
            helper.setBlock(localChange, Blocks.AIR);
            service.topologyChanged(level, change);

            for (int i = 0; i < 480; i++) {
                service.tick(level, ++due);
                if (finiteCount(service, level, cells) == cells.size()) break;
            }
            int finalFinite = finiteCount(service, level, cells);
            require(finalFinite == cells.size(), "fresh searches must eventually claim every safe tail cell; finite="
                    + finalFinite + "/" + cells.size() + ", timing=" + service.diagnosticTiming(level));
            helper.succeed();
        } finally {
            service.configureAtServerStart(vacuum, spaces);
        }
    }

    private static void buildRoom(GameTestHelper helper, List<BlockPos> cells) {
        for (int x = 0; x <= 11; x++) for (int z = 0; z <= 11; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 51, z), Blocks.STONE);
        }
        for (int y = 1; y <= 50; y++) {
            for (int z = 0; z <= 11; z++) {
                helper.setBlock(new BlockPos(0, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(11, y, z), Blocks.STONE);
            }
            for (int x = 0; x <= 11; x++) {
                helper.setBlock(new BlockPos(x, y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 11), Blocks.STONE);
            }
        }
        for (int x = 1; x <= 10; x++) for (int y = 1; y <= 50; y++) for (int z = 1; z <= 10; z++) {
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
