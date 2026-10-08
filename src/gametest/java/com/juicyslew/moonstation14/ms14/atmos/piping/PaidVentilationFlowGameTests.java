package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.AtmospherePrecisionBudget;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestTags({"ventilation", "atmosphere", "power"})
@PrefixGameTestTemplate(false)
public final class PaidVentilationFlowGameTests {
    private PaidVentilationFlowGameTests() { }

    @GameTestTags({"simulation-defaults", "suite-stabilization"})
    @GameTest(template = "atmos_large_empty", batch = "paid_ventilation_flow", timeoutTicks = 300, templateNamespace = "moonstation14")
    public static void onlyCompletedPaidSolveMovesConservedGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture f = fixture(helper);
        var countersBefore = f.atmosphere.diagnosticRuntime(level);
        var totalBefore = totalInventory(f);
        GasMixture pipeBefore = pipe(f);
        var roomBefore = roomInventory(f);
        awaitPaidInterval(helper, level, f.vent, () -> {
            require(PowerRuntime.ventilationMeter(level, f.vent).known(), "real power chain provides paid interval");
            GasMixture pipeAfter = pipe(f);
            var roomAfter = roomInventory(f);
            require(roomAfter.totalMoles() > roomBefore.totalMoles(), "paid vent increases finite room+device-cell gas: before="
                    + roomBefore + ", after=" + roomAfter);
            require(pipeAfter.totalMoles() < pipeBefore.totalMoles(), "paid vent decreases pipe gas; pipe before=" + gasState(pipeBefore)
                    + ", pipe after=" + gasState(pipeAfter) + "; room before=" + gasState(roomBefore) + ", room after=" + gasState(roomAfter)
                    + diagnostic(level, f));
            var totalAfter = totalInventory(f);
            double operations = operationCount(countersBefore, f.atmosphere.diagnosticRuntime(level), localFacePairs(f), 1);
            conserve(totalBefore, totalAfter, operations);
            double moved = pipeBefore.totalMoles() - pipeAfter.totalMoles();
            for (GasType gas : GasType.values()) require(totalBefore.moleBudget(gas, totalAfter, operations) < moved,
                    "rounding budget cannot hide loss of the measured paid packet " + gas + "; moved=" + moved
                            + " budget=" + totalBefore.moleBudget(gas, totalAfter, operations) + " operations=" + operations);

            PowerDeviceBlockEntity apc = (PowerDeviceBlockEntity) level.getBlockEntity(f.apc);
            require(apc != null && apc.toggleBreaker(player(level, f.apc), 0), "open APC breaker");
            GasMixture stoppedPipe = pipe(f);
            var stoppedTotal = totalInventory(f);
            var stoppedCounters = f.atmosphere.diagnosticRuntime(level);
            helper.runAfterDelay(40, () -> {
                require(same(stoppedPipe, pipe(f)), "breaker prevents additional pipe transfer");
                conserve(stoppedTotal, totalInventory(f), operationCount(stoppedCounters,
                        f.atmosphere.diagnosticRuntime(level), 1, 0));
                helper.succeed();
            });
        });
    }

    @GameTestTags({"simulation-defaults", "suite-stabilization"})
    @GameTest(template = "atmos_large_empty", batch = "paid_ventilation_flow", timeoutTicks = 300, templateNamespace = "moonstation14")
    public static void disabledDeviceDoesNotMoveGas(GameTestHelper helper) {
        Fixture f = fixture(helper);
        var settings = f.vent.config().settings();
        require(f.vent.config().compareAndSet(settings.revision(), new VentilationDeviceConfig.Settings(
                settings.revision(), false, settings.externalPressureBoundKPa(), settings.targetPressureKPa(),
                settings.targetPressureKPa(), settings.volumeRateM3PerSecond(), settings.scrubberMode(),
                settings.gasesToScrub(), settings.selectedLayer())), "disable vent config");
        var before = totalInventory(f);
        var roomBefore = roomInventory(f);
        GasMixture pipeBefore = pipe(f);
        require(roomBefore.totalMoles() == 0.0 && roomBefore.energy() == 0.0,
                "disabled finite room's two trusted cells start in identical vacuum; local equalization has no work");
        awaitPaidInterval(helper, f.level, f.vent, () -> {
            require(PowerRuntime.ventilationMeter(f.level, f.vent).known(), "paid solve exists");
            double operations = 1.0; // Both trusted-initialized finite cells are identical vacuum; no local pressure work is possible.
            require(same(pipeBefore, pipe(f)), "disabled configuration leaves pipe unchanged");
            requireInventoryStable(roomBefore, roomInventory(f), operations, "disabled configuration finite room");
            conserve(before, totalInventory(f), operations);
            helper.succeed();
        });
    }

    @GameTestTags({"simulation-defaults", "suite-stabilization"})
    @GameTest(template = "atmos_large_empty", batch = "paid_ventilation_flow", timeoutTicks = 300, templateNamespace = "moonstation14")
    public static void poweredScrubberCapturesOnlySelectedGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture f = fixture(helper, true);
        var countersBefore = f.atmosphere.diagnosticRuntime(level);
        var totalBefore = totalInventory(f);
        GasMixture pipeBefore = pipe(f);
        var roomBefore = roomInventory(f);
        awaitPaidInterval(helper, level, f.vent, () -> {
            require(PowerRuntime.ventilationMeter(level, f.vent).known(), "real power chain provides scrubber paid interval");
            GasMixture pipeAfter = pipe(f);
            var roomAfter = roomInventory(f);
            double operations = operationCount(countersBefore, f.atmosphere.diagnosticRuntime(level), localFacePairs(f), 1);
            require(pipeAfter.moles(GasType.CARBON_DIOXIDE) > pipeBefore.moles(GasType.CARBON_DIOXIDE),
                    "scrubber captures room CO2 into vacuum waste pipe; pipe before=" + gasState(pipeBefore) + ", pipe after=" + gasState(pipeAfter)
                            + "; room before=" + gasState(roomBefore) + ", room after=" + gasState(roomAfter) + diagnostic(level, f));
            require(pipeAfter.moles(GasType.OXYGEN) == 0 && pipeAfter.moles(GasType.NITROGEN) == 0,
                    "scrubber does not transfer unselected gases: O2=" + pipeAfter.moles(GasType.OXYGEN)
                            + " N2=" + pipeAfter.moles(GasType.NITROGEN));
            requireWithinBudget(roomBefore.moles(GasType.OXYGEN), roomAfter.moles(GasType.OXYGEN),
                    roomBefore.moleBudget(GasType.OXYGEN, roomAfter, operations), "scrubber preserves room oxygen", operations);
            requireWithinBudget(roomBefore.moles(GasType.NITROGEN), roomAfter.moles(GasType.NITROGEN),
                    roomBefore.moleBudget(GasType.NITROGEN, roomAfter, operations), "scrubber preserves room nitrogen", operations);
            require(roomAfter.moles(GasType.CARBON_DIOXIDE) < roomBefore.moles(GasType.CARBON_DIOXIDE),
                    "scrubber reduces room CO2");
            var totalAfter = totalInventory(f);
            conserve(totalBefore, totalAfter, operations);
            double moved = pipeAfter.moles(GasType.CARBON_DIOXIDE) - pipeBefore.moles(GasType.CARBON_DIOXIDE);
            require(totalBefore.moleBudget(GasType.CARBON_DIOXIDE, totalAfter, operations) < moved,
                    "rounding budget cannot hide measured scrubber capture; moved=" + moved + " budget="
                            + totalBefore.moleBudget(GasType.CARBON_DIOXIDE, totalAfter, operations)
                            + " operations=" + operations);

            PowerDeviceBlockEntity apc = (PowerDeviceBlockEntity) level.getBlockEntity(f.apc);
            require(apc != null && apc.toggleBreaker(player(level, f.apc), 0), "open APC breaker");
            GasMixture stoppedPipe = pipe(f);
            var stoppedTotal = totalInventory(f);
            var stoppedCounters = f.atmosphere.diagnosticRuntime(level);
            helper.runAfterDelay(40, () -> {
                require(same(stoppedPipe, pipe(f)), "breaker prevents further scrubber transfer");
                conserve(stoppedTotal, totalInventory(f), operationCount(stoppedCounters,
                        f.atmosphere.diagnosticRuntime(level), 1, 0));
                helper.succeed();
            });
        });
    }

    private static void awaitPaidInterval(GameTestHelper helper, ServerLevel level,
                                          VentilationDeviceBlockEntity vent, Runnable onPaidInterval) {
        awaitPaidInterval(helper, level, vent, onPaidInterval, 1, -1, -1);
    }

    private static void awaitPaidInterval(GameTestHelper helper, ServerLevel level,
            VentilationDeviceBlockEntity vent, Runnable onPaidInterval, int elapsed,
            int stableSince, long stableGeneration) {
        var meter = PowerRuntime.ventilationMeter(level, vent);
        long generation = PowerGraphService.topologyGeneration(level);
        double requested = vent.prototype().apcPowerReceiver().requestedWatts();
        boolean knownFullyPaid = meter.known() && meter.deliveredWatts() == requested;
        if (!knownFullyPaid || generation != stableGeneration && stableSince >= 0) {
            stableSince = -1;
            stableGeneration = -1;
        }
        if (knownFullyPaid) {
            if (stableSince < 0) {
                stableSince = elapsed;
                stableGeneration = generation;
            } else if (elapsed - stableSince >= PowerRuntime.TICKS_PER_SOLVE + 1) {
                onPaidInterval.run();
                return;
            }
        }
        if (elapsed >= 260)
            throw new GameTestAssertException("no stable fully paid interval before deadline; elapsed=" + elapsed
                    + " stableSince=" + stableSince + " stableTicks=" + (stableSince < 0 ? 0 : elapsed - stableSince)
                    + " topologyGeneration=" + generation + " stableGeneration=" + stableGeneration
                    + " meter=" + meter + " solve=" + PowerRuntime.lastSolveDiagnostic(level));
        int next = elapsed + 1;
        int nextStableSince = stableSince;
        long nextStableGeneration = stableGeneration;
        helper.runAtTickTime(next, () -> awaitPaidInterval(helper, level, vent, onPaidInterval,
                next, nextStableSince, nextStableGeneration));
    }

    private static Fixture fixture(GameTestHelper helper) {
        return fixture(helper, false);
    }

    private static Fixture fixture(GameTestHelper helper, boolean scrubber) {
        ServerLevel level = helper.getLevel();
        AtmosphereService atmosphere = AtmosphereService.INSTANCE;
        // These tests intentionally keep the fixture's full power/pipe/room neighborhood loaded:
        // production graph discovery must not depend on unrelated, randomly unloaded test chunks.
        BlockPos footprintMin = helper.absolutePos(new BlockPos(2, 0, 1));
        BlockPos footprintMax = helper.absolutePos(new BlockPos(7, 3, 6));
        for (int cx = (footprintMin.getX() >> 4) - 1; cx <= (footprintMax.getX() >> 4) + 1; cx++)
            for (int cz = (footprintMin.getZ() >> 4) - 1; cz <= (footprintMax.getZ() >> 4) + 1; cz++)
                level.getChunk(cx, cz);
        // One-cell sealed room, with the vent occupying the south wall and the room facing north.
        for (int x = 5; x <= 7; x++) for (int z = 3; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++) {
                boolean opening = x == 6 && z == 4 && y == 1;
                helper.setBlock(new BlockPos(x, y, z), opening ? Blocks.AIR : Blocks.STONE);
            }
        }
        BlockPos ventPos = helper.absolutePos(new BlockPos(6, 1, 5));
        helper.setBlock(new BlockPos(6, 1, 5), (scrubber ? ModBlocks.GAS_SCRUBBER.get() : ModBlocks.GAS_VENT.get()).defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.NORTH));
        BlockPos pipePos = helper.absolutePos(new BlockPos(6, 1, 6));
        helper.setBlock(new BlockPos(6, 1, 6), ModBlocks.STEEL_WALL_BLOCK.get());
        require(LoadedSealedWallSegment.prepareOne(level, pipePos, Direction.NORTH,
                PipeTopology.Layer.PRIMARY, UUID.randomUUID()).isPresent(), "admit straight sealed wall lane");
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, pipePos,
                PipeTopology.Layer.PRIMARY), "create sealed wall gas owner");
        // Live source -> substation -> APC power path.
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos sub = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 5));
        place(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        place(level, sub, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        place(level, apc, PowerDeviceKind.APC, Direction.WEST);
        for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(4, 1, z), Blocks.STONE);
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST, CableTier.HV), "HV cable");
        }
        for (int z = 3; z <= 5; z++) {
            helper.setBlock(new BlockPos(2, 1, z), Blocks.STONE);
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, z)), Direction.EAST, CableTier.MV), "MV cable");
        }
        helper.setBlock(new BlockPos(4, 1, 5), Blocks.STONE);
        require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, 5)), Direction.WEST, CableTier.APC), "APC cable");
        for (int x = 3; x <= 6; x++) {
            // Cable records require a full-collision sturdy host; the template's floor is not
            // guaranteed at these cells, and the room floor only covers x=5..7.
            helper.setBlock(new BlockPos(x, 0, 5), Blocks.STONE);
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(x, 0, 5)),
                    Direction.UP, CableTier.APC), "floor APC cable at x=" + x);
        }
        BlockPos room = helper.absolutePos(new BlockPos(6, 1, 4));
        java.util.List<BlockPos> finiteRoom = java.util.List.of(room, ventPos);
        GasMixture authoredRoom = scrubber
                ? new GasMixture(Map.of(GasType.OXYGEN, 100.0, GasType.NITROGEN, 300.0,
                        GasType.CARBON_DIOXIDE, 20.0), 300.0)
                : GasMixture.vacuum();
        require(atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level, finiteRoom, authoredRoom),
                "trusted setup proves complete sealed room+passable device cell before assigning controlled gas");
        var owner = LoadedSealedWallGasOwner.activeSnapshot(level, pipePos, PipeTopology.Layer.PRIMARY)
                .orElseThrow(() -> new GameTestAssertException("sealed wall gas owner"));
        PipeTopology.NodeKey key = new PipeTopology.NodeKey(pipePos, PipeTopology.Layer.PRIMARY);
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeGasInventory before = saved.snapshot();
        var entry = before.get(key).orElseThrow();
        var after = before.withMixture(key, owner.incarnation(), entry.version(), scrubber ? GasMixture.vacuum() : new GasMixture(
                Map.of(GasType.OXYGEN, 12.0, GasType.NITROGEN, 45.0), 310));
        require(saved.replace(before.revision(), before, after), "seed pipe gas");
        VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos);
        require(vent != null, "vent block entity");
        return new Fixture(level, atmosphere, vent, room, ventPos, pipePos, apc);
    }

    private static void place(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            default -> throw new IllegalArgumentException("unsupported power device");
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }
    private static GasMixture pipe(Fixture f) {
        return LoadedSealedWallGasOwner.activeSnapshot(f.level, f.pipe, PipeTopology.Layer.PRIMARY).orElseThrow().mixture();
    }
    private static AtmospherePrecisionBudget.Inventory roomInventory(Fixture f) {
        java.util.List<GasMixture> mixtures = new java.util.ArrayList<>();
        for (BlockPos cell : java.util.List.of(f.room, f.deviceCell)) {
            var reading = f.atmosphere.readAtmosphere(f.level, cell).orElseThrow(
                    () -> new GameTestAssertException("finite room/device-cell inventory unavailable at " + cell));
            require(reading.status() == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,
                    "room inventory is finite at " + cell);
            mixtures.add(reading.mixture());
        }
        return AtmospherePrecisionBudget.Inventory.capture(mixtures);
    }
    private static AtmospherePrecisionBudget.Inventory totalInventory(Fixture f) {
        java.util.List<GasMixture> mixtures = new java.util.ArrayList<>();
        for (BlockPos cell : java.util.List.of(f.room, f.deviceCell))
            mixtures.add(f.atmosphere.readAtmosphere(f.level, cell).orElseThrow().mixture());
        mixtures.add(pipe(f));
        return AtmospherePrecisionBudget.Inventory.capture(mixtures);
    }
    private static String diagnostic(ServerLevel level, Fixture f) {
        StringBuilder result = new StringBuilder("; fixture topology:");
        for (Direction direction : Direction.values()) {
            BlockPos pos = f.vent.getBlockPos().relative(direction);
            var state = level.getBlockState(pos);
            result.append(" [").append(direction).append('=').append(pos).append(',').append(state)
                    .append(",passable=").append(AtmosphereTopology.isPassable(level, pos));
            var gas = f.atmosphere.sample(level, pos);
            result.append(",finiteSample=").append(gas.map(PaidVentilationFlowGameTests::gasState).orElse("none")).append(']');
        }
        var roomChunk = level.getChunk(f.room);
        var data = roomChunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        result.append("; room=").append(f.room).append(",claim=")
                .append(data != null && data.isFiniteClaimed(f.room.getX() & 15, f.room.getY(), f.room.getZ() & 15));
        result.append("; ventFacing=").append(level.getBlockState(f.vent.getBlockPos()).getValue(VentilationDeviceBlock.FACING));
        result.append("; pipeOwner=").append(LoadedSealedWallGasOwner.activeSnapshot(level, f.pipe, PipeTopology.Layer.PRIMARY)
                .map(owner -> "active=" + owner.active() + ",version=" + owner.version() + ",gas=" + gasState(owner.mixture()))
                .orElse("none"));
        return result.toString();
    }
    private static String gasState(GasMixture mixture) {
        StringBuilder result = new StringBuilder("moles={");
        for (GasType gas : GasType.values()) {
            if (result.length() > 8) result.append(',');
            result.append(gas).append('=').append(mixture.moles(gas));
        }
        return result.append("},energy=").append(mixture.thermalEnergy()).append(",total=")
                .append(mixture.totalMoles()).toString();
    }
    private static String gasState(AtmospherePrecisionBudget.Inventory inventory) {
        return "moles=" + inventory.moles() + ",energy=" + inventory.energy() + ",total=" + inventory.totalMoles();
    }
    private static Player player(ServerLevel level, BlockPos pos) {
        Player result = new Player(level, pos, 0, new GameProfile(UUID.randomUUID(), "paid-vent-test")) {
            @Override public boolean isCreative() { return true; }
            @Override public boolean isSpectator() { return false; }
        };
        result.getAbilities().instabuild = true;
        return result;
    }
    private static boolean same(GasMixture a, GasMixture b) {
        if (Double.compare(a.thermalEnergy(), b.thermalEnergy()) != 0) return false;
        for (GasType gas : GasType.values()) if (Double.compare(a.moles(gas), b.moles(gas)) != 0) return false;
        return true;
    }
    private static double operationCount(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereRuntimeTelemetry.Snapshot before,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereRuntimeTelemetry.Snapshot after,
            int localFacePairs, int paidIntervals) {
        return AtmospherePrecisionBudget.operations(after.completedCycles() - before.completedCycles(),
                localFacePairs, paidIntervals);
    }
    private static void conserve(AtmospherePrecisionBudget.Inventory before,
            AtmospherePrecisionBudget.Inventory after, double operations) {
        for (GasType gas : GasType.values()) requireWithinBudget(before.moles(gas), after.moles(gas),
                before.moleBudget(gas, after, operations), "conserve " + gas, operations);
        requireWithinBudget(before.energy(), after.energy(), before.energyBudget(after, operations),
                "conserve thermal energy", operations);
    }
    private static void requireWithinBudget(double before, double after, double budget, String label, double operations) {
        double delta = after - before;
        require(Math.abs(delta) <= budget, label + ": before=" + before + " after=" + after + " delta=" + delta
                + " budget=" + budget + " operations=" + operations);
    }
    private static void requireInventoryStable(AtmospherePrecisionBudget.Inventory before,
            AtmospherePrecisionBudget.Inventory after, double operations, String label) {
        for (GasType gas : GasType.values()) requireWithinBudget(before.moles(gas), after.moles(gas),
                before.moleBudget(gas, after, operations), label + " " + gas, operations);
        requireWithinBudget(before.energy(), after.energy(), before.energyBudget(after, operations),
                label + " energy", operations);
    }
    private static int localFacePairs(Fixture fixture) {
        BlockPos a = fixture.room, b = fixture.deviceCell;
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ()) == 1 ? 1 : 0;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
    private record Fixture(ServerLevel level, AtmosphereService atmosphere, VentilationDeviceBlockEntity vent,
                           BlockPos room, BlockPos deviceCell, BlockPos pipe, BlockPos apc) { }
}
