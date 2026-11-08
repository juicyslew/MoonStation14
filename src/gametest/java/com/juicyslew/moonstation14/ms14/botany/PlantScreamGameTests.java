package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-scream"})
public final class PlantScreamGameTests {
    private PlantScreamGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 40)
    public static void committedManualHarvestPlaysResolvedScreamAtTrayOnlyOnce(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.setBlock(new BlockPos(3, 1, 3), com.juicyslew.moonstation14.block.ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState());
        var fixture = RegisteredServerPlayerFixture.register(level, "plant-scream-audio");
        try {
            helper.runAtTickTime(2, () -> {
                try {
                    var ready = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                    ready.body().setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                    fixture.player().setCamera(ready.body());
                    var sounds = new ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
                    var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(pos);
                    require(tray != null && tray.plantState() != null, "ordinary tray initialized");
                    var base = ModSeedProfiles.serverLookup(ModSeedProfiles.seedId("wheat"));
                    require(base != null, "resolved wheat profile available");
                    var scream = new SeedProfile.Scream(true, SeedProfile.Scream.DEFAULT_SOUND, -10f);
                    var seed = new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(),
                            new SeedProfile.Lifecycle(5, 25, 10, 5, 0, 1, SeedProfile.Harvest.REPEAT), base.chemicals(),
                            base.consumeGasses(), base.exudeGasses(), base.mutationTargets(), scream, base.sourceName());
                    tray.commitPlant(ModSeedProfiles.seedId("wheat"), seed, level.getGameTime());
                    var plant = tray.plantState();
                    plant.state(plant.age(), plant.health(), plant.lastProduceAge(), plant.skipAging(), true, false);
                    long revision = plant.revision();
                    require(!tray.commitHarvest(plant, revision + 1, seed), "stale source commit is denied");
                    drainSounds(fixture, sounds);
                    require(sounds.isEmpty(), "denied harvest emits no scream");
                    var denied = com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch.publishSystemHarvest(
                            level, pos, List.of(net.minecraft.world.item.ItemStack.EMPTY), () -> true,
                            () -> { throw new AssertionError("failed staging must not commit harvest"); });
                    drainSounds(fixture, sounds);
                    require(denied == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.DENIED
                                    && sounds.isEmpty(), "failed output staging emits no scream");
                    require(tray.commitHarvest(plant, revision, seed), "committed zero-output harvest succeeds");
                    drainSounds(fixture, sounds);
                    require(sounds.size() == 1, "committed harvest emits one scream");
                    var heard = sounds.getFirst();
                    require(heard.getSound().value() == BuiltInRegistries.SOUND_EVENT.get(SeedProfile.Scream.DEFAULT_SOUND), "resolved scream event is played");
                    require(heard.getSource() == net.minecraft.sounds.SoundSource.BLOCKS, "scream uses BLOCKS source");
                    require(Math.abs(heard.getVolume() - 0.31622776f) < 1e-6 && heard.getPitch() == 1f, "scream gain and pitch preserved");
                    var expected = new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                            net.minecraft.core.Holder.direct(BuiltInRegistries.SOUND_EVENT.get(SeedProfile.Scream.DEFAULT_SOUND)),
                            net.minecraft.sounds.SoundSource.BLOCKS, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                            0.31622776f, 1f, 0L);
                    require(heard.getX() == expected.getX() && heard.getY() == expected.getY() && heard.getZ() == expected.getZ(),
                            "scream packet uses native quantized tray-center coordinates");
                    require(Math.abs(HydroponicsTrayBlockEntity.screamGain(-10f) - 0.31622776f) < 1.0e-6,
                            "source -10 dB maps to linear sound gain");
                    plant.state(plant.age(), plant.health(), plant.lastProduceAge(), plant.skipAging(), true, false);
                    var silentSeed = new SeedProfile(seed.packet(), seed.product(), seed.visualKey(), seed.biology(),
                            seed.lifecycle(), seed.chemicals(), seed.consumeGasses(), seed.exudeGasses(), seed.mutationTargets(),
                            seed.scream().withEnabled(false), seed.sourceName());
                    require(tray.commitHarvest(plant, plant.revision(), silentSeed), "disabled scream still permits harvest");
                    drainSounds(fixture, sounds);
                    require(sounds.size() == 1, "disabled capability emits no audio");
                    helper.succeed();
                } catch (Throwable failure) { helper.testInfo.fail(failure); }
                finally { fixture.close(); }
            });
        } catch (Throwable failure) {
            fixture.close();
            helper.testInfo.fail(failure);
        }
    }

    private static void drainSounds(RegisteredServerPlayerFixture fixture,
            List<net.minecraft.network.protocol.game.ClientboundSoundPacket> sounds) {
        Object packet;
        while ((packet = fixture.pollOutbound()) != null)
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound
                    && sound.getSound().value() == BuiltInRegistries.SOUND_EVENT.get(SeedProfile.Scream.DEFAULT_SOUND)) sounds.add(sound);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
