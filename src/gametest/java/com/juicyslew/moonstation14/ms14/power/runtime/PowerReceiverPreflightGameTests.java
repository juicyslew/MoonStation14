package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.doors.AirlockCoreGameTests;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for power solve deferral while receiver footprints reconcile. */
@GameTestTags({"power_receiver_preflight"})
@PrefixGameTestTemplate(false)
public final class PowerReceiverPreflightGameTests {
    private PowerReceiverPreflightGameTests() { }

    @GameTest(template = "power_live_chain_clear", templateNamespace = "moonstation14", timeoutTicks = 300)
    public static void pendingTopologyDefersMeterUntilFreshSolve(GameTestHelper helper) {
        var fixture = AirlockCoreGameTests.poweredAirlock(helper);
        ServerLevel level = fixture.level();
        var door = fixture.door();

        // Queue graph work immediately before the next runtime solve. Receiver preparation
        // must leave the solve wholly deferred rather than publishing a partial meter.
        PowerGraphService.noteChanged(level, new ChunkPos(fixture.root()));
        if (PowerGraphService.topologyReady(level))
            throw new net.minecraft.gametest.framework.GameTestAssertException("fixture topology mutation must be pending");
        if (PowerRuntime.airlockPower(level, door).known())
            throw new net.minecraft.gametest.framework.GameTestAssertException("airlock meter became known during pending graph work");
        AirlockCoreGameTests.waitForFullPower(helper, fixture, 80, helper::succeed);
    }

    @GameTest(template = "power_live_chain_clear", templateNamespace = "moonstation14", timeoutTicks = 350)
    public static void preparationQueuesEveryLoadedMissingFootprintBeforeRuntimeSolve(GameTestHelper helper) {
        var fixture = AirlockCoreGameTests.poweredAirlock(helper);
        ServerLevel level = fixture.level();
        AirlockCoreGameTests.waitForFullPower(helper, fixture, 120, () -> {
            if (!PowerGraphService.topologyReady(level))
                throw new net.minecraft.gametest.framework.GameTestAssertException("fixture graph must start ready");

            BlockPos first = fixture.root().offset(4096, 0, 4096);
            BlockPos second = fixture.root().offset(4112, 0, 4096);
            FixtureChunkTickets.around(helper, first, first, 0);
            FixtureChunkTickets.around(helper, second, second, 0);
            LevelChunk firstChunk = (LevelChunk) level.getChunk(new ChunkPos(first).x, new ChunkPos(first).z);
            LevelChunk secondChunk = (LevelChunk) level.getChunk(new ChunkPos(second).x, new ChunkPos(second).z);
            assertEmptyPowerChunk(firstChunk);
            assertEmptyPowerChunk(secondChunk);

            // Simulate missed graph load bookkeeping while both native chunks remain FULL.
            PowerGraphService.onChunkUnload(new ChunkEvent.Unload(firstChunk));
            PowerGraphService.onChunkUnload(new ChunkEvent.Unload(secondChunk));
            if (!PowerGraphService.topologyReady(level))
                throw new net.minecraft.gametest.framework.GameTestAssertException("empty-chunk removal should not dirty the cable graph");

            if (PowerGraphService.prepareLoadedFootprint(level, first, 0))
                throw new net.minecraft.gametest.framework.GameTestAssertException("first missed FULL chunk must queue reconciliation");
            if (PowerGraphService.prepareLoadedFootprint(level, second, 0))
                throw new net.minecraft.gametest.framework.GameTestAssertException("preparation must still reconcile later footprints while pending");
            if (PowerGraphService.topologyReady(level))
                throw new net.minecraft.gametest.framework.GameTestAssertException("prepared footprints must remain pending until graph service reindexes them");

            AirlockCoreGameTests.waitForFullPower(helper, fixture, 120, helper::succeed);
        });
    }

    private static void assertEmptyPowerChunk(LevelChunk chunk) {
        if (chunk.getBlockEntities().values().stream().anyMatch(be ->
                be instanceof com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity
                        || be instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity
                        || be instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity
                        || be instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity
                        || be instanceof com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity))
            throw new net.minecraft.gametest.framework.GameTestAssertException("synthetic unload chunk contains a power consumer");
        var cableData = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (cableData != null && !cableData.snapshot().isEmpty())
            throw new net.minecraft.gametest.framework.GameTestAssertException("synthetic unload chunk contains cable topology");
    }
}
