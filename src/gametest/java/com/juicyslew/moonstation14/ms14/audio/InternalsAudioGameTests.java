package com.juicyslew.moonstation14.ms14.audio;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.actions.BodyActionRequest;
import com.juicyslew.moonstation14.ms14.actions.BodyActionResult;
import com.juicyslew.moonstation14.ms14.actions.BodyActionService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for the default SS14 connect-only internals cue. */
@PrefixGameTestTemplate(false)
@GameTestTags("internals_audio")
public final class InternalsAudioGameTests {
    private InternalsAudioGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void normalInternalsActionSoundsOnlyOnSuccessfulConnect(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "internalsaudio");
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void close() { fixture.close(); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldInfo,
                    net.minecraft.gametest.framework.GameTestInfo newInfo, net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                var authority = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                        .resolve(fixture.player()).orElseThrow();
                var mask = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("moonstation14", "breathing_mask"));
                require(mask != net.minecraft.world.item.Items.AIR, "registered breathing mask exists");
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body,
                        new net.minecraft.world.item.ItemStack(mask), () ->
                                com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                                        .revalidate(fixture.player(), authority)), "trusted mask give");
                final var maskHands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
                String maskHand = maskHands.handIds().stream().filter(h -> maskHands.stackCopy(h).filter(s -> s.is(mask)).isPresent())
                        .findFirst().orElseThrow();
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.quickEquip(fixture.player(),
                        maskHand, maskHands.token(maskHand).orElseThrow(), maskHands.revision())
                        == com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.Result.SUCCESS,
                        "mask equipped through inventory owner");
                var tank = com.juicyslew.moonstation14.item.custom.GasTankItem.createStack(level,
                        com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.OXYGEN_FILLED_ID);
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body, tank,
                        () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                                .revalidate(fixture.player(), authority)), "trusted finite oxygen tank give");
                final var tankHands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
                String token = tankHands.handIds().stream().filter(h -> tankHands.stackCopy(h)
                        .filter(s -> s.getItem() instanceof com.juicyslew.moonstation14.item.custom.GasTankItem).isPresent())
                        .map(tankHands::token).flatMap(java.util.Optional::stream).findFirst().orElseThrow();
                var pos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
                body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                fixture.player().setCamera(body);
                List<Packet<?>> packets = new ArrayList<>();
                fixture.observeOutbound(packets::add);
                var sound = com.juicyslew.moonstation14.sounds.ModSounds.INTERNALS_TOGGLE.get();
                var first = action(fixture, 1);
                require(first.status() == BodyActionResult.Status.ACCEPTED, "authenticated connect accepted");
                require(com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body)
                        .map(source -> source.token().equals(token)).orElse(false), "exact requested tank connected");
                var cues = internalsSounds(packets, sound, body.getId());
                require(cues.size() == 1, "connect emits exactly one internals sound");
                var cue = cues.getFirst();
                require(cue.getId() == body.getId() && cue.getId() != fixture.player().getId(),
                        "sound packet is attached to controlled body, not carrier");
                require(cue.getVolume() == com.juicyslew.moonstation14.ms14.hands.live.ItemSlotAudio.decibels(5f)
                                && cue.getPitch() == 1f, "connect uses SS14 +5 dB volume and default pitch");
                require(cue.getSource() == net.minecraft.sounds.SoundSource.PLAYERS, "connect uses player sound category");
                BoundedGameTestWait.safeDelay(helper, 21, () -> {
                    try {
                        require(internalsSounds(packets, sound, body.getId()).size() == 1,
                                "no recurring internals breathing cue from the connected body");
                        var off = action(fixture, 2);
                        require(off.status() == BodyActionResult.Status.ACCEPTED, "authenticated disconnect accepted");
                        require(!com.juicyslew.moonstation14.ms14.lung.InternalsSystem.isConnected(body), "disconnect commits state");
                        require(internalsSounds(packets, sound, body.getId()).size() == 1,
                                "disconnect emits no internals sound from the connected body");
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                "MS14_INTERNALS_AUDIO_PASS connect-only cue; selected tank; body packet; +5 dB; no loop; silent disconnect");
                        helper.succeed();
                    } catch (Throwable failure) { helper.testInfo.fail(failure); }
                });
            } catch (Throwable failure) { helper.testInfo.fail(failure); }
        });
    }

    private static BodyActionResult action(RegisteredServerPlayerFixture fixture, int sequence) {
        var snapshot = BodyActionService.query(fixture.player()).orElseThrow();
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "toggle_internals");
        var entry = snapshot.actions().stream().filter(a -> a.actionId().equals(id)).findFirst().orElseThrow();
        return BodyActionService.activate(fixture.player(), new BodyActionRequest(snapshot.bodyId(), snapshot.bodyEpoch(),
                snapshot.revision(), id, entry.providerId(), sequence));
    }

    private static List<ClientboundSoundEntityPacket> internalsSounds(List<Packet<?>> packets,
            net.minecraft.sounds.SoundEvent sound, int emitterId) {
        return packets.stream().filter(ClientboundSoundEntityPacket.class::isInstance)
                .map(ClientboundSoundEntityPacket.class::cast)
                .filter(packet -> packet.getSound().value() == sound && packet.getId() == emitterId).toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
