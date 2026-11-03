package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Proves an ordinarily adopted native monkey can pick up and authoritatively throw an item. */
@PrefixGameTestTemplate(false)
@GameTestTags({"lifecycle", "hands", "hand-authority", "native-creature-throw"})
public final class NativeCreatureThrowGameTests {
    private NativeCreatureThrowGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void adoptedMonkeyThrowsOrdinaryHeldItem(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 3), "ordinary");
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void adoptedMonkeyThrowsKitchenKnife(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.KITCHEN_KNIFE.get(), 1), "kitchen-knife");
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "NativeMonkeyThrow");
        var op = new ServerOpListEntry(fixture.player().getGameProfile(), 4, true);
        level.getServer().getPlayerList().getOps().add(op);
        var looseRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var thrownRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("moonstation14:monkey"));
        if (type == null || !(type.create(level) instanceof Mob body)) throw new GameTestAssertException("monkey entity type unavailable");
        var origin = helper.absolutePos(new net.minecraft.core.BlockPos(5, 1, 5));
        for (int x = 0; x < 12; x++) level.setBlock(origin.offset(x, -1, 0), Blocks.STONE.defaultBlockState(), 3);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        if (!level.addFreshEntity(body)) throw new GameTestAssertException("monkey spawn failed");
        var character = com.juicyslew.moonstation14.ms14.character.ModCharacters.characterForHost(level,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(body.getType())).orElseThrow();
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enroll(body, level, character);
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                try { if (looseRef[0] != null && !looseRef[0].isRemoved()) looseRef[0].discard(); if (thrownRef[0] != null && !thrownRef[0].isRemoved()) thrownRef[0].discard(); if (!body.isRemoved()) body.discard(); fixture.close(); level.getServer().getPlayerList().getOps().remove(op); }
                catch (Throwable failure) { if (info.getError() != null) info.getError().addSuppressed(failure); else info.fail(failure); }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
        });
        helper.runAtTickTime(2, () -> {
            var human = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(human.body(), java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 300f), 1f, true);
            helper.runAfterDelay(3, () -> {
                var player = fixture.player();
                var corpse = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
                if (corpse == null || !com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.requestGhost(player, corpse.epoch())) throw new GameTestAssertException("ghost entry failed");
                var ghost = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
                if (ghost == null || !RegisteredPlayerReadyDriver.readyGhost(fixture, ghost.authorityGeneration())) throw new GameTestAssertException("ghost ready failed");
                try { player.getServer().getCommands().getDispatcher().execute(player.getServer().getCommands().getDispatcher().parse("ms14dev possess " + body.getUUID(), player.createCommandSourceStack())); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new GameTestAssertException("possess failed: " + e); }
                com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer offer = null;
                Object packet; while ((packet = fixture.pollOutbound()) != null) if (packet instanceof ClientboundCustomPayloadPacket p && p.payload() instanceof com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer o) offer = o;
                if (offer == null) throw new GameTestAssertException("offer missing");
                RegisteredPlayerReadyDriver.acceptRoleOffer(fixture, offer);
                var active = LifecycleCharacterSessionControl.activeCharacterBody(player).orElseThrow();
                if (active.body() != body) throw new GameTestAssertException("native monkey was not adopted");
                body.setNoAi(true); body.setYRot(0); body.setYHeadRot(0); body.setXRot(0); body.setDeltaMovement(Vec3.ZERO);
                var initial = fixture.queryHands(active.epoch());
                if (!"left".equals(initial.activeHand()) || initial.hands().size() != 1) throw new GameTestAssertException("unexpected native hands: " + initial);
                var marker = java.util.UUID.randomUUID().toString();
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(marker));
                var loose = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, body.position().add(0, .3, 1), stack);
                loose.setNoGravity(true); looseRef[0] = loose;
                if (!level.addFreshEntity(loose)) throw new GameTestAssertException("loose item spawn failed");
                 var pickup = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(player, new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP_SELECTED, "left", active.epoch(), 1, initial.revision(), loose.getUUID(), null));
                 if (pickup.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK) throw new GameTestAssertException("pickup denied: " + pickup.reason());
                 var bodyHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                 if (bodyHands == null) throw new GameTestAssertException(label + " pickup did not publish authoritative live hands");
                 var expectedHeld = bodyHands.stackCopy("left").orElseThrow(() -> new GameTestAssertException(label + " pickup left hand unexpectedly empty"));
                 if (!net.minecraft.world.item.ItemStack.isSameItem(expectedHeld, stack) || expectedHeld.getCount() != stack.getCount()
                         || !marker.equals(expectedHeld.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString()))
                     throw new GameTestAssertException(label + " pickup changed item/count/marker: " + expectedHeld);
                 if (stack.is(com.juicyslew.moonstation14.item.ModItems.KITCHEN_KNIFE.get())
                         && !expectedHeld.has(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get()))
                     throw new GameTestAssertException("kitchen-knife pickup did not stage selected-insertion cooldown: " + expectedHeld);
                 var hands = fixture.queryHands(active.epoch());
                var hand = hands.hands().stream().filter(h -> h.id().equals("left")).findFirst().orElseThrow();
                body.setYRot(-90); body.setYHeadRot(-90); body.setXRot(0);
                 var thrown = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(player, new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.THROW_ITEM, "left", active.epoch(), 2, hands.revision(), null, hand.token()));
                   if (thrown.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK || thrown.hostTransition() == null) throw new GameTestAssertException(label + " throw rejected: " + thrown.reason() + " gate=" + com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow.lastPortableThrowGateForDiagnostics());
                  var output = level.getEntity(thrown.hostTransition().worldEntity());
                  if (!(output instanceof com.juicyslew.moonstation14.entities.LooseItemEntity item)) throw new GameTestAssertException("throw output was not a loose item entity");
                  thrownRef[0] = item;
                   if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(item.getItem(), expectedHeld)) throw new GameTestAssertException(label + " thrown components differ from authoritative held stack: expected=" + expectedHeld.getComponents() + " actual=" + item.getItem().getComponents());
                   if (item.getItem().getCount() != expectedHeld.getCount()) throw new GameTestAssertException(label + " throw count mismatch: expected=" + expectedHeld.getCount() + " actual=" + item.getItem().getCount());
                   if (item.getDeltaMovement().lengthSqr() == 0) throw new GameTestAssertException(label + " throw had no committed velocity: " + item.getDeltaMovement());
                  var afterThrow = fixture.queryHands(active.epoch());
                   if (afterThrow.hands().stream().anyMatch(h -> h.id().equals("left") && h.token() != null)) throw new GameTestAssertException("thrown stack remained in authoritative hand: " + afterThrow);
                   var matchingLoose = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class, body.getBoundingBox().inflate(16), e -> marker.equals(e.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString()));
                   if (matchingLoose.size() != 1 || matchingLoose.get(0) != item || item.getItem().getCount() != expectedHeld.getCount()) throw new GameTestAssertException(label + " throw duplicated/split marker-identified stack: matching=" + matchingLoose.size() + " count=" + item.getItem().getCount());
                if (LifecycleCharacterSessionControl.activeCharacterBody(player).orElseThrow().body() != body) throw new GameTestAssertException("throw detached monkey actor");
                helper.succeed();
            });
        });
    }
}
