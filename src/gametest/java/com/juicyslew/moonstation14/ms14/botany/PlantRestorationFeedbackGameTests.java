package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-care", "botany-restoration-feedback"})
public final class PlantRestorationFeedbackGameTests {
    private PlantRestorationFeedbackGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 4000)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining", "merge-main-stage-two"})
    public static void successfulOrdinaryTrayRestorationPollSendsTrackedActionbar(GameTestHelper helper) {
        run(helper, 0);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 4000)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining"})
    public static void failedProbabilityPollSendsNoRestorationFeedback(GameTestHelper helper) {
        run(helper, 1);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 4000)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining"})
    public static void alreadySeededEligiblePollSendsNoRestorationFeedback(GameTestHelper helper) {
        run(helper, 2);
    }

    private static void run(GameTestHelper helper, int testCase) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BotanyTrayGameTestEnvironment.prepare(helper, tray -> {
            RegisteredServerPlayerFixture fixture = null;
            try {
                fixture = RegisteredServerPlayerFixture.register(level, "restoration-viewer");
                fixture.enableChunkBatchAcknowledgements();
                var registeredFixture = fixture;
                helper.runAfterDelay(2, () -> {
                    try {
                        var active = RegisteredPlayerReadyDriver.readyAndQuery(registeredFixture);
                        var body = active.body();
                        body.teleportTo(level, tray.getBlockPos().getX() + .5, tray.getBlockPos().getY(),
                                tray.getBlockPos().getZ() + .5, Set.<RelativeMovement>of(),
                                body.getYRot(), body.getXRot());
                        registeredFixture.player().setCamera(body);
                        drain(registeredFixture);
                        awaitViewer(helper, registeredFixture, body, tray, testCase, 0);
                    } catch (Throwable failure) {
                        registeredFixture.close();
                        helper.testInfo.fail(failure);
                    }
                });
            } catch (Throwable failure) {
                if (fixture != null) fixture.close();
                helper.testInfo.fail(failure);
            }
        });
    }

    private static void awaitViewer(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                    net.minecraft.world.entity.LivingEntity body,
                                    HydroponicsTrayBlockEntity tray, int testCase, int waited) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BoundedGameTestWait.safeDelay(helper, 1, () -> {
            try {
                // Polling outbound processes real chunk-batch receipts and advances native chunk ACKs.
                drain(fixture);
                var viewers = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(tray.getBlockPos()), false);
                if (viewers.contains(fixture.player())) {
                    prepareAndPoll(helper, fixture, tray, testCase);
                    return;
                }
                if (waited >= 300) {
                    var player = fixture.player();
                    var targetChunk = new ChunkPos(tray.getBlockPos());
                    var targetHolder = level.getChunkSource().chunkMap.getChunkToSend(ChunkPos.asLong(targetChunk.x, targetChunk.z));
                    var batches = fixture.chunkBatchProgress();
                    var camera = player.getCamera();
                    var view = com.juicyslew.moonstation14.ms14.medical_observer.ObserverTracking.viewPosition(player);
                    throw new AssertionError("viewer wait=" + waited + " trayChunk=" + targetChunk
                            + " trackingView=" + view + " bodyChunk=" + body.chunkPosition()
                            + " nativePlayerChunk=" + player.chunkPosition() + " viewers=" + viewers.size()
                            + " trackingContains=" + player.getChunkTrackingView().contains(
                                    new ChunkPos(tray.getBlockPos()).x, new ChunkPos(tray.getBlockPos()).z)
                             + " targetPending=" + player.connection.chunkSender.isPending(
                                     ChunkPos.asLong(targetChunk.x, targetChunk.z))
                             + " targetSendReady=" + (targetHolder != null)
                             + " batches=" + batches.sent() + "/" + batches.received() + "/" + batches.acknowledged()
                            + " cameraMatchesBody=" + (camera == body)
                            + " player=" + player.getUUID() + " playerPos=" + player.position()
                            + " cameraPos=" + (camera == null ? "null" : camera.position()));
                }
                awaitViewer(helper, fixture, body, tray, testCase, waited + 1);
            } catch (Throwable failure) {
                fixture.close();
                helper.testInfo.fail(failure);
            }
        });
    }

    private static void prepareAndPoll(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                       HydroponicsTrayBlockEntity tray, int testCase) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        var seedId = ModSeedProfiles.seedId("wheat");
        var resolved = ModSeedProfiles.serverLookup(seedId);
        if (resolved == null) throw new AssertionError("resolved wheat profile unavailable");
        var life = resolved.lifecycle();
        var lifecycle = new SeedProfile.Lifecycle(life.growthStages(), life.lifespan(), life.maturation(), life.production(),
                life.yield(), 29, life.harvest(), life.viable(), testCase != 2);
        var seedless = new SeedProfile(resolved.packet(), resolved.product(), resolved.visualKey(), resolved.biology(), lifecycle,
                resolved.chemicals(), resolved.consumeGasses(), resolved.exudeGasses(), resolved.mutationTargets(), resolved.scream(), resolved.sourceName());
        tray.commitPlant(new GeneticValue(seedId, seedless), level.getGameTime());
        PlantInstance plant = tray.plantState();
        plant.state(plant.age(), 10f, plant.lastProduceAge(), plant.skipAging(), false, false);
        plant.markSeedShared();
        PlantSeedReference oldReference = plant.seedReference();
        var oldGenetics = oldReference.current();
        var sedin = ModReagents.createKey("sedin");
        var soil = new ReagentAttachment(tray.soilSolution().toComponent());
        require(soil.admitUnits(sedin, 1000, tray.resolvedTrayProfile().soilCapacityCents()) == 1000,
                "arrange 1000 cents of sedin in actual tray soil");
        require(tray.tryReplaceSoil(tray.placementIdentity(), plant, plant.revision(), tray.soilSolution(), soil),
                "tray accepts restoration-test reagent");
        drain(fixture);
        // Keep autonomous ticking from consuming the case; the selected time is a legitimate future poll clock.
        plant.updatePoll(level.getGameTime() + 10000);
        long selected = selectTime(level, tray.getBlockPos(), testCase != 1);
        waitForPollClock(helper, fixture, tray, testCase, selected, 0, oldReference, oldGenetics);
    }

    private static void waitForPollClock(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                         HydroponicsTrayBlockEntity tray, int testCase, long target, int waited,
                                         PlantSeedReference oldReference, GeneticValue oldGenetics) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        PlantInstance plant = tray.plantState();
        BoundedGameTestWait.safeDelay(helper, target <= level.getGameTime() ? 1 : target - level.getGameTime(), () -> {
            try {
                long now = level.getGameTime();
                double sample = HydroponicsTrayBlockEntity.probabilitySample(now ^ tray.getBlockPos().asLong()
                        ^ ((long) level.dimension().location().hashCode() << 32));
                if ((testCase == 1 ? sample < .2d : sample >= .2d) && waited < 3000) {
                    waitForPollClock(helper, fixture, tray, testCase, now + 1, waited + 1, oldReference, oldGenetics);
                    return;
                }
                require(waited < 3000, "bounded wait for legitimate chance clock");
                plant.updatePoll(now);
                HydroponicsTrayBlockEntity.tick(level, tray.getBlockPos(), level.getBlockState(tray.getBlockPos()), tray);
                var packets = drain(fixture);
                var restorationPackets = packets.stream().filter(ClientboundSetActionBarTextPacket.class::isInstance)
                        .map(ClientboundSetActionBarTextPacket.class::cast)
                        .filter(packet -> packet.text().getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals("botany-plant-seedsrestored")).toList();
                long restorationCount = restorationPackets.size();
                if (testCase == 0) {
                    require(restorationCount == 1, "tracked viewer receives exactly one real restoration actionbar packet");
                    require(restorationPackets.getFirst().text().getStyle().getColor() != null
                                    && restorationPackets.getFirst().text().getStyle().getColor().getValue() == ChatFormatting.GRAY.getColor(),
                            "restoration actionbar packet carries the source Small/gray visual style");
                    require(!plant.genetics().profile().lifecycle().seedless(), "restored genetics commit before feedback observation");
                    require(plant.seedReference() != oldReference && plant.seedReference().unique(), "poll detaches shared reference into unique genetics");
                    require(oldReference.current().equals(oldGenetics) && oldReference.current().profile().lifecycle().seedless(),
                            "old shared reference remains unchanged and seedless");
                } else {
                    require(restorationCount == 0, "failed probability poll sends no restoration actionbar");
                    require(plant.genetics().profile().lifecycle().seedless() == (testCase == 1),
                            testCase == 1 ? "failed probability leaves plant seedless" : "already-seeded plant remains seeded");
                }
                fixture.close();
                helper.succeed();
            } catch (Throwable failure) {
                fixture.close();
                helper.testInfo.fail(failure);
            }
        });
    }

    private static long selectTime(ServerLevel level, BlockPos pos, boolean success) {
        long start = level.getGameTime() + 2;
        for (long time = start; time < start + 1000; time++) {
            double sample = HydroponicsTrayBlockEntity.probabilitySample(time ^ pos.asLong()
                    ^ ((long) level.dimension().location().hashCode() << 32));
            if (success ? sample < .2d : sample >= .2d) return time;
        }
        throw new AssertionError("unable to select a finite future probability fixture clock");
    }

    private static java.util.List<Object> drain(RegisteredServerPlayerFixture fixture) {
        var packets = new java.util.ArrayList<Object>();
        Object packet;
        while ((packet = fixture.pollOutbound()) != null) packets.add(packet);
        return packets;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
