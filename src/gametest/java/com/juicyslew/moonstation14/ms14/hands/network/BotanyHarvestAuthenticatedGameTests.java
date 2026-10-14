package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany"})
public final class BotanyHarvestAuthenticatedGameTests {
    private BotanyHarvestAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 3200)
    @GameTestTags({"botany-harvest"})
    public static void registeredPlayerGrowsAndHarvestsWheatThroughOrdinaryUse(GameTestHelper helper) {
        runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()), "wheat", BotanyHarvestAuthenticatedGameTests::harvestAndVerify);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 3200)
    @GameTestTags({"botany-harvest", "harvest-placement"})
    public static void registeredPlayerHarvestsWheatBesideWall(GameTestHelper helper) {
        runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()), "wheat-wall",
                (h, f, e, hand, tray, pos, level, done) -> verifyObstructedHarvest(h, f, e, hand, tray, pos, level, done, false, false));
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 3200)
    @GameTestTags({"botany-harvest", "harvest-placement"})
    public static void registeredPlayerHarvestsWheatBesideCorner(GameTestHelper helper) {
        runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()), "wheat-corner",
                (h, f, e, hand, tray, pos, level, done) -> verifyObstructedHarvest(h, f, e, hand, tray, pos, level, done, false, true));
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 12000)
    @GameTestTags({"botany-harvest", "harvest-placement"})
    public static void registeredPlayerHarvestsTomatoBesideWall(GameTestHelper helper) {
        runAuthenticatedHarvest(helper, new ItemStack(ModItems.TOMATO_SEEDS.get()), "tomato-wall", 10000,
                (h, f, e, hand, tray, pos, level, done) -> verifyObstructedHarvest(h, f, e, hand, tray, pos, level, done, true, false));
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 12000)
    @GameTestTags({"botany-harvest", "harvest-placement"})
    public static void registeredPlayerHarvestsTomatoBesideCorner(GameTestHelper helper) {
        runAuthenticatedHarvest(helper, new ItemStack(ModItems.TOMATO_SEEDS.get()), "tomato-corner", 10000,
                (h, f, e, hand, tray, pos, level, done) -> verifyObstructedHarvest(h, f, e, hand, tray, pos, level, done, true, true));
    }

    private static void runAuthenticatedHarvest(GameTestHelper helper, ItemStack seeds, String label, ReadyPlantCallback readyCallback) {
        runAuthenticatedHarvest(helper, seeds, label, 2980, readyCallback);
    }

    private static void runAuthenticatedHarvest(GameTestHelper helper, ItemStack seeds, String label, int growthBudget, ReadyPlantCallback readyCallback) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-harvest-" + label + "-v1");
        var finished = new AtomicBoolean();
        try {
            helper.runAtTickTime(2, () -> {
                try {
                    var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                    ServerLevel level = (ServerLevel) helper.getLevel();
                    BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
                    level.setBlock(pos, ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState(), 3);
                    helper.runAfterDelay(2, () -> {
                        try {
                            var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(pos);
                            require(tray != null && tray.plantState() != null, "normal tray initialized from resolved profile");
                            var body = owner.body(); body.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 1.5);
                            var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                            String hand = liveHands.activeHand();
                            require(liveHands.handIds().contains(hand), "active hand is a configured hand");
                            var packet = LooseItemEntity.create(level, body.position().add(0, .1, .8), seeds);
                            packet.setNoGravity(true); require(level.addFreshEntity(packet), "seed packet published");
                            aimAt(body, packet);
                            var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                            require(BodyHandItemTransfer.pickupSelected(fixture.player(), packet.getUUID(), hand, hands.revision(), ignored -> {})
                                    == BodyHandItemTransfer.Result.SUCCESS, "normal authenticated pickup of " + label + " seeds");
                            var held = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 2));
                            var active = held.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                            var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                            require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(
                                    owner.epoch(), 1, held.revision(), hand, active.token(), pos, hit.getDirection(), false,
                                    BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                                    "normal admitted USE plants " + label);
                            require(tray.plantState().water() == 100 && tray.plantState().nutrition() == 100,
                                    "normal tray starts at full resolved water/nutrition");
                            var gas = AtmosphereService.INSTANCE.sample(level, pos.above()).orElse(null);
                            if (gas != null) require(gas.pressureKpa(1.0) >= 81 && gas.pressureKpa(1.0) <= 121
                                            && Math.abs(gas.temperatureKelvin() - 293) <= 10,
                                    "known exposed atmosphere within wheat growth bounds");
                             waitForHarvest(helper, fixture, owner.epoch(), hand, tray, pos, level, 0, growthBudget, finished, label, readyCallback);
                        } catch (Throwable failure) { failure.printStackTrace(); finish(helper, fixture, finished, label + " setup", failure); }
                    });
                } catch (Throwable failure) { finish(helper, fixture, finished, "player readiness", failure); }
            });
        } catch (Throwable failure) { finish(helper, fixture, finished, "fixture registration", failure); }
    }

    private static void waitForHarvest(GameTestHelper helper, RegisteredServerPlayerFixture fixture, long epoch,
                                       String hand, HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level,
                                       int elapsed, int growthBudget, AtomicBoolean finished, String label, ReadyPlantCallback readyCallback) {
        if (finished.get()) return;
        helper.runAfterDelay(20, () -> {
            if (finished.get()) return;
            try {
                var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body), "registered actor remains able to act");
                var gas = AtmosphereService.INSTANCE.sample(level, pos.above()).orElse(null);
                boolean atmosphereReady = gas != null && gas.pressureKpa(1.0) >= 81 && gas.pressureKpa(1.0) <= 121
                        && Math.abs(gas.temperatureKelvin() - 293) <= 10;
                if (!atmosphereReady || !tray.plantState().ready()) {
                    if (elapsed >= growthBudget) throw new AssertionError(label + " never became ready from ordinary BE ticks; atmosphere=" + gas
                            + " age=" + tray.plantState().age() + " status=" + tray.plantState().status());
                    waitForHarvest(helper, fixture, epoch, hand, tray, pos, level, elapsed + 20, growthBudget, finished, label, readyCallback);
                    return;
                }
                readyCallback.run(helper, fixture, epoch, hand, tray, pos, level, finished);
            } catch (Throwable failure) { finish(helper, fixture, finished, "natural growth wait", failure); }
        });
    }

    @FunctionalInterface
    private interface ReadyPlantCallback {
        void run(GameTestHelper helper, RegisteredServerPlayerFixture fixture, long epoch, String hand,
                 HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level, AtomicBoolean finished);
    }

    private static void harvestAndVerify(GameTestHelper helper, RegisteredServerPlayerFixture fixture, long epoch,
                                        String hand, HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level,
                                        AtomicBoolean finished) {
        var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
        var plant = tray.plantState(); float water = plant.water(), nutrition = plant.nutrition();
        var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8), e -> !e.getItem().isEmpty()).size();
        var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 3));
        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
        var harvestOutcome = BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2, query.revision(), hand,
                null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE));
        require(harvestOutcome == BodyBlockUseResult.Outcome.PLACED,
                "empty-hand ordinary USE harvests ready wheat; outcome=" + harvestOutcome + " revision=" + query.revision()
                        + " plant=" + plant.status() + "/" + plant.revision() + " hand=" + hand);
        var output = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8), e -> !e.getItem().isEmpty());
        require(output.size() == before + 3, "harvest publishes three fresh loose outputs");
         require(plant.seed() == null && plant.water() == water && plant.nutrition() == nutrition,
                 "no-repeat harvest clears plant but preserves soil resources");
        var publishedAppearance = tray.getUpdateTag(level.registryAccess());
        require("EMPTY".equals(publishedAppearance.getString("PlantAppearanceStatus"))
                        && publishedAppearance.getInt("PlantAppearanceStage") == 0
                        && publishedAppearance.getString("PlantAppearanceKey").isEmpty(),
                "immediate server update tag publishes empty appearance after harvest");
        var repeatQuery = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 4));
        hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 3, repeatQuery.revision(), hand,
                null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.ACTIVATE_WORLD)) == BodyBlockUseResult.Outcome.DENIED,
                "second intent cannot harvest same source again");
        require(level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8), e -> !e.getItem().isEmpty()).size() == before + 3,
                "repeat request creates no extra output");
        var selected = output.getFirst();
        aimAt(body, selected);
        var live = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        require(BodyHandItemTransfer.pickupSelected(fixture.player(), selected.getUUID(), hand, live.revision(), ignored -> {})
                == BodyHandItemTransfer.Result.SUCCESS, "normal pickup takes harvested produce");
        var picked = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 5)).hands().stream()
                .filter(value -> value.id().equals(hand)).findFirst().orElseThrow().presentation().stack();
        var reagent = picked.get(ModDataComponents.REAGENT.get());
        var flour = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("flour");
        var nutriment = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("nutriment");
        require(reagent != null && reagent.centContents().values().stream().mapToLong(Long::longValue).sum() == 650
                        && reagent.centContents().getOrDefault(flour, 0L) == 525
                        && reagent.centContents().getOrDefault(nutriment, 0L) == 125,
                "picked wheat retains generated flour 525/nutriment 125 chemistry");
        require(picked.get(ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents() == 1650,
                "picked wheat retains generated 1650-cent capacity metadata");
        finish(helper, fixture, finished, "", null);
    }

    private static void verifyObstructedHarvest(GameTestHelper helper, RegisteredServerPlayerFixture fixture, long epoch,
                                                String hand, HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level,
                                                AtomicBoolean finished, boolean tomato, boolean corner) {
        try {
            var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
            body.setPos(pos.getX() + .3, pos.getY() + 1, pos.getZ() - 1.5);
            level.setBlock(new BlockPos(pos.getX() - 1, pos.getY() + 1, pos.getZ() - 1),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            if (corner) level.setBlock(new BlockPos(pos.getX() - 1, pos.getY() + 1, pos.getZ() - 2),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            var plant = tray.plantState();
            float water = plant.water(), nutrition = plant.nutrition();
            var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8), e -> !e.getItem().isEmpty())
                    .stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
            var seed = com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.serverLookup(
                    com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId(tomato ? "tomato" : "wheat"));
            int preview = com.juicyslew.moonstation14.ms14.botany.PlantMetabolismEvaluator.previewHarvestYield(plant, seed, 1f);
            var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 3));
            var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
            require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2, query.revision(), hand,
                    null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                    "ordinary harvest succeeds with obstructed preferred scatter");
            var output = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8), e -> !e.getItem().isEmpty())
                    .stream().filter(e -> !before.contains(e.getUUID())).toList();
            require(output.size() == preview, "fresh outputs match profile harvest preview");
            for (var item : output) require(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.loadedItemBoxClear(level, item.position(),
                    com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(item.getItem(), false).physicalWidth(),
                    com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(item.getItem(), false).physicalHeight()),
                    "published output full profile AABB is clear");
            require(plant.water() == water && plant.nutrition() == nutrition && (tomato ? plant.seed() != null : plant.seed() == null),
                    "soil is preserved and harvested plant follows its resolved regrowth policy");
            finish(helper, fixture, finished, "obstructed harvest", null);
        } catch (Throwable failure) { finish(helper, fixture, finished, "obstructed harvest", failure); }
    }

    private static void aimAt(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.entity.item.ItemEntity item) {
        var delta = item.position().add(0, .2, 0).subtract(body.getEyePosition()).normalize();
        body.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        body.setXRot((float) -Math.toDegrees(Math.asin(delta.y)));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void finish(GameTestHelper helper, RegisteredServerPlayerFixture fixture, AtomicBoolean done,
                               String context, Throwable failure) {
        if (!done.compareAndSet(false, true)) return;
        try { fixture.close(); } finally {
            if (failure == null) helper.succeed(); else helper.fail("botany harvest " + context + ": " + failure);
        }
    }
}
