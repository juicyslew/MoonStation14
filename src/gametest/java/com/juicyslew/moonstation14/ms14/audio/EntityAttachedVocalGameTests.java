package com.juicyslew.moonstation14.ms14.audio;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.actions.BodyActionRequest;
import com.juicyslew.moonstation14.ms14.actions.BodyActionResult;
import com.juicyslew.moonstation14.ms14.actions.BodyActionService;
import com.juicyslew.moonstation14.ms14.character.components.VocalComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestTags("vocal_entity_audio")
public final class EntityAttachedVocalGameTests {
    private EntityAttachedVocalGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void normalScreamActionRoutesOneEntityBoundSoundToBothReadyBodyListeners(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var emitter = RegisteredServerPlayerFixture.register(level, "vocal-emitter");
        var observer = RegisteredServerPlayerFixture.register(level, "vocal-observer");
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void close() { emitter.close(); observer.close(); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldInfo,
                    net.minecraft.gametest.framework.GameTestInfo newInfo, net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
        });
        try {
            helper.runAtTickTime(2, () -> {
                try {
                    var active = RegisteredPlayerReadyDriver.readyAndQuery(emitter);
                    var listening = RegisteredPlayerReadyDriver.readyAndQuery(observer);
                    var emitterPos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
                    var observerPos = helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 2));
                    active.body().setPos(emitterPos.getX() + 0.5, emitterPos.getY(), emitterPos.getZ() + 0.5);
                    listening.body().setPos(observerPos.getX() + 0.5, observerPos.getY(), observerPos.getZ() + 0.5);
                    emitter.player().setCamera(active.body());
                    observer.player().setCamera(listening.body());
                    long bodyId = active.body().getId();
                    long carrierId = emitter.player().getId();
                    if (bodyId == carrierId) throw new GameTestAssertException("controlled vocal body must differ from carrier player");

                    List<Packet<?>> emitterPackets = new ArrayList<>();
                    List<Packet<?>> observerPackets = new ArrayList<>();
                    emitter.observeOutbound(emitterPackets::add);
                    observer.observeOutbound(observerPackets::add);

                    var snapshot = BodyActionService.query(emitter.player()).orElseThrow(
                            () -> new GameTestAssertException("ready emitter has no body action snapshot"));
                    var actionId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "scream");
                    var entry = snapshot.actions().stream().filter(a -> a.actionId().equals(actionId)).findFirst().orElseThrow(
                            () -> new GameTestAssertException("resolved body has no scream action"));
                    var result = BodyActionService.activate(emitter.player(), new BodyActionRequest(snapshot.bodyId(),
                            snapshot.bodyEpoch(), snapshot.revision(), actionId, entry.providerId(), 1));
                    require(result.status() == BodyActionResult.Status.ACCEPTED, "ordinary scream action accepted: " + result);

                    var vocal = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem
                            .resolveForActor(active.body()).flatMap(data -> data.component(VocalComponent.class)).orElseThrow();
                    var sent = entitySounds(emitterPackets);
                    var heard = entitySounds(observerPackets);
                    require(sent.size() == 1 && heard.size() == 1, "one entity sound reaches each ready body listener");
                    for (var packet : List.of(sent.getFirst(), heard.getFirst())) {
                        require(packet.getId() == bodyId, "packet identifies controlled vocal body");
                        require(packet.getId() != carrierId, "packet does not identify carrier player");
                        require(packet.getSound().value() == net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(vocal.screamSound())
                                        || packet.getSound().value() == net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(
                                                com.juicyslew.moonstation14.ms14.effect.VocalScreamDelivery.WILHELM),
                                "packet uses one of the resolved vocal component's allowed scream profiles");
                        require(packet.getSource() == net.minecraft.sounds.SoundSource.VOICE, "vocal packet uses VOICE category");
                        require(packet.getPitch() >= 1f - vocal.screamPitchVariation()
                                        && packet.getPitch() <= 1f + vocal.screamPitchVariation(), "pitch within resolved vocal variation");
                    }
                    require(entitySounds(emitterPackets).size() == 1 && entitySounds(observerPackets).size() == 1,
                            "no duplicate entity-bound cue");
                    require(emitterPackets.stream().noneMatch(p -> p instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket)
                                    && observerPackets.stream().noneMatch(p -> p instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket),
                            "scream does not also emit a coordinate sound packet");
                    var movedPos = helper.absolutePos(new net.minecraft.core.BlockPos(7, 1, 2));
                    active.body().setPos(movedPos.getX() + 0.5, movedPos.getY(), movedPos.getZ() + 0.5);
                    require(sent.getFirst().getId() == bodyId, "captured sound remains entity-bound after body relocation");
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("MS14_ENTITY_VOCAL_AUDIO_PASS normal action dispatch; both ready body listeners; controlled-body packet identity");
                    helper.succeed();
                } catch (Throwable failure) { helper.testInfo.fail(failure); }
            });
        } catch (Throwable failure) {
            helper.testInfo.fail(failure);
        }
    }

    private static List<ClientboundSoundEntityPacket> entitySounds(List<Packet<?>> packets) {
        return packets.stream().filter(ClientboundSoundEntityPacket.class::isInstance)
                .map(ClientboundSoundEntityPacket.class::cast).toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
