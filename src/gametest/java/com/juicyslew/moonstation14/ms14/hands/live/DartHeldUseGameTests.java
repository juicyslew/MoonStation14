package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Confirms ordinary held hypodart use stays silent and non-mutating at the server request boundary. */
@PrefixGameTestTemplate(false)
@GameTestTags({"thrown-combat", "thrown-controls"})
public final class DartHeldUseGameTests {
    private DartHeldUseGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    public static void heldHypodartUseIsDeniedWithoutHintOrMutation(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "dart-held-use-negative");
        var sourceRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var bodyRef = new net.minecraft.world.entity.LivingEntity[1];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean() {
                try { if (bodyRef[0] != null && !bodyRef[0].isRemoved()) bodyRef[0].discard(); } catch (Throwable ignored) { }
                try { if (sourceRef[0] != null && !sourceRef[0].isRemoved()) sourceRef[0].discard(); } catch (Throwable ignored) { }
                try { fixture.close(); } catch (Throwable ignored) { }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var origin = helper.absolutePos(new net.minecraft.core.BlockPos(5, 1, 5));
                for (int x = 0; x <= 5; x++) for (int z = 0; z <= 5; z++)
                    level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = owner.body();
                bodyRef[0] = body;
                if (body instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
                body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
                var water = net.minecraft.resources.ResourceKey.create(
                        com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
                var sourceStack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.HYPO_DART.get());
                sourceStack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                        com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(java.util.Map.of(water, 300L)));
                sourceStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("trusted-dart-held-use"));
                sourceRef[0] = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                        body.position().add(0, .3, 1), sourceStack);
                sourceRef[0].setNoGravity(true);
                require(level.addFreshEntity(sourceRef[0]), "native hypodart source published");

                var initial = fixture.queryHands(owner.epoch());
                String hand = initial.activeHand();
                var pickup = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP_SELECTED,
                                hand, owner.epoch(), 1, initial.revision(), sourceRef[0].getUUID(), null));
                require(pickup.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                        "actual server selected pickup succeeds");
                require(sourceRef[0].isRemoved() && level.getEntity(sourceRef[0].getUUID()) == null,
                        "picked-up world source has no duplicate entity");

                var query = fixture.queryHands(owner.epoch());
                var current = query.hands().stream().filter(h -> h.id().equals(hand)).findFirst().orElseThrow();
                var heldBefore = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                        .stackCopy(hand).orElseThrow();
                require(heldBefore.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get())
                        .centContents().equals(java.util.Map.of(water, 300L)), "held hypodart has exact 300 cents water");
                var outbound = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
                fixture.observeOutbound(outbound::add);
                var use = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.USE_IN_HAND,
                                hand, owner.epoch(), 2, query.revision(), null, current.token()));
                require(use.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED,
                        "ordinary in-hand use of hypodart is denied");

                var heldAfter = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                        .stackCopy(hand).orElseThrow();
                require(heldBefore.getCount() == heldAfter.getCount() && ItemStack.matches(heldBefore, heldAfter)
                                && heldAfter.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get())
                                .centContents().equals(java.util.Map.of(water, 300L))
                                && "trusted-dart-held-use".equals(heldAfter.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString())
                                && heldAfter.get(com.juicyslew.moonstation14.component.ModDataComponents.SYRINGE.get()) == null,
                        "denial conserves held item, name and reagent without syringe-mode state");
                require(outbound.stream().filter(net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class::isInstance)
                                .map(net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class::cast)
                                .filter(net.minecraft.network.protocol.game.ClientboundSystemChatPacket::overlay)
                                .map(net.minecraft.network.protocol.game.ClientboundSystemChatPacket::content)
                                .filter(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                                        && t.getKey().startsWith("message.moonstation14.hypodart.capability"))
                                .findAny().isEmpty(),
                        "ordinary use emits no obsolete hypodart capability action-bar hint");
                helper.succeed();
            } catch (Throwable failure) {
                throw failure instanceof RuntimeException r ? r : new GameTestAssertException("held dart use fixture failed: " + failure);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
