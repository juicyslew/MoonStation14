package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.effect.*;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.status_effect.*;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"muted-speech", "speech"})
public final class MuteToxinChatGameTests {
    private MuteToxinChatGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void decodedMuteToxinEffectBlocksCommittedBodyChat(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "muted-speech-fixture");
        net.minecraft.world.entity.LivingEntity body = null;
        try {
            body = RegisteredPlayerReadyDriver.readyAndQuery(fixture).body();
            final var committedBody = body;
            body.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2)).getX() + .5,
                    helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2)).getY(),
                    helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2)).getZ() + .5);
            fixture.player().setCamera(body);
            var reagent = ModReagents.require(level, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "mutetoxin"));
            var effect = reagent.metabolisms().get(MetabolismStage.BLOODSTREAM).effects().stream()
                    .filter(EffectData.GenericStatusEffect.class::isInstance).findFirst().orElseThrow();
            var context = new EffectContext(level, body, .5f, RandomSource.create(19), ConditionContext.unavailable(), EffectCause.METABOLISM);
            var effects = EffectSystem.withDefaults();
            drain(fixture);
            var first = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new ServerChatEvent(fixture.player(), "before mute", Component.literal("before mute")));
            require(first.isCanceled() && hasSpeech(drain(fixture)), "unmuted committed body speech reaches speaker payload");
            require(effects.apply(effect, context) == EffectResult.APPLIED, "resolved MuteToxin GenericStatusEffect applied through dispatcher");
            require(effects.apply(effect, context) == EffectResult.APPLIED, "repeat decoded effect adds duration");
            var statusKey = ModStatusEffects.createKey("muted");
            var attached = MS14Provider.get(body, StatusEffectSystem.bridge).get(statusKey).orElseThrow();
            require(attached.remainingDurationTicks().orElseThrow() == 200, "two scaled 10-second applications add to 200 ticks");
            require(StatusEffectSystem.hasActiveBehavior(body, com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior.SPEECH_BLOCK),
                    "actual body receives active SPEECH_BLOCK status");
            drain(fixture);
            helper.runAfterDelay(11, () -> {
                try {
                    drain(fixture);
                    var mutedEvent = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new ServerChatEvent(fixture.player(), "muted", Component.literal("muted")));
                    var mutedPackets = drain(fixture);
                    require(mutedEvent.isCanceled() && !hasSpeech(mutedPackets), "muted body chat emits no local speech payload");
                    require(!mutedPackets.stream().anyMatch(p -> p instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket), "muted chat emits no speech sound");
                    require(hasMuteFeedback(mutedPackets), "muted chat emits exact private speech-muted translation feedback");
                    StatusEffectSystem.clearAll(((IStatusEffectTrait) committedBody).toHandleSelf(), level);
                    helper.runAfterDelay(11, () -> {
                        try {
                            drain(fixture);
                            var recovered = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new ServerChatEvent(fixture.player(), "after mute", Component.literal("after mute")));
                            require(recovered.isCanceled() && hasSpeech(drain(fixture)), "removing body status restores local speech");
                            helper.succeed();
                        } finally { cleanup(committedBody, level, fixture); }
                    });
                } catch (RuntimeException | Error failure) { cleanup(committedBody, level, fixture); throw failure; }
            });
        } catch (RuntimeException | Error failure) {
            if (body != null) {
                cleanup(body, level, fixture);
            }
            throw failure;
        } finally {
            if (body == null) fixture.close();
        }
    }

    private static java.util.List<Object> drain(RegisteredServerPlayerFixture fixture) {
        var packets = new java.util.ArrayList<Object>(); Object packet;
        while ((packet = fixture.pollOutbound()) != null) packets.add(packet);
        return packets;
    }

    private static boolean hasSpeech(java.util.List<Object> packets) {
        return packets.stream().anyMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload);
    }

    private static boolean hasMuteFeedback(java.util.List<Object> packets) {
        return packets.stream().anyMatch(packet -> packet instanceof ClientboundSetActionBarTextPacket actionbar
                && actionbar.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("speech-muted"));
    }

    private static void cleanup(net.minecraft.world.entity.LivingEntity body, ServerLevel level,
                                RegisteredServerPlayerFixture fixture) {
        try { StatusEffectSystem.clearAll(((IStatusEffectTrait) body).toHandleSelf(), level); }
        finally { fixture.close(); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
