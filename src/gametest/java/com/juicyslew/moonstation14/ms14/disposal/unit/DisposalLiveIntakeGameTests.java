package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Regressions for finite-cell live disposal intake, separate from trusted closed-room simulation. */
@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class DisposalLiveIntakeGameTests {
    private DisposalLiveIntakeGameTests() { }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals", timeoutTicks = 100)
    public static void liveIntakeWorksWithoutWholeComponentProof(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var atmos = AtmosphereService.INSTANCE;
        boolean wasEnabled = atmos.isEnabled();
        var oldVacuum = atmos.vacuumDimensionsForTest();
        var dimensions = new java.util.HashSet<>(oldVacuum);
        dimensions.add(level.dimension());
        atmos.configureAtServerStart(true, dimensions);
        try {
            // Author a small sealed finite room and let the ordinary ownership pass establish it.
            for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                for (int y = 1; y <= 3; y++) if (x == 1 || x == 5 || z == 1 || z == 5)
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
            BlockPos unitPos = helper.absolutePos(new BlockPos(3, 1, 3));
            helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR); // intake cell is the room above unit
            level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
            var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
            require(unit != null && unit.prototype() != null && unit.reservoirAir() != null, "resolved unit and owned reservoir");
            long synthetic = Math.floorDiv(level.getGameTime(), (long) atmos.TICK_CADENCE) * atmos.TICK_CADENCE;
            for (int i = 0; i < 12 && atmos.finiteRoomEndpointForTrustedSimulation(level, unitPos.above()).isEmpty(); i++) {
                atmos.sample(level, unitPos.above());
                atmos.tick(level, synthetic += atmos.TICK_CADENCE);
            }
            require(atmos.finiteRoomEndpointForTrustedSimulation(level, unitPos.above()).isPresent(), "sealed finite source claimed");
            require(atmos.addGas(level, unitPos.above(), GasType.OXYGEN, 8, 300), "authored finite oxygen");
            require(atmos.addGas(level, unitPos.above(), GasType.NITROGEN, 24, 300), "authored finite nitrogen");

            // Make a neighboring cell passable after ownership; this invalidates whole-room proof
            // without a tick that would revoke the source's already-established local finite claim.
            helper.setBlock(new BlockPos(1, 2, 3), Blocks.AIR);
            require(atmos.finiteRoomEndpointForTrustedSimulation(level, unitPos.above()).isEmpty(), "strict whole-component endpoint rejects opened room");
            var liveEndpoint = atmos.finiteCellEndpointForImmediateTransfer(level, unitPos.above());
            require(liveEndpoint.isPresent(), "same-tick local finite endpoint remains usable");
            require(atmos.sample(level, unitPos.above()).orElseThrow().moles(GasType.OXYGEN) > 0, "live source has oxygen");

            GasMixture roomBefore = atmos.sample(level, unitPos.above()).orElseThrow();
            GasMixture inventoryBefore = unit.reservoirAir().sample().orElseThrow().mixture();
            GasMixture flushed = DisposalUnitFlushGas.prepare(level, unit);
            GasMixture roomAfter = atmos.sample(level, unitPos.above()).orElseThrow();
            GasMixture inventoryAfter = unit.reservoirAir().sample().orElseThrow().mixture();
            require(flushed.moles(GasType.OXYGEN) > 0 && inventoryAfter.moles(GasType.OXYGEN) > 0,
                    "actual prepare consumer transfers source oxygen");
            assertConserved(roomBefore, inventoryBefore, roomAfter, inventoryAfter);

            BlockPos entry = unitPos.east();
            require(com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData.forMutation(level)
                    .installExposed(level, new com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord(
                            java.util.UUID.randomUUID(), entry, "disposal_trunk",
                            new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(
                                    net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP),
                            true, "", "", 0,
                            com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord.HostKind.EXPOSED,
                            "", null)), "real exposed trunk installed for holder admission");
            var holder = DisposalTransitHolder.spawnFlushed(level, entry, flushed)
                    .orElseThrow(() -> new GameTestAssertException("actual 70L holder spawned"));
            var holderSample = holder.finiteAir().orElseThrow().sample().orElseThrow();
            require(close(holderSample.volumeCubicMeters(), .07, 1e-9), "holder uses resolved 70L volume");
            require(close(holderSample.pressureKpa(), 90, 4),
                    "resolved holder intake reaches healthy pressure near 90 kPa");
            helper.succeed();
        } finally { atmos.configureAtServerStart(wasEnabled, oldVacuum); }
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals", timeoutTicks = 100)
    public static void liveIntakeVacuumUnavailableAndStaleEndpointAreSafe(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var atmos = AtmosphereService.INSTANCE;
        boolean wasEnabled = atmos.isEnabled();
        var oldVacuum = atmos.vacuumDimensionsForTest();
        var dimensions = new java.util.HashSet<>(oldVacuum);
        dimensions.add(level.dimension());
        atmos.configureAtServerStart(true, dimensions);
        boolean callbackScheduled = false;
        try {
            for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                for (int y = 1; y <= 3; y++) if (x == 1 || x == 5 || z == 1 || z == 5)
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
            BlockPos unitPos = helper.absolutePos(new BlockPos(3, 1, 3));
            level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
            var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
            require(unit != null && unit.reservoirAir() != null, "real unit inventory exists");
            var inventory = unit.reservoirAir().sample().orElseThrow().mixture();
            GasMixture unchanged = DisposalUnitFlushGas.prepare(level, unit);
            require(same(inventory, unchanged), "unavailable/unclaimed source preserves exact inventory");

            long synthetic = Math.floorDiv(level.getGameTime(), (long) atmos.TICK_CADENCE) * atmos.TICK_CADENCE;
            for (int i = 0; i < 12 && atmos.finiteRoomEndpointForTrustedSimulation(level, unitPos.above()).isEmpty(); i++) {
                atmos.sample(level, unitPos.above());
                atmos.tick(level, synthetic += atmos.TICK_CADENCE);
            }
            require(atmos.initializeFiniteMixtureForTrustedSetup(level, unitPos.above(),
                    new GasMixture(Map.of(), 293.15)), "trusted authored finite vacuum");
            var vacuum = atmos.sample(level, unitPos.above()).orElseThrow();
            require(vacuum.totalMoles() == 0, "finite source is vacuum");
            GasMixture afterVacuumPrepare = DisposalUnitFlushGas.prepare(level, unit);
            require(afterVacuumPrepare.totalMoles() == 0, "vacuum intake does not create oxygen");

            var endpoint = atmos.finiteCellEndpointForImmediateTransfer(level, unitPos.above()).orElseThrow();
            GasMixture sourceBefore = atmos.sample(level, unitPos.above()).orElseThrow();
            callbackScheduled = true;
            helper.runAfterDelay(1, () -> {
                try {
                    require(atmos.isEnabled(), "atmosphere remains enabled through delayed game tick");
                    require(!endpoint.eligible(), "endpoint from previous game tick is ineligible");
                    require(endpoint.snapshot() == null, "stale endpoint has no valid snapshot");
                    GasMixture sourceAfter = atmos.sample(level, unitPos.above()).orElseThrow();
                    require(same(sourceBefore, sourceAfter), "stale endpoint cannot mutate physical source gas");
                    helper.succeed();
                } finally { atmos.configureAtServerStart(wasEnabled, oldVacuum); }
            });
        } finally {
            if (!callbackScheduled) atmos.configureAtServerStart(wasEnabled, oldVacuum);
        }
    }

    private static void assertConserved(GasMixture roomBefore, GasMixture invBefore, GasMixture roomAfter, GasMixture invAfter) {
        for (GasType gas : GasType.values()) require(close(roomBefore.moles(gas) + invBefore.moles(gas),
                roomAfter.moles(gas) + invAfter.moles(gas), 1e-7), "conserves " + gas);
        require(close(roomBefore.thermalEnergy() + invBefore.thermalEnergy(),
                roomAfter.thermalEnergy() + invAfter.thermalEnergy(), 1e-7), "conserves thermal energy");
    }

    private static boolean same(GasMixture a, GasMixture b) {
        for (GasType gas : GasType.values()) if (Double.compare(a.moles(gas), b.moles(gas)) != 0) return false;
        return Double.compare(a.thermalEnergy(), b.thermalEnergy()) == 0;
    }
    private static boolean close(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance * Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
