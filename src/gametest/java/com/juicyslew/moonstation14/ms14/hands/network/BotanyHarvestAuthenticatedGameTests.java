package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.GeneticValue;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.botany.PlantSeedReference;
import com.juicyslew.moonstation14.ms14.botany.GeneticCarriers;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany"})
public final class BotanyHarvestAuthenticatedGameTests {
    private BotanyHarvestAuthenticatedGameTests() { }

    @GameTest(template = "botany_fixture_space", templateNamespace = "moonstation14", timeoutTicks = 12000)
    @GameTestTags({"botany-meatwheat", "botany-harvest", "botany-genetics"})
    public static void registeredMeatwheatNaturallyHarvestsExtractsAndReplants(GameTestHelper helper) {
        var species = ModSeedProfiles.seedId("meatwheat");
        var expected = new GeneticValue(species, ModSeedProfiles.serverLookup(species));
        runAuthenticatedHarvest(helper, new ItemStack(ModItems.MEATWHEAT_SEEDS.get()), "meatwheat-native-roundtrip",
                (h, fixture, epoch, hand, tray, pos, level, finished) -> {
                    try {
                        var plant = tray.plantState();
                        require(plant.ready() && expected.equals(plant.genetics()),
                                "ordinary simulation grows registered meatwheat packet to harvest readiness");
                        var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                                e -> !e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                                .collect(java.util.stream.Collectors.toSet());
                        var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                        var query = fixture.queryHands(epoch);
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2,
                                query.revision(), hand, null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE))
                                == BodyBlockUseResult.Outcome.PLACED, "ordinary authenticated USE harvests meatwheat");
                        var output = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                                e -> !e.getItem().isEmpty() && !before.contains(e.getUUID())
                                        && e.getItem().is(ModItems.MEATWHEAT_BUSHEL.get())).stream().findFirst().orElseThrow();
                        require(output.getItem().get(ModDataComponents.GENETIC_VALUE.get()).equals(expected),
                                "canonical bushel output carries resolved meatwheat genetics");
                        continueGeneticsThroughExtractor(h, fixture, epoch, hand, pos, level, finished, expected, output);
                    } catch (Throwable failure) { finish(h, fixture, finished, "meatwheat harvest", failure); }
                });
    }

    @GameTest(template = "botany_fixture_space", templateNamespace = "moonstation14", timeoutTicks = 12000)
    @GameTestTags({"botany-catalog", "botany-harvest", "botany-seed-extractor"})
    public static void registeredCarrotNaturallyHarvestsExtractsAndReplants(GameTestHelper helper) {
        runCatalogRoundtrip(helper, "carrots", ModItems.CARROTS_SEEDS.get(), ModItems.CARROT.get(), false);
    }

    @GameTest(template = "botany_fixture_space", templateNamespace = "moonstation14", timeoutTicks = 12000)
    @GameTestTags({"botany-catalog", "botany-harvest", "botany-seed-extractor"})
    public static void registeredLemonNaturallyHarvestsExtractsAndReplants(GameTestHelper helper) {
        runCatalogRoundtrip(helper, "lemon", ModItems.LEMON_SEEDS.get(), ModItems.LEMON.get(), true);
    }

    private static void runCatalogRoundtrip(GameTestHelper helper, String speciesName,
            net.minecraft.world.item.Item seedItem, net.minecraft.world.item.Item productItem, boolean repeats) {
        var species = ModSeedProfiles.seedId(speciesName);
        var expected = new GeneticValue(species, ModSeedProfiles.serverLookup(species));
        runAuthenticatedHarvest(helper, new ItemStack(seedItem), speciesName + "-catalog-roundtrip", 10000,
                (h, fixture, epoch, hand, tray, pos, level, finished) -> {
                    try {
                        var plant = tray.plantState();
                        require(plant.ready() && expected.equals(plant.genetics()),
                                speciesName + " naturally grows using its resolved production profile");
                        var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                                e -> !e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                                .collect(java.util.stream.Collectors.toSet());
                        var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                        var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 3));
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2,
                                query.revision(), hand, null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE))
                                == BodyBlockUseResult.Outcome.PLACED, "ordinary authenticated USE harvests " + speciesName);
                        var output = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                                e -> !e.getItem().isEmpty() && !before.contains(e.getUUID()) && e.getItem().is(productItem))
                                .stream().findFirst().orElseThrow();
                        var stack = output.getItem();
                        require(expected.equals(stack.get(ModDataComponents.GENETIC_VALUE.get())), "product retains resolved " + speciesName + " genetics");
                        require(stack.get(ModDataComponents.PLANT_SEED_REFERENCE.get()) != null, "product carries seed reference");
                        require(stack.get(ModDataComponents.REAGENT.get()) != null
                                        && stack.get(ModDataComponents.PRODUCE_INSTANCE.get()) != null,
                                "product is generated with chemistry and produce instance");
                        require((plant.seed() != null) == repeats, repeats
                                ? "repeat lemon source remains planted" : "no-repeat carrot source is cleared");
                        continueGeneticsThroughExtractor(h, fixture, epoch, hand, pos, level, finished, expected, output);
                    } catch (Throwable failure) { finish(h, fixture, finished, speciesName + " catalog roundtrip", failure); }
                });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-harvest"})
    public static void registeredPlayerGrowsAndHarvestsWheatThroughOrdinaryUse(GameTestHelper helper) {
        runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()), "wheat", BotanyHarvestAuthenticatedGameTests::harvestAndVerify);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-harvest", "botany-pickup"})
    public static void registeredUseAndProducePickupAreBothAdmitted(GameTestHelper helper) {
        runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()), "tray-use-under-output",
                (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            try {
                var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var plant = tray.plantState();
                require(plant.ready(), "ordinary growth reaches harvest readiness before testing USE under loose produce");
                var produce = com.juicyslew.moonstation14.ms14.botany.ProduceFactory.createHarvest(
                        com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId("wheat"), 5);
                var published = com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch.publishSystemHarvest(
                        level, pos, java.util.List.of(produce), () -> true, () -> {});
                require(published == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,
                        "production output batch publishes produce above tray");
                var output = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2),
                        entity -> !entity.getItem().isEmpty() && entity.getItem().is(produce.getItem())).stream().findFirst().orElseThrow();
                var query = fixture.queryHands(epoch);
                var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                var outcome = BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2,
                        query.revision(), hand, null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE));
                require(outcome == BodyBlockUseResult.Outcome.PLACED,
                        "normal admitted USE reaches ready tray beneath dropped produce; outcome=" + outcome);
                require(plant.seed() == null, "ordinary harvest clears the mature wheat plant beneath loose produce");
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                aimAt(body, output);
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), output.getUUID(), hand, hands.revision(), ignored -> {})
                        == BodyHandItemTransfer.Result.SUCCESS, "ordinary authenticated pickup still admits produce above the tray");
                finish(h, fixture, finished, "tray E USE admission and produce pickup", null);
            } catch (Throwable failure) { finish(h, fixture, finished, "tray E USE admission and produce pickup", failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-scream", "botany-harvest", "merge-main-remaining", "merge-main-stage-two"})
    public static void authenticatedManualHarvestOnlyScreamsAfterOutputCommit(GameTestHelper helper) {
        var species = ModSeedProfiles.seedId("wheat");
        var base = ModSeedProfiles.serverLookup(species);
        var scream = new SeedProfile.Scream(true, SeedProfile.Scream.DEFAULT_SOUND, -10f);
        var profile = new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(), base.lifecycle(),
                base.chemicals(), base.consumeGasses(), base.exudeGasses(), base.mutationTargets(), scream, base.sourceName());
        var packet = new ItemStack(ModItems.WHEAT_SEEDS.get());
        packet.set(ModDataComponents.GENETIC_VALUE.get(), new GeneticValue(species, profile));
        runAuthenticatedHarvest(helper, packet, "scream-manual-wheat", (h, fixture, epoch, hand, tray, pos, world, finished) -> {
                try {
                    drainPlantScreamPackets(fixture);
                    var before = world.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                            entity -> !entity.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                            .collect(java.util.stream.Collectors.toSet());
                    var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                    var staleSnapshot = fixture.queryHands(epoch);
                    var staleHit = RegisteredPlayerReadyDriver.aimAt(body, world, pos);
                    require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2,
                                    staleSnapshot.revision() - 1, hand, null, pos, staleHit.getDirection(), false,
                                    BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.DENIED,
                            "stale authenticated manual harvest is denied");
                    require(drainPlantScreamPackets(fixture).isEmpty() && world.getEntitiesOfClass(LooseItemEntity.class,
                                    new net.minecraft.world.phys.AABB(pos).inflate(8), entity -> !entity.getItem().isEmpty()).stream()
                                    .map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet()).equals(before),
                            "stale denial creates no output and emits no scream");
                    var query = fixture.queryHands(epoch);
                    var hit = RegisteredPlayerReadyDriver.aimAt(body, world, pos);
                    require(AuthenticatedBlockUseDriver.use(fixture.player(), new BodyBlockUseRequest(epoch, 3,
                                    query.revision(), hand, null, pos, hit.getDirection(), false,
                                    BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                            "authenticated manual harvest commits through normal hand-use path");
                    var outputs = world.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                            entity -> !entity.getItem().isEmpty() && !before.contains(entity.getUUID()));
                    require(outputs.size() == profile.lifecycle().yield(), "committed manual harvest publishes expected outputs");
                    var screamPackets = drainPlantScreamPackets(fixture);
                    require(screamPackets.size() == 1, "successful manual output commit publishes exactly one scream packet");
                    assertPlantScreamPacket(screamPackets.getFirst(), pos);
                    require(tray.plantState().seed() == null, "no-repeat source plant lifecycle committed");
                    finish(h, fixture, finished, "authenticated manual scream harvest", null);
                } catch (Throwable failure) { finish(h, fixture, finished, "authenticated manual scream harvest", failure); }
            });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-sampling", "botany", "merge-main-remaining", "merge-main-root-causes"})
    public static void registeredWirecutterSamplesThroughOrdinaryAuthenticatedUse(GameTestHelper helper) {
        var species = ModSeedProfiles.seedId("wheat");
        var base = ModSeedProfiles.serverLookup(species);
        var scream = new SeedProfile.Scream(true, SeedProfile.Scream.DEFAULT_SOUND, -10f);
        var profiled = new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(), base.lifecycle(),
                base.chemicals(), base.consumeGasses(), base.exudeGasses(), base.mutationTargets(), scream, base.sourceName());
        ItemStack seeds = new ItemStack(ModItems.WHEAT_SEEDS.get());
        seeds.set(ModDataComponents.GENETIC_VALUE.get(), new GeneticValue(species, profiled));
        runAuthenticatedHarvest(helper, seeds, "wirecutter-sampling", (h, fixture, epoch, emptyHand, tray, pos, world, finished) -> {
                try {
                    var plant = tray.plantState();
                    var reference = plant.seedReference();
                    float healthBefore = plant.health();
                    var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                    var tool = new ItemStack(ModItems.WIRECUTTER.get());
                    var toolEntity = LooseItemEntity.create(world, body.position().add(0, .1, .8), tool);
                    toolEntity.setNoGravity(true); require(world.addFreshEntity(toolEntity), "wirecutter world item published");
                    aimAt(body, toolEntity);
                    var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    require(BodyHandItemTransfer.pickupSelected(fixture.player(), toolEntity.getUUID(), emptyHand, hands.revision(), ignored -> {})
                            == BodyHandItemTransfer.Result.SUCCESS, "registered player normally picks up real wirecutter");
                    var beforeIds = world.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                            entity -> !entity.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                            .collect(java.util.stream.Collectors.toSet());
                    drainPlantScreamPackets(fixture);
                    var query = fixture.queryHands(epoch);
                    var heldTool = query.hands().stream().filter(value -> value.id().equals(emptyHand)).findFirst().orElseThrow();
                    String token = heldTool.token(); int count = heldTool.presentation().stack().getCount();
                    var hit = RegisteredPlayerReadyDriver.aimAt(body, world, pos);
                    var outcome = BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2,
                            query.revision(), emptyHand, token, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE));
                    require(outcome == BodyBlockUseResult.Outcome.PLACED, "authenticated wirecutter sample commits via ordinary USE; outcome=" + outcome);
                    require(plant.health() >= 0 && plant.health() <= profiled.biology().endurance()
                                    && (healthBefore - plant.health() == 30 || healthBefore - plant.health() == 40),
                            "source health loss and final endurance clamp applied");
                    require(!reference.unique(), "sampling marks the exact source seed reference shared after publication");
                    var afterHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    require(afterHands.token(emptyHand).filter(token::equals).isPresent()
                                    && afterHands.stackCopy(emptyHand).orElseThrow().getCount() == count,
                            "sampling does not consume or damage held wirecutter");
                    var packets = new java.util.ArrayList<ItemStack>();
                    for (String id : afterHands.handIds()) afterHands.stackCopy(id).filter(stack -> stack.is(ModItems.WHEAT_SEEDS.get()))
                            .ifPresent(packets::add);
                    world.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                            entity -> !entity.getItem().isEmpty() && !beforeIds.contains(entity.getUUID())
                                    && entity.getItem().is(ModItems.WHEAT_SEEDS.get())).forEach(entity -> packets.add(entity.getItem()));
                    require(packets.size() == 1 && packets.getFirst().get(ModDataComponents.PLANT_SEED_REFERENCE.get()) == reference,
                            "exactly one packet retains current seed-reference identity");
                    require(packets.getFirst().get(ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get()) == null,
                            "ready packet omits health override");
                    var packetsOut = drainPlantScreamPackets(fixture);
                    require(packetsOut.size() == 1, "committed sampling publishes exactly one scream packet to the nearby registered player");
                    assertPlantScreamPacket(packetsOut.getFirst(), pos);
                    require(packets.size() == 1 && packets.getFirst().get(ModDataComponents.PLANT_SEED_REFERENCE.get()) == reference,
                            "scream packet is published only after the sampled packet is visible");
                    finish(h, fixture, finished, "registered authenticated wirecutter sample", null);
                } catch (Throwable failure) { finish(h, fixture, finished, "registered authenticated wirecutter sample", failure); }
            });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-genetics"})
    public static void registeredPlayerHarvestTransmitsAlteredWheatGenetics(GameTestHelper helper) {
        runAlteredWheatGeneticsThroughExtractor(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-seed-reference"})
    public static void registeredPlayerUniqueSeedReferenceSurvivesHarvestExtractionAndReplant(GameTestHelper helper) {
        runAlteredWheatGeneticsThroughExtractor(helper, true);
    }

    private static void runAlteredWheatGeneticsThroughExtractor(GameTestHelper helper, boolean uniqueSingleOutput) {
        var species = ModSeedProfiles.seedId("wheat");
        var base = ModSeedProfiles.serverLookup(species);
        var flour = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("flour");
        var arrangedProfile = alteredWheatProfile(base, flour);
        if (uniqueSingleOutput) {
            var life = arrangedProfile.lifecycle();
            arrangedProfile = new SeedProfile(arrangedProfile.packet(), arrangedProfile.product(), arrangedProfile.visualKey(), arrangedProfile.biology(),
                    new SeedProfile.Lifecycle(life.growthStages(), life.lifespan(), life.maturation(), life.production(),
                            1, life.potency(), life.harvest(), life.viable(), false), arrangedProfile.chemicals(),
                    arrangedProfile.consumeGasses(), arrangedProfile.exudeGasses(), arrangedProfile.mutationTargets(), arrangedProfile.scream(), arrangedProfile.sourceName());
        }
        final var altered = arrangedProfile;
        var alteredLife = altered.lifecycle();
        var expected = new GeneticValue(species, altered);
        var seeds = new ItemStack(ModItems.WHEAT_SEEDS.get());
        seeds.set(ModDataComponents.GENETIC_VALUE.get(), expected);
        PlantSeedReference expectedReference = uniqueSingleOutput ? new PlantSeedReference(expected, true) : null;
        if (expectedReference != null) GeneticCarriers.attach(seeds, expectedReference);
        runAuthenticatedHarvest(helper, seeds, "genetics-wheat", (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            try {
                    var plant = tray.plantState();
                require(plant.ready(), "altered-genetics plant became naturally harvest-ready");
                require(expected.equals(plant.genetics()) && altered.equals(plant.effectiveProfile()),
                        "planted tray retains carried nonbaseline effective profile");
                if (uniqueSingleOutput) require(plant.seedReference() == expectedReference && plant.seedReference().unique(),
                        "initial plant retains exact unique seed-reference object");
                require(!plant.effectiveProfile().consumeGasses().isEmpty()
                                && !plant.effectiveProfile().exudeGasses().isEmpty(),
                        "planted carried genetics retain nonempty gas-consumption and gas-exudation maps");
                float water = plant.water(), nutrition = plant.nutrition();
                var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                        e -> !e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                        .collect(java.util.stream.Collectors.toSet());
                var query = fixture.queryHands(epoch);
                var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2, query.revision(), hand,
                        null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                        "one fresh-revision ordinary USE harvests altered-genetics plant");
                var outputs = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                        e -> !e.getItem().isEmpty() && !before.contains(e.getUUID()));
                require(outputs.size() == alteredLife.yield(), "altered genetic yield controls exact fresh harvest output count");
                if (uniqueSingleOutput) require(outputs.size() == 1
                                && outputs.getFirst().getItem().get(ModDataComponents.PLANT_SEED_REFERENCE.get()) == expectedReference
                                && expectedReference.unique(), "one harvested output retains exact unique reference identity");
                for (var output : outputs) {
                    var stack = output.getItem();
                    require(expected.equals(stack.get(ModDataComponents.GENETIC_VALUE.get())),
                            "each harvested product carries exact altered genetic snapshot");
                    var carriedProfile = stack.get(ModDataComponents.GENETIC_VALUE.get()).profile();
                    require(carriedProfile.consumeGasses().equals(altered.consumeGasses())
                                    && carriedProfile.exudeGasses().equals(altered.exudeGasses()),
                            "harvested products retain both arranged gas maps");
                    var reagent = stack.get(ModDataComponents.REAGENT.get());
                    require(reagent != null && reagent.centContents().getOrDefault(flour, 0L) == 500,
                            "generated chemistry uses altered potency 10 and carried flour formula");
                    require(stack.get(ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents() == 1650,
                            "generated capacity reflects altered profile chemistry");
                }
                require(plant.seed() == null && plant.water() == water && plant.nutrition() == nutrition,
                        "no-repeat harvest clears plant while preserving soil resources");
                continueGeneticsThroughExtractor(h, fixture, epoch, hand, pos, level, finished, expected, outputs.getFirst(), false, null,
                        uniqueSingleOutput);
            } catch (Throwable failure) { finish(h, fixture, finished, "genetics harvest", failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-genetics", "botany-species-mutation"})
    public static void registeredPlayerSpeciesChangedPlantHarvestExtractsAndReplantsTomato(GameTestHelper helper) {
        var wheat = ModSeedProfiles.seedId("wheat");
        var tomato = ModSeedProfiles.seedId("tomato");
        var wheatProfile = ModSeedProfiles.serverLookup(wheat);
        var flour = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("flour");
        var alteredBase = alteredWheatProfile(wheatProfile, flour);
        var speciesChemicals = new java.util.HashMap<>(alteredBase.chemicals());
        speciesChemicals.put(flour.location(), new SeedProfile.Chemical(0, 10, 2, false));
        var altered = new SeedProfile(alteredBase.packet(), alteredBase.product(), alteredBase.visualKey(), alteredBase.biology(),
                alteredBase.lifecycle(), speciesChemicals, alteredBase.consumeGasses(), alteredBase.exudeGasses(), java.util.List.of(tomato), alteredBase.scream(), alteredBase.sourceName());
        var source = new GeneticValue(wheat, altered);
        var tomatoProfile = ModSeedProfiles.serverLookup(tomato);
        var expected = source.transformSpecies(tomato, tomatoProfile);
        var packet = new ItemStack(ModItems.WHEAT_SEEDS.get());
        packet.set(ModDataComponents.GENETIC_VALUE.get(), source);
        runAuthenticatedHarvest(helper, packet, "species-mutation-wheat-to-tomato", (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            try {
                var plant = tray.plantState();
                require(plant.ready() && source.equals(plant.genetics()), "naturally grown arranged wheat is harvest-ready");
                plant.adjustMutationLevel(1f);
                // Deliberate synthetic species-transition edge; not a claim this is an eligible source mutation.
                var mutation = new com.juicyslew.moonstation14.ms14.botany.PlantMutationTable.Entry("synthetic_wheat_to_tomato", 1f,
                        true, true, true, com.juicyslew.moonstation14.ms14.botany.PlantMutationTable.EffectKind.SPECIES,
                        java.util.Optional.empty(), java.util.Optional.empty(), "");
                var cycle = com.juicyslew.moonstation14.ms14.botany.PlantMutationSystem.cycle(plant,
                        new com.juicyslew.moonstation14.ms14.botany.PlantMutationTable(java.util.List.of(mutation)),
                        new java.util.Random(0x51ec1eL), () -> 0f,
                        com.juicyslew.moonstation14.ms14.botany.ModWeightedRandomFillSolutions.serverCatalog(),
                        com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverReagents(), ModSeedProfiles.serverCatalog());
                require(cycle.severity() == 1f && cycle.unsupported().isEmpty(), "synthetic species mutation applied via production cycle owner");
                tray.setChanged();
                require(expected.equals(plant.genetics()) && tomato.equals(plant.seed())
                                && altered.lifecycle().yield() == plant.effectiveProfile().lifecycle().yield()
                                && altered.lifecycle().potency() == plant.effectiveProfile().lifecycle().potency()
                                && plant.effectiveProfile().chemicals().equals(expected.profile().chemicals())
                                && expected.profile().chemicals().get(flour.location()).equals(speciesChemicals.get(flour.location()))
                                && !expected.profile().chemicals().get(flour.location()).inherent()
                                && plant.effectiveProfile().consumeGasses().equals(altered.consumeGasses())
                                && plant.effectiveProfile().exudeGasses().equals(altered.exudeGasses()),
                        "species transition retains arranged lifecycle, chemistry, and gas state");
                var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                        e -> !e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
                var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var query = fixture.queryHands(epoch);
                var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2, query.revision(), hand,
                        null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                        "ordinary authenticated USE harvests species-changed plant");
                var outputs = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                        e -> !e.getItem().isEmpty() && !before.contains(e.getUUID()));
                require(outputs.size() == altered.lifecycle().yield(), "species-changed profile controls harvest yield");
                var tomatoProduct = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(expected.profile().product());
                require(outputs.stream().allMatch(e -> expected.equals(e.getItem().get(ModDataComponents.GENETIC_VALUE.get()))
                                && e.getItem().is(tomatoProduct)), "world harvest stack is tomato with transitioned genetics");
                continueGeneticsThroughExtractor(h, fixture, epoch, hand, pos, level, finished, expected, outputs.getFirst());
            } catch (Throwable failure) { finish(h, fixture, finished, "species mutation harvest", failure); }
        });
    }

    private static SeedProfile alteredWheatProfile(SeedProfile base,
            net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> flour) {
        var life = base.lifecycle();
        var alteredLife = new SeedProfile.Lifecycle(life.growthStages(), life.lifespan(), life.maturation(), life.production(),
                life.yield() + 2, life.potency() + 5, life.harvest());
        var chemicals = new java.util.HashMap<>(base.chemicals());
        chemicals.put(flour.location(), new SeedProfile.Chemical(0, 10, 2));
        return new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(), alteredLife, chemicals,
                java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN, 0f),
                java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 0.01f), base.mutationTargets(), base.scream(), base.sourceName());
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-genetics", "botany-stat-mutation"})
    public static void registeredPlayerSeedlessProduceCannotBeExtracted(GameTestHelper helper) {
        var species = ModSeedProfiles.seedId("wheat");
        var base = ModSeedProfiles.serverLookup(species);
        var life = base.lifecycle();
        // Arranged carried mutation result; this fixture does not claim RNG-created mutation.
        var seedless = new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(),
                new SeedProfile.Lifecycle(life.growthStages(), life.lifespan(), life.maturation(), life.production(),
                        life.yield(), life.potency(), life.harvest(), true, true), base.chemicals(), base.sourceName());
        var expected = new GeneticValue(species, seedless);
        var packet = new ItemStack(ModItems.WHEAT_SEEDS.get());
        packet.set(ModDataComponents.GENETIC_VALUE.get(), expected);
        runAuthenticatedHarvest(helper, packet, "seedless-wheat", 4000,
                (h, fixture, epoch, hand, tray, pos, level, finished) -> {
                    try {
                        var plant = tray.plantState();
                        require(plant.ready() && expected.equals(plant.genetics()),
                                "normally grown plant retained arranged effective seedless genetics");
                        var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                                e -> !e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                                .collect(java.util.stream.Collectors.toSet());
                        var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                        var harvestQuery = fixture.queryHands(epoch);
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2,
                                harvestQuery.revision(), hand, null, pos, hit.getDirection(), false,
                                BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                                "ordinary harvest accepts seedless produce");
                        var output = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                                e -> !e.getItem().isEmpty() && !before.contains(e.getUUID())).stream().findFirst().orElseThrow();
                        require(expected.equals(output.getItem().get(ModDataComponents.GENETIC_VALUE.get())),
                                "harvested produce retains carried seedless profile");
                        continueGeneticsThroughExtractor(h, fixture, epoch, hand, pos, level, finished, expected, output, true);
                    } catch (Throwable failure) { finish(h, fixture, finished, "seedless harvest", failure); }
                });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-genetics", "botany-stat-mutation", "botany-harvest"})
    public static void registeredPlayerZeroYieldHarvestClearsWithoutOutputs(GameTestHelper helper) {
        var species = ModSeedProfiles.seedId("wheat");
        var base = ModSeedProfiles.serverLookup(species);
        var life = base.lifecycle();
        // Arranged carried profile for output semantics, not an RNG mutation result.
        var zeroYield = new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(),
                new SeedProfile.Lifecycle(life.growthStages(), life.lifespan(), life.maturation(), life.production(),
                        0, life.potency(), life.harvest(), life.viable(), life.seedless()), base.chemicals(), base.sourceName());
        var expected = new GeneticValue(species, zeroYield);
        var packet = new ItemStack(ModItems.WHEAT_SEEDS.get());
        packet.set(ModDataComponents.GENETIC_VALUE.get(), expected);
        var stage = new java.util.concurrent.atomic.AtomicReference<>("registered fixture startup / natural growth");
        runAuthenticatedHarvest(helper, packet, "zero-yield-wheat", 2980,
                stage,
                (h, fixture, epoch, hand, tray, pos, level, finished) -> {
                    try {
                        stage.set("ready plant / zero-output ordinary USE");
                        var plant = tray.plantState();
                        require(plant.ready() && expected.equals(plant.genetics()),
                                "ordinary growth reaches readiness with arranged zero-yield genetics");
                        var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                        var query = fixture.queryHands(epoch);
                        require(query.reason() == BodyHandStateSnapshot.Reason.OK,
                                "before-harvest hand snapshot admitted: sequence=" + query.sequence() + " reason=" + query.reason());
                        var emptyHand = query.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                        require(emptyHand.token() == null && emptyHand.itemId() == null && emptyHand.count() == 0,
                                "harvest begins with selected hand empty under canonical snapshot contract");
                        long handRevision = query.revision();
                        var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                                e -> !e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                                .collect(java.util.stream.Collectors.toSet());
                        float water = plant.water(), nutrition = plant.nutrition();
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        stage.set("zero-output ordinary USE / AfterHarvest verification");
                        var outcome = BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2,
                                query.revision(), hand, null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE));
                        require(outcome == BodyBlockUseResult.Outcome.PLACED,
                                "ordinary manual USE accepts ready zero-yield harvest; outcome=" + outcome);
                        var after = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                                e -> !e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                                .collect(java.util.stream.Collectors.toSet());
                        require(after.equals(before), "zero-yield harvest publishes no fresh loose output");
                        require(plant.seed() == null && plant.water() == water && plant.nutrition() == nutrition,
                                "zero-yield no-repeat harvest clears source and preserves soil resources");
                        var handsAfter = fixture.queryHands(epoch);
                        require(handsAfter.reason() == BodyHandStateSnapshot.Reason.OK,
                                "after-harvest hand snapshot admitted: sequence=" + handsAfter.sequence() + " reason=" + handsAfter.reason());
                        var emptyAfter = handsAfter.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                        require(handsAfter.revision() == handRevision && emptyAfter.token() == null
                                        && emptyAfter.itemId() == null && emptyAfter.count() == 0,
                                "zero-yield harvest leaves empty-hand ownership and revision unchanged");
                        stage.set("zero-output USE and AfterHarvest verification complete");
                        finish(h, fixture, finished, "zero-yield harvest", null);
                    } catch (Throwable failure) { finish(h, fixture, finished, "zero-yield harvest", failure); }
                });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-genetics", "botany-chemical-mutation"})
    public static void registeredPlayerMutationPersistsThroughHarvestExtractionAndReplant(GameTestHelper helper) {
        var vestine = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("vestine");
        var stage = new java.util.concurrent.atomic.AtomicReference<>("registered fixture startup / natural growth");
        runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()), "chemical-mutation-wheat",
                stage,
                (h, fixture, epoch, hand, tray, pos, level, finished) -> {
                    try {
                        stage.set("ready callback: install 100-cent Vestine");
                        var plant = tray.plantState();
                        require(plant.genetics() != null, "plain wheat has initialized genetics");
                        var initial = new GeneticValue(plant.genetics().species(), plant.genetics().profile());
                        long initialRevision = plant.revision();
                        require(initial.profile().chemicals().values().stream().allMatch(SeedProfile.Chemical::inherent),
                                "plain wheat starts without acquired chemistry");
                        var soilBefore = tray.soilSolution();
                        var soil = new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(soilBefore.toComponent());
                        require(soil.admitUnits(vestine, 100, tray.resolvedTrayProfile().soilCapacityCents()) == 100,
                                "fixture admits exactly 100 cents of Vestine");
                        require(tray.tryReplaceSoil(tray.placementIdentity(), plant, initialRevision, soilBefore, soil),
                                "fixture installs Vestine into authenticated tray soil");
                        stage.set("ordinary tray poll: await Vestine consumption and mutation (300-tick budget)");
                        awaitChemicalMutation(h, fixture, epoch, hand, tray, pos, level, finished, initial, initialRevision, vestine, 0, stage);
                    } catch (Throwable failure) { finish(h, fixture, finished, "chemical mutation fixture", failure); }
                });
    }

    private static void awaitChemicalMutation(GameTestHelper helper, RegisteredServerPlayerFixture fixture, long epoch, String hand,
            HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level, AtomicBoolean finished, GeneticValue initial,
            long initialRevision, net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> vestine, int elapsed,
            java.util.concurrent.atomic.AtomicReference<String> stage) {
        if (finished.get()) return;
        BoundedGameTestWait.safeDelay(helper, 20, () -> {
            try {
                if (tray.soilSolution().snapshotUnits().getOrDefault(vestine, 0L) == 0
                        && tray.plantState().genetics() != null && !tray.plantState().genetics().equals(initial)) {
                    var mutated = tray.plantState().genetics();
                    stage.set("ordinary authenticated harvest");
                    require(!mutated.equals(initial) && tray.plantState().revision() > initialRevision,
                            "poll mutation changes genetics from immutable initial snapshot and increases revision");
                    var acquired = mutated.profile().chemicals().values().stream().filter(c -> !c.inherent()).toList();
                    require(acquired.size() == 1 && acquired.getFirst().min() <= acquired.getFirst().max()
                                    && acquired.getFirst().potencyDivisor() > 0, "one acquired chemical has valid bounds and divisor");
                    var table = com.juicyslew.moonstation14.ms14.botany.PlantChemicalMutation.prepare("evilrandomfillsolution",
                            com.juicyslew.moonstation14.ms14.botany.ModWeightedRandomFillSolutions.serverCatalog(),
                            com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverReagents());
                    var acquiredId = mutated.profile().chemicals().entrySet().stream().filter(e -> !e.getValue().inherent())
                            .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
                    require(table.fills().stream().anyMatch(group -> group.reagents().contains(acquiredId)),
                            "acquired chemical ID belongs to resolved canonical EvilRandomFillSolution table");
                    require(initial.profile().chemicals().entrySet().stream().allMatch(e -> e.getValue().inherent()
                                    && e.getValue().equals(mutated.profile().chemicals().get(e.getKey()))),
                            "all initial inherent wheat chemistry entries remain unchanged");
                    var freshWheat = ModSeedProfiles.serverLookup(initial.species());
                    require(freshWheat.equals(ModSeedProfiles.serverLookup(ModSeedProfiles.seedId("wheat")))
                                    && initial.profile().equals(freshWheat),
                            "mutation did not change the freshly resolved wheat catalog profile");
                    harvestChemicalMutation(helper, fixture, epoch, hand, tray, pos, level, finished, initial, mutated, vestine, stage);
                    return;
                }
                if (elapsed >= 300) throw new AssertionError(stage.get() + " timed out after " + elapsed
                        + " ticks; remaining Vestine cents=" + tray.soilSolution().snapshotUnits().getOrDefault(vestine, 0L)
                        + ", genetics=" + tray.plantState().genetics() + ", revision=" + tray.plantState().revision());
                awaitChemicalMutation(helper, fixture, epoch, hand, tray, pos, level, finished, initial, initialRevision, vestine, elapsed + 20, stage);
            } catch (Throwable failure) { finish(helper, fixture, finished, "chemical mutation polling", failure); }
        });
    }

    private static void harvestChemicalMutation(GameTestHelper helper, RegisteredServerPlayerFixture fixture, long epoch, String hand,
            HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level, AtomicBoolean finished, GeneticValue initial,
            GeneticValue expected, net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> vestine,
            java.util.concurrent.atomic.AtomicReference<String> stage) {
        try {
            var plant = tray.plantState();
            var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8), e -> !e.getItem().isEmpty())
                    .stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
            var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
            var query = fixture.queryHands(epoch);
            var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
            require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 2, query.revision(), hand,
                    null, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                    "ordinary authenticated USE harvests chemically mutated plant");
            var outputs = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8), e -> !e.getItem().isEmpty()
                    && !before.contains(e.getUUID()));
            require(outputs.size() == expected.profile().lifecycle().yield(), "mutated profile determines harvest yield");
            var acquiredEntry = expected.profile().chemicals().entrySet().stream().filter(e -> !e.getValue().inherent())
                    .findFirst().orElseThrow(() -> new AssertionError("mutated snapshot has no acquired chemical"));
            var acquiredReagent = net.minecraft.resources.ResourceKey.create(
                    com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, acquiredEntry.getKey());
            long expectedAcquiredCents = acquiredEntry.getValue().amountCents(expected.profile().lifecycle().potency());
            for (var output : outputs) {
                var stack = output.getItem();
                require(expected.equals(stack.get(ModDataComponents.GENETIC_VALUE.get())), "all outputs snapshot mutated genetics");
                var reagent = stack.get(ModDataComponents.REAGENT.get());
                require(reagent != null && reagent.centContents().getOrDefault(acquiredReagent, 0L) == expectedAcquiredCents
                                && stack.get(ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents()
                                    >= reagent.centContents().values().stream().mapToLong(Long::longValue).sum(),
                        "generated acquired reagent matches shared SeedProfile amountCents semantics within capacity");
            }
            stage.set("harvested produce pickup");
            continueGeneticsThroughExtractor(helper, fixture, epoch, hand, pos, level, finished, expected, outputs.getFirst(), false, stage);
        } catch (Throwable failure) { finish(helper, fixture, finished, "chemical mutation harvest", failure); }
    }

    private static void continueGeneticsThroughExtractor(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            long epoch, String hand, BlockPos trayPos, ServerLevel level, AtomicBoolean finished, GeneticValue expected,
            LooseItemEntity harvested) {
        continueGeneticsThroughExtractor(helper, fixture, epoch, hand, trayPos, level, finished, expected, harvested, false);
    }

    private static void continueGeneticsThroughExtractor(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            long epoch, String hand, BlockPos trayPos, ServerLevel level, AtomicBoolean finished, GeneticValue expected,
            LooseItemEntity harvested, boolean expectDenied) {
        continueGeneticsThroughExtractor(helper, fixture, epoch, hand, trayPos, level, finished, expected, harvested, expectDenied, null);
    }

    private static void continueGeneticsThroughExtractor(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            long epoch, String hand, BlockPos trayPos, ServerLevel level, AtomicBoolean finished, GeneticValue expected,
            LooseItemEntity harvested, boolean expectDenied, java.util.concurrent.atomic.AtomicReference<String> stage) {
        continueGeneticsThroughExtractor(helper, fixture, epoch, hand, trayPos, level, finished, expected, harvested,
                expectDenied, stage, false);
    }

    private static void continueGeneticsThroughExtractor(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            long epoch, String hand, BlockPos trayPos, ServerLevel level, AtomicBoolean finished, GeneticValue expected,
            LooseItemEntity harvested, boolean expectDenied, java.util.concurrent.atomic.AtomicReference<String> stage,
            boolean forceSingleOutput) {
        try {
            if (stage != null) stage.set("harvested produce pickup");
            var carrierReference = harvested.getItem().get(ModDataComponents.PLANT_SEED_REFERENCE.get());
            require(carrierReference != null, "committed harvest product carries its source seed-reference identity");
            var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
            body.setPos(trayPos.getX() + .5, trayPos.getY() + 1, trayPos.getZ() - 1.5);
            aimAt(body, harvested);
            var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            require(BodyHandItemTransfer.pickupSelected(fixture.player(), harvested.getUUID(), hand, hands.revision(), ignored -> {})
                    == BodyHandItemTransfer.Result.SUCCESS, "normal registered-player pickup of altered harvested wheat");
            if (stage != null) stage.set("extractor power topology await");
            var power = AirAlarmPowerFixture.place(helper);
            BlockPos machinePos = helper.absolutePos(new BlockPos(5, 12, 8));
            BlockPos secondTrayPos = helper.absolutePos(new BlockPos(7, 12, 8));
            level.setBlock(machinePos, ModBlocks.SEED_EXTRACTOR.get().defaultBlockState(), 3);
            level.setBlock(secondTrayPos, ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
            PowerFixtureReadiness.await(helper, level, 55,
                    () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level,
                            new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(power.cable(),
                                    net.minecraft.core.Direction.WEST,
                                    com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC)),
                    () -> "extractor APC topology ready; " + com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.lastSolveDiagnostic(level),
                    () -> PowerFixtureReadiness.await(helper, level, 35, () -> {
                        var extractor = (com.juicyslew.moonstation14.ms14.botany.SeedExtractorBlockEntity) level.getBlockEntity(machinePos);
                        if (extractor == null) return false;
                        var meter = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, extractor);
                        return meter.known() && meter.deliveredWatts() == 1000;
                    }, () -> "await extractor 1000 W allocation; meter="
                            + com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, machinePos), () -> {
                 try {
                     if (stage != null) stage.set("extractor power allocation await");
                    var machine = (com.juicyslew.moonstation14.ms14.botany.SeedExtractorBlockEntity) level.getBlockEntity(machinePos);
                    var secondTray = (HydroponicsTrayBlockEntity) level.getBlockEntity(secondTrayPos);
                    require(machine != null && machine.isPowered() && secondTray != null && secondTray.plantState() != null,
                            "resolved extractor powers and empty second tray initializes normally");
                    var meter = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(level, machine);
                     require(meter.known() && meter.deliveredWatts() == 1000,
                             "extractor has fresh real 1000 W allocation: " + meter);
                     if (stage != null) stage.set("authenticated extractor USE");
                    body.setPos(machinePos.getX() + .5, machinePos.getY() + 1, machinePos.getZ() - 1.5);
                    var produceQuery = fixture.queryHands(epoch);
                    var heldProduce = produceQuery.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                    var machineHit = RegisteredPlayerReadyDriver.aimAt(body, level, machinePos);
                    String produceToken = heldProduce.token();
                    var heldBefore = heldProduce.presentation().stack().copy();
                    var reagentBefore = heldBefore.get(ModDataComponents.REAGENT.get());
                     var outputBefore = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(machinePos).inflate(8),
                            entity -> !entity.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                             .collect(java.util.stream.Collectors.toSet());
                      if (forceSingleOutput) {
                          int candidate = -1;
                          for (int seed = 0; seed <= 255; seed++) {
                              level.random.setSeed(seed);
                              if (level.random.nextInt(machine.resolvedProfile().minimumPacketYield(),
                                      machine.resolvedProfile().maximumPacketYield() + 1) == 1) { candidate = seed; break; }
                          }
                          require(candidate >= 0, "bounded fixture RNG seed yields exactly one packet");
                          level.random.setSeed(candidate);
                      }
                      var outcome = BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 3,
                            produceQuery.revision(), hand, produceToken, machinePos, machineHit.getDirection(), false,
                            BodyBlockUseRequest.Intent.USE));
                     if (expectDenied) {
                         if (stage != null) stage.set("seedless extractor denial verification");
                        require(outcome == BodyBlockUseResult.Outcome.DENIED, "seedless carried genetics deny admitted extractor USE");
                        var afterQuery = fixture.queryHands(epoch);
                        var afterHand = afterQuery.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                        require(afterHand.token().equals(produceToken) && ItemStack.isSameItemSameComponents(
                                        afterHand.presentation().stack(), heldBefore)
                                        && afterHand.presentation().stack().getCount() == heldBefore.getCount()
                                        && expected.equals(afterHand.presentation().stack().get(ModDataComponents.GENETIC_VALUE.get())),
                                "denial preserves selected hand token, count and genetic stack");
                        require(java.util.Objects.equals(reagentBefore, afterHand.presentation().stack().get(ModDataComponents.REAGENT.get())),
                                "denial preserves source reagent contents");
                        require(level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(machinePos).inflate(8),
                                        entity -> !entity.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                                        .collect(java.util.stream.Collectors.toSet()).equals(outputBefore),
                                "denial publishes no machine-area output");
                        require(machine.isPowered() && com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime
                                        .processingMeter(level, machine).deliveredWatts() == 1000,
                                "denial leaves powered extractor allocation unchanged");
                        finish(helper, fixture, finished, "seedless extraction denial", null);
                        return;
                    }
                     require(outcome == BodyBlockUseResult.Outcome.PLACED,
                             "fresh-revision admitted ordinary USE extracts harvested produce");
                     var expectedPacketItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(expected.profile().packet());
                     require(expectedPacketItem != net.minecraft.world.item.Items.AIR, "expected genetics packet resolves from current profile");
                     if (forceSingleOutput) {
                         var actualHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                         long heldPackets = actualHands.handIds().stream().map(actualHands::stackCopy).flatMap(java.util.Optional::stream)
                                 .filter(stack -> stack.is(expectedPacketItem)
                                         && stack.get(ModDataComponents.PLANT_SEED_REFERENCE.get()) == carrierReference).count();
                         long worldPackets = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(machinePos).inflate(8),
                                 entity -> !entity.getItem().isEmpty() && entity.getItem().is(expectedPacketItem)
                                         && entity.getItem().get(ModDataComponents.PLANT_SEED_REFERENCE.get()) == carrierReference
                                         && !outputBefore.contains(entity.getUUID())).size();
                         require(heldPackets + worldPackets == 1, "extractor publishes exactly one packet with same reference object");
                         require(carrierReference.unique(), "extracted packet reference remains unique");
                     }
                     if (stage != null) stage.set("extracted packet pickup");
                    require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                            .token(hand).filter(produceToken::equals).isEmpty(), "extractor consumes source produce exactly once");
                    var handsAfter = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    var handsSnapshot = handsAfter;
                     var heldPacketHand = handsSnapshot.handIds().stream().filter(id -> handsSnapshot.stackCopy(id)
                             .filter(stack -> stack.is(expectedPacketItem) && expected.equals(stack.get(ModDataComponents.GENETIC_VALUE.get())))
                            .isPresent()).findFirst().orElse(null);
                    LooseItemEntity worldPacket = null;
                      if (heldPacketHand == null) {
                        worldPacket = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(machinePos).inflate(8),
                                 entity -> entity.getItem().is(expectedPacketItem)
                                        && expected.equals(entity.getItem().get(ModDataComponents.GENETIC_VALUE.get())))
                                .stream().findFirst().orElse(null);
                        require(worldPacket != null, "extracted packet is owned either by hands or published world");
                        body.setPos(machinePos.getX() + .5, machinePos.getY() + 1, machinePos.getZ() - 1.5);
                        aimAt(body, worldPacket);
                        handsAfter = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        require(BodyHandItemTransfer.pickupSelected(fixture.player(), worldPacket.getUUID(), hand, handsAfter.revision(), ignored -> {})
                                == BodyHandItemTransfer.Result.SUCCESS, "normal pickup of extracted altered-genetics packet");
                        heldPacketHand = hand;
                      }
                     if (stage != null) stage.set("ordinary USE replant");
                    var plantedQuery = fixture.queryHands(epoch);
                    String selectedPacketHand = heldPacketHand;
                    var packetInHand = plantedQuery.hands().stream().filter(value -> value.id().equals(selectedPacketHand)).findFirst().orElseThrow();
                      require(expected.equals(packetInHand.presentation().stack().get(ModDataComponents.GENETIC_VALUE.get())),
                              "held extracted packet retains full altered genetic value");
                      require(carrierReference.equals(packetInHand.presentation().stack().get(ModDataComponents.PLANT_SEED_REFERENCE.get())),
                              "extracted packet preserves the exact opaque source seed-reference identity");
                     var packetProfile = packetInHand.presentation().stack().get(ModDataComponents.GENETIC_VALUE.get()).profile();
                     require(packetProfile.consumeGasses().equals(expected.profile().consumeGasses())
                                     && packetProfile.exudeGasses().equals(expected.profile().exudeGasses()),
                             "extracted packet retains both carried gas maps, including legitimate empty maps");
                    body.setPos(secondTrayPos.getX() + .5, secondTrayPos.getY() + 1, secondTrayPos.getZ() - 1.5);
                    var plantHit = RegisteredPlayerReadyDriver.aimAt(body, level, secondTrayPos);
                    require(BodyHandActionNetworking.handleBlockUse(fixture.player(), new BodyBlockUseRequest(epoch, 4,
                            plantedQuery.revision(), heldPacketHand, packetInHand.token(), secondTrayPos, plantHit.getDirection(), false,
                            BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                            "fresh-revision ordinary USE replants extracted seed packet");
                     require(expected.equals(secondTray.plantState().genetics()) && expected.profile().equals(secondTray.plantState().effectiveProfile()),
                             "replanted tray uses extracted altered profile rather than catalog baseline");
                     require(carrierReference.equals(secondTray.plantState().seedReference()),
                              "ordinary replant adopts the carried seed-reference identity");
                     if (forceSingleOutput) require(secondTray.plantState().seedReference() == carrierReference
                                     && secondTray.plantState().seedReference().unique(),
                             "replanted tray retains exact unique source reference object");
                    require(secondTray.plantState().effectiveProfile().consumeGasses().equals(expected.profile().consumeGasses())
                                    && secondTray.plantState().effectiveProfile().exudeGasses().equals(expected.profile().exudeGasses()),
                            "replanted genetics retain carried gas maps, including legitimate empty maps");
                    require(secondTray.plantState().effectiveProfile().lifecycle().potency() == expected.profile().lifecycle().potency()
                                    && secondTray.plantState().effectiveProfile().lifecycle().yield() == expected.profile().lifecycle().yield()
                                    && secondTray.plantState().effectiveProfile().chemicals().equals(expected.profile().chemicals()),
                            "replant preserves nonbaseline potency, yield, and chemistry consumers");
                    finish(helper, fixture, finished, "genetics harvest/extract/replant", null);
                } catch (Throwable failure) { finish(helper, fixture, finished, "genetics extraction/replant", failure); }
            }));
        } catch (Throwable failure) { finish(helper, fixture, finished, "genetics extraction setup", failure); }
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

    public static void runAuthenticatedHarvest(GameTestHelper helper, ItemStack seeds, String label, ReadyPlantCallback readyCallback) {
        runAuthenticatedFixture(helper, seeds, label, 2980, null, true, readyCallback);
    }

    /** Runs the same admitted registered-player planting setup, then calls back without waiting for growth. */
    public static void runAuthenticatedPlanting(GameTestHelper helper, ItemStack seeds, String label, ReadyPlantCallback callback) {
        runAuthenticatedFixture(helper, seeds, label, 0, null, false, callback);
    }

    private static void runAuthenticatedHarvest(GameTestHelper helper, ItemStack seeds, String label,
            java.util.concurrent.atomic.AtomicReference<String> diagnosticStage, ReadyPlantCallback readyCallback) {
        runAuthenticatedFixture(helper, seeds, label, 2980, diagnosticStage, true, readyCallback);
    }

    private static void runAuthenticatedHarvest(GameTestHelper helper, ItemStack seeds, String label, int growthBudget, ReadyPlantCallback readyCallback) {
        runAuthenticatedFixture(helper, seeds, label, growthBudget, null, true, readyCallback);
    }

    private static void runAuthenticatedHarvest(GameTestHelper helper, ItemStack seeds, String label, int growthBudget,
            java.util.concurrent.atomic.AtomicReference<String> diagnosticStage, ReadyPlantCallback readyCallback) {
        runAuthenticatedFixture(helper, seeds, label, growthBudget, diagnosticStage, true, readyCallback);
    }

    private static void runAuthenticatedFixture(GameTestHelper helper, ItemStack seeds, String label, int growthBudget,
            java.util.concurrent.atomic.AtomicReference<String> diagnosticStage, boolean awaitNaturalHarvest, ReadyPlantCallback readyCallback) {
        boolean guarded = diagnosticStage != null;
        var stageRef = diagnosticStage == null ? new java.util.concurrent.atomic.AtomicReference<>("registered fixture startup / natural growth") : diagnosticStage;
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-harvest-" + label + "-v1");
        var finished = new AtomicBoolean();
        var diagnosticTray = new java.util.concurrent.atomic.AtomicReference<HydroponicsTrayBlockEntity>();
        var diagnosticPos = new java.util.concurrent.atomic.AtomicReference<BlockPos>();
        var ticketLease = new java.util.concurrent.atomic.AtomicReference<com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.Lease>();
        if (guarded) BoundedGameTestWait.safeAtTickTime(helper, 3800, () -> {
            if (finished.get()) return;
            var tray = diagnosticTray.get();
            var pos = diagnosticPos.get();
            ServerLevel diagnosticLevel = (ServerLevel) helper.getLevel();
            try {
                var plant = tray == null ? null : tray.plantState();
                var profile = plant == null ? null : plant.effectiveProfile();
                var gas = pos == null ? null : AtmosphereService.INSTANCE.sample(diagnosticLevel, pos.above()).orElse(null);
                String state = plant == null ? "uninitialized" : plant.status() + "/age=" + plant.age() + "/health=" + plant.health()
                        + "/viability=" + (profile == null ? "unknown" : profile.lifecycle().viable()) + "/pests=" + plant.pests()
                        + "/water=" + plant.water() + "/nutrition=" + plant.nutrition()
                        + "/seed=" + (plant.seed() == null ? "none" : plant.seed())
                        + "/genetics=" + (plant.genetics() == null ? "none" : plant.genetics().species());
                finish(helper, fixture, finished, stageRef.get(), new AssertionError("test-local watchdog reached tick 3800 before callback completion; stage="
                        + stageRef.get() + ", gameTime=" + diagnosticLevel.getGameTime() + ", plant=" + state + ", atmosphere="
                        + (gas == null ? "unknown" : "pressureKpa=" + gas.pressureKpa(1.0) + ", temperatureK=" + gas.temperatureKelvin())
                        + ", entityTicking=" + (pos != null && diagnosticLevel.isPositionEntityTicking(pos))));
            } catch (Throwable diagnosticFailure) {
                finish(helper, fixture, finished, stageRef.get(), new AssertionError("test-local watchdog diagnostic failed at stage="
                        + stageRef.get(), diagnosticFailure));
            }
        });
        try {
            helper.runAtTickTime(2, () -> {
                try {
                    var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                    ServerLevel level = (ServerLevel) helper.getLevel();
                    BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
                    diagnosticPos.set(pos);
                    if (guarded) stageRef.set("registered player readiness / tray startup");
                    var lease = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.around(helper,
                            pos.offset(-4, 0, -4), pos.offset(4, 3, 4), 0);
                    ticketLease.set(lease);
                    com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(helper, lease, 40, () -> {
                    com.juicyslew.moonstation14.ms14.botany.BotanyTrayGameTestEnvironment.prepareAuthenticatedRoom(helper, tray -> {
                        try {
                            diagnosticTray.set(tray);
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
                            var held = fixture.queryHands(owner.epoch());
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
                             stageRef.set("ordinary planted tray growth");
                              if (awaitNaturalHarvest) waitForHarvest(helper, fixture, owner.epoch(), hand, tray, pos, level, 0, growthBudget, finished, label, readyCallback, stageRef);
                              else {
                                  stageRef.set("ordinary planting callback");
                                  readyCallback.run(helper, fixture, owner.epoch(), hand, tray, pos, level, finished);
                              }
                        } catch (Throwable failure) { failure.printStackTrace(); finish(helper, fixture, finished, label + " setup", failure); }
                    });
                    }, pos, pos.above());
                } catch (Throwable failure) { finish(helper, fixture, finished, "player readiness", failure); }
            });
        } catch (Throwable failure) { finish(helper, fixture, finished, "fixture registration", failure); }
    }

    private static void waitForHarvest(GameTestHelper helper, RegisteredServerPlayerFixture fixture, long epoch,
                                       String hand, HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level,
                                       int elapsed, int growthBudget, AtomicBoolean finished, String label, ReadyPlantCallback readyCallback,
                                       java.util.concurrent.atomic.AtomicReference<String> diagnosticStage) {
        if (finished.get()) return;
        BoundedGameTestWait.safeDelay(helper, 20, () -> {
            if (finished.get()) return;
            try {
                var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body), "registered actor remains able to act");
                var gas = AtmosphereService.INSTANCE.sample(level, pos.above()).orElse(null);
                boolean atmosphereReady = gas != null && gas.pressureKpa(1.0) >= 81 && gas.pressureKpa(1.0) <= 121
                        && Math.abs(gas.temperatureKelvin() - 293) <= 10;
                if (!atmosphereReady || !tray.plantState().ready()) {
                    if (elapsed >= growthBudget) throw new AssertionError(label + " never became ready from ordinary BE ticks; stage=" + diagnosticStage.get()
                        + ", gameTime=" + level.getGameTime() + ", atmosphere="
                        + (gas == null ? "unknown" : "pressureKpa=" + gas.pressureKpa(1.0) + ", temperatureK=" + gas.temperatureKelvin())
                        + ", entityTicking=" + level.isPositionEntityTicking(pos)
                        + ", lastCycle=" + tray.plantState().lastCycle() + ", nextUpdate=" + tray.plantState().nextUpdate()
                        + ", skipAging=" + tray.plantState().skipAging()
                            + " age=" + tray.plantState().age() + " status=" + tray.plantState().status() + " health=" + tray.plantState().health()
                            + " viability=" + (tray.plantState().effectiveProfile() == null ? "unknown" : tray.plantState().effectiveProfile().lifecycle().viable())
                            + " pests=" + tray.plantState().pests() + " seed=" + (tray.plantState().seed() == null ? "none" : tray.plantState().seed())
                            + " genetics=" + (tray.plantState().genetics() == null ? "none" : tray.plantState().genetics().species())
                            + " water=" + tray.plantState().water() + " nutrition=" + tray.plantState().nutrition());
                    waitForHarvest(helper, fixture, epoch, hand, tray, pos, level, elapsed + 20, growthBudget, finished, label, readyCallback, diagnosticStage);
                    return;
                }
                diagnosticStage.set(label.equals("chemical-mutation-wheat") ? "ready plant callback / install Vestine" : "ready plant callback");
                readyCallback.run(helper, fixture, epoch, hand, tray, pos, level, finished);
            } catch (Throwable failure) { finish(helper, fixture, finished, "natural growth wait", failure); }
        });
    }

    @FunctionalInterface
    public interface ReadyPlantCallback {
        void run(GameTestHelper helper, RegisteredServerPlayerFixture fixture, long epoch, String hand,
                 HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level, AtomicBoolean finished);
    }

    private static void harvestAndVerify(GameTestHelper helper, RegisteredServerPlayerFixture fixture, long epoch,
                                        String hand, HydroponicsTrayBlockEntity tray, BlockPos pos, ServerLevel level,
                                        AtomicBoolean finished) {
        var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
        var plant = tray.plantState(); float water = plant.water(), nutrition = plant.nutrition();
        var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8), e -> !e.getItem().isEmpty()).size();
        var query = fixture.queryHands(epoch);
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
        var repeatQuery = fixture.queryHands(epoch);
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
        var picked = fixture.queryHands(epoch).hands().stream()
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
            var query = fixture.queryHands(epoch);
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
    private static java.util.List<net.minecraft.network.protocol.game.ClientboundSoundPacket> drainPlantScreamPackets(
            RegisteredServerPlayerFixture fixture) {
        var sounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
        Object packet;
        while ((packet = fixture.pollOutbound()) != null)
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound
                    && sound.getSound().value() == net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(SeedProfile.Scream.DEFAULT_SOUND))
                sounds.add(sound);
        return sounds;
    }

    public static boolean matchesPlantScreamPacket(net.minecraft.network.protocol.game.ClientboundSoundPacket actual, BlockPos pos) {
        var expected = new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(SeedProfile.Scream.DEFAULT_SOUND)),
                net.minecraft.sounds.SoundSource.BLOCKS, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                (float) Math.pow(10d, -10d / 20d), 1f, 0L);
        return actual.getSound().value() == expected.getSound().value() && actual.getSource() == expected.getSource()
                        && actual.getX() == expected.getX() && actual.getY() == expected.getY() && actual.getZ() == expected.getZ()
                        && actual.getVolume() == expected.getVolume() && actual.getPitch() == expected.getPitch();
    }

    private static void assertPlantScreamPacket(net.minecraft.network.protocol.game.ClientboundSoundPacket actual, BlockPos pos) {
        require(matchesPlantScreamPacket(actual, pos),
                "scream packet has expected sound, source, tray-center coordinates, gain, and pitch");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void finish(GameTestHelper helper, RegisteredServerPlayerFixture fixture, AtomicBoolean done,
                               String context, Throwable failure) {
        if (!done.compareAndSet(false, true)) return;
        Throwable terminalFailure = failure;
        try { fixture.close(); }
        catch (Throwable cleanupFailure) {
            if (terminalFailure == null) terminalFailure = cleanupFailure;
            else terminalFailure.addSuppressed(cleanupFailure);
        }
        if (terminalFailure == null) helper.succeed();
        else {
            terminalFailure.addSuppressed(new AssertionError("botany harvest failure stage: " + context));
            helper.testInfo.fail(terminalFailure);
        }
    }
}
