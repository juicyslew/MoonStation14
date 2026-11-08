package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.entities.MouseMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.function.Consumer;

/** Shared canonical corpse -> ghost -> ordinary mouse role ownership setup for native fixtures. */
public final class CommittedMouseRoleSetup {
    private CommittedMouseRoleSetup() { }

    public static void start(GameTestHelper helper, RegisteredServerPlayerFixture fixture, MouseMob mouse,
                             Consumer<MouseMob> committed) {
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                if (!(active.body() instanceof net.minecraft.world.entity.Mob))
                    throw new GameTestAssertException("canonical lifecycle creates an authenticated character Mob");
                if (com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(active.body(),
                        java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 300f), 1f, true)
                        != com.juicyslew.moonstation14.ms14.damage.DamageSystem.Result.APPLIED)
                    throw new GameTestAssertException("native lethal damage enters corpse lifecycle");
                helper.runAfterDelay(2, () -> awaitCorpse(helper, fixture, mouse, committed, 0));
            } catch (RuntimeException | Error failure) {
                fixture.close(); mouse.discard(); throw failure;
            }
        });
    }

    private static void awaitCorpse(GameTestHelper helper, RegisteredServerPlayerFixture fixture, MouseMob mouse,
                                    Consumer<MouseMob> committed, int elapsed) {
        try {
            var player = fixture.player();
            var corpse = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
            if (corpse == null || corpse.mode() != com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.CORPSE) {
                if (elapsed >= 30) throw new GameTestAssertException("lifecycle did not reach corpse before canonical mouse offer");
                helper.runAfterDelay(1, () -> awaitCorpse(helper, fixture, mouse, committed, elapsed + 1));
                return;
            }
            if (!com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.requestGhost(player, corpse.epoch()))
                throw new GameTestAssertException("corpse observer must transition through bound ghost lifecycle");
            var ghost = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
            if (ghost == null || ghost.mode() != com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.GHOST
                    || !RegisteredPlayerReadyDriver.readyGhost(fixture, ghost.authorityGeneration()))
                throw new GameTestAssertException("bound GHOST ready commits before role offer");
            if (!com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl
                    .offerRoleTarget(player, mouse)) throw new GameTestAssertException("loaded mouse role offer denied");
            Object packet;
            com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer offer = null;
            while ((packet = fixture.pollOutbound()) != null)
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket payload
                        && payload.payload() instanceof com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer found)
                    offer = found;
            if (offer == null) throw new GameTestAssertException("server emitted exact mouse takeover offer");
            RegisteredPlayerReadyDriver.acceptRoleOffer(fixture, offer);
            var ownership = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl
                    .activeCharacterBody(player).orElse(null);
            if (ownership == null || ownership.body() != mouse || player.getCamera() != mouse)
                throw new GameTestAssertException("role commit owns exact original mouse and camera");
            committed.accept(mouse);
        } catch (RuntimeException | Error failure) {
            fixture.close(); mouse.discard(); throw failure;
        }
    }
}
