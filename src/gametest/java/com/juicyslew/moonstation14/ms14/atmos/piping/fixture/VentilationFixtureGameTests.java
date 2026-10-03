package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.HashSet;

/** End-to-end coverage of the explicitly-built fixture through normal server systems. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VentilationFixtureGameTests {
    private VentilationFixtureGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "ventilation_fixture", timeoutTicks = 600)
    public static void builtFixtureInitializesAndReportsResolvedRuntime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereService atmos = AtmosphereService.INSTANCE;
        boolean oldEnabled = atmos.isEnabled();
        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum = atmos.vacuumDimensionsForTest();
        atmos.configureAtServerStart(true, Set.of(level.dimension()));
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
            helper.runAtTickTime(5, () -> initializeWhenReady(helper, level, origin, plan, 5, oldEnabled, oldVacuum, newlyForced));
        } catch (RuntimeException | Error failure) {
            unforce(level, newlyForced);
            atmos.configureAtServerStart(oldEnabled, oldVacuum);
            throw failure;
        }
    }

    private static void initializeWhenReady(GameTestHelper helper, ServerLevel level, BlockPos origin,
                                            VentilationFixturePlan plan, int tick, boolean oldEnabled,
                                             Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum,
                                             Set<Long> newlyForced) {
        try {
            if (!AtmosphereService.INSTANCE.sample(level, origin.offset(plan.sourceCells().getFirst())).isPresent()
                    || !AtmosphereService.INSTANCE.sample(level, origin.offset(plan.recipientCells().getFirst())).isPresent()) {
                if (tick >= 300) throw new GameTestAssertException("normal atmosphere loop did not claim both fixture rooms");
                helper.runAtTickTime(tick + 10, () -> initializeWhenReady(helper, level, origin, plan, tick + 10, oldEnabled, oldVacuum, newlyForced));
                return;
            }
            String initialized = VentilationFixtureService.initialize(level, origin);
            require(initialized.startsWith("Initialized 18 finite room cells") && initialized.contains("2 mol Plasma per cell"), "initialize service: " + initialized);
            require(VentilationFixtureService.initialize(level, origin).contains("already initialized"), "repeat initialization is guarded");
            String status = VentilationFixtureService.status(level, origin);
            require(status.contains("source 18/18 finite cells") && status.contains("recipient 18/18 finite cells"), "status reports measured finite rooms: " + status);
            require(status.contains("connected nodes Plasma=") && status.contains("concealed pipe=owner active Plasma="), "status reports actual pipe inventories: " + status);
            require(status.contains("alarm power=meter known delivered=") && status.contains("scrubber power=meter known delivered="), "status reports live delivered power: " + status);
            require(status.contains("scrubber enabled=true mode=FILTER") && status.contains("vent enabled=false"), "resolved device settings match fixture scenario");
            BlockPos scrubPos = origin.offset(plan.scrubber());
            var scrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(scrubPos);
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
            helper.runAtTickTime(10, () -> captureScrubbedGas(helper, level, origin, plan, oldEnabled, oldVacuum, newlyForced, initial, 10));
        } catch (RuntimeException | Error failure) {
            AtmosphereService.INSTANCE.configureAtServerStart(oldEnabled, oldVacuum);
            unforce(level, newlyForced);
            throw failure;
        }
    }

    private record GasTotals(double source, double recipient, double pipe) {
        double totalTestGas() { return source + recipient + pipe; }
    }

    private static GasTotals totals(ServerLevel level, BlockPos origin, VentilationFixturePlan plan) {
        double source = roomGas(level, origin, plan.sourceCells(), plan.testGas());
        double recipient = roomGas(level, origin, plan.recipientCells(), plan.testGas());
        BlockPos first = origin.offset(plan.pipes().stream().filter(p -> !p.concealed()).findFirst().orElseThrow().position());
        var component = LoadedPipeComponent.discover(level, first, VisualPipeBlockEntity.PRIMARY)
                .orElseThrow(() -> new GameTestAssertException("fixture pipe component unavailable during test-gas conservation check"));
        double pipe = component.nodes().stream().mapToDouble(n -> n.mixture().moles(plan.testGas())).sum();
        return new GasTotals(source, recipient, pipe);
    }

    private static double roomGas(ServerLevel level, BlockPos origin, java.util.List<BlockPos> cells, GasType gas) {
        double total = 0;
        for (BlockPos relative : cells) {
            var reading = AtmosphereService.INSTANCE.readAtmosphere(level, origin.offset(relative))
                    .orElseThrow(() -> new GameTestAssertException("finite fixture room sample unavailable"));
            require(reading.status() == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,
                    "fixture room remains finite");
            total += reading.mixture().moles(gas);
        }
        return total;
    }

    private static void captureScrubbedGas(GameTestHelper helper, ServerLevel level, BlockPos origin, VentilationFixturePlan plan,
                                           boolean oldEnabled, Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum,
                                           Set<Long> newlyForced, GasTotals initial, int elapsed) {
        try {
            GasTotals now = totals(level, origin, plan);
            if (now.source() < initial.source() - 1e-6 && now.pipe() > initial.pipe() + 1e-6) {
                require(Math.abs(now.totalTestGas() - initial.totalTestGas()) < 1e-6, "scrubber phase conserves all fixture test gas: " + now);
                require(now.recipient() < 1e-9, "disabled vent keeps recipient at zero test gas");
                VentilationDeviceBlockEntity scrubber = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.scrubber()));
                var current = scrubber.config().settings();
                var disabled = new VentilationDeviceConfig.Settings(current.revision(), false, current.externalPressureBoundKPa(),
                        current.targetPressureChangeKPaPerSecond(), current.targetPressureKPa(), current.volumeRateM3PerSecond(),
                        current.scrubberMode(), current.filteredGases(), current.selectedLayer());
                require(scrubber.compareAndSetSettings(current.revision(), disabled), "disable scrubber through revision-checked device settings");
                System.out.println("VENTILATION_FIXTURE_PASS finite-scrub-capture");
                helper.runAtTickTime(elapsed + 10, () -> releaseCapturedGas(helper, level, origin, plan, oldEnabled, oldVacuum, newlyForced, initial, now, elapsed + 10));
                return;
            }
            if (elapsed >= 200) throw new GameTestAssertException("normal paid scrubber did not capture source test gas; source=" + now.source() + " pipe=" + now.pipe());
            helper.runAtTickTime(elapsed + 10, () -> captureScrubbedGas(helper, level, origin, plan, oldEnabled, oldVacuum, newlyForced, initial, elapsed + 10));
        } catch (RuntimeException | Error failure) { cleanup(level, oldEnabled, oldVacuum, newlyForced); throw failure; }
    }

    private static void releaseCapturedGas(GameTestHelper helper, ServerLevel level, BlockPos origin, VentilationFixturePlan plan,
                                          boolean oldEnabled, Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum,
                                          Set<Long> newlyForced, GasTotals initial, GasTotals captured, int elapsed) {
        try {
            GasTotals now = totals(level, origin, plan);
            require(Math.abs(now.source() - captured.source()) < 1e-9, "disabled scrubber source inventory is stable");
            VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(origin.offset(plan.vent()));
            var current = vent.config().settings();
            var enabled = new VentilationDeviceConfig.Settings(current.revision(), true, current.externalPressureBoundKPa(),
                    current.targetPressureChangeKPaPerSecond(), current.targetPressureKPa(), current.volumeRateM3PerSecond(),
                    current.scrubberMode(), current.filteredGases(), current.selectedLayer());
            if (!current.configuredEnabled()) require(vent.compareAndSetSettings(current.revision(), enabled), "enable vent through revision-checked device settings");
            if (now.recipient() > 1e-6 && now.pipe() < captured.pipe() - 1e-6) {
                require(Math.abs(now.totalTestGas() - initial.totalTestGas()) < 1e-6, "release phase conserves all fixture test gas: " + now);
                System.out.println("VENTILATION_FIXTURE_PASS finite-vent-release");
                require(VentilationFixtureService.initialize(level, origin).contains("already initialized"), "gas initialization remains one-shot after transfer");
                cleanup(level, oldEnabled, oldVacuum, newlyForced);
                helper.succeed(); return;
            }
            if (elapsed >= 200) throw new GameTestAssertException("normal vent did not release captured pipe test gas; pipe=" + now.pipe() + " recipient=" + now.recipient());
            helper.runAtTickTime(elapsed + 10, () -> releaseCapturedGas(helper, level, origin, plan, oldEnabled, oldVacuum, newlyForced, initial, captured, elapsed + 10));
        } catch (RuntimeException | Error failure) { cleanup(level, oldEnabled, oldVacuum, newlyForced); throw failure; }
    }

    private static void cleanup(ServerLevel level, boolean oldEnabled, Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> oldVacuum, Set<Long> chunks) {
        AtmosphereService.INSTANCE.configureAtServerStart(oldEnabled, oldVacuum);
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
}
