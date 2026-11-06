package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.CabinGasInventorySnapshot;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.EnumMap;
import java.util.ArrayList;

/** Live normal-system cabin verification on the authored moving Sable fixture. */
@GameTestTags("shuttle-cabin-ventilation")
@PrefixGameTestTemplate(false)
public final class ShuttleCabinVentilationGameTests {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ShuttleCabinVentilationGameTests.class);
    private static final double FRAME_SPEED_BLOCKS_PER_SECOND = 20.0;
    // Above float pose quantization near X=2000 (~0.000122 blocks), below observed native motion (~0.02).
    private static final double MIN_PULSE_AXIS_DELTA_BLOCKS = 0.01;
    private static final int OBSERVE_TICKS = 120;
    private static final int SCENE_MARGIN_BLOCKS = 12;
    private static final int SCENE_HEADROOM_BLOCKS = 2;
    private static final int SCENE_WIDTH = ((int) Math.ceil(FRAME_SPEED_BLOCKS_PER_SECOND * OBSERVE_TICKS / 20.0
            + ShuttleCabinFixture.WIDTH + 2 * SCENE_MARGIN_BLOCKS) + 15) / 16 * 16;
    private ShuttleCabinVentilationGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "shuttle_cabin_ventilation", timeoutTicks = 900,
            templateNamespace = "moonstation14")
    public static void paidVentilationMovesFiniteGasWithNativeFrameMotion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos world = new BlockPos(2000, 300, 1600);
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, world.offset(-SCENE_MARGIN_BLOCKS, 0, -SCENE_MARGIN_BLOCKS), SCENE_WIDTH,
                ShuttleCabinFixture.HEIGHT + SCENE_HEADROOM_BLOCKS, 32);
        SableFixtureScene.awaitEmpty(helper, scene, 40, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var built = SableFixturePlot.allocateAndAuthor(level, new Vector3d(world.getX()+.5, world.getY(), world.getZ()+.5), plot -> {
                BlockPos storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH, ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                return ShuttleCabinFixture.build(level, storage, plot.engineId());
            });
            var plot = built.plot();
            SableFixtureScene.trackPlot(helper, plot);
            var cabin = built.result();
            BlockPos ventPos = cabin.at(ShuttleCabinFixture.VENT), scrubPos = cabin.at(ShuttleCabinFixture.SCRUBBER);
            var vent = (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos);
            var scrub = (VentilationDeviceBlockEntity) level.getBlockEntity(scrubPos);
            var alarm = (AirAlarmBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.MACHINE));
            require(vent != null && scrub != null && alarm != null, "resolved fixture devices exist");
            BlockPos supplyPos = cabin.at(new BlockPos(5,5,3)), returnPos = cabin.at(new BlockPos(5,5,5));
            var supply = LoadedPipeComponent.discover(level, supplyPos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
            var returns = LoadedPipeComponent.discover(level, returnPos, VisualPipeBlockEntity.SECONDARY).orElseThrow();
            var supplyIds = supply.nodes().stream().map(LoadedPipeComponent.Node::incarnation).toList();
            var returnIds = returns.nodes().stream().map(LoadedPipeComponent.Node::incarnation).toList();
            require(supply.nodes().stream().noneMatch(n -> returnIds.contains(n.incarnation())), "supply/return UUIDs do not alias");
            require(supply.nodes().stream().anyMatch(n -> n.key().position().equals(cabin.at(ShuttleCabinFixture.PIPE))
                    && n.incarnation().equals(cabin.pipeIdentity())), "original cabin pipe identity remains in supply");
            require(scrub.config().settings().selectedLayer() == com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Layer.SECONDARY,
                    "scrubber uses authored Secondary return lane");
            require(alarm.deviceLinks().size() == 2 && alarm.deviceLinks().stream().anyMatch(link -> link.deviceId().equals(vent.deviceId()))
                    && alarm.deviceLinks().stream().anyMatch(link -> link.deviceId().equals(scrub.deviceId())), "alarm links exactly the two ventilation UUIDs");
            var handle = RigidBodyHandle.of(plot.subLevel());
            PowerFixtureReadiness.await(helper, level, 450,
                    () -> PowerRuntime.ventilationMeter(level, vent).known()
                            && PowerRuntime.ventilationMeter(level, vent).deliveredWatts() == vent.prototype().apcPowerReceiver().requestedWatts()
                            && PowerRuntime.ventilationMeter(level, scrub).known()
                            && PowerRuntime.ventilationMeter(level, scrub).deliveredWatts() == scrub.prototype().apcPowerReceiver().requestedWatts()
                            && PowerRuntime.airAlarmMeter(level, alarm).known()
                            && PowerRuntime.airAlarmMeter(level, alarm).deliveredWatts() == 100,
                    () -> "resolved 5W/5W/100W demand not paid", () -> {
                        require(vent.prototype().apcPowerReceiver().requestedWatts() == 5
                                && scrub.prototype().apcPowerReceiver().requestedWatts() == 5, "resolved device defaults are 5W");
                        require(vent.config().settings().externalPressureBoundKPa() == 101.325
                                && scrub.config().settings().volumeRateM3PerSecond() == .2, "inherited ventilation settings retained");
                        for (BlockPos cell : cabin.roomCells()) {
                            GasMixture current = AtmosphereService.INSTANCE.readAtmosphere(level, cell).orElseThrow().mixture();
                            EnumMap<GasType, Double> gases = new EnumMap<>(GasType.class);
                            gases.putAll(current.gasMoles());
                            gases.merge(GasType.CARBON_DIOXIDE, .25, Double::sum);
                            require(AtmosphereService.INSTANCE.initializeFiniteMixtureForTrustedSetup(level,
                                    cell, new GasMixture(gases, current.temperatureKelvin())),
                                    "trusted one-shot finite CO2 test charge");
                        }
                        require(new java.util.LinkedHashSet<>(supply.nodes().stream().map(LoadedPipeComponent.Node::key).toList())
                                .equals(new java.util.LinkedHashSet<>(cabin.supplyPipeNodes())), "all authored supply owners discovered");
                        require(new java.util.LinkedHashSet<>(returns.nodes().stream().map(LoadedPipeComponent.Node::key).toList())
                                .equals(new java.util.LinkedHashSet<>(cabin.returnPipeNodes())), "all authored return owners discovered");
                        CabinGasInventorySnapshot baseline = CabinGasInventorySnapshot.capture(level, cabin);
                        require(baseline.incarnations().get(new PipeTopology.NodeKey(cabin.at(ShuttleCabinFixture.PIPE),
                                PipeTopology.Layer.PRIMARY)).equals(cabin.pipeIdentity()),
                                "original cabin pipe identity unchanged at baseline");
                        Vector3d previousPose = plot.subLevel().logicalPose().position().get(new Vector3d());
                        long startedTick = level.getGameTime();
            double baselineRoomPressure = averageRoomPressure(level, cabin);
            handle.addLinearAndAngularVelocity(new Vector3d(FRAME_SPEED_BLOCKS_PER_SECOND,0,0), new Vector3d());
            BoundedGameTestWait.safeDelay(helper, 1, () -> {
                            var state = new Observation(baseline, previousPose, startedTick, baselineRoomPressure);
                            state.observationStartTick = level.getGameTime();
            BoundedGameTestWait.observeServerTicks(helper, OBSERVE_TICKS, event -> observe(helper, level, plot, cabin, scene, state),
                                    () -> {
                                        if (!state.complete) failObservation(helper, level, plot, cabin, state);
                                        else {
                                            state.baseline.assertConserved(CabinGasInventorySnapshot.capture(level, cabin),
                                                    CabinGasInventorySnapshot.precisionOperations(cabin,
                                                            level.getGameTime() - state.startTick));
                                            BoundedGameTestWait.succeedAfterObservation(helper);
                                        }
                                    });
                        });
                    });
        });
    }

    private static final class Observation {
        final CabinGasInventorySnapshot baseline;
        Vector3d previousPose;
        CabinGasInventorySnapshot previous;
        long startTick;
        long previousTick;
        boolean ventWhileMoving;
        boolean scrubWhileMoving;
        boolean complete;
        double lastPoseDelta;
        double lastPulseAxisDelta;
        final double baselineRoomPressure;
        final ArrayList<String> history = new ArrayList<>(OBSERVE_TICKS + 1);
        int movingTickCount, paidMovingTickCount, positiveGasTickCount;
        double maxPoseDelta;
        long observationStartTick;
        Observation(CabinGasInventorySnapshot baseline, Vector3d pose, long tick, double baselineRoomPressure) {
            this.baseline = baseline; previous = baseline; previousPose = pose; startTick = previousTick = tick;
            this.baselineRoomPressure = baselineRoomPressure;
            history.add("pulse tick=" + tick + " roomPressureKPa=" + baselineRoomPressure + " externalBoundKPa=101.325"
                    + " roomO2=" + baseline.roomInventory().moles(GasType.OXYGEN)
                    + " roomN2=" + baseline.roomInventory().moles(GasType.NITROGEN)
                    + " roomCO2=" + baseline.roomInventory().moles(GasType.CARBON_DIOXIDE)
                    + " totalCO2=" + (baseline.roomInventory().moles(GasType.CARBON_DIOXIDE)
                    + baseline.supplyInventory().moles(GasType.CARBON_DIOXIDE)
                    + baseline.returnInventory().moles(GasType.CARBON_DIOXIDE))
                    + " sourceSpecies=" + inventorySpecies(baseline.supplyInventory()));
        }
    }

    private static void observe(GameTestHelper helper, ServerLevel level, SableFixturePlot plot, ShuttleCabinFixture.Built cabin,
                                SableFixtureScene.Region scene, Observation state) {
        assertFrameOwned(level, plot, cabin, scene);
        CabinGasInventorySnapshot now;
        try { now = CabinGasInventorySnapshot.capture(level, cabin, state.baseline.incarnations()); }
        catch (GameTestAssertException failure) {
            var frame = plot.subLevel();
            var storage = cabin.at(ShuttleCabinFixture.PIPE);
            Vector3d worldProjection = frame.logicalPose().transformPosition(new Vector3d(storage.getX(), storage.getY(), storage.getZ()));
            boolean projectedChunkLoaded = level.getChunkSource().getChunk((int) Math.floor(worldProjection.x) >> 4,
                    (int) Math.floor(worldProjection.z) >> 4, ChunkStatus.FULL, false) instanceof LevelChunk;
            String detail = "frame=" + frame.getUniqueId() + ",removed=" + frame.isRemoved()
                    + ",containerExact=" + (SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) == frame)
                    + ",pose=" + frame.logicalPose().position() + ",storageProjection=" + worldProjection
                    + ",projectedChunkLoaded=" + projectedChunkLoaded
                    + ",currentTick=" + level.getGameTime() + ",previousTick=" + state.previousTick
                    + ",previousOwners=" + state.previous.incarnations().keySet().size()
                    + ",testDone=" + helper.testInfo.isDone();
            String message = failure.getMessage() + " observer{ " + detail + " }";
            throw new GameTestAssertException(message.substring(0, Math.min(900, message.length())));
        }
        Vector3d pose = plot.subLevel().logicalPose().position().get(new Vector3d());
        long tick = level.getGameTime();
        double operations = CabinGasInventorySnapshot.precisionOperations(cabin, tick - state.startTick);
        CabinGasInventorySnapshot previous = state.previous;
        state.lastPoseDelta = pose.distance(state.previousPose);
        state.lastPulseAxisDelta = pose.x - state.previousPose.x;
        boolean moving = state.lastPoseDelta > 1e-6 && state.lastPulseAxisDelta > MIN_PULSE_AXIS_DELTA_BLOCKS;
        double intervalOperations = CabinGasInventorySnapshot.precisionOperations(cabin, tick - state.previousTick);
        double intervalSupplyLoss = -speciesDelta(previous.supplyInventory(), now.supplyInventory(), GasType.OXYGEN)
                - speciesDelta(previous.supplyInventory(), now.supplyInventory(), GasType.NITROGEN);
        double intervalRoomGain = speciesDelta(previous.roomInventory(), now.roomInventory(), GasType.OXYGEN)
                + speciesDelta(previous.roomInventory(), now.roomInventory(), GasType.NITROGEN);
        double intervalSupplyBudget = previous.supplyInventory().moleBudget(GasType.OXYGEN, now.supplyInventory(), intervalOperations)
                + previous.supplyInventory().moleBudget(GasType.NITROGEN, now.supplyInventory(), intervalOperations);
        double intervalRoomBudget = previous.roomInventory().moleBudget(GasType.OXYGEN, now.roomInventory(), intervalOperations)
                + previous.roomInventory().moleBudget(GasType.NITROGEN, now.roomInventory(), intervalOperations);
        double intervalCo2Gain = speciesDelta(previous.returnInventory(), now.returnInventory(), GasType.CARBON_DIOXIDE);
        double intervalCo2Loss = -speciesDelta(previous.roomInventory(), now.roomInventory(), GasType.CARBON_DIOXIDE);
        double intervalReturnBudget = previous.returnInventory().moleBudget(GasType.CARBON_DIOXIDE, now.returnInventory(), intervalOperations);
        double intervalCo2RoomBudget = previous.roomInventory().moleBudget(GasType.CARBON_DIOXIDE, now.roomInventory(), intervalOperations);
        var ventMeter = PowerRuntime.ventilationMeter(level,
                (VentilationDeviceBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.VENT)));
        var scrubMeter = PowerRuntime.ventilationMeter(level,
                (VentilationDeviceBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.SCRUBBER)));
        boolean paid = ventMeter.known() && ventMeter.deliveredWatts() == 5
                && scrubMeter.known() && scrubMeter.deliveredWatts() == 5;
        state.ventWhileMoving |= moving && paid && intervalSupplyLoss > intervalSupplyBudget && intervalRoomGain > intervalRoomBudget;
        state.scrubWhileMoving |= moving && paid && intervalCo2Gain > intervalReturnBudget && intervalCo2Loss > intervalCo2RoomBudget;
        if (moving) state.movingTickCount++;
        if (moving && paid) state.paidMovingTickCount++;
        boolean positiveGas = intervalSupplyLoss > intervalSupplyBudget && intervalRoomGain > intervalRoomBudget
                || intervalCo2Gain > intervalReturnBudget && intervalCo2Loss > intervalCo2RoomBudget;
        if (moving && paid && positiveGas) state.positiveGasTickCount++;
        state.maxPoseDelta = Math.max(state.maxPoseDelta, state.lastPoseDelta);
        state.history.add("tick=" + tick + " fromTick=" + state.previousTick + " actualMoving=" + moving
                + " poseDelta=" + state.lastPoseDelta + " pulseAxisXDelta=" + state.lastPulseAxisDelta
                + " vent=" + ventMeter.known() + "/" + ventMeter.deliveredWatts()
                + " scrub=" + scrubMeter.known() + "/" + scrubMeter.deliveredWatts()
                + " supplyLoss=" + intervalSupplyLoss + "/" + intervalSupplyBudget
                + " roomGain=" + intervalRoomGain + "/" + intervalRoomBudget
                + " co2ReturnGain=" + intervalCo2Gain + "/" + intervalReturnBudget
                + " co2RoomLoss=" + intervalCo2Loss + "/" + intervalCo2RoomBudget
                + " ventSample{" + PowerRuntime.ventilationMeterDiagnostic(level,
                (VentilationDeviceBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.VENT))) + "}"
                + " scrubSample{" + PowerRuntime.ventilationMeterDiagnostic(level,
                (VentilationDeviceBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.SCRUBBER))) + "}"
                + " solve{" + PowerRuntime.lastSolveDiagnostic(level) + "}");
        // Positive-flow acceptance is proven by the strict paid, pulse-axis-moving per-interval deltas above;
        // this separate whole-cabin budget permits ordinary rounding across all atmosphere operations.
        try { state.baseline.assertConserved(now, operations); }
        catch (GameTestAssertException failure) {
            LOGGER.warn("moving ventilation per-sample diagnostic history: {}", state.history);
            throw failure;
        }
        state.previous = now; state.previousPose = pose; state.previousTick = tick;
        if (!state.complete && state.ventWhileMoving && state.scrubWhileMoving) state.complete = true;
    }

    private static double averageRoomPressure(ServerLevel level, ShuttleCabinFixture.Built cabin) {
        double total = 0;
        for (BlockPos cell : cabin.roomCells())
            total += AtmosphereService.INSTANCE.readAtmosphere(level, cell).orElseThrow().mixture().pressureKpa();
        return total / cabin.roomCells().size();
    }

    private static String inventorySpecies(com.juicyslew.moonstation14.gametest.support.AtmospherePrecisionBudget.Inventory inventory) {
        EnumMap<GasType, Double> values = new EnumMap<>(GasType.class);
        for (GasType gas : GasType.values()) values.put(gas, inventory.moles(gas));
        return values.toString();
    }

    private static void assertFrameOwned(ServerLevel level, SableFixturePlot plot, ShuttleCabinFixture.Built cabin,
                                         SableFixtureScene.Region scene) {
        var frame = plot.subLevel();
        if (frame.isRemoved() || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame)
            throw new GameTestAssertException("moving cabin frame left live container: removed=" + frame.isRemoved()
                    + " id=" + frame.getUniqueId());
        BlockPos origin = cabin.at(BlockPos.ZERO);
        var pose = frame.logicalPose();
        Vector3d[] corners = new Vector3d[8];
        int index = 0;
        for (int x : new int[]{0, ShuttleCabinFixture.WIDTH})
            for (int y : new int[]{0, ShuttleCabinFixture.HEIGHT})
                for (int z : new int[]{0, ShuttleCabinFixture.DEPTH})
                    corners[index++] = pose.transformPosition(new Vector3d(origin.getX() + x, origin.getY() + y,
                            origin.getZ() + z));
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Vector3d corner : corners) {
            minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y); maxZ = Math.max(maxZ, corner.z);
        }
        AABB worldBounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        AABB sceneBounds = new AABB(scene.min.getX(), scene.min.getY(), scene.min.getZ(),
                scene.max.getX() + 1.0, scene.max.getY() + 1.0, scene.max.getZ() + 1.0);
        if (minX < sceneBounds.minX || minY < sceneBounds.minY || minZ < sceneBounds.minZ
                || maxX > sceneBounds.maxX || maxY > sceneBounds.maxY || maxZ > sceneBounds.maxZ)
            throw new GameTestAssertException("moving cabin world bounds left owned scene voxel volume: bounds="
                    + worldBounds + " sceneVolume=" + sceneBounds);
        int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY), z0 = (int) Math.floor(minZ);
        int x1 = (int) Math.ceil(maxX) - 1, y1 = (int) Math.ceil(maxY) - 1, z1 = (int) Math.ceil(maxZ) - 1;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!scene.tickets.entityTicking(pos))
                throw new GameTestAssertException("moving cabin world bounds left owned entity-ticking scene: bounds="
                        + worldBounds + " escaped=" + pos + " scene=" + scene.min + ".." + scene.max);
        }
    }

    private static double speciesDelta(com.juicyslew.moonstation14.gametest.support.AtmospherePrecisionBudget.Inventory before,
            com.juicyslew.moonstation14.gametest.support.AtmospherePrecisionBudget.Inventory after, GasType gas) {
        return after.moles(gas) - before.moles(gas);
    }

    private static void failObservation(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            ShuttleCabinFixture.Built cabin, Observation state) {
        var end = CabinGasInventorySnapshot.capture(level, cabin);
        LOGGER.warn("moving ventilation per-sample diagnostic history: {}", state.history);
        String message = "moving paid ventilation incomplete: sourceDelta="
                + (state.baseline.supplyInventory().moles(GasType.OXYGEN)-end.supplyInventory().moles(GasType.OXYGEN))
                + " roomDelta=" + (end.roomInventory().moles(GasType.OXYGEN)-state.baseline.roomInventory().moles(GasType.OXYGEN))
                + " returnCO2Delta=" + (end.returnInventory().moles(GasType.CARBON_DIOXIDE)-state.baseline.returnInventory().moles(GasType.CARBON_DIOXIDE))
                + " ventPower=" + PowerRuntime.ventilationMeter(level, (VentilationDeviceBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.VENT)))
                + " scrubPower=" + PowerRuntime.ventilationMeter(level, (VentilationDeviceBlockEntity) level.getBlockEntity(cabin.at(ShuttleCabinFixture.SCRUBBER)))
                + " frame=" + state.previousPose + " lastFrameDelta=" + state.lastPoseDelta
                + " lastPulseAxisXDelta=" + state.lastPulseAxisDelta
                + " ticks=" + state.previousTick + " pulseTick=" + state.startTick + " observeStart=" + state.observationStartTick
                + " moving/paidMoving/positiveGas=" + state.movingTickCount + "/" + state.paidMovingTickCount
                + "/" + state.positiveGasTickCount + " maxPoseDelta=" + state.maxPoseDelta
                + " baselineRoomPressureKPa=" + state.baselineRoomPressure
                + " flags=" + state.ventWhileMoving + "/" + state.scrubWhileMoving;
        throw new GameTestAssertException(message.substring(0, Math.min(900, message.length())));
    }

    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
