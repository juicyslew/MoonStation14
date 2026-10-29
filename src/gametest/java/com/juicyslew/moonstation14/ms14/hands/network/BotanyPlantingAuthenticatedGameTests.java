package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.botany.BotanyHoeGameTestSupport;
import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.PlantInstance;
import com.juicyslew.moonstation14.ms14.botany.PlantSeedReference;
import com.juicyslew.moonstation14.ms14.botany.GeneticValue;
import com.juicyslew.moonstation14.ms14.botany.SeedItemProfile;
import com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany"})
public final class BotanyPlantingAuthenticatedGameTests {
    private BotanyPlantingAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200, batch = "catalog-botany-seed-item-composition")
    @GameTestTags({"botany-planting"})
    public static void registeredPlayerPlantsOnceAndOccupiedTrayRejectsSecondSeed(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-planting-v2");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                ServerLevel level = (ServerLevel) helper.getLevel();
                BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
                level.setBlock(pos, ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
                helper.runAtTickTime(2, () -> {
                    try {
                        var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(pos);
                        require(tray != null && tray.plantState() != null, "tray tick initializes resolved empty profile");
                        var body = owner.body(); body.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 1.5);
                         var initialHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                         String handId = initialHands.activeHand();
                        ItemStack packet = new ItemStack(ModItems.WHEAT_SEEDS.get());
                        var loose = LooseItemEntity.create(level, body.position().add(0, .1, .8), packet);
                        loose.setNoGravity(true); require(level.addFreshEntity(loose), "seed packet enters world");
                        aimAtItem(body, loose);
                        var pickup = BodyHandItemTransfer.pickupSelected(fixture.player(), loose.getUUID(), handId,
                                body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).revision(), ignored -> {});
                        require(BodyHandItemTransfer.Result.SUCCESS == pickup, "normal pickup obtains first packet; result=" + pickup
                                + " eye=" + body.getEyePosition() + " look=" + body.getLookAngle() + " item=" + loose.position()
                                + " selected=" + com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(
                                        level, body, loose.getUUID(), com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH));
                        var first = fixture.queryHands(owner.epoch());
                        var firstHand = first.hands().stream().filter(h -> h.id().equals(handId)).findFirst().orElseThrow();
                        var firstHit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        var firstRequest = new BodyBlockUseRequest(owner.epoch(), 1, first.revision(), handId, firstHand.token(), pos,
                                firstHit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        require(BodyBlockUseResult.Outcome.PLACED == BodyHandActionNetworking.handleBlockUse(fixture.player(), firstRequest), "normal admitted seed use plants");
                        var planted = tray.plantState();
                        require(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat").equals(planted.seed()), "planted wheat profile");
                        require(planted.age() == 1f && planted.health() == 100f, "plant starts age one at resolved endurance");
                         var empty = fixture.queryHands(owner.epoch());
                         require(empty.hands().stream().filter(h -> h.id().equals(handId)).findFirst().orElseThrow().token() == null, "one packet consumed");
                         var emptyHit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                         var emptyUse = new BodyBlockUseRequest(owner.epoch(), 2, empty.revision(), handId, null, pos,
                                 emptyHit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                         require(BodyHandActionNetworking.handleBlockUse(fixture.player(), emptyUse) == BodyBlockUseResult.Outcome.DENIED,
                                 "empty-hand ordinary USE is safely denied before harvest exists");

                         var secondLoose = LooseItemEntity.create(level, body.position().add(0, .1, .8), packet.copy());
                        secondLoose.setNoGravity(true); require(level.addFreshEntity(secondLoose), "second seed enters world");
                        aimAtItem(body, secondLoose);
                        var beforeSecondPickup = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        require(BodyHandItemTransfer.Result.SUCCESS == BodyHandItemTransfer.pickupSelected(fixture.player(), secondLoose.getUUID(), handId,
                                beforeSecondPickup.revision(), ignored -> {}), "normal pickup obtains second packet");
                        var currentHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        var second = fixture.queryHands(owner.epoch());
                        var secondHand = second.hands().stream().filter(h -> h.id().equals(handId)).findFirst().orElseThrow();
                        var secondHit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        var secondRequest = new BodyBlockUseRequest(owner.epoch(), 2, second.revision(), handId, secondHand.token(), pos,
                                secondHit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        require(BodyBlockUseResult.Outcome.DENIED == BodyHandActionNetworking.handleBlockUse(fixture.player(), secondRequest), "occupied tray rejects second planting");
                        require(secondHand.token().equals(currentHands.token(handId).orElseThrow()), "rejection preserves held packet token");
                        require(planted.equals(tray.plantState()), "rejection preserves tray plant");
                        helper.succeed();
                    } catch (Throwable failure) { helper.fail("botany admitted planting fixture: " + failure); }
                    finally { fixture.close(); }
                });
            } catch (Throwable failure) { fixture.close(); helper.fail("botany player readiness: " + failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    @GameTestTags({"botany-seed-item-composition", "merge-main-remaining", "merge-main-stage-two"})
    public static void cocoaBeansRequireExplicitSeedItemAdmission(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-seed-item-composition-v1");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                ServerLevel level = (ServerLevel) helper.getLevel();
                BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
                level.setBlock(pos, ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
                helper.runAtTickTime(2, () -> {
                    try {
                        var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(pos);
                        require(tray != null && tray.plantState() != null, "tray tick initializes resolved empty profile");
                        var body = owner.body(); body.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 1.5);
                        String handId = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).handIds().getFirst();
                        ItemStack beans = new ItemStack(ModItems.FOOD_COCOA_BEANS.get());
                        var seed = new PlantSeedReference(new GeneticValue(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat"),
                                com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.serverLookup(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat"))), true);
                        com.juicyslew.moonstation14.ms14.botany.GeneticCarriers.attach(beans, seed);
                        var replacement = LooseItemEntity.create(level, body.position().add(0, .1, .8), beans);
                        replacement.setNoGravity(true); require(level.addFreshEntity(replacement), "genetic cocoa beans enter world");
                        aimAtItem(body, replacement);
                        var state = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        require(BodyHandItemTransfer.Result.SUCCESS == BodyHandItemTransfer.pickupSelected(fixture.player(), replacement.getUUID(), handId, state.revision(), ignored -> {}), "pickup cocoa beans with valid wheat genetics");
                             var manager = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
                             var type = ModSeedItemProfiles.TYPE;
                             var previous = manager.snapshot(type);
                             var priorPlant = tray.plantState();
                             var cleanupOnce = new java.util.concurrent.atomic.AtomicBoolean();
                             Runnable cleanup = () -> {
                                 if (!cleanupOnce.compareAndSet(false, true)) return;
                                 try {
                                     var rawPrevious = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
                                     previous.asMap().forEach((key, value) -> rawPrevious.put(key, type.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject()));
                                     manager.reload(type, rawPrevious);
                                 } finally { fixture.close(); }
                             };
                              helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                                  @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
                                  @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info,
                                          net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
                                  @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
                                  @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo info,
                                          net.minecraft.gametest.framework.GameTestInfo rerunInfo,
                                          net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
                              });
                             try {
                            var snapshot = fixture.queryHands(owner.epoch());
                             var hand = snapshot.hands().stream().filter(value -> value.id().equals(handId)).findFirst().orElseThrow();
                             var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                             ItemStack heldBeforeDenial = liveHands.stackCopy(handId).orElseThrow();
                              var geneticsBeforeDenial = heldBeforeDenial.get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get());
                             int countBeforeDenial = heldBeforeDenial.getCount();
                             long revisionBeforeDenial = liveHands.revision();
                              var tokenBeforeDenial = liveHands.token(handId).orElseThrow();
                              var referenceBeforeDenial = tray.plantState();
                              var genesBeforeDenial = referenceBeforeDenial.genetics();
                              long plantRevisionBeforeDenial = referenceBeforeDenial.revision();
                               var seedReferenceBeforeDenial = tray.plantState().seedReference();
                              var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                             require(liveHands.activeHand().equals(handId), "cocoa USE source is the current active hand");
                             require(BodyBlockUseTargetProbe.probe(level, body, pos, hit.getDirection()) != null,
                                     "unprofiled cocoa USE target is admitted");
                             var denied = new BodyBlockUseRequest(owner.epoch(), 1, snapshot.revision(), handId, hand.token(), pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                            require(BodyHandActionNetworking.handleBlockUse(fixture.player(), denied) == BodyBlockUseResult.Outcome.DENIED, "unprofiled cocoa item denied on ordinary USE");
                             var afterDenialHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                              require(tray.plantState() == priorPlant && tray.plantState() == referenceBeforeDenial
                                      && tray.plantState().revision() == plantRevisionBeforeDenial
                                      && java.util.Objects.equals(tray.plantState().genetics(), genesBeforeDenial)
                                       && seedReferenceBeforeDenial == null && tray.plantState().seedReference() == null && tray.plantState().seed() == null,
                                      "denial preserves tray instance/revision and nullable empty genetics/seed references");
                             ItemStack heldAfterDenial = afterDenialHands.stackCopy(handId).orElseThrow();
                             require(ItemStack.matches(heldBeforeDenial, heldAfterDenial) && heldBeforeDenial.getComponents().equals(heldAfterDenial.getComponents())
                                      && heldAfterDenial.getCount() == countBeforeDenial && heldAfterDenial.get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get()).equals(geneticsBeforeDenial), "denial preserves actual held stack, components, count, and genetics");
                             require(afterDenialHands.revision() == revisionBeforeDenial && afterDenialHands.token(handId).orElseThrow().equals(tokenBeforeDenial), "denial preserves live-hand revision and token");

                            var encoded = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
                            previous.asMap().forEach((key, value) -> encoded.put(key, type.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject()));
                            encoded.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.FOOD_COCOA_BEANS.get()), type.codec().encodeStart(JsonOps.INSTANCE, new SeedItemProfile(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat"))).getOrThrow().getAsJsonObject());
                             manager.reload(type, encoded);
                             var resolvedProfile = ModSeedItemProfiles.serverLookup(
                                     BuiltInRegistries.ITEM.getKey(ModItems.FOOD_COCOA_BEANS.get()));
                             var heldForAdmission = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                     .stackCopy(handId).orElseThrow();
                             var resolvedPacket = com.juicyslew.moonstation14.ms14.botany.GeneticCarriers.resolveSeedPacket(heldForAdmission);
                              require(resolvedProfile != null && seed.current().species().equals(resolvedProfile.seedId())
                                              && resolvedPacket != null && resolvedPacket.genetics().equals(seed.current()),
                                      "resolved cocoa admission: profile=" + (resolvedProfile != null)
                                              + " species=" + (resolvedProfile == null ? "none" : resolvedProfile.seedId())
                                              + " packet=" + (resolvedPacket != null)
                                              + " packetSpecies=" + (resolvedPacket == null ? "none" : resolvedPacket.genetics().species()));
                               var admittedSnapshot = fixture.queryHands(owner.epoch());
                               var admittedHand = admittedSnapshot.hands().stream().filter(value -> value.id().equals(handId)).findFirst().orElseThrow();
                               var currentAuthority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElse(null);
                               var currentHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                               var currentHeld = currentHands == null ? null : currentHands.stackCopy(handId).orElse(null);
                               String guards = "issued=" + (currentAuthority != null && com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.hasIssued(fixture.player(), currentAuthority))
                                       + ",act=" + com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body)
                                       + ",registered=" + (currentAuthority != null && currentAuthority.body() == body && level.getEntity(body.getUUID()) == body)
                                       + ",rev=" + (currentHands == null ? -1 : currentHands.revision()) + "/" + admittedSnapshot.revision()
                                       + ",active=" + (currentHands != null && currentHands.activeHand().equals(handId))
                                       + ",handIds=" + (currentHands != null && currentAuthority != null && currentHands.handIds().equals(currentAuthority.handIds()))
                                       + ",token=" + (currentHands != null && java.util.Objects.equals(currentHands.token(handId).orElse(null), admittedHand.token()))
                                       + ",stack=" + (currentHands != null && currentHeld != null && ItemStack.matches(currentHeld, currentHands.stackCopy(handId).orElse(null)))
                                       + ",tray=" + (tray.isLoadedValid() && tray.plantState() != null && tray.plantState().seed() == null);
                               var admitted = new BodyBlockUseRequest(owner.epoch(), 2, admittedSnapshot.revision(), handId, admittedHand.token(), pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                               var admittedOutcome = BodyHandActionNetworking.handleBlockUse(fixture.player(), admitted);
                                require(admittedOutcome == BodyBlockUseResult.Outcome.PLACED,
                                        "explicit cocoa USE outcome=" + admittedOutcome + " trayRev=" + tray.plantState().revision()
                                                + " token=" + liveHands.token(handId).isPresent() + " guards=" + guards);
                              require(tray.plantState() == referenceBeforeDenial && tray.plantState().genetics().equals(seed.current())
                                      && tray.plantState().genetics().species().equals(seed.current().species()), "explicit profile plants source carried seed genetics in-place");
                               require(tray.plantState().revision() > plantRevisionBeforeDenial && tray.plantState().seedReference() == seed
                                      && tray.plantState().age() == 1f && tray.plantState().health() == 100f,
                                      "planted instance advances in-place with carried seed reference, age one, and source endurance health");
                             var consumed = fixture.queryHands(owner.epoch());
                             var consumedHand = consumed.hands().stream().filter(value -> value.id().equals(handId)).findFirst().orElseThrow();
                             require(consumedHand.token() == null && body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(handId).isEmpty(), "one cocoa bean consumed and hand ownership cleared");
                              var replayPlant = tray.plantState(); var replayPlantRevision = replayPlant.revision();
                               var replaySeedReference = tray.plantState().seedReference(); var replayGenes = replayPlant.genetics();
                             var replayHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                             long replayRevision = replayHands.revision(); var replayToken = replayHands.token(handId);
                             require(BodyHandActionNetworking.handleBlockUse(fixture.player(), admitted) == BodyBlockUseResult.Outcome.DENIED, "exact request replay denied by stale revision/token admission (no duplicate-sequence cache)");
                             var afterReplayHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                              require(tray.plantState() == replayPlant && tray.plantState().revision() == replayPlantRevision
                                       && tray.plantState().seedReference() == replaySeedReference && tray.plantState().genetics().equals(replayGenes), "stale replay leaves planted instance, revision, seed reference, and genes unchanged");
                              require(afterReplayHands.revision() == replayRevision && afterReplayHands.token(handId).equals(replayToken) && afterReplayHands.stackCopy(handId).isEmpty(), "stale replay leaves hand revision and cleared ownership unchanged");
                             com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> {
                                 try {
                                     var handsNow = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                     ItemStack secondBeans = new ItemStack(ModItems.FOOD_COCOA_BEANS.get());
                                     com.juicyslew.moonstation14.ms14.botany.GeneticCarriers.attach(secondBeans, seed);
                                     var secondEntity = LooseItemEntity.create(level, body.position().add(0, .1, .8), secondBeans);
                                     secondEntity.setNoGravity(true); require(level.addFreshEntity(secondEntity), "second genetic cocoa beans enter world");
                                     aimAtItem(body, secondEntity);
                                     require(BodyHandItemTransfer.Result.SUCCESS == BodyHandItemTransfer.pickupSelected(fixture.player(), secondEntity.getUUID(), handId, handsNow.revision(), ignored -> {}), "normal pickup obtains second cocoa beans");
                                     var occupiedPlant = tray.plantState(); var occupiedRev = occupiedPlant.revision();
                                     var occupiedSeed = occupiedPlant.seedReference(); var occupiedGenes = occupiedPlant.genetics();
                                     float water = occupiedPlant.water(), nutrition = occupiedPlant.nutrition();
                                     float occupiedHealth = occupiedPlant.health(), occupiedAge = occupiedPlant.age();
                                     var occupiedSnapshot = fixture.queryHands(owner.epoch());
                                     var occupiedHand = occupiedSnapshot.hands().stream().filter(value -> value.id().equals(handId)).findFirst().orElseThrow();
                                     var actualBefore = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                     var stackBefore = actualBefore.stackCopy(handId).orElseThrow();
                                     long handRev = actualBefore.revision(); var token = actualBefore.token(handId).orElseThrow();
                                     var occupiedHit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                                     var occupiedRequest = new BodyBlockUseRequest(owner.epoch(), 3, occupiedSnapshot.revision(), handId, occupiedHand.token(), pos, occupiedHit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                                     require(BodyHandActionNetworking.handleBlockUse(fixture.player(), occupiedRequest) == BodyBlockUseResult.Outcome.DENIED, "second valid cocoa seed denied by occupied tray");
                                     var after = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                     require(tray.plantState() == occupiedPlant && occupiedPlant.revision() == occupiedRev && occupiedPlant.seedReference() == occupiedSeed && occupiedPlant.genetics().equals(occupiedGenes)
                                             && occupiedPlant.water() == water && occupiedPlant.nutrition() == nutrition && occupiedPlant.health() == occupiedHealth && occupiedPlant.age() == occupiedAge,
                                             "occupied denial preserves tray identity, state, resources, and growth");
                                     require(ItemStack.matches(stackBefore, after.stackCopy(handId).orElseThrow()) && stackBefore.getComponents().equals(after.stackCopy(handId).orElseThrow().getComponents())
                                             && after.revision() == handRev && after.token(handId).orElseThrow().equals(token), "occupied denial preserves actual held item and live-hand authority");
                                     helper.succeed();
                                 } catch (Throwable failure) { helper.testInfo.fail(failure); }
                                 finally { cleanup.run(); }
                             });
                         } finally {
                             // The delayed occupied-tray phase owns cleanup.
                         }
                     } catch (Throwable failure) { helper.testInfo.fail(failure); fixture.close(); }
                     finally { /* callback owns fixture/profile through the next natural tick */ }
                });
            } catch (Throwable failure) { fixture.close(); helper.fail("botany seed item readiness: " + failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    @GameTestTags({"botany-hoe"})
    public static void registeredPlayerHoeClearsWeedsAndPublishesRevision(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-hoe-v1");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                ServerLevel level = (ServerLevel) helper.getLevel();
                BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
                level.setBlock(pos, ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
                helper.runAtTickTime(2, () -> {
                    try {
                        var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(pos);
                        require(tray != null && tray.plantState() != null, "tray initialized");
                        var body = owner.body(); body.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 1.5);
                        var plant = tray.plantState();
                        BotanyHoeGameTestSupport.setWeeds(tray, 3);
                        long beforeRevision = plant.revision();
                        float beforeWater = plant.water(), beforeNutrition = plant.nutrition(), beforeToxins = plant.toxins();
                        String handId = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).handIds().getFirst();
                        var hoe = LooseItemEntity.create(level, body.position().add(0, .1, .8), new ItemStack(ModItems.BOTANY_HOE.get()));
                        hoe.setNoGravity(true); require(level.addFreshEntity(hoe), "hoe enters world");
                        aimAtItem(body, hoe);
                        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        require(BodyHandItemTransfer.Result.SUCCESS == BodyHandItemTransfer.pickupSelected(fixture.player(), hoe.getUUID(), handId, hands.revision(), ignored -> {}), "normal pickup obtains hoe");
                        var snapshot = fixture.queryHands(owner.epoch());
                        var hand = snapshot.hands().stream().filter(value -> value.id().equals(handId)).findFirst().orElse(null);
                        require(hand != null, "authoritative query includes active hoe hand; reason=" + snapshot.reason());
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        var request = new BodyBlockUseRequest(owner.epoch(), 1, snapshot.revision(), handId, hand.token(), pos,
                                hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        require(BodyBlockUseResult.Outcome.PLACED == BodyHandActionNetworking.handleBlockUse(fixture.player(), request), "ordinary admitted USE hoes tray");
                        require(tray.plantState() == plant, "plant identity preserved");
                        require(plant.weeds() == 0 && plant.water() == beforeWater && plant.nutrition() == beforeNutrition
                                && plant.toxins() == beforeToxins, "hoe clears weeds without changing other plant resources");
                        require(plant.revision() > beforeRevision, "hoe advances plant revision");
                        var update = tray.getUpdateTag(level.registryAccess());
                        var decoded = PlantInstance.CODEC.parse(NbtOps.INSTANCE, update.get("PlantState")).getOrThrow();
                        require(decoded.weeds() == 0 && decoded.revision() == plant.revision(), "published update tag contains cleared weeds and current revision");
                        helper.succeed();
                    } catch (Throwable failure) { helper.fail("botany authenticated hoe fixture: " + failure); }
                    finally { fixture.close(); }
                });
            } catch (Throwable failure) { fixture.close(); helper.fail("botany hoe player readiness: " + failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 180)
    @GameTestTags({"botany-watering"})
    public static void registeredPlayerWatersTrayThroughOrdinaryUse(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-watering-v1");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                ServerLevel level = (ServerLevel) helper.getLevel();
                BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
                level.setBlock(pos, ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
                helper.runAtTickTime(2, () -> {
                    try {
                        var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(pos);
                        require(tray != null && tray.plantState() != null, "tray initialized");
                        var body = owner.body(); body.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 1.5);
                        String handId = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).handIds().getFirst();
                        var water = ModReagents.createKey("water");
                        var nutrient = ModReagents.createKey("eznutrient");
                        ItemStack filled = new ItemStack(ModItems.BEAKER.get());
                        filled.set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(java.util.Map.of(water, 500L, nutrient, 500L)));
                        var loose = LooseItemEntity.create(level, body.position().add(0, .1, .8), filled);
                        loose.setNoGravity(true); require(level.addFreshEntity(loose), "filled beaker enters world");
                        aimAtItem(body, loose);
                        var beforeHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        require(BodyHandItemTransfer.Result.SUCCESS == BodyHandItemTransfer.pickupSelected(fixture.player(), loose.getUUID(), handId, beforeHands.revision(), ignored -> {}), "ordinary pickup obtains filled beaker");
                        var snapshot = fixture.queryHands(owner.epoch());
                        var hand = snapshot.hands().stream().filter(value -> value.id().equals(handId)).findFirst().orElseThrow();
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        var request = new BodyBlockUseRequest(owner.epoch(), 1, snapshot.revision(), handId, hand.token(), pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), request) == BodyBlockUseResult.Outcome.PLACED, "ordinary admitted USE waters tray");
                        var soil = tray.soilSolution().snapshotUnits();
                        require(soil.getOrDefault(water, 0L) == 250 && soil.getOrDefault(nutrient, 0L) == 250, "exact proportional 250-cent transfer to soil");
                        var afterHands = fixture.queryHands(owner.epoch());
                        var afterHand = afterHands.hands().stream().filter(value -> value.id().equals(handId)).findFirst().orElseThrow();
                        var sourceContents = afterHand.presentation().stack().get(ModDataComponents.REAGENT.get()).centContents();
                        require(sourceContents.getOrDefault(water, 0L) == 250
                                && sourceContents.getOrDefault(nutrient, 0L) == 250, "exact source decrement");
                        require(tray.plantState().water() == 0 && tray.plantState().nutrition() == 0, "pour does not apply metabolism immediately");
                        helper.runAtTickTime(65, () -> {
                            try {
                                require(tray.plantState().water() == 1f && tray.plantState().nutrition() == 2f, "next resolved poll applies fixed inherited effects");
                                require(tray.soilSolution().snapshotUnits().getOrDefault(water, 0L) == 150
                                        && tray.soilSolution().snapshotUnits().getOrDefault(nutrient, 0L) == 150, "poll consumes 100 cents each");
                                helper.succeed();
                            } catch (Throwable failure) { helper.fail("botany watering metabolism fixture: " + failure); }
                            finally { fixture.close(); }
                        });
                    } catch (Throwable failure) { helper.fail("botany authenticated watering fixture: " + failure); fixture.close(); }
                });
            } catch (Throwable failure) { fixture.close(); helper.fail("botany watering player readiness: " + failure); }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void aimAtItem(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.entity.item.ItemEntity item) {
        var delta = item.position().add(0, .2, 0).subtract(body.getEyePosition()).normalize();
        body.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        body.setXRot((float) -Math.toDegrees(Math.asin(delta.y)));
    }
}
