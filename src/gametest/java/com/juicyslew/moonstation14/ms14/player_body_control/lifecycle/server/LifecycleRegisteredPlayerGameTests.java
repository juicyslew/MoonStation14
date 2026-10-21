package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsFixtureTransfer;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered-player lifecycle authority fixture; not connected-client input evidence. */
@PrefixGameTestTemplate(false)
@GameTestTags({"feature-readiness"})
public final class LifecycleRegisteredPlayerGameTests {
    private LifecycleRegisteredPlayerGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void lifecycleReadyGrantsHandQuery(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(
                helper.getLevel(), "lifecycle-ready");
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                exerciseOrdinaryHeldIngestion(helper, fixture, active.body(), active.epoch());
            } catch (RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"observer", "lifecycle"})
    public static void registeredLifecycleDeathGhostReviveAndReturn(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(
                helper.getLevel(), "lifecycle-return");
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                require(active.body() instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity,
                        "normal registered login owns the persistent character body");
                var body = (com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity) active.body();
                var player = fixture.player();
                var bodyId = body.getUUID();
                body.setHealth(0);
                body.die(helper.getLevel().damageSources().generic());
                require(body.hasConfirmedDeath() && !body.isAlive(), "native character death is confirmed without removing corpse");
                var corpse = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
                require(corpse != null && corpse.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.CORPSE
                                && corpse.corpseBodyId().equals(bodyId), "production death claim begins linked corpse observer session");
                dispatchObserver(fixture, com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Request.Kind.GHOST, corpse.epoch());
                var ghost = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
                require(ghost != null && ghost.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.GHOST,
                        "production request dispatch stages linked ghost");
                var ghostBegin = takeLifecycleBegin(fixture, com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.GHOST);
                require(ghostBegin != null, "ghost staging emitted canonical GHOST Begin");
                RegisteredPlayerReadyDriver.readyGhost(fixture, ghostBegin);
                require(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl
                                .canonicalGhost(player).isPresent(), "production GHOST Ready commits native ghost ownership");
                int revived;
                try { revived = helper.getLevel().getServer().getCommands().getDispatcher()
                        .execute("ms14 revive", player.createCommandSourceStack().withPermission(4)); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                    throw new GameTestAssertException("registered /ms14 revive failed: " + failure.getMessage());
                }
                require(revived == 1 && helper.getLevel().getEntity(bodyId) == body && body.isAlive(),
                        "operator revive restores exact retained body");
                var session = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
                require(session != null && session.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.GHOST,
                        "revive retains ghost until explicit return request");
                com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.sendSnapshot(player, true);
                com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot snapshot = null;
                Object outbound;
                while ((outbound = fixture.pollOutbound()) != null)
                    if (outbound instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
                            && packet.payload() instanceof com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot captured) snapshot = captured;
                require(snapshot != null && snapshot.returnAvailable() && snapshot.mode()
                                == com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST,
                        "production observer snapshot marks revived linked body returnable");
                long staleEpoch = Math.max(0, snapshot.epoch() - 1);
                require(staleEpoch != snapshot.epoch(), "ghost observer epoch advances beyond initial corpse epoch");
                dispatchObserver(fixture, com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Request.Kind.RETURN,
                        staleEpoch);
                require(com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player) == session,
                        "stale production RETURN payload leaves ghost owner unchanged");
                dispatchObserver(fixture, com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Request.Kind.RETURN,
                        snapshot.epoch());
                require(com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player) == null,
                        "production RETURN payload retires observer claim");
                com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.sendSnapshot(player, true);
                var transition = new ObserverReturnTransition();
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 80,
                        () -> {
                            fixture.close();
                            return "return transition timed out; characterBegin=" + (transition.characterBegin != null)
                                    + " END=" + transition.ended + " registered=" + fixture.isRegisteredAndAlive()
                                    + " active=" + LifecycleCharacterSessionControl.activeCharacterBody(player).isPresent()
                                    + " body=" + body.getUUID();
                        }, () -> {
                            Object next;
                            while ((next = fixture.pollOutbound()) != null)
                                if (next instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet) {
                                    if (packet.payload() instanceof com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot endedSnapshot
                                            && endedSnapshot.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.END)
                                        transition.ended = true;
                                    if (packet.payload() instanceof com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Begin begin
                                            && begin.harnessKind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER)
                                        transition.characterBegin = begin;
                                }
                            return transition.ended && transition.characterBegin != null;
                        }, () -> {
                            try {
                                var returned = RegisteredPlayerReadyDriver.readyAndQuery(fixture, transition.characterBegin);
                                require(returned.body() == body && returned.body().getUUID().equals(bodyId),
                                        "canonical character Ready after return controls original exact body");
                                helper.succeed();
                            } finally { fixture.close(); }
                        });
            } catch (RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    private static final class ObserverReturnTransition {
        private com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Begin characterBegin;
        private boolean ended;
    }

    private static void dispatchObserver(RegisteredServerPlayerFixture fixture,
            com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Request.Kind kind, long epoch) {
        var listener = (net.minecraft.server.network.ServerGamePacketListenerImpl) fixture.connection().getPacketListener();
        listener.handleCustomPayload(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Request(kind, epoch)));
    }

    private static com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Begin takeLifecycleBegin(
            RegisteredServerPlayerFixture fixture, com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind kind) {
        com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Begin result = null;
        Object outbound;
        while ((outbound = fixture.pollOutbound()) != null)
            if (outbound instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Begin begin
                    && begin.harnessKind() == kind) result = begin;
        return result;
    }

    private static void exerciseOrdinaryHeldIngestion(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                                       net.minecraft.world.entity.LivingEntity body, long epoch) {
        try {
            LiveHands before = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            require(before != null && before.compatible(body), "Ready bootstrapped supported lifecycle hands");
            String hand = before.activeHand();
            String token = "readiness-drink";
            ItemStack drink = new ItemStack(ModItems.BEAKER.get());
            var water = ModReagents.createKey("water");
            drink.set(ModDataComponents.REAGENT.get(), new ReagentComponent(java.util.Map.of(water, 10f)));
            var trait = (com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) drink.getItem();
            var policy = trait.drinkSolution(drink, body.level());
            require(policy.enabled() && policy.consumeAmount() == 5f && policy.personalDelayTicks() == 20,
                    "resolved beaker ancestry drives the actual drink consumer's 5u/20-tick policy");
            var unsupportedDrink = new ItemStack(ModItems.SYRINGE.get());
            var unsupportedPolicy = ((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) unsupportedDrink.getItem())
                    .drinkSolution(unsupportedDrink, body.level());
            require(!unsupportedPolicy.enabled(), "resolved non-edible syringe composition has no drink permission");
            require(LiveHandsFixtureTransfer.put(fixture.player(), body, hand, token, drink),
                    "held source staged through LiveHands API and authenticated LiveHandsCommitGateway");

            BodyHandStateSnapshot state = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 2));
            require(state.reason() == BodyHandStateSnapshot.Reason.OK && state.epoch() == epoch
                            && state.activeHand().equals(hand) && state.revision() == before.revision() + 1,
                    "fresh full state query supplies request epoch, active hand and revision");
            String issuedToken = state.hands().stream().filter(slot -> slot.id().equals(hand))
                    .map(BodyHandStateSnapshot.Hand::token).findFirst().orElse(null);
            require(token.equals(issuedToken), "request token comes from issued full-state snapshot");
            BodyHandActionRequest stale = new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,
                    hand, epoch, 1, state.revision(), null, "stale-token");
            require(BodyHandRequestService.apply(fixture.player(), stale).reason()
                            == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.STALE_HAND,
                    "stale source token is rejected before changing drink contents");
            require(sourceUnits(body, hand) == 1000, "stale-source rejection conserves the complete drink");
            BodyHandActionRequest request = new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,
                    hand, epoch, 2, state.revision(), null, issuedToken);
            require(BodyHandRequestService.apply(fixture.player(), request).reason()
                            == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                    "ordinary authoritative USE_IN_HAND request starts held ingestion");
            require(sourceUnits(body, hand) == 1000, "request start does not consume source early");
            helper.runAfterDelay(10, () -> {
                try {
                    require(sourceUnits(body, hand) == 1000, "no source dose before resolved 20-tick deadline");
                    helper.runAfterDelay(15, () -> {
                        try {
                            require(sourceUnits(body, hand) == 500, "one completed request consumes exactly one 5u dose");
                            long swallowed = ReagentUnits.fromFloat(com.juicyslew.moonstation14.ms14.MS14Provider
                                    .get(body, com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH)
                                    .getMap().getOrDefault(water, 0f));
                            require(swallowed >= 450 && swallowed <= 500,
                                    "single completed serving reaches resolved body's stomach before/after metabolism tick, water=" + swallowed);
                            helper.succeed();
                        } finally { fixture.close(); }
                    });
                } catch (RuntimeException | Error failure) {
                    fixture.close();
                    throw failure;
                }
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static long sourceUnits(net.minecraft.world.entity.LivingEntity body, String hand) {
        var stack = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                .stackCopy(hand).orElseThrow();
        return ReagentUnits.fromFloat(stack.get(ModDataComponents.REAGENT.get()).contents()
                .getOrDefault(ModReagents.createKey("water"), 0f));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
