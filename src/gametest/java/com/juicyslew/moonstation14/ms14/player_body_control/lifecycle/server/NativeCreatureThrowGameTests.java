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
@GameTestTags({"lifecycle", "hands", "hand-authority", "native-creature-throw", "thrown-combat", "thrown-embedding"})
public final class NativeCreatureThrowGameTests {
    private NativeCreatureThrowGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 120)
    @GameTestTags({"thrown-combat", "thrown-audio"})
    public static void adoptedMonkeyBolaThrowAndLandingAudio(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BOLA.get(), 1), "bola-audio", false);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void adoptedMonkeyThrowsOrdinaryHeldItem(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 3), "ordinary");
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void adoptedMonkeyThrowsKitchenKnife(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.KITCHEN_KNIFE.get(), 1), "kitchen-knife");
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void adoptedMonkeyThrowingKnifeHitsLivingBody(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.THROWING_KNIFE.get(), 1), "throwing-knife", 10f, 15f);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"thrown-combat", "thrown-embedding"})
    public static void adoptedMonkeyEmbeddedKnifeSurvivesTargetDiscard(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.THROWING_KNIFE.get(), 1), "terminate-knife", 10f, 15f, null, true, false, true);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"thrown-combat", "thrown-embedding"})
    public static void adoptedMonkeyDartHitsLivingBody(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.DART.get(), 1), "dart", 0f, 4f);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"thrown-combat", "thrown-armor", "thrown-embedding"})
    public static void adoptedMonkeyDartRespectsWornArmor(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.DART.get(), 1), "armored-dart", 0f, 2.52f, null, true, true);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"thrown-combat", "thrown-armor", "thrown-injection", "thrown-embedding"})
    public static void adoptedMonkeyDartSlotBlockedButArmorDamageApplies(GameTestHelper helper) {
        var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        var dart = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.DART.get(), 1);
        dart.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(java.util.Map.of(water, 200L)));
        runThrowFixture(helper, dart, "armored-dart-slot-blocked", 0f, 2.52f, water, true, true);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"thrown-combat", "thrown-injection", "thrown-embedding"})
    public static void adoptedMonkeyHypodartInjectsOnEmbed(GameTestHelper helper) {
        var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        var hypodart = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.HYPO_DART.get(), 1);
        hypodart.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(java.util.Map.of(water, 1000L)));
        runThrowFixture(helper, hypodart, "hypodart", 0f, 4f, water);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"thrown-combat", "thrown-range", "thrown-embedding"})
    public static void hypodartEmbedsBeyondPreviousThrowAimLimit(GameTestHelper helper) {
        runThrowFixture(helper, new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.HYPO_DART.get(), 1),
                "hypodart-far", 0f, 4f, null, true, false, false, 9.0, -5f);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"thrown-combat", "thrown-armor", "thrown-injection", "thrown-embedding"})
    public static void adoptedMonkeyArmoredHypodartStillInjectsFullDose(GameTestHelper helper) {
        var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        var hypodart = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.HYPO_DART.get(), 1);
        hypodart.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(java.util.Map.of(water, 1000L)));
        runThrowFixture(helper, hypodart, "armored-hypodart", 0f, 2.52f, water, true, true);
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label) {
        runThrowFixture(helper, stack, label, false);
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label, boolean impactVerification) {
        runThrowFixture(helper, stack, label, 10f, 15f, impactVerification);
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label, float expectedSlash, float expectedPiercing) {
        runThrowFixture(helper, stack, label, expectedSlash, expectedPiercing, null);
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label, float expectedSlash, float expectedPiercing, boolean impactVerification) {
        runThrowFixture(helper, stack, label, expectedSlash, expectedPiercing, null, impactVerification);
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label, float expectedSlash, float expectedPiercing,
                                        net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> doseKey) {
        runThrowFixture(helper, stack, label, expectedSlash, expectedPiercing, doseKey, true);
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label, float expectedSlash, float expectedPiercing,
                                        net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> doseKey, boolean impactVerification) {
        runThrowFixture(helper, stack, label, expectedSlash, expectedPiercing, doseKey, impactVerification, false);
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label, float expectedSlash, float expectedPiercing,
                                         net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> doseKey,
                                         boolean impactVerification, boolean armored) {
        runThrowFixture(helper, stack, label, expectedSlash, expectedPiercing, doseKey, impactVerification, armored, false);
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label, float expectedSlash, float expectedPiercing,
                                         net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> doseKey,
                                         boolean impactVerification, boolean armored, boolean terminateTargetOnEmbed) {
        runThrowFixture(helper, stack, label, expectedSlash, expectedPiercing, doseKey, impactVerification, armored, terminateTargetOnEmbed, 3.0, 0f);
    }

    private static void runThrowFixture(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, String label, float expectedSlash, float expectedPiercing,
                                         net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> doseKey,
                                         boolean impactVerification, boolean armored, boolean terminateTargetOnEmbed, double targetDistance, float pitch) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "NativeMonkeyThrow");
        var controlledSounds = new java.util.concurrent.CopyOnWriteArrayList<com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundPayload>();
        fixture.observeOutbound(packet -> { if (packet instanceof ClientboundCustomPayloadPacket p && p.payload() instanceof com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundPayload sound) controlledSounds.add(sound); });
        var op = new ServerOpListEntry(fixture.player().getGameProfile(), 4, true);
        level.getServer().getPlayerList().getOps().add(op);
        var looseRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var thrownRef = new com.juicyslew.moonstation14.entities.LooseItemEntity[1];
        var targetRef = new net.minecraft.world.entity.LivingEntity[1];
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("moonstation14:monkey"));
        if (type == null || !(type.create(level) instanceof Mob body)) throw new GameTestAssertException("monkey entity type unavailable");
        var origin = helper.absolutePos(new net.minecraft.core.BlockPos(5, 1, 5));
         for (int x = 0; x <= 12; x++) for (int z = 0; z <= 10; z++) level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        if (!level.addFreshEntity(body)) throw new GameTestAssertException("monkey spawn failed");
        var character = com.juicyslew.moonstation14.ms14.character.ModCharacters.characterForHost(level,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(body.getType())).orElseThrow();
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enroll(body, level, character);
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
             private void cleanup(net.minecraft.gametest.framework.GameTestInfo info) {
                 Throwable failure = null;
                 for (var entity : new net.minecraft.world.entity.Entity[]{looseRef[0], thrownRef[0], targetRef[0], body}) {
                     try { if (entity != null && !entity.isRemoved()) entity.discard(); }
                     catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                 }
                 try { fixture.close(); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                 try { level.getServer().getPlayerList().getOps().remove(op); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
                 if (failure != null) { if (info.getError() != null) info.getError().addSuppressed(failure); else info.fail(failure); }
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
                 if (impactVerification) {
                     var target = EntityType.VILLAGER.create(level);
                     if (target == null) throw new GameTestAssertException("impact target creation failed");
                     target.setNoAi(true);
                      target.setPos(body.position().add(targetDistance, 0, 0));
                     if (!level.addFreshEntity(target)) throw new GameTestAssertException("impact target spawn failed");
                     targetRef[0] = target;
                     var targetProfile = com.juicyslew.moonstation14.ms14.character.ModCharacters.characterForHost(level,
                             net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType())).orElseThrow();
                     com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enroll(target, level, targetProfile);
                     if (armored) {
                         com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(target).orElseThrow();
                          com.juicyslew.moonstation14.ms14.hands.live.EquipmentStepTriggerProtectionGameTests.equipOwnedFixture(target, "left", "outerClothing", "armor-vest", new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.ARMOR_VEST.get()));
                          com.juicyslew.moonstation14.ms14.hands.live.EquipmentStepTriggerProtectionGameTests.equipOwnedFixture(target, "left", "head", "basic-helmet", new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BASIC_HELMET.get()));
                         com.juicyslew.moonstation14.ms14.damage.DamageSystem.reconcile(target);
                     }
                 }
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
                 body.setYRot(-90); body.setYHeadRot(-90); body.setXRot(pitch);
                  var throwTarget = com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget.world(
                          body.getEyePosition().add(body.getLookAngle().scale(4)));
                  var thrown = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(player,
                          com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.throwItem(
                                  "left", active.epoch(), 2, hands.revision(), hand.token(), throwTarget));
                   if (thrown.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK || thrown.hostTransition() == null) throw new GameTestAssertException(label + " throw rejected: " + thrown.reason() + " gate=" + com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow.lastPortableThrowGateForDiagnostics());
                  var output = level.getEntity(thrown.hostTransition().worldEntity());
                  if (!(output instanceof com.juicyslew.moonstation14.entities.LooseItemEntity item)) throw new GameTestAssertException("throw output was not a loose item entity");
                   thrownRef[0] = item;
                    var bolaLaunchPosition = item.position();
                   if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(item.getItem(), expectedHeld)) throw new GameTestAssertException(label + " thrown components differ from authoritative held stack: expected=" + expectedHeld.getComponents() + " actual=" + item.getItem().getComponents());
                   if (item.getItem().getCount() != expectedHeld.getCount()) throw new GameTestAssertException(label + " throw count mismatch: expected=" + expectedHeld.getCount() + " actual=" + item.getItem().getCount());
                   if (item.getDeltaMovement().lengthSqr() == 0) throw new GameTestAssertException(label + " throw had no committed velocity: " + item.getDeltaMovement());
                  var afterThrow = fixture.queryHands(active.epoch());
                   if (afterThrow.hands().stream().anyMatch(h -> h.id().equals("left") && h.token() != null)) throw new GameTestAssertException("thrown stack remained in authoritative hand: " + afterThrow);
                   var matchingLoose = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class, body.getBoundingBox().inflate(16), e -> marker.equals(e.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString()));
                   if (matchingLoose.size() != 1 || matchingLoose.get(0) != item || item.getItem().getCount() != expectedHeld.getCount()) throw new GameTestAssertException(label + " throw duplicated/split marker-identified stack: matching=" + matchingLoose.size() + " count=" + item.getItem().getCount());
                if (LifecycleCharacterSessionControl.activeCharacterBody(player).orElseThrow().body() != body) throw new GameTestAssertException("throw detached monkey actor");
                 if (impactVerification) {
                     var impactTarget = targetRef[0];
                     helper.runAfterDelay(8, () -> {
                         float slash = NativeCreatureCombatGameTests.typed(impactTarget, com.juicyslew.moonstation14.ms14.damage.DamageKeys.SLASH);
                         float piercing = NativeCreatureCombatGameTests.typed(impactTarget, com.juicyslew.moonstation14.ms14.damage.DamageKeys.PIERCING);
                         var attachment = item.attachment().orElse(null);
                          if (Math.abs(slash - expectedSlash) > .011f || Math.abs(piercing - expectedPiercing) > .011f
                                 || attachment == null || attachment.kind() != com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.EMBEDDED
                                 || !impactTarget.getUUID().equals(attachment.targetUUID())
                                 || com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.resolveForStack(item.getItem(), false).isEmpty())
                              throw new GameTestAssertException("physical throwing-knife contact failed: slash=" + slash + " piercing=" + piercing + " attachment=" + attachment);
                          if (terminateTargetOnEmbed) {
                              var source = item;
                              var itemId = source.getUUID();
                              var capturedStack = source.getItem().copy();
                              var capturedMarker = capturedStack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                              impactTarget.discard();
                              helper.runAfterDelay(2, () -> {
                                  var matches = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                                          source.getBoundingBox().inflate(16), e -> capturedMarker != null
                                                  && capturedMarker.equals(e.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)));
                                  if (level.getEntity(itemId) != source || source.isRemoved() || source.attachment().isPresent()
                                          || source.activeThrownCombat() || !net.minecraft.world.item.ItemStack.matches(capturedStack, source.getItem())
                                          || source.getItem().getCount() != 1 || capturedMarker == null || matches.size() != 1 || matches.get(0) != source)
                                      throw new GameTestAssertException("embedded knife target-discard conservation failed: source=" + source
                                              + " attachment=" + source.attachment() + " matches=" + matches.size());
                                  helper.succeed();
                              });
                              return;
                          }
                          if (label.equals("hypodart-far")) {
                              if (item.attachment().isEmpty() || item.activeThrownCombat()
                                      || !impactTarget.getUUID().equals(item.attachment().orElseThrow().targetUUID()))
                                  throw new GameTestAssertException("far hypodart contact did not embed/finish: attachment=" + item.attachment());
                              helper.succeed();
                              return;
                          }
                         if (doseKey != null) {
                             var sourceSolution = item.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
                             var bloodstream = com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess.inspect(impactTarget).orElseThrow(
                                     () -> new GameTestAssertException("embedded target has no inspectable bloodstream"));
                             long delivered = bloodstream.units().getOrDefault(doseKey, 0L);
                              boolean slotBlockedDart = label.equals("armored-dart-slot-blocked");
                              if (sourceSolution == null || (slotBlockedDart
                                      ? com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.total(sourceSolution.centContents().values()) != 200L || delivered != 0L
                                      : com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.total(sourceSolution.centContents().values()) != 0L || delivered <= 0L || delivered > 1000L)
                                      || item.getItem().getCount() != 1 || !marker.equals(item.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString()))
                                  throw new GameTestAssertException("dart dose conservation/slot gate failed: source=" + sourceSolution + " targetWater=" + delivered);
                              if (slotBlockedDart) {
                                  helper.runAfterDelay(2, () -> verifyRetrieval(helper, fixture, body, active.epoch(), item, marker, 10, false));
                                  return;
                              }
                             helper.runAfterDelay(2, () -> {
                                 var later = com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess.inspect(impactTarget).orElseThrow();
                                 long laterWater = later.units().getOrDefault(doseKey, 0L);
                                 if (laterWater > delivered || com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.total(item.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get()).centContents().values()) != 0L
                                         || item.getItem().getCount() != 1 || item.attachment().isEmpty())
                                     throw new GameTestAssertException("hypodart repeated dose/source mutation: first=" + delivered + " later=" + laterWater);
                                  verifyRetrieval(helper, fixture, body, active.epoch(), item, marker, 10, true);
                             });
                             return;
                         }
                          verifyRetrieval(helper, fixture, body, active.epoch(), item, marker,
                                  stack.is(com.juicyslew.moonstation14.item.ModItems.THROWING_KNIFE.get()) ? 30 : 10, doseKey != null);
                     });
                 } else if (label.equals("bola-audio")) {
                     var thrownId = item.getUUID();
                     helper.runAfterDelay(60, () -> {
                         var throwSound = com.juicyslew.moonstation14.sounds.ModSounds.BOLA_THROW.get().getLocation();
                         var landSound = com.juicyslew.moonstation14.sounds.ModSounds.BOLA_LAND.get().getLocation();
                           var ownThrows = controlledSounds.stream().filter(s -> s.sound().equals(throwSound)
                                   && new Vec3(s.x(), s.y(), s.z()).distanceToSqr(bolaLaunchPosition) < 1.0e-6).toList();
                          var source = level.getEntity(thrownId);
                          var flightBounds = net.minecraft.world.phys.AABB.ofSize(bolaLaunchPosition, 1, 1, 1).minmax(item.getBoundingBox()).inflate(.5);
                          var ownLands = controlledSounds.stream().filter(s -> s.sound().equals(landSound)
                                  && flightBounds.contains(s.x(), s.y(), s.z())).toList();
                            long drops = controlledSounds.stream().filter(s -> s.sound().equals(com.juicyslew.moonstation14.sounds.ModSounds.ITEM_DROP.get().getLocation())
                                  && flightBounds.contains(s.x(), s.y(), s.z())).count();
                          var validThrow = ownThrows.size() == 1 && ownThrows.getFirst().volume() == 1f && ownThrows.getFirst().pitch() == 1f
                                   && ownThrows.getFirst().category() == net.minecraft.sounds.SoundSource.NEUTRAL;
                          var validLand = ownLands.size() == 1 && ownLands.getFirst().volume() == 1f && ownLands.getFirst().pitch() == 1f
                                   && ownLands.getFirst().category() == net.minecraft.sounds.SoundSource.NEUTRAL;
                           if (!validThrow || !validLand || drops != 0 || source != item || item.activeThrownCombat() || !item.onGround())
                                throw new GameTestAssertException("Bola audio/settlement regression: ownThrow=" + ownThrows + " ownLand=" + ownLands + " ownSceneDrops=" + drops + " sameSource=" + (source == item) + " active=" + item.activeThrownCombat() + " onGround=" + item.onGround() + " observed=" + controlledSounds.stream().filter(s -> s.sound().equals(throwSound) || s.sound().equals(landSound) || s.sound().equals(com.juicyslew.moonstation14.sounds.ModSounds.ITEM_DROP.get().getLocation())).limit(3).toList());
                         helper.succeed();
                     });
                 } else helper.succeed();
            });
        });
    }

    private static void verifyRetrieval(GameTestHelper helper, RegisteredServerPlayerFixture fixture, Mob body,
                                        long epoch, com.juicyslew.moonstation14.entities.LooseItemEntity item,
                                        String marker, int expectedTicks, boolean expectEmptySolution) {
        var before = item.getItem().copy();
        var snapshot = fixture.queryHands(epoch);
        var hand = snapshot.hands().stream().filter(h -> h.id().equals(snapshot.activeHand())).findFirst()
                .orElseThrow(() -> new GameTestAssertException("active hand missing for retrieval: " + snapshot));
        var result = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),
                new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                        com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.ALTERNATE_TARGET,
                        snapshot.activeHand(), epoch, 3, snapshot.revision(), item.getUUID(), hand.token()));
        if (result.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK)
            throw new GameTestAssertException("retrieval request denied: " + result.reason() + " body=" + body.position());
        helper.runAfterDelay(expectedTicks - 1, () -> {
            if (item.attachment().isEmpty() || !(item.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) || serverLevel.getEntity(item.getUUID()) != item)
                throw new GameTestAssertException("retrieval detached before duration: attachment=" + item.attachment()
                        + " sourceExists=" + !item.isRemoved() + " sourcePos=" + item.position() + " bodyPos=" + body.position()
                        + " hands=" + body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()));
        });
        helper.runAfterDelay(expectedTicks + 2, () -> {
            var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var recoveredStacks = snapshot.hands().stream().map(h -> hands == null ? null : hands.stackCopy(h.id()).orElse(null))
                    .filter(stack -> stack != null && marker.equals(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) == null
                            ? "" : stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString())).toList();
            var recovered = recoveredStacks.size() == 1 ? recoveredStacks.getFirst() : null;
            if (recovered == null || recovered.getCount() != 1)
                throw new GameTestAssertException("retrieval did not preserve unique marked item: matches=" + recoveredStacks
                        + " attachment=" + item.attachment() + " sourceExists=" + !item.isRemoved() + " sourcePos=" + item.position()
                        + " bodyPos=" + body.position() + " hands=" + hands);
            var beforeCopy = before.copy();
            var afterCopy = recovered.copy();
            beforeCopy.remove(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get());
            afterCopy.remove(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get());
            if (!net.minecraft.world.item.ItemStack.matches(beforeCopy, afterCopy))
                throw new GameTestAssertException("retrieved stack components differ from thrown source: before="
                        + beforeCopy.getComponents() + " after=" + afterCopy.getComponents());
            var solution = recovered.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            if (expectEmptySolution && (solution == null || com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.total(solution.centContents().values()) != 0L))
                throw new GameTestAssertException("retrieved hypodart solution was not empty: " + solution);
            if (!item.isRemoved()) throw new GameTestAssertException("retrieval left source world entity present");
            helper.succeed();
        });
    }

}
