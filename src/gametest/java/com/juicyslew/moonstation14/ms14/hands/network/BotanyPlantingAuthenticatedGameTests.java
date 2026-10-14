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
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany"})
public final class BotanyPlantingAuthenticatedGameTests {
    private BotanyPlantingAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
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
                        String handId = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).handIds().getFirst();
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
                        var first = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 10));
                        var firstHand = first.hands().stream().filter(h -> h.id().equals(handId)).findFirst().orElseThrow();
                        var firstHit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        var firstRequest = new BodyBlockUseRequest(owner.epoch(), 1, first.revision(), handId, firstHand.token(), pos,
                                firstHit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        require(BodyBlockUseResult.Outcome.PLACED == BodyHandActionNetworking.handleBlockUse(fixture.player(), firstRequest), "normal admitted seed use plants");
                        var planted = tray.plantState();
                        require(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat").equals(planted.seed()), "planted wheat profile");
                        require(planted.age() == 1f && planted.health() == 100f, "plant starts age one at resolved endurance");
                         var empty = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 11));
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
                        var second = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 12));
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
                        var snapshot = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 2));
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
                        var snapshot = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 30));
                        var hand = snapshot.hands().stream().filter(value -> value.id().equals(handId)).findFirst().orElseThrow();
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        var request = new BodyBlockUseRequest(owner.epoch(), 1, snapshot.revision(), handId, hand.token(), pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), request) == BodyBlockUseResult.Outcome.PLACED, "ordinary admitted USE waters tray");
                        var soil = tray.soilSolution().snapshotUnits();
                        require(soil.getOrDefault(water, 0L) == 250 && soil.getOrDefault(nutrient, 0L) == 250, "exact proportional 250-cent transfer to soil");
                        var afterHands = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 31));
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
