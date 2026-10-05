package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.Config;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.explosion.server.ServerExplosionService;
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
@GameTestTags({"atmosphere", "explosion"})
public final class AtmosphereCraterOwnershipGameTests {
    private static final BlastPlanner.Proposal BLAST = new BlastPlanner.Proposal(120, 3, 10);

    private AtmosphereCraterOwnershipGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_crater_ownership", timeoutTicks = 160, templateNamespace = "moonstation14")
    public static void craterFloorChangesWakeUnclaimedFiniteCavity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService global = AtmosphereService.INSTANCE;
        boolean enabled = Config.ENABLE_ATMOSPHERICS.get();
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> vacuum =
                Config.parseAtmosphereVacuumDimensions(Config.ATMOSPHERE_VACUUM_DIMENSIONS.get());
        if (global.isEnabled() != enabled) throw new GameTestAssertException("global atmosphere config mismatch");
        buildChamber(helper, 1);
        buildChamber(helper, 10);
        BlockPos aInterior = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos bInterior = helper.absolutePos(new BlockPos(12, 2, 3));
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 3));
        List<BlockPos> intactFloorMask = new ArrayList<>();
        for (int x = 2; x <= 6; x++) for (int z = 2; z <= 6; z++)
            intactFloorMask.add(helper.absolutePos(new BlockPos(x, 0, z)));
        try {
            global.configureAtServerStart(true, Set.of(level.dimension()));
            int due = classify(global, level, aInterior, 0, 600);
            require(due >= 0, "blast fixture chamber classification timed out");
            due = classify(global, level, bInterior, due, 600);
            require(due >= 0, "creative control chamber classification timed out");
            seed(global, level, helper, 1);
            seed(global, level, helper, 10);

            var result = ServerExplosionService.execute(level, source, ModExplosions.DEFAULT_ID, BLAST);
            require(result.status() == ServerExplosionService.Status.APPLIED,
                    "crater blast applied: " + result);
            List<BlockPos> openedFloor = new ArrayList<>();
            for (BlockPos floor : intactFloorMask) {
                if (level.getBlockState(floor).isAir()) openedFloor.add(floor);
            }
            // Replay the exact changed floor mask as ordinary creative block writes in an otherwise
            // identical chamber. No blast heat, damage, or explosion callback reaches this control.
            List<BlockPos> control = new ArrayList<>();
            for (BlockPos floor : openedFloor) {
                int localX = floor.getX() - helper.absolutePos(new BlockPos(0, 0, 0)).getX();
                int localY = floor.getY() - helper.absolutePos(new BlockPos(0, 0, 0)).getY();
                int localZ = floor.getZ() - helper.absolutePos(new BlockPos(0, 0, 0)).getZ();
                BlockPos mirroredLocal = new BlockPos(localX + 9, localY, localZ);
                BlockPos mirrored = helper.absolutePos(mirroredLocal);
                helper.setBlock(mirroredLocal, Blocks.AIR);
                global.topologyChanged(level, mirrored);
                control.add(mirrored);
            }
            require(!openedFloor.isEmpty(), "blast must expose at least one loaded floor voxel for crater fixture");
            for (BlockPos floor : openedFloor)
                require(level.getBlockState(floor.below()).is(Blocks.BEDROCK),
                        "blast must remain contained above bedrock at " + floor);

            for (int i = 1; i <= 600; i++) global.tick(level, due + i);
            List<String> statuses = new ArrayList<>();
            for (int i = 0; i < openedFloor.size(); i++) {
                String blastStatus = global.diagnosticCell(level, openedFloor.get(i)).status();
                String creativeStatus = global.diagnosticCell(level, control.get(i)).status();
                statuses.add(blastStatus + "/" + creativeStatus);
                require(blastStatus.equals("finite") && creativeStatus.equals("finite"),
                        "opened sealed-foundation voxels must eventually become finite, not exterior; statuses="
                                + statuses + ", timing=" + global.diagnosticTiming(level));
            }
            helper.succeed();
        } finally {
            global.configureAtServerStart(enabled, vacuum);
        }
    }

    private static void buildChamber(GameTestHelper helper, int x0) {
        // A roofed chamber above a bedrock-backed hollow pocket: floor cells become loaded,
        // passable and connected to a sealed finite volume, but have no sky path.
        for (int x = x0; x <= x0 + 6; x++) for (int z = 1; z <= 7; z++) {
            helper.setBlock(new BlockPos(x, -1, z), Blocks.BEDROCK);
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 4, z), Blocks.BEDROCK);
        }
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 7; z++) {
                helper.setBlock(new BlockPos(x0, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x0 + 6, y, z), Blocks.STONE);
            }
            for (int x = x0; x <= x0 + 6; x++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
            }
        }
        for (int x = x0 + 1; x < x0 + 6; x++) for (int z = 2; z < 7; z++)
            for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    private static int classify(AtmosphereService service, ServerLevel level, BlockPos pos, int due, int maxAdditionalDue) {
        service.activateCellAndNeighbors(level, pos);
        for (int i = 0; i <= maxAdditionalDue; i++) {
            if (service.diagnosticCell(level, pos).status().equals("finite")) return due;
            if (i < maxAdditionalDue) service.tick(level, ++due);
        }
        return service.diagnosticCell(level, pos).status().equals("finite") ? due : -1;
    }

    private static void seed(AtmosphereService service, ServerLevel level, GameTestHelper helper, int x0) {
        for (int x = x0 + 1; x < x0 + 6; x++) for (int z = 2; z < 7; z++)
            for (int y = 1; y <= 3; y++) require(service.addGas(level,
                    helper.absolutePos(new BlockPos(x, y, z)), GasType.NITROGEN, 3.0, 293.15), "fresh chamber seed rejected");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
