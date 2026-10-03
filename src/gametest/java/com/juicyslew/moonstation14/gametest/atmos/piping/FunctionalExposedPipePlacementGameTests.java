package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FunctionalExposedPipePlacementGameTests {
    private FunctionalExposedPipePlacementGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void crossingLaneAdmissionPreservesExistingGasAndIdentity(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(visual != null && visual.setLayersForPlacement(VisualPipeBlockEntity.SECONDARY), "secondary-only visual setup");

        UUID secondaryId = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.SECONDARY).orElseThrow();
        var data = PipeGasInventorySavedData.existing(level).orElseThrow();
        var secondaryKey = new PipeTopology.NodeKey(pos, PipeTopology.Layer.SECONDARY);
        var before = data.snapshot();
        var secondary = before.get(secondaryKey).orElseThrow();
        GasMixture seededGas = new GasMixture(Map.of(GasType.OXYGEN, 5.0), 5.0 * 20.8 * 300.0);
        require(data.replace(before.revision(), before,
                before.withMixture(secondaryKey, secondaryId, secondary.version(), seededGas)), "seed five moles oxygen at 300 K by CAS");
        var expectedSecondary = data.snapshot().get(secondaryKey).orElseThrow();

        require(visual.addFunctionalPlacementLayer(VisualPipeBlockEntity.PRIMARY), "add primary crossing lane");
        UUID primaryId = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        require(!primaryId.equals(secondaryId), "primary has independent identity");
        require(LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).isEmpty(), "duplicate primary owner rejected");
        assertGas(expectedSecondary, data.snapshot().get(secondaryKey).orElseThrow(), "primary admission preserves secondary gas");
        require(visual.functionalIdentity(VisualPipeBlockEntity.SECONDARY).orElseThrow().equals(secondaryId), "secondary identity unchanged");
        require(LoadedPipeGasOwner.activeSnapshot(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow().mixture().gasMoles().isEmpty(), "new primary is vacuum");

        require(visual.addFunctionalPlacementLayer(VisualPipeBlockEntity.TERTIARY), "add tertiary crossing lane");
        UUID tertiaryId = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.TERTIARY).orElseThrow();
        require(!tertiaryId.equals(primaryId) && !tertiaryId.equals(secondaryId), "tertiary identity independent");
        require(visual.getLayerMask() == (VisualPipeBlockEntity.PRIMARY | VisualPipeBlockEntity.SECONDARY | VisualPipeBlockEntity.TERTIARY), "all three layers present");
        require(LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.SECONDARY).isEmpty()
                && LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.TERTIARY).isEmpty(), "duplicate lane admissions rejected");
        assertGas(expectedSecondary, data.snapshot().get(secondaryKey).orElseThrow(), "later admissions preserve secondary gas");
        require(visual.functionalIdentity(VisualPipeBlockEntity.PRIMARY).orElseThrow().equals(primaryId)
                && visual.functionalIdentity(VisualPipeBlockEntity.TERTIARY).orElseThrow().equals(tertiaryId), "all identities retained");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cosmeticSameMaskAndSeededFreshLaneRollbackFailClosed(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(visual != null && visual.setLayersForPlacement(VisualPipeBlockEntity.TERTIARY), "tertiary cosmetic-only setup");
        require(!visual.addFunctionalPlacementLayer(VisualPipeBlockEntity.TERTIARY), "existing cosmetic mask cannot be upgraded through add method");
        require(visual.functionalIdentity(VisualPipeBlockEntity.TERTIARY).isEmpty(), "cosmetic lane remains inert");

        require(visual.addFunctionalPlacementLayer(VisualPipeBlockEntity.PRIMARY), "add fresh lane for rollback case");
        UUID id = LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        var data = PipeGasInventorySavedData.existing(level).orElseThrow();
        var key = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var before = data.snapshot();
        var admitted = before.get(key).orElseThrow();
        GasMixture gas = new GasMixture(Map.of(GasType.OXYGEN, 5.0), 5.0 * 20.8 * 300.0);
        require(data.replace(before.revision(), before, before.withMixture(key, id, admitted.version(), gas)), "seed rollback lane by CAS");
        var expected = data.snapshot().get(key).orElseThrow();
        int expectedMask = visual.getLayerMask();
        require(!LoadedPipeGasOwner.rollbackFreshVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY, id), "seeded owner cannot be rolled back as fresh vacuum");
        require(visual.getLayerMask() == expectedMask && visual.functionalIdentity(VisualPipeBlockEntity.PRIMARY).orElseThrow().equals(id), "failed rollback keeps mask and identity");
        assertGas(expected, data.snapshot().get(key).orElseThrow(), "failed rollback preserves exact gas");
        require(LoadedPipeGasOwner.activeSnapshot(level, pos, VisualPipeBlockEntity.PRIMARY).isPresent(), "seeded lane remains active");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void failedConsumptionRestorationRequiresConfirmedOriginal(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        var original = level.getBlockState(pos);
        var originalEntity = level.getBlockEntity(pos);
        require(original.isAir() && originalEntity == null, "destination starts as empty air");
        require(level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3), "place visual for rollback test");
        require(FunctionalExposedPipePlacement.restoreAfterFailedConsumption(level, pos, original, originalEntity,
                () -> false) == FunctionalExposedPipePlacement.Result.RECOVERY_REQUIRED,
                "failed setBlock cannot deny consumption with a pipe still present");
        require(FunctionalExposedPipePlacement.restoreAfterFailedConsumption(level, pos, original, originalEntity,
                () -> true) == FunctionalExposedPipePlacement.Result.RECOVERY_REQUIRED,
                "reported success without restored state cannot deny consumption");
        var visualState = level.getBlockState(pos);
        require(FunctionalExposedPipePlacement.restoreAfterFailedConsumption(level, pos, visualState, null,
                () -> true) == FunctionalExposedPipePlacement.Result.RECOVERY_REQUIRED,
                "reported success without restored block entity cannot deny consumption");
        require(FunctionalExposedPipePlacement.restoreAfterFailedConsumption(level, pos, original, originalEntity,
                () -> level.setBlock(pos, original, 3)) == FunctionalExposedPipePlacement.Result.DENIED,
                "confirmed exact restoration can deny consumption");
        require(level.getBlockState(pos) == original && level.getBlockEntity(pos) == originalEntity,
                "successful rollback leaves original state and entity");
        helper.succeed();
    }

    private static void assertGas(PipeGasInventory.Entry expected, PipeGasInventory.Entry actual, String message) {
        require(expected.incarnation().equals(actual.incarnation()) && expected.mixture().gasMoles().equals(actual.mixture().gasMoles())
                && Double.compare(expected.mixture().thermalEnergy(), actual.mixture().thermalEnergy()) == 0, message);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
