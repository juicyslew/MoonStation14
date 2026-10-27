package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.ProduceFactory;
import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.GeneticCarriers;
import com.juicyslew.moonstation14.ms14.botany.SeedExtractorBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-seed-extractor", "botany-seed-extractor-unbound"})
public final class SeedExtractorRegisteredPlayerGameTests {
    private SeedExtractorRegisteredPlayerGameTests() { }

    @GameTest(template = "botany_extractor_power_clear", templateNamespace = "moonstation14", timeoutTicks = 220)
    @GameTestTags({"botany-seed-extractor-inhands", "network", "merge-main-remaining", "merge-main-stage-two"})
    public static void registeredPlayerExtractsAndPlantsFreshWheatSeedPacket(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-extractor-inhands-v1");
        helper.runAtTickTime(2, () -> {
            try {
                ServerLevel level = (ServerLevel) helper.getLevel();
                // Power topology readiness does not load the one-chunk halo required by the loaded-only interaction probe.
                FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(2, 8, 2)),
                        helper.absolutePos(new BlockPos(10, 16, 12)), 16);
                var power = AirAlarmPowerFixture.place(helper);
                BlockPos machinePos = helper.absolutePos(new BlockPos(5, 12, 8));
                BlockPos trayPos = helper.absolutePos(new BlockPos(7, 12, 8));
                level.setBlock(machinePos, ModBlocks.SEED_EXTRACTOR.get().defaultBlockState(), 3);
                level.setBlock(trayPos, ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
                PowerFixtureReadiness.await(helper, level, 55,
                        () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level,
                                new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(power.cable(),
                                        net.minecraft.core.Direction.WEST,
                                        com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC)),
                        () -> "extractor APC topology ready; " + com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.lastSolveDiagnostic(level),
                        () -> PowerFixtureReadiness.await(helper, level, 35, () -> {
                                    var extractor = (SeedExtractorBlockEntity) level.getBlockEntity(machinePos);
                                    if (extractor == null) return false;
                                    var meter = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, extractor);
                                    return meter.known() && meter.deliveredWatts() == 1000;
                 }, () -> "await actual extractor 1000 W allocation; meter="
                                         + com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, machinePos), () -> {
                     try {
                         var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                         var body = owner.body(); body.setPos(machinePos.getX() + .5, machinePos.getY() + 1, machinePos.getZ() - 1.5);
                        var machine = (SeedExtractorBlockEntity) level.getBlockEntity(machinePos);
                        var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(trayPos);
                        require(machine != null && machine.isPowered() && tray != null && tray.plantState() != null, "machine and tray initialized/powered");
                        var meter = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, machine);
                        require(meter.known() && meter.deliveredWatts() == 1000, "extractor has fresh real 1000 W allocation: " + meter);
                         String hand = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).activeHand();
                         // Cocoa is edible but has no bound plant provenance, so ordinary extraction must reject it.
                          var cocoa = new ItemStack(ModItems.FOOD_COCOA_BEANS.get());
                          var cocoaProfile = ModProduceProfiles.serverCatalog().get(
                                  net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "food_cocoa_beans"));
                          require(cocoaProfile != null && cocoaProfile.seed() == null,
                                  "resolved cocoa produce profile exists and has no seed binding");
                          require(GeneticCarriers.resolve(cocoa, () -> null) == null
                                          && cocoa.get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()) == null,
                                  "actual cocoa stack has no genetic carrier fallback or produce instance");
                         require(cocoa.get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get()) == null
                                         && cocoa.get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get()) == null
                                         && cocoa.get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()) == null,
                                 "plain cocoa beans have no genetic, seed-reference, or produce-instance provenance");
                         var bean = LooseItemEntity.create(level, body.position().add(1.5, 1.0, -1.5), cocoa);
                         bean.setNoGravity(true); require(level.addFreshEntity(bean), "real unbound cocoa beans published");
                         aimAt(body, bean);
                          var cocoaHandsBeforePickup = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                          require(BodyHandItemTransfer.pickupSelected(fixture.player(), bean.getUUID(), hand, cocoaHandsBeforePickup.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS,
                                  "normal cocoa bean pickup");
                          // Pickup commits a replacement LiveHands value; the pre-pickup snapshot is immutable.
                          var cocoaHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                          var cocoaQuery = fixture.queryHands(owner.epoch());
                         var cocoaHeld = cocoaQuery.hands().stream().filter(h -> h.id().equals(hand)).findFirst()
                                 .orElseThrow(() -> new AssertionError("missing cocoa source hand"));
                         String cocoaToken = cocoaHeld.token();
                          var cocoaHit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                          var cocoaTarget = BodyBlockUseTargetProbe.probe(level, body, machinePos, cocoaHit.getDirection());
                          require(cocoaTarget != null && cocoaTarget.pos().equals(machinePos)
                                          && cocoaTarget.face() == cocoaHit.getDirection(),
                                  "cocoa USE target is admitted at the exact machine position and face");
                          var cocoaHeldStack = cocoaHands.stackCopy(hand);
                          require(cocoaHands.activeHand().equals(hand) && cocoaHeldStack.isPresent()
                                          && cocoaHands.token(hand).filter(cocoaToken::equals).isPresent(),
                                  "cocoa pickup ownership admitted: active=" + cocoaHands.activeHand().equals(hand)
                                          + " stack=" + cocoaHeldStack.isPresent() + " token="
                                          + cocoaHands.token(hand).filter(cocoaToken::equals).isPresent());
                          var cocoaBefore = cocoaHeldStack.orElseThrow().copy();
                          long cocoaRevisionBefore = cocoaHands.revision();
                          var nearbyItemsBefore = level.getEntitiesOfClass(LooseItemEntity.class,
                                  new net.minecraft.world.phys.AABB(machinePos).inflate(8), e -> true).stream()
                                  .map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
                          var allHandsBefore = cocoaHands.handIds().stream().collect(java.util.stream.Collectors.toMap(
                                  id -> id, id -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                                          cocoaHands.stackCopy(id).orElse(ItemStack.EMPTY).copy(), cocoaHands.token(id).orElse(null))));
                          require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(owner.epoch(), 1, cocoaQuery.revision(), hand, cocoaToken, machinePos, cocoaHit.getDirection(), false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.DENIED,
                                  "ordinary extractor USE rejects unbound cocoa beans");
                          var handsAfterReject = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                          require(handsAfterReject.revision() == cocoaRevisionBefore
                                          && handsAfterReject.token(hand).filter(cocoaToken::equals).isPresent(), "rejected cocoa ownership token and revision unchanged");
                          var retainedCocoa = handsAfterReject.stackCopy(hand).orElseThrow();
                          require(retainedCocoa.getCount() == cocoaBefore.getCount()
                                          && ItemStack.isSameItemSameComponents(retainedCocoa, cocoaBefore)
                                          && retainedCocoa.get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get()) == null
                                         && retainedCocoa.get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get()) == null
                                         && retainedCocoa.get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()) == null,
                                  "rejected cocoa count and provenance components unchanged");
                          for (String id : handsAfterReject.handIds()) {
                              var expected = allHandsBefore.get(id);
                              require(expected != null && java.util.Objects.equals(expected.getValue(), handsAfterReject.token(id).orElse(null)),
                                      "rejection preserves token for hand " + id);
                              var actual = handsAfterReject.stackCopy(id).orElse(ItemStack.EMPTY);
                              require(actual.getCount() == expected.getKey().getCount()
                                              && ItemStack.isSameItemSameComponents(actual, expected.getKey()),
                                      "rejection preserves stack and all components for hand " + id);
                          }
                          var nearbyItemsAfter = level.getEntitiesOfClass(LooseItemEntity.class,
                                  new net.minecraft.world.phys.AABB(machinePos).inflate(8), e -> true).stream()
                                  .map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
                          require(nearbyItemsAfter.equals(nearbyItemsBefore), "rejected cocoa creates no loose output of any kind");
                         require(level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(machinePos).inflate(8), e -> e.getItem().is(ModItems.WHEAT_SEEDS.get())).isEmpty(),
                                 "rejected cocoa creates no seed output");
                         var postRejectMeter = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, machine);
                         require(postRejectMeter.known() && postRejectMeter.deliveredWatts() == 1000, "extractor remains at 1000 W after rejection: " + postRejectMeter);
                         require(BodyHandItemTransfer.drop(fixture.player(), hand, cocoaToken, handsAfterReject.revision()) == BodyHandItemTransfer.Result.SUCCESS,
                                 "drop rejected cocoa before wheat control");
                         var produce = ProduceFactory.createHarvest(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), 5);
                        // Keep the nominated item outside the power fixture's stone wall, at eye height.
                         var loose = LooseItemEntity.create(level, body.position().add(1.5, 1.0, 1.5), produce);
                         loose.setNoGravity(true); require(level.addFreshEntity(loose), "real wheat produce published");
                         aimAt(body, loose);
                         require(level.getServer().isSameThread() && body.level() == level && body.isAlive() && !body.isRemoved()
                                         && body.isAddedToLevel() && level.getEntity(body.getUUID()) == body
                                         && loose.level() == level && loose.isAddedToLevel() && !loose.isRemoved()
                                         && level.getEntity(loose.getUUID()) == loose,
                                 "probe admission fixture preconditions; bodyAlive=" + body.isAlive() + " bodyRemoved=" + body.isRemoved()
                                         + " bodyAdded=" + body.isAddedToLevel() + " bodyLookup=" + (level.getEntity(body.getUUID()) == body)
                                         + " itemAdded=" + loose.isAddedToLevel() + " itemRemoved=" + loose.isRemoved()
                                         + " itemLookup=" + (level.getEntity(loose.getUUID()) == loose) + " controller=" + fixture.player().getUUID()
                                         + " epoch=" + owner.epoch());
                         var rayEye = body.getEyePosition(); var rayEnd = rayEye.add(body.getLookAngle().scale(BodyItemTargetProbe.MAX_REACH));
                         require(!level.isOutsideBuildHeight(BlockPos.containing(rayEye)) && level.getWorldBorder().isWithinBounds(rayEye)
                                         && !level.isOutsideBuildHeight(BlockPos.containing(rayEnd)) && level.getWorldBorder().isWithinBounds(rayEnd),
                                 "probe ray endpoints inside world bounds; eye=" + rayEye + " end=" + rayEnd);
                         var projected = BodyItemTargetProbe.probeSelected(level, body, loose.getUUID(), BodyItemTargetProbe.MAX_REACH);
                         var probeEye = body.getEyePosition();
                         var probeLook = body.getLookAngle();
                         var probeEnd = probeEye.add(probeLook.scale(BodyItemTargetProbe.MAX_REACH));
                         String probeState = "controller=" + fixture.player().getUUID() + " epoch=" + owner.epoch()
                                 + " serverThread=" + level.getServer().isSameThread()
                                 + " bodyLevel=" + (body.level() == level) + " bodyAlive=" + body.isAlive()
                                 + " bodyRemoved=" + body.isRemoved() + " bodyAdded=" + body.isAddedToLevel()
                                 + " bodyLookupIdentity=" + (level.getEntity(body.getUUID()) == body)
                                 + " itemLevel=" + (loose.level() == level) + " itemAdded=" + loose.isAddedToLevel()
                                 + " itemRemoved=" + loose.isRemoved() + " itemLookupIdentity=" + (level.getEntity(loose.getUUID()) == loose)
                                 + " eye=" + probeEye + " end=" + probeEnd
                                 + " eyeInBounds=" + (!level.isOutsideBuildHeight(net.minecraft.core.BlockPos.containing(probeEye)) && level.getWorldBorder().isWithinBounds(probeEye))
                                 + " endInBounds=" + (!level.isOutsideBuildHeight(net.minecraft.core.BlockPos.containing(probeEnd)) && level.getWorldBorder().isWithinBounds(probeEnd));
                         int rayMinX = (Math.min(net.minecraft.core.BlockPos.containing(probeEye).getX(), net.minecraft.core.BlockPos.containing(probeEnd).getX()) >> 4) - 1;
                         int rayMaxX = (Math.max(net.minecraft.core.BlockPos.containing(probeEye).getX(), net.minecraft.core.BlockPos.containing(probeEnd).getX()) >> 4) + 1;
                         int rayMinZ = (Math.min(net.minecraft.core.BlockPos.containing(probeEye).getZ(), net.minecraft.core.BlockPos.containing(probeEnd).getZ()) >> 4) - 1;
                         int rayMaxZ = (Math.max(net.minecraft.core.BlockPos.containing(probeEye).getZ(), net.minecraft.core.BlockPos.containing(probeEnd).getZ()) >> 4) + 1;
                         var missingHalo = new java.util.ArrayList<String>();
                         for (int x = rayMinX; x <= rayMaxX; x++) for (int z = rayMinZ; z <= rayMaxZ; z++)
                             if (level.getChunkSource().getChunkNow(x, z) == null) missingHalo.add(x + "," + z);
                         net.minecraft.world.phys.BlockHitResult blocker = level.clip(new net.minecraft.world.level.ClipContext(
                                 probeEye, probeEnd, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                 net.minecraft.world.level.ClipContext.Fluid.NONE, body));
                         var indexedItems = level.getEntities(body,
                                 new net.minecraft.world.phys.AABB(probeEye, probeEnd).inflate(BodyItemTargetProbe.TARGET_MARGIN),
                                 entity -> entity instanceof net.minecraft.world.entity.item.ItemEntity)
                                 .stream().map(net.minecraft.world.entity.Entity::getUUID).toList();
                         var nominatedHit = loose.getBoundingBox().inflate(BodyItemTargetProbe.TARGET_MARGIN)
                                 .clip(probeEye, probeEnd).orElse(null);
                         String colliderDiagnostic = "type=" + blocker.getType() + " pos=" + blocker.getBlockPos()
                                 + " direction=" + blocker.getDirection() + " location=" + blocker.getLocation()
                                 + " distance=" + probeEye.distanceTo(blocker.getLocation()) + " inside=" + blocker.isInside()
                                 + (blocker.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                                 ? " state=" + level.getBlockState(blocker.getBlockPos()) : "");
                         require(projected != null, "fresh produce is the normal projected target; eye=" + body.getEyePosition()
                                 + " look=" + body.getLookAngle() + " bounds=" + loose.getBoundingBox()
                                 + " projected=" + projected + " " + probeState + " missingHalo=" + missingHalo
                                 + " firstCollider{" + colliderDiagnostic + "} indexedItemUuids=" + indexedItems
                                 + " nominatedIndexed=" + indexedItems.contains(loose.getUUID()) + " nominatedInflatedClip=" + nominatedHit);
                        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        var pickup = BodyHandItemTransfer.pickupSelected(fixture.player(), loose.getUUID(), hand, hands.revision(), ignored -> {});
                        require(pickup == BodyHandItemTransfer.Result.SUCCESS, "normal produce pickup; result=" + pickup
                                + " projected=" + projected + " eye=" + body.getEyePosition() + " look=" + body.getLookAngle()
                                + " bounds=" + loose.getBoundingBox());
                         var query = fixture.queryHands(owner.epoch());
                        var held = query.hands().stream().filter(h -> h.id().equals(hand)).findFirst()
                                .orElseThrow(() -> new AssertionError("missing source hand; returned hand ids=" + query.hands().stream().map(h -> h.id()).toList()));
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                         var use = new BodyBlockUseRequest(owner.epoch(), 2, query.revision(), hand, held.token(), machinePos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        String produceToken = held.token();
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), use) == BodyBlockUseResult.Outcome.PLACED, "ordinary admitted extractor USE consumes produce");
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), use) == BodyBlockUseResult.Outcome.DENIED,
                                "duplicate original extractor request is rejected");
                        var handsAfterExtract = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        require(handsAfterExtract.token(hand).filter(produceToken::equals).isEmpty(), "source produce ownership removed");
                        var packets = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(machinePos).inflate(8), e -> e.getItem().is(ModItems.WHEAT_SEEDS.get()));
                        int totalSeeds = packets.stream().mapToInt(e -> e.getItem().getCount()).sum()
                                + handsAfterExtract.handIds().stream().map(handsAfterExtract::stackCopy).flatMap(java.util.Optional::stream)
                                .filter(stack -> stack.is(ModItems.WHEAT_SEEDS.get())).mapToInt(ItemStack::getCount).sum();
                        require(totalSeeds >= 1 && totalSeeds <= 3, "inclusive resolved random yield 1..3 across world and hands");
                        var packet = packets.isEmpty() ? null : packets.getFirst();
                        String seedHand = handsAfterExtract.handIds().stream().filter(id -> handsAfterExtract.stackCopy(id)
                                .filter(stack -> stack.is(ModItems.WHEAT_SEEDS.get())).isPresent()).findFirst().orElse(null);
                        if (seedHand == null) {
                            require(packet != null, "extracted packet exists either in hands or in world");
                            aimAt(body, packet);
                            hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                            require(BodyHandItemTransfer.pickupSelected(fixture.player(), packet.getUUID(), hand, hands.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS, "normal pickup of extracted packet");
                            seedHand = hand;
                        }
                        hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        String selectedSeedHand = seedHand;
                        body.setPos(trayPos.getX() + .5, trayPos.getY() + 1, trayPos.getZ() - 1.5);
                         var plantQuery = fixture.queryHands(owner.epoch());
                        var seedHeld = plantQuery.hands().stream().filter(h -> h.id().equals(selectedSeedHand)).findFirst()
                                .orElseThrow(() -> new AssertionError("missing extracted seed hand; returned hand ids=" + plantQuery.hands().stream().map(h -> h.id()).toList()));
                        var plantHit = RegisteredPlayerReadyDriver.aimAt(body, level, trayPos);
                         require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(owner.epoch(), 3, plantQuery.revision(), selectedSeedHand, seedHeld.token(), trayPos, plantHit.getDirection(), false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED, "ordinary USE plants extracted packet");
                        require(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat").equals(tray.plantState().seed()), "tray contains wheat");
                        require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).token(selectedSeedHand).isEmpty(), "planted packet consumed");
                        helper.succeed();
                    } catch (Throwable failure) { helper.testInfo.fail(failure); }
                    finally { fixture.close(); }
                }));
            } catch (Throwable failure) { fixture.close(); helper.fail("extractor player readiness: " + failure); }
        });
    }
    private static void aimAt(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.entity.item.ItemEntity item) {
        var delta = item.getBoundingBox().getCenter().subtract(body.getEyePosition()).normalize();
        body.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        body.setXRot((float) -Math.toDegrees(Math.asin(delta.y)));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
