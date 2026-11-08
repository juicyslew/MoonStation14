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

@GameTestTags({"ventilation", "atmosphere", "sealed-wall-gas-retirement"})
@PrefixGameTestTemplate(false)
public final class LoadedSealedWallGasOwnerGameTests {
    private LoadedSealedWallGasOwnerGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    @GameTestTags({"ventilation", "atmosphere", "sealed-wall-gas-retirement"})
    public static void hostReplacementWithoutLaneGasOwnerIsHarmless(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        LoadedSealedWallSegment.prepareOne(level, pos, Direction.EAST, PipeTopology.Layer.PRIMARY, UUID.randomUUID()).orElseThrow();
        var savedBefore = PipeGasInventorySavedData.existing(level).orElse(null);
        var inventoryBefore = savedBefore == null ? null : savedBefore.snapshot();
        var revisionBefore = inventoryBefore == null ? -1 : inventoryBefore.revision();
        require(inventoryBefore == null || inventoryBefore.get(new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY)).isEmpty(),
                "fixture lane has no gas owner");
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        var savedAfter = PipeGasInventorySavedData.existing(level).orElse(null);
        require(savedAfter == savedBefore, "host invalidation does not create gas inventory");
        if (savedBefore != null) {
            var inventoryAfter = savedAfter.snapshot();
            require(inventoryAfter.revision() == revisionBefore && inventoryAfter.snapshot().equals(inventoryBefore.snapshot())
                    && inventoryAfter.retiredSnapshot().equals(inventoryBefore.retiredSnapshot()),
                    "lane without an owner leaves existing unrelated inventory unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    @GameTestTags({"ventilation", "atmosphere", "sealed-wall-gas-retirement"})
    public static void hostReplacementPreservesMismatchedForeignOwner(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        var record = LoadedSealedWallSegment.prepareOne(level, pos, Direction.EAST,
                PipeTopology.Layer.PRIMARY, UUID.randomUUID()).orElseThrow();
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, pos, PipeTopology.Layer.PRIMARY), "fixture owner admitted");
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var key = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var initial = saved.snapshot();
        var idA = initial.get(key).orElseThrow().incarnation();
        var removedA = initial.retire(key, idA);
        require(saved.replace(initial.revision(), initial, removedA), "remove exact fixture-owned admission before staging mismatch");
        var foreignId = UUID.randomUUID();
        var beforeForeign = saved.snapshot();
        var staged = beforeForeign.admit(key, foreignId, record.volume());
        require(saved.replace(beforeForeign.revision(), beforeForeign, staged), "stage mismatched foreign owner");
        var foreignBefore = saved.snapshot().get(key).orElseThrow();

        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        var after = saved.snapshot();
        var foreignAfter = after.get(key).orElseThrow();
        require(foreignAfter.equals(foreignBefore), "mismatched foreign owner remains byte-for-byte unchanged");
        require(!after.retiredSnapshot().containsKey(foreignId), "foreign owner is not archived or retired");
        require(LoadedSealedWallSegment.matchingLive(level, pos, PipeTopology.Layer.PRIMARY).isEmpty(), "host record is quarantined after real block write");

        var cleanupBefore = saved.snapshot();
        var cleanupAfter = cleanupBefore.retire(key, foreignId);
        require(saved.replace(cleanupBefore.revision(), cleanupBefore, cleanupAfter), "clean up fixture-staged foreign owner exactly");
        helper.succeed();
    }

@GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void sealedWallLaneOwnershipRequiresLiveRecordAndNeverReadmits(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos unrelated = helper.absolutePos(new BlockPos(5, 1, 3));
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(unrelated, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        for (PipeTopology.Layer lane : PipeTopology.Layer.values()) {
            LoadedSealedWallSegment.prepareOne(level, pos, Direction.EAST, lane, UUID.randomUUID()).orElseThrow();
            require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, pos, lane), "fresh record owns independent vacuum");
            require(LoadedSealedWallGasOwner.activeSnapshot(level, pos, lane).isPresent(), "matching owner is active");
            require(!LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, pos, lane), "existing owner is never readmitted");
        }
        LoadedSealedWallSegment.prepareOne(level, unrelated, Direction.EAST, PipeTopology.Layer.PRIMARY, UUID.randomUUID()).orElseThrow();
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, unrelated, PipeTopology.Layer.PRIMARY), "unrelated host owner admitted");
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var primaryKey = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var before = saved.snapshot();
        var primary = before.get(primaryKey).orElseThrow();
        var oxygen = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(
                java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 2.0), 120.0,
                before.mixture(primaryKey).volumeCubicMeters());
        require(saved.replace(before.revision(), before, before.withMixture(primaryKey, primary.incarnation(), primary.version(), oxygen)), "seed primary gas");
        var primaryId = primary.incarnation();
        var secondaryKey = new PipeTopology.NodeKey(pos, PipeTopology.Layer.SECONDARY);
        var beforeSecondary = saved.snapshot();
        var secondary = beforeSecondary.get(secondaryKey).orElseThrow();
        var secondaryGas = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(
                java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN, 3.0), 240.0,
                beforeSecondary.mixture(secondaryKey).volumeCubicMeters());
        require(saved.replace(beforeSecondary.revision(), beforeSecondary,
                beforeSecondary.withMixture(secondaryKey, secondary.incarnation(), secondary.version(), secondaryGas)), "seed secondary gas");
        var secondaryId = secondary.incarnation();
        require(LoadedSealedWallGasOwner.deactivate(level, pos, PipeTopology.Layer.SECONDARY), "secondary owner becomes dormant");
        var tertiaryKey = new PipeTopology.NodeKey(pos, PipeTopology.Layer.TERTIARY);
        var tertiaryId = saved.snapshot().get(tertiaryKey).orElseThrow().incarnation();
        var unrelatedKey = new PipeTopology.NodeKey(unrelated, PipeTopology.Layer.PRIMARY);
        var unrelatedId = saved.snapshot().get(unrelatedKey).orElseThrow().incarnation();
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        require(LoadedSealedWallGasOwner.activeSnapshot(level, pos, PipeTopology.Layer.PRIMARY).isEmpty(), "replacement host fails closed");
        require(!LoadedSealedWallGasOwner.reconcileExisting(level, pos, PipeTopology.Layer.PRIMARY), "stale record cannot reconnect");
        var retained = saved.snapshot();
        require(retained.get(primaryKey).isEmpty() && retained.get(secondaryKey).isEmpty()
                && retained.get(tertiaryKey).isEmpty(), "host replacement retires all three active lanes");
        var archived = retained.retiredSnapshot().get(primaryId);
        require(archived != null, "finite owner archived");
        require(archived.mixture().gasMoles().equals(oxygen.gasMoles())
                && Double.compare(archived.mixture().thermalEnergy(), oxygen.thermalEnergy()) == 0,
                "finite gas is conserved in archive");
        var archivedSecondary = retained.retiredSnapshot().get(secondaryId);
        require(archivedSecondary != null && archivedSecondary.mixture().gasMoles().equals(secondaryGas.gasMoles())
                && Double.compare(archivedSecondary.mixture().thermalEnergy(), secondaryGas.thermalEnergy()) == 0,
                "dormant exact owner gas is archived on host replacement");
        require(!retained.retiredSnapshot().containsKey(tertiaryId), "vacuum owner retires without archive entry");
        require(retained.get(unrelatedKey).map(entry -> entry.incarnation().equals(unrelatedId) && entry.active()).orElse(false),
                "unrelated host owner remains active");
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        require(saved.snapshot().retiredSnapshot().values().stream().filter(entry -> entry.incarnation().equals(primaryId)).count() == 1,
                "repeated replacement does not duplicate archived gas");
        require(saved.snapshot().get(unrelatedKey).map(entry -> entry.incarnation().equals(unrelatedId) && entry.active()).orElse(false),
                "repeated callback leaves unrelated host untouched");
        require(retained.get(new PipeTopology.NodeKey(pos, PipeTopology.Layer.TERTIARY)).isEmpty(), "no tertiary owner at clicked position");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
