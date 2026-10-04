package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestTags({"ventilation", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class LoadedSealedWallGasOwnerGameTests {
    private LoadedSealedWallGasOwnerGameTests() { }

@GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void sealedWallLaneOwnershipRequiresLiveRecordAndNeverReadmits(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        for (PipeTopology.Layer lane : new PipeTopology.Layer[]{PipeTopology.Layer.PRIMARY, PipeTopology.Layer.SECONDARY}) {
            LoadedSealedWallSegment.prepareOne(level, pos, Direction.EAST, lane, UUID.randomUUID()).orElseThrow();
            require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, pos, lane), "fresh record owns independent vacuum");
            require(LoadedSealedWallGasOwner.activeSnapshot(level, pos, lane).isPresent(), "matching owner is active");
            require(!LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, pos, lane), "existing owner is never readmitted");
        }
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var primaryKey = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var before = saved.snapshot();
        var primary = before.get(primaryKey).orElseThrow();
        var oxygen = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(
                java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 2.0), 120.0);
        require(saved.replace(before.revision(), before, before.withMixture(primaryKey, primary.incarnation(), primary.version(), oxygen)), "seed primary gas");
        var primaryId = primary.incarnation();
        var secondaryKey = new PipeTopology.NodeKey(pos, PipeTopology.Layer.SECONDARY);
        var secondaryId = saved.snapshot().get(secondaryKey).orElseThrow().incarnation();
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        require(LoadedSealedWallGasOwner.activeSnapshot(level, pos, PipeTopology.Layer.PRIMARY).isEmpty(), "replacement host fails closed");
        require(!LoadedSealedWallGasOwner.reconcileExisting(level, pos, PipeTopology.Layer.PRIMARY), "stale record cannot reconnect");
        var retained = saved.snapshot();
        var retainedPrimary = retained.get(primaryKey).orElseThrow();
        require(retainedPrimary.incarnation().equals(primaryId)
                && retainedPrimary.mixture().gasMoles().equals(oxygen.gasMoles())
                && Double.compare(retainedPrimary.mixture().thermalEnergy(), oxygen.thermalEnergy()) == 0,
                "primary owner and seeded gas survive invalidation");
        require(retained.get(secondaryKey).orElseThrow().incarnation().equals(secondaryId), "secondary owner survives invalidation");
        require(retained.get(new PipeTopology.NodeKey(pos, PipeTopology.Layer.TERTIARY)).isEmpty(), "no tertiary owner at clicked position");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
