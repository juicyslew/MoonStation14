package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.HashSet;

/** End-to-end coverage of the explicitly-built fixture through normal server systems. */
@GameTestTags({"ventilation", "atmosphere", "power"})
@PrefixGameTestTemplate(false)
public final class VentilationFixtureGameTests {
    private VentilationFixtureGameTests() { }

@GameTest(template = "atmos_large_empty", batch = "ventilation_fixture", timeoutTicks = 600, templateNamespace = "moonstation14")
    @GameTestTags("repair-vent-fixture")
    public static void builtFixtureInitializesAndReportsResolvedRuntime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService atmos = AtmosphereService.INSTANCE;
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum = atmos.vacuumDimensionsForTest();
        atmos.configureAtServerStart(Set.of(level.dimension()), atmos.spaceDimensionsForTest());
        BlockPos origin = new BlockPos(200, 20, 200);
        VentilationFixturePlan plan = VentilationFixturePlan.standard();
        BlockPos min = origin, max = origin.offset(VentilationFixturePlan.WIDTH - 1,
                VentilationFixturePlan.HEIGHT - 1, VentilationFixturePlan.DEPTH - 1);
        Set<Long> newlyForced = new HashSet<>();
        for (int cx = (min.getX() >> 4) - 1; cx <= (max.getX() >> 4) + 1; cx++)
            for (int cz = (min.getZ() >> 4) - 1; cz <= (max.getZ() >> 4) + 1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                if (!level.getForcedChunks().contains(key)) {
                    level.setChunkForced(cx, cz, true);
                    newlyForced.add(key);
                }
                level.getChunk(cx, cz);
            }
        try {
            for (var cell : plan.blocks()) require(level.getBlockState(origin.offset(cell.pos())).isAir(), "remote footprint must start empty");
            require(VentilationFixtureService.build(level, origin).startsWith("Built finite ventilation fixture"), "service builds real fixture");
            helper.runAfterDelay(5, () -> initializeWhenReady(helper, level, origin, plan, 5, oldVacuum, newlyForced));
        } catch (RuntimeException | Error failure) {
            unforce(level, newlyForced);
            atmos.configureAtServerStart(oldVacuum, atmos.spaceDimensionsForTest());
            throw failure;
        }
    }

    private static void initializeWhenReady(GameTestHelper helper, ServerLevel level, BlockPos origin,
                                             VentilationFixturePlan plan, int tick,
                                             Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum,
                                             Set<Long> newlyForced) {
        try {
            BlockPos scrubPos = origin.offset(plan.scrubber());
            BlockPos alarmPos = origin.offset(plan.alarm());
            var scrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(scrubPos);
            var alarm = (AirAlarmBlockEntity) level.getBlockEntity(alarmPos);
            var scrubberMeter = scrubber == null ? null : PowerRuntime.ventilationMeter(level, scrubber);
            var alarmMeter = alarm == null ? null : PowerRuntime.airAlarmMeter(level, alarm);
            boolean roomsOwned = AtmosphereService.INSTANCE.sample(level, origin.offset(plan.sourceCells().getFirst())).isPresent()
                    && AtmosphereService.INSTANCE.sample(level, origin.offset(plan.recipientCells().getFirst())).isPresent();
            boolean powerPaid = scrubber != null && alarm != null
                    && scrubberMeter.known() && scrubberMeter.deliveredWatts() >= scrubber.prototype().apcPowerReceiver().requestedWatts()
                    && alarmMeter.known() && alarmMeter.deliveredWatts() >= alarm.prototype().apcPowerReceiver().requestedWatts();
            if (!roomsOwned || !powerPaid) {
                if (tick >= 300) throw new GameTestAssertException("normal atmosphere ownership and fully paid ventilation power did not become ready before deadline; scrubber=" + scrubberMeter + ", alarm=" + alarmMeter);
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 10, () -> initializeWhenReady(helper, level, origin, plan, tick + 10, oldVacuum, newlyForced));
                return;
            }
            String initialized = VentilationFixtureService.initialize(level, origin);
            require(initialized.startsWith("Initialized 18 finite room cells") && initialized.contains("2 mol Plasma per cell"), "initialize service: " + initialized);
            BlockPos recipientOutlet = origin.offset(plan.vent()).relative(plan.ventFacing());
            var recipientEndpoint = AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, recipientOutlet);
            require(recipientEndpoint.isPresent() && recipientEndpoint.get().snapshot().totalMoles() == 0.0,
                    "trusted initialization leaves the actual vent recipient endpoint vacuum");
            require(VentilationFixtureService.initialize(level, origin).contains("already initialized"), "repeat initialization is guarded");
            String status = VentilationFixtureService.status(level, origin);
            require(status.contains("source 18/18 finite cells") && status.contains("recipient 18/18 finite cells"), "status reports measured finite rooms: " + status);
            require(status.contains("connected nodes Plasma=") && status.contains("concealed pipe=owner active Plasma="), "status reports actual pipe inventories: " + status);
            require(status.contains("alarm power=meter known delivered=") && status.contains("scrubber power=meter known delivered="), "status reports live delivered power: " + status);
            require(status.contains("scrubber enabled=true mode=FILTER") && status.contains("vent enabled=false"), "resolved device settings match fixture scenario");
            require(scrubber != null && scrubber.config().settings().filteredGases().equals(Set.of(plan.testGas())), "scrubber filters only fixture test gas");
            var meter = PowerRuntime.ventilationMeter(level, scrubber);
            require(meter.known() && meter.deliveredWatts() >= scrubber.prototype().apcPowerReceiver().requestedWatts(),
                    "real scrubber meter is fully paid: " + meter);
            System.out.println("VENTILATION_FIXTURE_PASS initialized-and-status");
            GasTotals initial = totals(level, origin, plan);
            require(Math.abs(initial.totalTestGas() - 36.0) < 1e-6, "initialized Plasma inventory is 36 mol: " + initial);
            require(com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility.hasOverlay(plan.testGas())
                    && com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility.alphaByte(plan.testGas(), plan.testGasMolesPerCell(), 1.0) > 0,
                    "initial source concentration has a visible gas overlay");
            helper.runAfterDelay(5, () -> captureScrubbedGas(helper, level, origin, plan, oldVacuum, newlyForced, initial, 5));
        } catch (RuntimeException | Error failure) {
            AtmosphereService.INSTANCE.configureAtServerStart(oldVacuum, AtmosphereService.INSTANCE.spaceDimensionsForTest());
            unforce(level, newlyForced);
            throw failure;
        }
    }

    private record GasTotals(double source, double recipient, double pipe, Set<BlockPos> sourceMembers, Set<BlockPos> recipientMembers) {
        double totalTestGas() { return source + recipient + pipe; }
        String diagnostic() { return "source=" + source + " recipient=" + recipient + " pipe=" + pipe + " total=" + totalTestGas()
                + " sourceFiniteMembers=" + sourceMembers.size() + " recipientFiniteMembers=" + recipientMembers.size(); }
    }

    private static GasTotals totals(ServerLevel level, BlockPos origin, VentilationFixturePlan plan) {
        RoomMeasure source = roomGas(level, origin, plan.sourceCells(), plan.testGas());
        RoomMeasure recipient = roomGas(level, origin, plan.recipientCells(), plan.testGas());
        BlockPos first = origin.offset(plan.pipes().stream().filter(p -> !p.concealed()).findFirst().orElseThrow().position());
        var component = LoadedPipeComponent.discover(level, first, VisualPipeBlockEntity.PRIMARY)
                .orElseThrow(() -> new GameTestAssertException("fixture pipe component unavailable during test-gas conservation check"));
        double pipe = component.nodes().stream().mapToDouble(n -> n.mixture().moles(plan.testGas())).sum();
        return new GasTotals(source.moles(), recipient.moles(), pipe, source.members(), recipient.members());
    }

    private record RoomMeasure(double moles, Set<BlockPos> members) { }

    private static RoomMeasure roomGas(ServerLevel level, BlockPos origin, java.util.List<BlockPos> cells, GasType gas) {
        double total = 0;
        int authoredFinite = 0;
        for (BlockPos relative : cells) {
            var reading = AtmosphereService.INSTANCE.readAtmosphere(level, origin.offset(relative))
                    .orElseThrow(() -> new GameTestAssertException("finite fixture room sample unavailable"));
            require(reading.status() == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,
                    "fixture room remains finite");
            authoredFinite++;
        }
        require(authoredFinite == cells.size(), "all authored room cells remain finite");

        // Device blocks can be gas-passable even though they are not authored as room cells.
        // Measure every currently-owned finite endpoint in the bounded room envelope, not just
        // the 18 cells listed for trusted fixture initialization.
        int minX = cells.stream().mapToInt(BlockPos::getX).min().orElseThrow() - 1;
        int maxX = cells.stream().mapToInt(BlockPos::getX).max().orElseThrow() + 1;
        int minY = cells.stream().mapToInt(BlockPos::getY).min().orElseThrow() - 1;
        int maxY = cells.stream().mapToInt(BlockPos::getY).max().orElseThrow() + 1;
        int minZ = cells.stream().mapToInt(BlockPos::getZ).min().orElseThrow() - 1;
        int maxZ = cells.stream().mapToInt(BlockPos::getZ).max().orElseThrow() + 1;
        Set<BlockPos> members = new HashSet<>();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            BlockPos pos = origin.offset(x, y, z);
            if (AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, pos).isEmpty()) continue;
            var reading = AtmosphereService.INSTANCE.readAtmosphere(level, pos).orElse(null);
            if (reading != null && reading.status() == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE) {
                members.add(new BlockPos(x, y, z));
                total += reading.mixture().moles(gas);
            }
        }
        require(members.containsAll(cells), "all authored cells are members of the measured finite enclosure");
        return new RoomMeasure(total, Set.copyOf(members));
    }

    private static void captureScrubbedGas(GameTestHelper helper, ServerLevel level, BlockPos origin, VentilationFixturePlan plan,
                                           Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum,
                                           Set<Long> newlyForced, GasTotals initial, int elapsed) {
        try {
            GasTotals now = totals(level, origin, plan);
            if (now.source() < initial.source() - 1e-6 && now.pipe() > initial.pipe() + 1e-6) {
                require(withinFloatRoundoff(now.totalTestGas(), initial.totalTestGas()), "scrubber phase conserves all fixture test gas: " + now.diagnostic());
                require(now.recipient() < 1e-9, "disabled vent keeps recipient at zero test gas");
                VentilationDeviceBlockEntity scrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.scrubber()));
                var current = scrubber.config().settings();
                var disabled = new VentilationDeviceConfig.Settings(current.revision(), false, current.externalPressureBoundKPa(),
                        current.targetPressureChangeKPaPerSecond(), current.targetPressureKPa(), current.volumeRateM3PerSecond(),
                        current.scrubberMode(), current.filteredGases(), current.selectedLayer());
                require(scrubber.compareAndSetSettings(current.revision(), disabled), "disable scrubber through revision-checked device settings");
                System.out.println("VENTILATION_FIXTURE_PASS finite-scrub-capture");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 10, () -> releaseCapturedGas(helper, level, origin, plan, oldVacuum, newlyForced, initial, now, 0));
                return;
            }
            if (elapsed >= 200) throw new GameTestAssertException("normal paid scrubber did not capture source test gas; source=" + now.source() + " pipe=" + now.pipe());
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 10, () -> captureScrubbedGas(helper, level, origin, plan, oldVacuum, newlyForced, initial, elapsed + 10));
        } catch (RuntimeException | Error failure) { cleanup(level, oldVacuum, newlyForced); throw failure; }
    }

    private static void releaseCapturedGas(GameTestHelper helper, ServerLevel level, BlockPos origin, VentilationFixturePlan plan,
                                           Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum,
                                          Set<Long> newlyForced, GasTotals initial, GasTotals captured, int elapsed) {
        try {
            GasTotals now = totals(level, origin, plan);
            require(now.sourceMembers().equals(captured.sourceMembers()) && now.recipientMembers().equals(captured.recipientMembers()),
                    "finite enclosure membership is unchanged during disabled interval; captured=" + captured.diagnostic() + " current=" + now.diagnostic());
            require(withinFloatRoundoff(now.source(), captured.source()),
                    "disabled scrubber source inventory is stable within existing float-storage precision; captured=" + captured.diagnostic() + " current=" + now.diagnostic());
            VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.vent()));
            var current = vent.config().settings();
            String roomPressure = ventRoomPressure(level, origin, plan);
            var endpoint = AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(
                    level, origin.offset(plan.vent()).relative(plan.ventFacing()));
            require(endpoint.isPresent() && endpoint.get().snapshot().pressureKpa(1.0)
                            < Math.min(current.externalPressureBoundKPa(), current.targetPressureKPa()),
                    "recipient pressure must be below resolved vent target before enabling; pressure=" + roomPressure
                            + " targetKPa=" + current.targetPressureKPa() + " externalBoundKPa=" + current.externalPressureBoundKPa()
                            + "; captured=" + captured.diagnostic());
            var enabled = new VentilationDeviceConfig.Settings(current.revision(), true, current.externalPressureBoundKPa(),
                    current.targetPressureChangeKPaPerSecond(), current.targetPressureKPa(), current.volumeRateM3PerSecond(),
                    current.scrubberMode(), current.filteredGases(), current.selectedLayer());
            if (!current.configuredEnabled()) require(vent.compareAndSetSettings(current.revision(), enabled), "enable vent through revision-checked device settings");
            if (now.recipient() > 1e-6 && now.pipe() < captured.pipe() - 1e-6) {
                require(withinFloatRoundoff(now.totalTestGas(), initial.totalTestGas()), "release phase conserves all fixture test gas: " + now.diagnostic());
                System.out.println("VENTILATION_FIXTURE_PASS finite-vent-release");
                require(VentilationFixtureService.initialize(level, origin).contains("already initialized"), "gas initialization remains one-shot after transfer");
                cleanup(level, oldVacuum, newlyForced);
                helper.succeed(); return;
            }
            if (elapsed >= 200) throw new GameTestAssertException("normal vent did not release captured pipe test gas; "
                    + now.diagnostic() + " " + ventRoomPressure(level, origin, plan) + " " + ventFlowDiagnostic(level, origin, plan));
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 10, () -> releaseCapturedGas(helper, level, origin, plan, oldVacuum, newlyForced, initial, captured, elapsed + 10));
        } catch (RuntimeException | Error failure) { cleanup(level, oldVacuum, newlyForced); throw failure; }
    }

    private static void cleanup(ServerLevel level, Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum, Set<Long> chunks) {
        AtmosphereService.INSTANCE.configureAtServerStart(oldVacuum, AtmosphereService.INSTANCE.spaceDimensionsForTest());
        unforce(level, chunks);
    }

    private static void unforce(ServerLevel level, Set<Long> chunks) {
        for (long key : chunks) {
            ChunkPos pos = new ChunkPos(key);
            level.setChunkForced(pos.x, pos.z, false);
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static String ventRoomPressure(ServerLevel level, BlockPos origin, VentilationFixturePlan plan) {
        VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.vent()));
        BlockPos roomPos = origin.offset(plan.vent()).relative(plan.ventFacing());
        var endpoint = AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, roomPos);
        if (endpoint.isEmpty() || endpoint.get().snapshot() == null) return "ventRoom=" + roomPos + " finite endpoint unavailable";
        var mixture = endpoint.get().snapshot();
        var settings = vent.config().settings();
        return "ventRoom=" + roomPos + " pressureKPa=" + mixture.pressureKpa(1.0)
                + " totalMoles=" + mixture.totalMoles() + " plasma=" + mixture.moles(plan.testGas())
                + " targetKPa=" + settings.targetPressureKPa() + " externalBoundKPa=" + settings.externalPressureBoundKPa();
    }

    private static String ventFlowDiagnostic(ServerLevel level, BlockPos origin, VentilationFixturePlan plan) {
        BlockPos pos = origin.offset(plan.vent());
        var vent = (VentilationDeviceBlockEntity) level.getBlockEntity(pos);
        var settings = vent.config().settings();
        int lane = switch (settings.selectedLayer()) {
            case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
            case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
            case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
        };
        var component = LoadedPipeComponent.discover(level, vent.pipePortPos(), lane);
        String portInventory = component.flatMap(snapshot -> snapshot.nodes().stream()
                .filter(node -> node.key().position().equals(vent.pipePortPos())).findFirst())
                .map(node -> "incarnation=" + node.incarnation() + " gas=" + node.mixture().gasMoles())
                .orElse("missing");
        String nodes = component.map(snapshot -> snapshot.nodes().stream()
                .map(node -> node.key().position().toShortString() + "#" + node.incarnation()
                        + " " + node.mixture().gasMoles())
                .collect(java.util.stream.Collectors.joining("; "))).orElse("unresolved");
        return "ventEnabled=" + settings.configuredEnabled() + " ventMeter=" + PowerRuntime.ventilationMeter(level, vent)
                + " meterDiagnostic=" + PowerRuntime.ventilationMeterDiagnostic(level, vent)
                + " requestedWatts=" + vent.prototype().apcPowerReceiver().requestedWatts()
                + " facing=" + vent.getBlockState().getValue(VentilationDeviceBlock.FACING)
                + " pipePort=" + vent.pipePortPos().toShortString() + " selectedLayer=" + settings.selectedLayer()
                + " actualPortInventory=" + portInventory
                + " componentNodes=" + (component.map(s -> s.nodes().size()).orElse(0)) + " pipeNodes=[" + nodes + "]";
    }

    /** Matches the established conservation bound in PipeGasTransfer: eight float ULPs. */
    private static boolean withinFloatRoundoff(double a, double b) {
        return Math.abs(a - b) <= Math.max(Math.ulp((float) a), Math.ulp((float) b)) * 8.0;
    }
}
