package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

/** Proves SELF_HARVEST through normal registered tray ticks and world output publication. */
@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-genetics", "botany-auto-harvest", "botany-scream"})
public final class PlantAutoHarvestGameTests {
    private PlantAutoHarvestGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 4000)
    @GameTestTags({"botany-seed-reference"})
    public static void ordinaryTrayTicksPublishAndRepeatSelfHarvestOutputs(GameTestHelper helper) {
        runOrdinaryAutoHarvest(helper, 6, 6, true);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 4000)
    @GameTestTags({"botany-seed-reference"})
    public static void ordinaryTrayTicksPublishAndRepeatZeroYieldMinimumOutputs(GameTestHelper helper) {
        runOrdinaryAutoHarvest(helper, 0, 1, true);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 4000)
    @GameTestTags({"botany-seed-reference"})
    public static void disabledScreamDoesNotSuppressAutomaticHarvest(GameTestHelper helper) {
        runOrdinaryAutoHarvest(helper, 6, 6, false);
    }

    private static void runOrdinaryAutoHarvest(GameTestHelper helper, int yield, int expectedOutputs, boolean screamEnabled) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        AtomicBoolean finished = new AtomicBoolean();
        var screamCount = new AtomicInteger();
        var screamEvent = SeedProfile.Scream.DEFAULT_SOUND;
        final RegisteredServerPlayerFixture[] viewer = new RegisteredServerPlayerFixture[1];
        BotanyTrayGameTestEnvironment.prepare(helper, tray -> {
            try {
                viewer[0] = RegisteredServerPlayerFixture.register(level, "auto-harvest");
            } catch (Throwable failure) { fail(helper, finished, failure, screamCount, viewer); return; }
            helper.runAfterDelay(2, () -> {
                try {
                    BlockPos trayPos = tray.getBlockPos();
                    var ready = RegisteredPlayerReadyDriver.readyAndQuery(viewer[0]);
                    ready.body().setPos(trayPos.getX() + .5, trayPos.getY() + 3, trayPos.getZ() + .5);
                    require(level.noCollision(ready.body(), ready.body().getBoundingBox()),
                            "registered audio viewer body box has no collision from solid chamber roof");
                    viewer[0].player().setCamera(ready.body());
                    require(tray != null && tray.plantState() != null, "resolved ordinary tray initialized");
                    var id = ModSeedProfiles.seedId("wheat");
                    var base = ModSeedProfiles.serverLookup(id);
                    require(base != null, "resolved wheat profile exists");
                    var old = base.lifecycle();
                    var life = new SeedProfile.Lifecycle(old.growthStages(), old.lifespan(), 1f, 0.1f,
                            yield, old.potency(), SeedProfile.Harvest.SELF_HARVEST, old.viable(), old.seedless());
                    var arranged = new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(), life,
                            base.chemicals(), base.consumeGasses(), base.exudeGasses(), base.mutationTargets(),
                            new SeedProfile.Scream(screamEnabled, screamEvent, -10f), base.sourceName());
                    var atmosphere = AtmosphereService.INSTANCE.sample(level, trayPos.above()).orElseThrow();
                    var environment = new PlantMetabolismEvaluator.Environment((float) atmosphere.pressureKpa(1.0),
                            (float) atmosphere.temperatureKelvin());
                    var warnings = PlantMetabolismEvaluator.improperEnvironment(arranged, environment);
                    require(Float.isFinite(environment.pressureKpa()) && Float.isFinite(environment.temperatureK())
                                    && !warnings.pressure() && !warnings.temperature(),
                            "ordinary tray atmosphere outside wheat biology bounds at cell=" + trayPos.above()
                                    + " tick=" + level.getGameTime() + ": pressureKPa=" + environment.pressureKpa()
                                    + " expected=[" + arranged.biology().lowPressure() + "," + arranged.biology().highPressure()
                                    + "] temperatureK=" + environment.temperatureK() + " expected=["
                                    + (arranged.biology().idealHeat() - arranged.biology().heatTolerance()) + ","
                                    + (arranged.biology().idealHeat() + arranged.biology().heatTolerance()) + "]");
                     var arrangedGenetics = new GeneticValue(id, arranged);
                     tray.commitPlant(arrangedGenetics, level.getGameTime());
                     var plant = tray.plantState();
                     plant.ensureUniqueSeed();
                     var capturedSeedReference = plant.seedReference();
                     require(capturedSeedReference != null && capturedSeedReference.unique(),
                             "planted source seed reference is unique before automatic harvest");
                     int budget = Math.min(3600, Math.max(1800, tray.resolvedTrayProfile().cycleDelayTicks() * 7 + 400));
                      awaitFirstReady(helper, level, tray, trayPos, finished, arrangedGenetics, capturedSeedReference, expectedOutputs, budget, 0, screamCount, viewer);
                 } catch (Throwable failure) { fail(helper, finished, failure, screamCount, viewer); }
            });
        });
    }


    private static void awaitFirstReady(GameTestHelper helper, ServerLevel level, HydroponicsTrayBlockEntity tray,
            BlockPos pos, AtomicBoolean finished, GeneticValue genetics, PlantSeedReference capturedSeedReference, int expectedOutputs, int budget, int elapsed,
            AtomicInteger screamCount, RegisteredServerPlayerFixture[] viewer) {
        if (finished.get()) return;
        BoundedGameTestWait.safeDelay(helper, 20, () -> {
            try {
                var plant = tray.plantState();
                if (plant.ready()) {
                    require(tray.autoHarvestOutcome() == HydroponicsTrayBlockEntity.AutoHarvestOutcome.NONE,
                            "newly-ready plant waits until next actual cycle");
                    int before = outputs(level, pos);
                    long readyCycle = plant.lastCycle();
                    awaitHarvest(helper, level, tray, pos, finished, genetics, capturedSeedReference, expectedOutputs, budget, elapsed, readyCycle, before, 0, screamCount, viewer);
                    return;
                }
                if (elapsed >= budget) throw new AssertionError("readiness timeout budget=" + budget + ": age=" + plant.age() + " health="
                        + plant.health() + " cycle=" + plant.lastCycle() + " status=" + plant.status() + " atmosphere="
                        + AtmosphereService.INSTANCE.sample(level, pos.above()).orElse(null));
                awaitFirstReady(helper, level, tray, pos, finished, genetics, capturedSeedReference, expectedOutputs, budget, elapsed + 20, screamCount, viewer);
            } catch (Throwable failure) { fail(helper, finished, failure, screamCount, viewer); }
        });
    }

    private static void awaitHarvest(GameTestHelper helper, ServerLevel level, HydroponicsTrayBlockEntity tray,
            BlockPos pos, AtomicBoolean finished, GeneticValue genetics, PlantSeedReference capturedSeedReference, int expectedOutputs, int budget,
            int elapsed, long previousCycle, int previousOutputs, int successes,
            AtomicInteger screamCount, RegisteredServerPlayerFixture[] viewer) {
        if (finished.get()) return;
        BoundedGameTestWait.safeDelay(helper, 20, () -> {
            try {
                var plant = tray.plantState();
                int count = outputs(level, pos);
                collectScreamPackets(viewer[0], pos, screamCount);
                if (tray.autoHarvestOutcome() == HydroponicsTrayBlockEntity.AutoHarvestOutcome.SUCCESS
                        && count > previousOutputs) {
                    require(count - previousOutputs == expectedOutputs, "one ordinary automatic batch publishes expected separate produce entities=" + expectedOutputs);
                    var drops = level.getEntitiesOfClass(LooseItemEntity.class,
                            new net.minecraft.world.phys.AABB(pos).inflate(3, 2, 3));
                    require(drops.size() == count && drops.stream().allMatch(drop -> {
                        var outputGenetics = drop.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get());
                         return genetics.equals(outputGenetics)
                                 && drop.getItem().get(ModDataComponents.PLANT_SEED_REFERENCE.get()) == capturedSeedReference;
                     }), "every actual output carries arranged genetics and the exact captured seed reference");
                    require(plant.seed() != null && plant.genetics().profile().lifecycle().harvest() == SeedProfile.Harvest.SELF_HARVEST,
                            "source plant and SELF_HARVEST genetics remain after output");
                    require(plant.seedReference() == capturedSeedReference && !capturedSeedReference.unique(),
                            "self-harvest preserves attached source reference while marking it nonunique");
                    require(!plant.ready() && plant.lastProduceAge() == plant.age(), "successful source commit clears readiness at current age");
                    int completed = successes + 1;
                    if (completed == 2) {
                        int expectedScreams = genetics.profile().scream().enabled() ? 2 : 0;
                        require(screamCount.get() == expectedScreams,
                                "committed auto harvest screams match resolved capability expected=" + expectedScreams + " actual=" + screamCount.get());
                        finish(helper, finished, null, screamCount, viewer); return;
                    }
                      awaitNextReady(helper, level, tray, pos, finished, genetics, capturedSeedReference, expectedOutputs, budget, elapsed, plant.lastCycle(), count, completed, screamCount, viewer);
                    return;
                }
                if (tray.plantState().lastCycle() > previousCycle && tray.plantState().ready()
                        && tray.autoHarvestOutcome() == HydroponicsTrayBlockEntity.AutoHarvestOutcome.NONE) {
                    // It became ready on this cycle; output is only eligible on a later cycle.
                      awaitHarvest(helper, level, tray, pos, finished, genetics, capturedSeedReference, expectedOutputs, budget, elapsed + 20, tray.plantState().lastCycle(), previousOutputs, successes, screamCount, viewer);
                    return;
                }
                if (elapsed >= budget) throw new AssertionError("automatic output timeout budget=" + budget + ": outcome=" + tray.autoHarvestOutcome()
                        + " age=" + plant.age() + " health=" + plant.health() + " ready=" + plant.ready()
                        + " lastProduce=" + plant.lastProduceAge() + " outputs=" + count + " cycle=" + plant.lastCycle());
                  awaitHarvest(helper, level, tray, pos, finished, genetics, capturedSeedReference, expectedOutputs, budget, elapsed + 20, previousCycle, previousOutputs, successes, screamCount, viewer);
            } catch (Throwable failure) { fail(helper, finished, failure, screamCount, viewer); }
        });
    }

    private static void awaitNextReady(GameTestHelper helper, ServerLevel level, HydroponicsTrayBlockEntity tray,
            BlockPos pos, AtomicBoolean finished, GeneticValue genetics, PlantSeedReference capturedSeedReference, int expectedOutputs, int budget,
            int elapsed, long cycle, int outputs, int successes,
            AtomicInteger screamCount, RegisteredServerPlayerFixture[] viewer) {
        if (finished.get()) return;
        BoundedGameTestWait.safeDelay(helper, 20, () -> {
            try {
                var plant = tray.plantState();
                if (plant.ready() && plant.lastCycle() > cycle) {
                     awaitHarvest(helper, level, tray, pos, finished, genetics, capturedSeedReference, expectedOutputs, budget, elapsed, plant.lastCycle(), outputs, successes, screamCount, viewer);
                    return;
                }
                if (elapsed >= budget) throw new AssertionError("repeat readiness timeout budget=" + budget + ": age=" + plant.age()
                        + " health=" + plant.health() + " lastProduce=" + plant.lastProduceAge() + " cycle=" + plant.lastCycle()
                        + " atmosphere=" + AtmosphereService.INSTANCE.sample(level, pos.above()).orElse(null));
                  awaitNextReady(helper, level, tray, pos, finished, genetics, capturedSeedReference, expectedOutputs, budget, elapsed + 20, cycle, outputs, successes, screamCount, viewer);
             } catch (Throwable failure) { fail(helper, finished, failure, screamCount, viewer); }
        });
    }

    private static int outputs(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(LooseItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(3, 2, 3)).size();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void fail(GameTestHelper helper, AtomicBoolean finished, Throwable failure, AtomicInteger screamCount,
                             RegisteredServerPlayerFixture[] viewer) { finish(helper, finished, failure, screamCount, viewer); }
    private static void finish(GameTestHelper helper, AtomicBoolean finished, Throwable failure, AtomicInteger screamCount,
                               RegisteredServerPlayerFixture[] viewer) {
        if (!finished.compareAndSet(false, true)) return;
        if (viewer[0] != null) viewer[0].close();
        if (failure == null) helper.succeed(); else helper.testInfo.fail(failure);
    }
    private static void collectScreamPackets(RegisteredServerPlayerFixture fixture, BlockPos pos, AtomicInteger count) {
        var expectedCoordinates = new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(BuiltInRegistries.SOUND_EVENT.get(SeedProfile.Scream.DEFAULT_SOUND)),
                net.minecraft.sounds.SoundSource.BLOCKS, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                HydroponicsTrayBlockEntity.screamGain(-10f), 1f, 0L);
        Object packet;
        while ((packet = fixture.pollOutbound()) != null) {
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound
                    && sound.getSound().value() == BuiltInRegistries.SOUND_EVENT.get(SeedProfile.Scream.DEFAULT_SOUND)
                    && sound.getSource() == net.minecraft.sounds.SoundSource.BLOCKS
                    && sound.getX() == expectedCoordinates.getX() && sound.getY() == expectedCoordinates.getY()
                    && sound.getZ() == expectedCoordinates.getZ()
                    && Math.abs(sound.getVolume() - HydroponicsTrayBlockEntity.screamGain(-10f)) < 1e-6)
                count.incrementAndGet();
        }
    }
}
