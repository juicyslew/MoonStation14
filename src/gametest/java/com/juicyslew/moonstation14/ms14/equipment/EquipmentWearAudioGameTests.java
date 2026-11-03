package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
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

/** Authenticated timed equipment actions publish their wear cues only after commit. */
@PrefixGameTestTemplate(false)
@GameTestTags("equipment_audio")
public final class EquipmentWearAudioGameTests {
    private EquipmentWearAudioGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 240)
    @GameTestTags("equipment_audio")
    public static void normalTimedHelmetWearAndRemovalEmitOneBodyAttachedCueAfterCommit(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "equipment-audio");
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void close() { fixture.close(); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info,
                    net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info,
                    net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldInfo,
                    net.minecraft.gametest.framework.GameTestInfo newInfo,
                    net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
        });
        helper.runAtTickTime(2, () -> start(helper, fixture));
    }

    private static void start(GameTestHelper helper, RegisteredServerPlayerFixture fixture) {
        try {
            var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            var body = active.body();
            // Keep this controlled body inside this fixture's authored footprint; other native
            // fixtures can legitimately be audible to the registered observer.
            var site = helper.absolutePos(new net.minecraft.core.BlockPos(8, 2, 8)).getCenter();
            body.teleportTo(site.x, site.y, site.z);
            fixture.player().teleportTo(site.x, site.y, site.z);
            var actor = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow();
            var item = com.juicyslew.moonstation14.item.ModEquipmentItems.EVA_HELMET;
            require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body,
                    new net.minecraft.world.item.ItemStack(item.get()), () ->
                            com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(fixture.player(), actor)),
                    "trusted EVA helmet given to controlled body");
            var hands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
            String hand = hands.handIds().stream().filter(id -> hands.stackCopy(id).filter(stack -> stack.is(item)).isPresent())
                    .findFirst().orElseThrow();
            String helmetToken = hands.token(hand).orElseThrow();
            var query = fixture.queryHands(active.epoch());
            List<Packet<?>> packets = new ArrayList<>();
            fixture.observeOutbound(packets::add);
            var quickEquip = new BodyHandActionRequest(BodyHandActionRequest.Action.QUICK_EQUIP, hand,
                    active.epoch(), 1, query.revision(), null, helmetToken);
            var accepted = BodyHandRequestService.apply(fixture.player(), quickEquip);
            require(accepted.accepted(), "normal authenticated QUICK_EQUIP accepted: " + accepted.reason());
            require(com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.entries(body).stream()
                    .noneMatch(entry -> entry.slotId().equals("head") && entry.stack().is(item.get())),
                    "helmet is not worn at timed action start");
            require(ownSounds(packets, body).isEmpty(), "no own-body cue before timed equipment commit; observed=" + soundSources(packets));
            long startedAt = levelTime(helper);
            BoundedGameTestWait.safeDelay(helper, 40, () -> pollEquip(helper, fixture, body, active.epoch(), helmetToken,
                    packets, startedAt, 0));
        } catch (Throwable failure) { helper.testInfo.fail(failure); }
    }

    /** TimedInteractionSystem runs in the post-server-tick hook; observe commit with a bounded slack window. */
    private static void pollEquip(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity body, long epoch, String token, List<Packet<?>> packets,
            long startedAt, int waited) {
        try {
            var equipment = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.equipmentSnapshot(body).orElseThrow();
            var worn = equipment.slots().stream().filter(slot -> slot.token().filter(token::equals).isPresent()).findFirst().orElse(null);
            if (worn == null) {
                require(ownSounds(packets, body).isEmpty(), "no own-body cue before timed equip commit; observed=" + soundSources(packets));
                if (waited >= 60) throw new GameTestAssertException("normal timed helmet equip did not commit within bounded slack");
                BoundedGameTestWait.safeDelay(helper, 1, () -> pollEquip(helper, fixture, body, epoch, token,
                        packets, startedAt, waited + 1));
                return;
            }
            long elapsed = levelTime(helper) - startedAt;
            require(elapsed >= 40, "helmet remains unworn until the authored 40-tick equip duration");
            require("head".equals(worn.id()), "helmet committed to head slot");
            require(ownSounds(packets, body).size() == 1, "exactly one own-body equipment cue after equip commit; observed=" + soundSources(packets));
            checkCue(ownSounds(packets, body).getFirst(), body);
            var query = fixture.queryHands(epoch);
            var state = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            String empty = state.handIds().stream().filter(id -> state.stackCopy(id).isEmpty()).findFirst().orElseThrow();
            var slot = query.equipment().stream().filter(value -> value.id().equals("head")
                    && value.token().equals(token)).findFirst().orElseThrow();
            var unequip = new BodyHandActionRequest(BodyHandActionRequest.Action.UNEQUIP_SLOT, empty,
                    epoch, 2, query.revision(), null, slot.token(), null, -1, -1, -1, -1, -1, -1,
                    null, "head", null, null);
            long removalStartedAt = levelTime(helper);
            var result = BodyHandRequestService.apply(fixture.player(), unequip);
            require(result.accepted(), "normal authenticated UNEQUIP_SLOT accepted: " + result.reason());
            require(ownSounds(packets, body).size() == 1, "no own-body cue before timed removal commit; observed=" + soundSources(packets));
            BoundedGameTestWait.safeDelay(helper, 40, () -> pollUnequip(helper, body, token, packets, removalStartedAt, 0));
        } catch (Throwable failure) { helper.testInfo.fail(failure); }
    }

    private static void pollUnequip(GameTestHelper helper, net.minecraft.world.entity.LivingEntity body,
            String token, List<Packet<?>> packets, long startedAt, int waited) {
        try {
            var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            boolean returned = hands.handIds().stream().anyMatch(id -> hands.token(id).filter(token::equals).isPresent()
                    && hands.stackCopy(id).filter(stack -> stack.is(
                            com.juicyslew.moonstation14.item.ModEquipmentItems.EVA_HELMET.get())).isPresent());
            boolean stillWorn = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.equipmentSnapshot(body).orElseThrow()
                    .slots().stream().anyMatch(slot -> slot.token().filter(token::equals).isPresent());
            if (!returned || stillWorn) {
                require(ownSounds(packets, body).size() == 1, "no own-body removal cue before timed unequip commit; observed=" + soundSources(packets));
                if (waited >= 60) throw new GameTestAssertException("normal timed helmet removal did not commit within bounded slack");
                BoundedGameTestWait.safeDelay(helper, 1,
                        () -> pollUnequip(helper, body, token, packets, startedAt, waited + 1));
                return;
            }
            require(returned, "removed helmet returns to a body hand with its exact token");
            require(levelTime(helper) - startedAt >= 40, "helmet remains worn until the authored 40-tick unequip duration");
            require(ownSounds(packets, body).size() == 2, "exactly one additional own-body equipment cue after removal commit; observed=" + soundSources(packets));
            checkCue(ownSounds(packets, body).get(1), body);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "MS14_EQUIPMENT_AUDIO_PASS normalQuickEquip=true normalUnequip=true equipAfterCommit=true unequipAfterCommit=true bodySource=true");
            helper.succeed();
        } catch (Throwable failure) { helper.testInfo.fail(failure); }
    }

    private static long levelTime(GameTestHelper helper) { return helper.getLevel().getGameTime(); }

    private static List<ClientboundSoundEntityPacket> sounds(List<Packet<?>> packets) {
        var key = ResourceLocation.fromNamespaceAndPath("moonstation14", "equipment/eva_helmet");
        return packets.stream().filter(ClientboundSoundEntityPacket.class::isInstance)
                .map(ClientboundSoundEntityPacket.class::cast)
                .filter(packet -> key.equals(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                        .getKey(packet.getSound().value()))).toList();
    }

    private static List<ClientboundSoundEntityPacket> ownSounds(List<Packet<?>> packets, net.minecraft.world.entity.LivingEntity body) {
        return sounds(packets).stream().filter(packet -> packet.getId() == body.getId()).toList();
    }

    private static List<String> soundSources(List<Packet<?>> packets) {
        return sounds(packets).stream().map(packet -> "entity=" + packet.getId()).toList();
    }

    private static void checkCue(ClientboundSoundEntityPacket cue, net.minecraft.world.entity.LivingEntity body) {
        require(cue.getId() == body.getId(), "cue attaches to controlled body entity");
        require(cue.getSource() == net.minecraft.sounds.SoundSource.PLAYERS, "player sound category");
        require(cue.getVolume() == 1f && cue.getPitch() == 1f, "default cue volume and pitch");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
