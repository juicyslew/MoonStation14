package com.juicyslew.moonstation14.gametest.power;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestTags({"power", "ventilation"})
@PrefixGameTestTemplate(false)
public final class VentilationPowerGameTests {
    private VentilationPowerGameTests() { }

    @GameTest(template = "power_receiver_tall_clear", timeoutTicks = 240, templateNamespace = "moonstation14")
    public static void loadedInheritedDefinitionsDriveDefaultsAndReceiver(GameTestHelper helper) {
        var catalog = PrototypeRuntime.serverVentilationDevices();
        var ventDefinition = catalog.get(ModVentilationDevices.VENT);
        var scrubberDefinition = catalog.get(ModVentilationDevices.SCRUBBER);
        require(ventDefinition != null && scrubberDefinition != null,
                "resource reload publishes both inherited concrete definitions");
        require(ventDefinition.apcPowerReceiver().requestedWatts() == 5
                        && scrubberDefinition.apcPowerReceiver().requestedWatts() == 5,
                "resolved inherited receiver prototypes request the verified default 5 W");
        Fixture fixture = connectVent(helper);
        VentilationDeviceBlockEntity vent = fixture.vent();
        require(vent.prototype() == ventDefinition, "live BE resolves published vent definition");
        var settings = vent.config().settings();
        require(settings.externalPressureBoundKPa() == ventDefinition.gasVentPump().externalPressureBoundKPa()
                && settings.targetPressureChangeKPaPerSecond() == ventDefinition.gasVentPump().targetPressureChangeKPaPerSecond()
                && settings.targetPressureKPa() == ventDefinition.gasVentPump().externalPressureBoundKPa()
                && settings.selectedLayer().name().equals(ventDefinition.pipeLane().name()),
                "new vent defaults derive from inherited definition");
        BlockPos scrubberPos = helper.absolutePos(new BlockPos(4, 1, 10));
        fixture.level().setBlock(scrubberPos, ModBlocks.GAS_SCRUBBER.get().defaultBlockState(), 3);
        VentilationDeviceBlockEntity scrubber = (VentilationDeviceBlockEntity) fixture.level().getBlockEntity(scrubberPos);
        require(scrubber != null && scrubber.prototype() == scrubberDefinition
                && scrubber.config().settings().volumeRateM3PerSecond() == scrubberDefinition.gasVentScrubber().volumeRateM3PerSecond()
                && scrubber.config().settings().filteredGases().equals(java.util.Set.copyOf(scrubberDefinition.gasVentScrubber().filteredGases()))
                && scrubber.selectedLayer().name().equals(scrubberDefinition.pipeLane().name()),
                "new scrubber defaults derive from inherited definition");
        awaitMeters(helper, fixture.level(), vent, scrubber, 0, 160, () -> {
            require(PowerRuntime.ventilationMeter(fixture.level(), vent).known()
                    && PowerRuntime.ventilationMeter(fixture.level(), scrubber).known()
                    && PowerRuntime.ventilationMeter(fixture.level(), scrubber).deliveredWatts()
                        == scrubberDefinition.apcPowerReceiver().requestedWatts(),
                    "prototype reach and requested watts supply the graph receiver and solve");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void unconnectedVentHasUnknownPowerMeter(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.GAS_VENT.get().defaultBlockState(), 3);
        VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(pos);
        require(vent != null, "vent block entity exists");
        require(!PowerRuntime.ventilationMeter(level, vent).known(), "unconnected vent power is unknown");
        helper.succeed();
    }

    @GameTest(template = "power_live_chain_clear", timeoutTicks = 240, templateNamespace = "moonstation14")
    @GameTestTags({"simulation-defaults", "suite-stabilization"})
    public static void poweredVentMeterTracksBreakerAndReplacement(GameTestHelper helper) {
        Fixture fixture = connectVent(helper);
        ServerLevel level = fixture.level();
        VentilationDeviceBlockEntity vent = fixture.vent();
        PowerDeviceBlockEntity apcEntity = (PowerDeviceBlockEntity) level.getBlockEntity(fixture.apc());
        var firstPaidTick = new java.util.concurrent.atomic.AtomicInteger(-1);
        var recloseTick = new java.util.concurrent.atomic.AtomicInteger(-1);
        var completed = new java.util.concurrent.atomic.AtomicBoolean();
        // Prequeue initial admission and post-reclose probes within the existing
        // 100/40-tick phase budgets; callbacks never append to the running task iterator.
        for (int tick = 1; tick <= 140; tick++) {
            int checkTick = tick;
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, tick, () -> {
                if (completed.get()) return;
                if (firstPaidTick.get() < 0) {
                    var meter = PowerRuntime.ventilationMeter(level, vent);
                    if (meter.known() && meter.deliveredWatts() == 5
                            && ventilationFixtureReady(level, fixture)) {
                        firstPaidTick.set(checkTick);
                        Player player = creativePlayer(level, fixture.apc());
                        require(apcEntity.toggleBreaker(player, 0), "open APC breaker");
                        require(!PowerRuntime.ventilationMeter(level, vent).known(),
                                "open breaker immediately invalidates meter");
                        require(apcEntity.toggleBreaker(player, 0), "close APC breaker");
                        recloseTick.set(checkTick);
                    } else if (checkTick == 100) {
                        require(false, "vent has no fresh 5 W paid meter within existing 100-tick admission phase: "
                                + meter + ", " + ventMeterDiagnostic(level, fixture, "admission", checkTick));
                    }
                    return;
                }
                int closedAt = recloseTick.get();
                if (closedAt < 0 || checkTick <= closedAt) return;
                var meter = PowerRuntime.ventilationMeter(level, vent);
                if (meter.known() && meter.deliveredWatts() == 5
                        && ventilationFixtureReady(level, fixture)) {
                    if (!completed.compareAndSet(false, true)) return;
                    level.setBlock(fixture.ventPos(), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(fixture.ventPos(), ModBlocks.GAS_VENT.get().defaultBlockState()
                            .setValue(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock.FACING,
                                    Direction.NORTH), 3);
                    VentilationDeviceBlockEntity replacement = (VentilationDeviceBlockEntity)
                            level.getBlockEntity(fixture.ventPos());
                    require(replacement != null && replacement != vent, "replacement vent has a fresh block entity");
                    require(!PowerRuntime.ventilationMeter(level, vent).known(), "removed vent meter is unknown");
                    require(!PowerRuntime.ventilationMeter(level, replacement).known(),
                            "replacement awaits fresh solve");
                    helper.succeed();
                } else if (checkTick == closedAt + 40) {
                    require(false, "vent meter did not return within existing 40-tick reclose phase: "
                            + meter + ", " + ventMeterDiagnostic(level, fixture, "reclose", checkTick - closedAt));
                }
            });
        }
    }

    @GameTest(template = "power_receiver_tall_clear", timeoutTicks = 240, templateNamespace = "moonstation14")
    public static void localExtensionReceiverRespectsRadiusAndCableCuts(GameTestHelper helper) {
        FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(2, 0, 2)),
                helper.absolutePos(new BlockPos(4, 1, 10)), 0);
        ServerLevel level = (ServerLevel) helper.getLevel();
        // Keep this whole chain above cables in neighboring GameTests' floor-level fixtures.
        int y = 12;
        BlockPos source = helper.absolutePos(new BlockPos(3, y, 2));
        BlockPos substation = helper.absolutePos(new BlockPos(3, y, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, y, 6));
        BlockPos within = helper.absolutePos(new BlockPos(4, y, 10));
        BlockPos lastCable = helper.absolutePos(new BlockPos(4, y, 7));
        BlockPos remainingCable = helper.absolutePos(new BlockPos(4, y, 6));
        for (int z = 2; z <= 7; z++)
            level.setBlock(helper.absolutePos(new BlockPos(4, y, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(2, y, z)), Blocks.STONE.defaultBlockState(), 3);
        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        for (int z = 2; z <= 4; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.HV), "place elevated HV cable");
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, y, z)), Direction.EAST, CableTier.MV), "place elevated MV cable");
        for (int z = 6; z <= 7; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.APC), "place elevated APC cable");
        // nodesNear fails closed if even an empty chunk intersecting the radius is unloaded.
        // Include the full source chain and a one-block cable-frontier margin as well.
        for (int cx = (Math.min(source.getX(), within.getX()) - 4) >> 4;
                  cx <= (Math.max(source.getX(), within.getX()) + 4) >> 4; cx++)
            for (int cz = (Math.min(source.getZ(), within.getZ()) - 4) >> 4;
                     cz <= (Math.max(source.getZ(), within.getZ()) + 4) >> 4; cz++)
                level.getChunk(cx, cz);
        // The last APC cable is exactly three blocks away; its predecessor is four away.
        level.setBlock(within, ModBlocks.GAS_SCRUBBER.get().defaultBlockState(), 3);
        VentilationDeviceBlockEntity scrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(within);
        require(scrubber != null, "scrubber exists at radius three");
        awaitExtensionSolve(helper, scrubber, new CableFaceNode(lastCable, Direction.WEST, CableTier.APC),
                new CableFaceNode(remainingCable, Direction.WEST, CableTier.APC), 0);
    }

    private static void awaitExtensionSolve(GameTestHelper helper, VentilationDeviceBlockEntity scrubber,
                                            CableFaceNode intended, CableFaceNode remaining, int elapsed) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        CableFaceNode selected = PowerGraphService.nearestApcExtensionNode(level, scrubber.getBlockPos(), 3);
        if (selected != null)
            require(selected.equals(intended), "radius-three receiver selects the intended APC cable, not a foreign node: " + selected);
        var meter = PowerRuntime.ventilationMeter(level, scrubber);
        if (meter.known() && meter.deliveredWatts() == 5) {
            require(intended.equals(selected), "paid solve uses the intended radius-three APC receiver");
            require(CableStorage.remove(level, intended.host(), intended.face(), CableTier.APC) == CableTier.APC,
                    "cut nearest APC cable");
            require(!PowerRuntime.ventilationMeter(level, scrubber).known(), "cut invalidates old sample immediately");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> awaitCutTopology(helper, scrubber, remaining, 0));
            return;
        }
        require(elapsed < 175, "radius-three extension receives a completed 5 W solve without a DOWN cable; meter="
                + meter + ", topologyReady=" + PowerGraphService.topologyReady(level)
                + ", nearby=" + PowerGraphService.nodesNear(level, scrubber.getBlockPos(), 3, CableTier.APC)
                + ", nearest=" + selected
                + ", lastSolve=" + PowerRuntime.lastSolveDiagnostic(level));
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5,
                () -> awaitExtensionSolve(helper, scrubber, intended, remaining, elapsed + 5));
    }

    private static Fixture connectVent(GameTestHelper helper) {
        FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(2, 0, 2)),
                helper.absolutePos(new BlockPos(4, 1, 8)), 0);
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos substation = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 6));
        BlockPos ventPos = helper.absolutePos(new BlockPos(3, 1, 8));
        // The receiver search fails closed if any chunk in its radius-three footprint is unloaded.
        // GameTest templates can straddle chunk edges, so explicitly load that query footprint.
        for (int cx = (ventPos.getX() - 3) >> 4; cx <= (ventPos.getX() + 3) >> 4; cx++)
            for (int cz = (ventPos.getZ() - 3) >> 4; cz <= (ventPos.getZ() + 3) >> 4; cz++)
                level.getChunk(cx, cz);
        for (int z = 2; z <= 8; z++)
            level.setBlock(helper.absolutePos(new BlockPos(4, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(2, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(ventPos, ModBlocks.GAS_VENT.get().defaultBlockState().setValue(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock.FACING, Direction.NORTH), 3);
        level.setBlock(helper.absolutePos(new BlockPos(3, 0, 8)), Blocks.STONE.defaultBlockState(), 3);
        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        for (int z = 2; z <= 4; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.HV), "place HV cable");
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, z)), Direction.EAST, CableTier.MV), "place MV cable");
        for (int z = 6; z <= 7; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.APC), "place APC cable");

        VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos);
        require(vent != null, "vent block entity exists");
        return new Fixture(level, vent, apc, ventPos);
    }

    private static String ventMeterDiagnostic(ServerLevel level, Fixture fixture, String phase, int phaseTick) {
        StringBuilder portStates = new StringBuilder();
        for (CableFaceNode port : expectedFixturePorts(fixture)) portStates.append(port).append('=').append(PowerGraphService.state(level, port)).append(';');
        return "phase=" + phase + ",phaseTick=" + phaseTick
                + ",meterGate=" + PowerRuntime.ventilationMeterDiagnostic(level, fixture.vent())
                + ",expectedPorts=" + portStates
                + ",topology=" + PowerGraphService.topologyReady(level)
                + ",graph=" + PowerGraphService.diagnosticSnapshot(level)
                + ",solve=" + PowerRuntime.lastSolveSummary(level);
    }

    private static boolean ventilationFixtureReady(ServerLevel level, Fixture fixture) {
        return PowerFixtureReadiness.topologyReadyWithKnownPorts(level, expectedFixturePorts(fixture));
    }

    private static CableFaceNode[] expectedFixturePorts(Fixture fixture) {
        BlockPos vent = fixture.ventPos();
        return new CableFaceNode[]{
                new CableFaceNode(vent.offset(1, 0, -6), Direction.WEST, CableTier.HV),
                new CableFaceNode(vent.offset(1, 0, -4), Direction.WEST, CableTier.HV),
                new CableFaceNode(vent.offset(-1, 0, -4), Direction.EAST, CableTier.MV),
                new CableFaceNode(vent.offset(-1, 0, -2), Direction.EAST, CableTier.MV),
                new CableFaceNode(vent.offset(1, 0, -2), Direction.WEST, CableTier.APC),
                new CableFaceNode(vent.offset(1, 0, -1), Direction.WEST, CableTier.APC)};
    }

    private static void awaitMeters(GameTestHelper helper, ServerLevel level, VentilationDeviceBlockEntity vent,
                                    VentilationDeviceBlockEntity scrubber, int waited, int limit, Runnable ready) {
        PowerFixtureReadiness.await(helper, level, limit,
                () -> PowerRuntime.ventilationMeter(level, vent).known()
                        && PowerRuntime.ventilationMeter(level, scrubber).known(),
                () -> "ventilation receiver solve pending: vent=" + PowerRuntime.ventilationMeter(level, vent)
                        + ", scrubber=" + PowerRuntime.ventilationMeter(level, scrubber) + ", waited=" + waited,
                ready);
    }

    private static void awaitCutTopology(GameTestHelper helper, VentilationDeviceBlockEntity scrubber,
                                         CableFaceNode remaining, int waited) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        PowerFixtureReadiness.await(helper, level, 100,
                () -> PowerGraphService.topologyReady(level)
                        && PowerGraphService.state(level, remaining).knowledge()
                        == com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph.Knowledge.KNOWN
                        && remaining.equals(PowerGraphService.nearestApcExtensionNode(level, scrubber.getBlockPos(), 4)),
                () -> "waiting for radius-four cable topology; waited=" + waited + ", node="
                        + PowerGraphService.state(level, remaining), () -> {
                    require(CableStorage.has(level, remaining.host(), remaining.face(), CableTier.APC),
                            "radius-four APC cable remains after cut");
                    require(PowerGraphService.nearestApcExtensionNode(level, scrubber.getBlockPos(), 3) == null,
                            "no APC receiver remains within radius three after cut");
                    require(!PowerRuntime.ventilationMeter(level, scrubber).known(),
                            "remaining radius-four APC cable cannot power scrubber");
                    helper.succeed();
                });
    }

    private static Player creativePlayer(ServerLevel level, BlockPos pos) {
        Player player = new Player(level, pos, 0, new GameProfile(UUID.randomUUID(), "vent-power-test")) {
            @Override public boolean isCreative() { return true; }
            @Override public boolean isSpectator() { return false; }
        };
        player.getAbilities().instabuild = true;
        return player;
    }

    private record Fixture(ServerLevel level, VentilationDeviceBlockEntity vent, BlockPos apc, BlockPos ventPos) { }

    private static void placeDevice(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            default -> throw new IllegalArgumentException("Unsupported device in fixture: " + kind);
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
