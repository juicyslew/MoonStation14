package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.actions.BodyActionRequest;
import com.juicyslew.moonstation14.ms14.actions.BodyActionResult;
import com.juicyslew.moonstation14.ms14.actions.BodyActionService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the victim's real alternate-target self-removal route with both hands occupied. */
@PrefixGameTestTemplate(false)
@GameTestTags({"thrown-combat", "thrown-ensnare", "timed-interaction"})
public final class BolaBreakoutGameTests {
    private BolaBreakoutGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 180)
    public static void fullHandsVictimBreaksFreeFromThrownBola(GameTestHelper helper) {
        runFixture(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 180)
    public static void otherPlayerFreesThrownBola(GameTestHelper helper) {
        runFixture(helper, true);
    }

    private static void runFixture(GameTestHelper helper, boolean otherRemover) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var thrower = RegisteredServerPlayerFixture.register(level, "BolaThrower");
        var victim = RegisteredServerPlayerFixture.register(level, "BolaVictim");
        var sourceOp = new ServerOpListEntry(thrower.player().getGameProfile(), 4, true);
        var victimOp = new ServerOpListEntry(victim.player().getGameProfile(), 4, true);
        level.getServer().getPlayerList().getOps().add(sourceOp);
        level.getServer().getPlayerList().getOps().add(victimOp);
        var bodies = new net.minecraft.world.entity.LivingEntity[2];
        var loose = new com.juicyslew.moonstation14.entities.LooseItemEntity[2];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                Throwable cleanupFailure = null;
                for (var body : bodies) if (body != null && !body.isRemoved()) try { body.discard(); } catch (Throwable t) { cleanupFailure = combine(cleanupFailure, t); }
                for (var entity : loose) if (entity != null && !entity.isRemoved()) try { entity.discard(); } catch (Throwable t) { cleanupFailure = combine(cleanupFailure, t); }
                try { thrower.close(); } catch (Throwable t) { cleanupFailure = combine(cleanupFailure, t); }
                try { victim.close(); } catch (Throwable t) { cleanupFailure = combine(cleanupFailure, t); }
                try { level.getServer().getPlayerList().getOps().remove(sourceOp); } catch (Throwable t) { cleanupFailure = combine(cleanupFailure, t); }
                try { level.getServer().getPlayerList().getOps().remove(victimOp); } catch (Throwable t) { cleanupFailure = combine(cleanupFailure, t); }
                if (cleanupFailure != null) { if (info.getError() != null) info.getError().addSuppressed(cleanupFailure); else info.fail(cleanupFailure); }
            }
            private Throwable combine(Throwable accumulated, Throwable next) { if (accumulated == null) return next; accumulated.addSuppressed(next); return accumulated; }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(i); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var sourceState = RegisteredPlayerReadyDriver.readyAndQuery(thrower); net.minecraft.world.entity.LivingEntity source = sourceState.body();
                var targetState = RegisteredPlayerReadyDriver.readyAndQuery(victim); net.minecraft.world.entity.LivingEntity target = targetState.body();
                bodies[0] = source; bodies[1] = target;
                var origin = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
                 for (int x = 0; x <= 12; x++) for (int z = 0; z <= 10; z++) level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                source.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
                target.setPos(origin.getX() + 3.5, origin.getY(), origin.getZ() + .5);
                setNoAi(source); setNoAi(target); source.setYRot(-90); source.setYHeadRot(-90);
                source.setXRot(0); source.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                com.juicyslew.moonstation14.ms14.hands.live.EquipmentStepTriggerProtectionGameTests.putOwnedHandFixture(target, "left", "bola-victim-left", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
                com.juicyslew.moonstation14.ms14.hands.live.EquipmentStepTriggerProtectionGameTests.putOwnedHandFixture(target, "right", "bola-victim-right", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
                var initial = thrower.queryHands(sourceState.epoch());
                var handId = initial.activeHand();
                var marker = java.util.UUID.randomUUID().toString();
                var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BOLA.get());
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(marker));
                var sourceEpoch = initial.epoch();
                var pickupLoose = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, source.position().add(0, .3, 1), stack);
                loose[1] = pickupLoose;
                pickupLoose.setNoGravity(true);
                if (!level.addFreshEntity(pickupLoose)) throw new GameTestAssertException("bola loose item spawn failed");
                var pickup = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(thrower.player(), new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP_SELECTED, handId, sourceEpoch, 1, initial.revision(), pickupLoose.getUUID(), null));
                if (pickup.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK) throw new GameTestAssertException("bola pickup denied: " + pickup.reason());
                var carried = thrower.queryHands(sourceEpoch);
                var sourceHand = carried.hands().stream().filter(h -> h.id().equals(handId)).findFirst().orElseThrow();
                source.setYRot(-90); source.setYHeadRot(-90); source.setXRot(0);
                var thrown = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(thrower.player(),
                        com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.throwItem(handId, sourceEpoch, 2,
                                carried.revision(), sourceHand.token(), com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget.world(
                                        source.getEyePosition().add(source.getLookAngle().scale(4)))));
                if (thrown.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK || thrown.hostTransition() == null) throw new GameTestAssertException("bola throw denied: " + thrown.reason());
                var output = level.getEntity(thrown.hostTransition().worldEntity());
                if (!(output instanceof com.juicyslew.moonstation14.entities.LooseItemEntity item)) throw new GameTestAssertException("throw transition did not resolve to loose bola");
                loose[0] = item;
                var snapSounds = new java.util.concurrent.CopyOnWriteArrayList<com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundPayload>();
                victim.observeOutbound(packet -> { if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket p && p.payload() instanceof com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundPayload sound) snapSounds.add(sound); });
                var victimEpoch = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl.activeCharacterBody(victim.player()).orElseThrow().epoch();
                helper.runAfterDelay(8, () -> {
                    var contact = item.position();
                    var snapCount = snapSounds.stream().filter(s -> s.sound().equals(net.minecraft.resources.ResourceLocation.parse("moonstation14:ss14/effects/snap")) && new net.minecraft.world.phys.Vec3(s.x(), s.y(), s.z()).distanceToSqr(contact) < .25 * .25).count();
                    if (snapCount != 1) throw new GameTestAssertException("expected exactly one catch snap at contact, got " + snapCount);
                    var snap = snapSounds.stream().filter(s -> s.sound().equals(net.minecraft.resources.ResourceLocation.parse("moonstation14:ss14/effects/snap")) && new net.minecraft.world.phys.Vec3(s.x(), s.y(), s.z()).distanceToSqr(contact) < .25 * .25).findFirst().orElseThrow();
                    if (snap.volume() != 1f || snap.pitch() != 1f || snap.category() != net.minecraft.sounds.SoundSource.NEUTRAL) throw new GameTestAssertException("catch snap payload parameters incorrect: " + snap);
                    var attachment = item.attachment().orElse(null);
                    if (attachment == null || attachment.kind() != com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.ENSNARED || !target.getUUID().equals(attachment.targetUUID())) throw new GameTestAssertException("bola did not ensnare victim: " + attachment);
                    if (!com.juicyslew.moonstation14.entities.ItemAttachmentQueries.has(target, com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.ENSNARED)) throw new GameTestAssertException("ensnare attachment query did not expose victim status");
                    double walk = com.juicyslew.moonstation14.ms14.combat.throwing.EnsnaringSystem.movementMultiplier(target, false);
                    double sprint = com.juicyslew.moonstation14.ms14.combat.throwing.EnsnaringSystem.movementMultiplier(target, true);
                    if (Math.abs(walk - .5) > .001 || Math.abs(sprint - .5) > .001) throw new GameTestAssertException("unexpected ensnare movement factors=" + walk + "/" + sprint);
                    if (com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(target)) throw new GameTestAssertException("bola incorrectly stunned victim");
                    var remover = otherRemover ? thrower : victim;
                    var removerEpoch = otherRemover ? thrower.queryHands(sourceEpoch).epoch() : victimEpoch;
                    if (otherRemover) {
                        var query = remover.queryHands(removerEpoch);
                        var active = query.hands().stream().filter(h -> h.id().equals(query.activeHand())).findFirst().orElseThrow();
                        var result = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(remover.player(),
                                new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                        com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.ALTERNATE_TARGET,
                                        active.id(), removerEpoch, 3, query.revision(), item.getUUID(), active.token()));
                        if (result.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK)
                            throw new GameTestAssertException("other-player alternate-target removal denied: " + result.reason());
                    } else {
                        var query = BodyActionService.query(victim.player()).orElseThrow();
                        var action = net.minecraft.resources.ResourceLocation.parse("moonstation14:remove_attached_item");
                        var entry = query.actions().stream().filter(e -> e.actionId().equals(action)).findFirst().orElseThrow();
                        var result = BodyActionService.activate(victim.player(),
                                new BodyActionRequest(query.bodyId(), query.bodyEpoch(), query.revision(), action, entry.providerId(), 1));
                        if (result.status() != BodyActionResult.Status.ACCEPTED)
                            throw new GameTestAssertException("self-removal capability denied: " + result.status());
                    }
                    helper.runAfterDelay(otherRemover ? 39 : 69, () -> {
                        if (item.attachment().map(a -> a.kind() != com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.ENSNARED || !target.getUUID().equals(a.targetUUID())).orElse(true)
                                || Math.abs(com.juicyslew.moonstation14.ms14.combat.throwing.EnsnaringSystem.movementMultiplier(target, false) - .5) > .001) throw new GameTestAssertException("bola released before timed action completed");
                        helper.runAfterDelay(3, () -> {
                            if (item.attachment().isPresent() || com.juicyslew.moonstation14.entities.ItemAttachmentQueries.has(target, com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.ENSNARED) || Math.abs(com.juicyslew.moonstation14.ms14.combat.throwing.EnsnaringSystem.movementMultiplier(target, false) - 1) > .001) throw new GameTestAssertException("bola did not release on timed completion");
                            long finalSnapCount = snapSounds.stream().filter(s -> s.sound().equals(net.minecraft.resources.ResourceLocation.parse("moonstation14:ss14/effects/snap")) && new net.minecraft.world.phys.Vec3(s.x(), s.y(), s.z()).distanceToSqr(contact) < .25 * .25).count();
                            if (finalSnapCount != 1) throw new GameTestAssertException("catch snap count changed after removal: " + finalSnapCount);
                            if (otherRemover) {
                                var after = thrower.queryHands(sourceEpoch);
                                var transferred = after.hands().stream().filter(h -> h.id().equals(handId)).findFirst().orElseThrow();
                                if (level.getEntity(item.getUUID()) != null || transferred.token() == null || transferred.count() != 1 || !transferred.itemId().equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(com.juicyslew.moonstation14.item.ModItems.BOLA.get()).toString())) throw new GameTestAssertException("other-player catch did not transfer bola to thrower hand");
                            } else if (level.getEntity(item.getUUID()) != item || item.getItem().getCount() != 1 || !marker.equals(item.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString())) throw new GameTestAssertException("world bola identity/count/marker not conserved");
                            var hands = victim.queryHands(victimEpoch);
                            if (hands.hands().stream().filter(h -> h.id().equals("left") || h.id().equals("right")).anyMatch(h -> h.token() == null)) throw new GameTestAssertException("victim stone hand unexpectedly empty");
                            var matching = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class, source.getBoundingBox().inflate(16), e -> e.getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) && marker.equals(e.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString()));
                            if ((!otherRemover && (matching.size() != 1 || matching.get(0) != item)) || (otherRemover && !matching.isEmpty())) throw new GameTestAssertException("bola world conservation failed: matching=" + matching.size());
                            helper.succeed();
                        });
                    });
                });
            } catch (Throwable failure) { throw failure instanceof RuntimeException r ? r : new GameTestAssertException(failure.toString()); }
        });
    }
    private static void setNoAi(net.minecraft.world.entity.LivingEntity body) {
        if (body instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
        else throw new GameTestAssertException("fixture character is not a Mob");
    }
}
