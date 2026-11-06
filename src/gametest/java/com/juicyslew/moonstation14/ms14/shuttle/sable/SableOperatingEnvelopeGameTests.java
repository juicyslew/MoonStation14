package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Paid authored cabin proof in the intended near-base and approximately 5 km stock-precision envelope. */
@PrefixGameTestTemplate(false)
public final class SableOperatingEnvelopeGameTests {
    private static final int SCENE_Y = 300;

    private SableOperatingEnvelopeGameTests() { }

    @GameTestTags({"shuttle", "sable-operating-envelope"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "sable_envelope", timeoutTicks = 1400)
    public static void paidCabinMovesFromNearBaseToFiveKilometerRadius(GameTestHelper helper) {
        provePaidCabin(helper, new BlockPos(200, SCENE_Y, 200), "near-base",
                () -> provePaidCabin(helper, new BlockPos(5200, SCENE_Y, 200), "five-kilometer-radius", helper::succeed));
    }

    private static void provePaidCabin(GameTestHelper helper, BlockPos worldAnchor, String scenario, Runnable completed) {
        ServerLevel level = helper.getLevel();
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, worldAnchor,
                ShuttleCabinFixture.WIDTH, ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
        SableFixtureScene.awaitEmpty(helper, scene, 40, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            SableFixturePlot plot = null;
            try {
                Vector3d requested = new Vector3d(worldAnchor.getX() + .5, worldAnchor.getY(), worldAnchor.getZ() + .5);
                var authored = SableFixturePlot.allocateAndAuthor(level, requested, building -> {
                    BlockPos storage = building.storageOrigin();
                    building.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                            ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                    return ShuttleCabinFixture.build(level, storage);
                });
                plot = authored.plot();
                SableFixtureScene.trackPlot(helper, plot);
                ShuttleCabinFixture.Built cabin = authored.result();

                BlockPos doorPos = cabin.at(ShuttleCabinFixture.AIRLOCK);
                BlockPos machinePos = cabin.at(ShuttleCabinFixture.MACHINE);
                BlockPos pipePos = cabin.at(ShuttleCabinFixture.PIPE);
                AirlockBlockEntity door = (AirlockBlockEntity) level.getBlockEntity(doorPos);
                AirAlarmBlockEntity machine = (AirAlarmBlockEntity) level.getBlockEntity(machinePos);
                VisualPipeBlockEntity pipe = (VisualPipeBlockEntity) level.getBlockEntity(pipePos);
                require(door != null && door.prototype() != null && machine != null && machine.prototype() != null && pipe != null,
                        scenario + ": existing authored builder creates resolved live cabin owners");
                var doorId = door.identity();
                var pipeBefore = LoadedPipeGasOwner.activeSnapshot(level, pipePos, VisualPipeBlockEntity.PRIMARY).orElseThrow();
                var roomCell = cabin.roomCells().getFirst();
                var roomBefore = AtmosphereService.INSTANCE.readAtmosphere(level, roomCell).orElseThrow().mixture();
                var engineId = plot.engineId();
                var handle = RigidBodyHandle.of(plot.subLevel());

                // The synchronous allocation/author/finalize factory returned only after native placement seeding.
                requireWorldNear(plot.worldOrigin(), requested, .002,
                        scenario + ": finalized native placement preserves its authored world anchor");

                SableFixturePlot finalPlot = plot;
                BoundedGameTestWait.await(helper, 80,
                        () -> scenario + ": native body not ready for " + engineId,
                        handle::isValid, () -> PowerFixtureReadiness.await(helper, level, 500,
                                () -> PowerRuntime.airAlarmMeter(level, machine).known()
                                        && PowerRuntime.airAlarmMeter(level, machine).deliveredWatts() == 100
                                        && PowerRuntime.airlockPower(level, door).known()
                                        && PowerRuntime.airlockPower(level, door).deliveredWatts() == 20,
                                () -> "alarm=" + PowerRuntime.airAlarmMeter(level, machine)
                                        + ", door=" + PowerRuntime.airlockPower(level, door), () -> {
                                    try {
                                        Vector3d before = finalPlot.worldOrigin();
                                        requireWorldNear(before, requested, 2.0,
                                                scenario + ": cabin remains in the operational envelope after power readiness");
                                        // One bounded native diagnostic impulse lifts clear of the authored pad;
                                        // both horizontal axes remain required observable motion, not gravity-only drift.
                                        handle.addLinearAndAngularVelocity(new Vector3d(2, 2, 2), new Vector3d());
                                        Vector3d admitted = handle.getLinearVelocity(new Vector3d());
                                        require(admitted.x > .1 && admitted.y > .1 && admitted.z > .1,
                                                scenario + ": engine admits positive native XYZ velocity " + admitted);
                                        BoundedGameTestWait.safeDelay(helper, 4, () -> {
                                            try {
                                                Vector3d after = finalPlot.worldOrigin();
                                                requireWorldNear(after, requested, 2.0,
                                                        scenario + ": world-projected cabin pose stays within operational short-flight envelope");
                                                require(after.x > before.x + .02 && after.z > before.z + .02,
                                                        scenario + ": cabin has positive native horizontal X/Z movement; delta="
                                                                + new Vector3d(after).sub(before));
                                                require(finalPlot.subLevel().getUniqueId().equals(engineId)
                                                                && dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                                                                .getContainer(level).getSubLevel(engineId) == finalPlot.subLevel(),
                                                        scenario + ": same native craft UUID and sublevel remain live");
                                                require(level.getBlockEntity(doorPos) == door && door.identity().equals(doorId)
                                                                && level.getBlockEntity(machinePos) == machine
                                                                && level.getBlockEntity(pipePos) == pipe,
                                                        scenario + ": airlock, paid alarm, and pipe owners retain storage identity");
                                                require(PowerRuntime.airAlarmMeter(level, machine).known()
                                                                && PowerRuntime.airAlarmMeter(level, machine).deliveredWatts() == 100
                                                                && PowerRuntime.airlockPower(level, door).known()
                                                                && PowerRuntime.airlockPower(level, door).deliveredWatts() == 20,
                                                        scenario + ": real power owners retain 100 W / 20 W delivery; alarm="
                                                                + PowerRuntime.airAlarmMeter(level, machine) + ", door="
                                                                + PowerRuntime.airlockPower(level, door) + ", doorDiagnostic="
                                                                + PowerRuntime.airlockPowerDiagnostic(level, door) + ", graph="
                                                                + PowerGraphService.diagnosticSnapshot(level));
                                                require(LoadedPipeGasOwner.activeSnapshot(level, pipePos, VisualPipeBlockEntity.PRIMARY)
                                                                .map(now -> now.incarnation().equals(pipeBefore.incarnation())
                                                                        && now.mixture().equals(pipeBefore.mixture())).orElse(false),
                                                        scenario + ": finite pipe mixture and owner UUID remain unchanged");
                                                var roomAfter = AtmosphereService.INSTANCE.readAtmosphere(level, roomCell)
                                                        .orElseThrow().mixture();
                                                require(roomAfter.gasMoles().equals(roomBefore.gasMoles())
                                                                && roomAfter.temperatureKelvin() == roomBefore.temperatureKelvin(), scenario
                                                        + ": finite room atmosphere remains at its authored storage cells; before="
                                                        + roomBefore.gasMoles() + "@" + roomBefore.temperatureKelvin()
                                                        + ", after=" + roomAfter.gasMoles() + "@" + roomAfter.temperatureKelvin());
                                            } finally {
                                                // Keep the first plot allocated until the whole GameTest ends:
                                                // pipe SavedData is keyed by storage position and outlives plot removal.
                                                SableFixtureScene.clearStoneSupportPad(level, scene);
                                            }
                                            completed.run();
                                        });
                                    } catch (RuntimeException | Error failure) {
                                        SableFixtureScene.clearStoneSupportPad(level, scene);
                                        throw failure;
                                    }
                                }));
            } catch (RuntimeException | Error failure) {
                SableFixtureScene.clearStoneSupportPad(level, scene);
                throw failure;
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static void requireWorldNear(Vector3d actual, Vector3d expected, double maxDistance, String message) {
        require(Double.isFinite(actual.x) && Double.isFinite(actual.y) && Double.isFinite(actual.z)
                        && actual.distance(expected) <= maxDistance,
                message + ": projected=" + actual + ", expected=" + expected
                        + ", delta=" + new Vector3d(actual).sub(expected));
    }
}
