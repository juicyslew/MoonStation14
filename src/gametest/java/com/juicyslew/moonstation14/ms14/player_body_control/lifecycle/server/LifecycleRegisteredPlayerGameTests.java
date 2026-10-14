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
