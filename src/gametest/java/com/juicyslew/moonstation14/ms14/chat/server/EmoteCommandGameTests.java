package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Live dispatcher/admission coverage; the fixture's embedded peer is not network-handshake evidence. */
@PrefixGameTestTemplate(false)
@GameTestTags({"chat", "mouse-emote"})
public final class EmoteCommandGameTests {
    private EmoteCommandGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void committedHumanCannotUseMouseOnlySqueak(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture.register(level, "emotehuman");
        var sounds = new ArrayList<String>();
        var activeBody = new net.minecraft.world.entity.Entity[1];
        Consumer<PlayLevelSoundEvent.AtEntity> listener = event -> {
            if (event.getLevel() == level && (event.getEntity() == fixture.player() || event.getEntity() == activeBody[0]))
                event.getSound().unwrapKey().map(key -> key.location().toString() + " from "
                        + event.getEntity().getUUID()).ifPresent(sounds::add);
        };
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                activeBody[0] = active.body();
                NeoForge.EVENT_BUS.addListener(listener);
                require(LocalSpeechServerHooks.authenticatedSpeaker(fixture.player()) != null,
                        "committed lifecycle human resolves through the shared authenticated speech context");
                require(com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(active.body())
                                .flatMap(data -> data.component(com.juicyslew.moonstation14.ms14.character.components.SpeechComponent.class))
                                .orElseThrow().allowedEmotes().isEmpty(), "human composition defaults to no emote capability");
                var source = fixture.player().createCommandSourceStack();
                int result;
                try {
                    result = level.getServer().getCommands().getDispatcher().execute(
                            level.getServer().getCommands().getDispatcher().parse("emote squeak", source));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                    throw new GameTestAssertException("emote command failed to dispatch: " + failure);
                }
                require(result == 0 && sounds.isEmpty(),
                        "actual server dispatcher rejects unlisted emote without sound from human body/player; result="
                                + result + " sounds=" + sounds + " body=" + active.body().getUUID()
                                + " player=" + fixture.player().getUUID());
                helper.succeed();
            } finally {
                if (activeBody[0] != null) NeoForge.EVENT_BUS.unregister(listener);
                fixture.close();
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 300)
    public static void committedMouseOwnerUsesSqueakOnceFromTheOwnedBody(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture.register(level, "emotemouse");
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "normal mouse entity can be selected as a living role");
        mouse.setPos(fixture.player().position().add(2, 0, 0));
        require(level.addFreshEntity(mouse), "unclaimed mouse joins the role level");
        var sounds = new ArrayList<ResourceLocation>();
        var soundPositions = new ArrayList<net.minecraft.world.phys.Vec3>();
        Consumer<PlayLevelSoundEvent.AtEntity> listener = event -> {
            if (event.getLevel() == level && event.getEntity() == mouse) {
                event.getSound().unwrapKey().map(key -> key.location()).ifPresent(sounds::add);
                soundPositions.add(event.getEntity().position());
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            com.juicyslew.moonstation14.gametest.support.CommittedMouseRoleSetup.start(helper, fixture, mouse, committedMouse -> {
                try {
                var player = fixture.player();
                require(LocalSpeechServerHooks.authenticatedSpeaker(player) != null,
                        "the real role-owned mouse resolves through committed lifecycle speaker authority");
                var identity = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(committedMouse)
                        .orElseThrow();
                var resolved = identity.component(com.juicyslew.moonstation14.ms14.character.components.SpeechComponent.class)
                        .orElseThrow();
                require(resolved.allowedEmotes().equals(java.util.Set.of("squeak")),
                        "the owned mouse's resolved local prototype grants only Squeak");
                drainPackets(fixture);
                int first = dispatch(fixture);
                int rapidRepeat = dispatch(fixture);
                var chatLines = drainSqueakChat(fixture);
                var binding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                        .PlayerCharacterHarnessBinder.binding(committedMouse);
                var character = com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData
                        .existing(level.getServer().overworld()).orElseThrow()
                        .character(new com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry
                                .CharacterKey(binding.accountId(), binding.profileKey())).orElseThrow();
                String expectedName = character.name();
                require(!expectedName.equals(binding.accountId().toString()) && !expectedName.equals("Mouse")
                                && !expectedName.equals(identity.metadata().name()),
                        "bound mouse uses saved personal identity, not account or species/prototype metadata: name="
                                + expectedName + " metadata=" + identity.metadata().name());
                ResourceLocation squeak = ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse_squeak");
                require(first == 1 && rapidRepeat == 0 && sounds.equals(java.util.List.of(squeak))
                                && chatLines.equals(java.util.List.of(expectedName)),
                        "one authorized dispatcher invocation produces exactly one body-origin squeak and visible emote line; "
                                + "results=" + first + "/" + rapidRepeat + " sounds=" + sounds + " chat=" + chatLines);
                require(soundPositions.get(0).distanceToSqr(mouse.position()) < 1e-8,
                        "spatial sound originates at the owned mouse entity");
                helper.succeed();
                } finally {
                    NeoForge.EVENT_BUS.unregister(listener);
                    fixture.close();
                    committedMouse.discard();
                }
            });
        } catch (RuntimeException | Error failure) {
            NeoForge.EVENT_BUS.unregister(listener);
            fixture.close();
            mouse.discard();
            throw failure;
        }
    }

    private static int dispatch(com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture) {
        var dispatcher = fixture.player().getServer().getCommands().getDispatcher();
        try {
            return dispatcher.execute(dispatcher.parse("emote squeak", fixture.player().createCommandSourceStack()));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("mouse emote command did not parse/dispatch: " + failure);
        }
    }

    private static java.util.List<String> drainSqueakChat(
            com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture) {
        var lines = new ArrayList<String>();
        Object packet;
        while ((packet = fixture.pollOutbound()) != null) {
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat
                    && chat.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents line
                    && line.getKey().equals("moonstation14.emote.squeak")
                    && line.getArgs().length > 0 && line.getArgs()[0] instanceof net.minecraft.network.chat.Component name)
                lines.add(name.getString());
        }
        return lines;
    }

    private static void drainPackets(com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture) {
        while (fixture.pollOutbound() != null) { }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
