package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-sampling"})
public final class BotanySamplingAdmissionGameTests {
    private BotanySamplingAdmissionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany", "botany-sampling"})
    public static void authenticatedSamplingDeniesRawStageOneSampledAndDeadWithoutSideEffects(GameTestHelper helper) {
        var species = ModSeedProfiles.seedId("wheat");
        var base = ModSeedProfiles.serverLookup(species);
        var slowLife = new SeedProfile.Lifecycle(base.lifecycle().growthStages(), base.lifecycle().lifespan(), 12,
                base.lifecycle().production(), base.lifecycle().yield(), base.lifecycle().potency(), base.lifecycle().harvest(),
                base.lifecycle().viable(), base.lifecycle().seedless());
        var slow = new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(), slowLife,
                base.chemicals(), base.consumeGasses(), base.exudeGasses(), base.mutationTargets(),
                new SeedProfile.Scream(true, SeedProfile.Scream.DEFAULT_SOUND, -10), base.sourceName());
        var source = new GeneticValue(species, slow);
        ItemStack seeds = new ItemStack(ModItems.WHEAT_SEEDS.get());
        seeds.set(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get(), source);
        var sounds = new AtomicInteger();
        var position = new AtomicReference<BlockPos>();
        ServerLevel level = (ServerLevel) helper.getLevel();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == level && event.getSound().unwrapKey()
                    .map(key -> key.location().equals(SeedProfile.Scream.DEFAULT_SOUND)).orElse(false)) {
                BlockPos pos = position.get();
                if (pos != null && event.getPosition().distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) < 1e-6)
                    sounds.incrementAndGet();
            }
        };
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper, seeds, "sample-admission-denials",
                (h, fixture, epoch, emptyHand, tray, pos, world, finished) -> {
            position.set(pos);
            try {
                NeoForge.EVENT_BUS.addListener(listener);
                var body = HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var plant = tray.plantState();
                var reference = plant.seedReference();
                plant.transitionSpecies(new PlantSeedReference(source, true));
                reference = plant.seedReference();
                plant.state(2, 100, 0, 0, false, false);
                tray.setChanged();
                var wirecutter = LooseItemEntity.create(world, body.position().add(0, .1, .8), new ItemStack(ModItems.WIRECUTTER.get()));
                wirecutter.setNoGravity(true); require(world.addFreshEntity(wirecutter), "fixture wirecutter published");
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), wirecutter.getUUID(), emptyHand, hands.revision(), ignored -> {})
                        == BodyHandItemTransfer.Result.SUCCESS, "real wirecutter acquired through trusted fixture pickup");
                var before = world.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                        entity -> !entity.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                        .collect(java.util.stream.Collectors.toSet());
                float health = plant.health();
                var seedValue = reference.current();
                boolean sampledBefore = plant.sampled(), deadBefore = plant.dead();
                long plantRevision = plant.revision();
                String toolToken;

                var staleQuery = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 3));
                toolToken = staleQuery.hands().stream().filter(value -> value.id().equals(emptyHand)).findFirst().orElseThrow().token();
                var hit = RegisteredPlayerReadyDriver.aimAt(body, world, pos);
                var stale = new BodyBlockUseRequest(epoch, 2, staleQuery.revision() - 1, emptyHand, toolToken,
                        pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                require(AuthenticatedBlockUseDriver.use(fixture.player(), stale) == BodyBlockUseResult.Outcome.DENIED,
                        "stale hand revision is denied by authenticated ordinary USE");
                assertUnchanged(fixture.player(), epoch, 4, emptyHand, toolToken, health, reference, seedValue,
                        plant, before, world, pos, sounds, sampledBefore, deadBefore, plantRevision);

                plant.state(2, health, 0, 0, false, false);
                tray.setChanged();
                denyCurrent(fixture.player(), epoch, 3, 5, emptyHand, toolToken, body, world, pos);
                assertUnchanged(fixture.player(), epoch, 6, emptyHand, toolToken, health, reference, seedValue,
                        plant, before, world, pos, sounds, false, false, plantRevision);

                plant.state(8, health, 0, 0, false, false);
                plant.sampled(true);
                tray.setChanged();
                sampledBefore = plant.sampled(); deadBefore = plant.dead(); plantRevision = plant.revision();
                denyCurrent(fixture.player(), epoch, 4, 7, emptyHand, toolToken, body, world, pos);
                assertUnchanged(fixture.player(), epoch, 8, emptyHand, toolToken, health, reference, seedValue,
                        plant, before, world, pos, sounds, sampledBefore, deadBefore, plantRevision);

                plant.sampled(false);
                plant.state(8, health, 0, 0, false, true);
                tray.setChanged();
                sampledBefore = plant.sampled(); deadBefore = plant.dead(); plantRevision = plant.revision();
                denyCurrent(fixture.player(), epoch, 5, 9, emptyHand, toolToken, body, world, pos);
                assertUnchanged(fixture.player(), epoch, 10, emptyHand, toolToken, health, reference, seedValue,
                        plant, before, world, pos, sounds, sampledBefore, deadBefore, plantRevision);
                finish(h, fixture, finished, "sampling denial admission guards", null);
            } catch (Throwable failure) { finish(h, fixture, finished, "sampling denial admission guards", failure); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany", "botany-sampling"})
    public static void authenticatedNonreadySampleUsesWorldFallbackAndPreservesPacketHealthAndTool(GameTestHelper helper) {
        runAuthenticatedNonreadySample(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany", "botany-sampling"})
    public static void authenticatedAudioFailureStillFinalizesNegativeSampleHealth(GameTestHelper helper) {
        runAuthenticatedNonreadySample(helper, true);
    }

    private static void runAuthenticatedNonreadySample(GameTestHelper helper, boolean injectAudioFailure) {
        var species = ModSeedProfiles.seedId("wheat");
        var base = ModSeedProfiles.serverLookup(species);
        var profile = new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(), base.lifecycle(),
                base.chemicals(), base.consumeGasses(), base.exudeGasses(), base.mutationTargets(),
                new SeedProfile.Scream(true, SeedProfile.Scream.DEFAULT_SOUND, -10), base.sourceName());
        ItemStack seedPacket = new ItemStack(ModItems.WHEAT_SEEDS.get());
        seedPacket.set(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get(), new GeneticValue(species, profile));
        var soundCount = new AtomicInteger();
        var soundObservedCommittedPacket = new AtomicBoolean();
        var injectedAudioFailure = new AtomicBoolean();
        var healthAtSound = new AtomicReference<Float>();
        var trayPos = new AtomicReference<BlockPos>();
        var currentPlant = new AtomicReference<PlantInstance>();
        ServerLevel level = (ServerLevel) helper.getLevel();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() != level || !event.getSound().unwrapKey()
                    .map(key -> key.location().equals(SeedProfile.Scream.DEFAULT_SOUND)).orElse(false)) return;
            BlockPos pos = trayPos.get();
            if (pos == null || event.getPosition().distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) >= 1e-6) return;
            soundCount.incrementAndGet();
            var plant = currentPlant.get();
            float rawHealth = plant == null ? Float.NaN : plant.health();
            healthAtSound.set(rawHealth);
            soundObservedCommittedPacket.set(plant != null && (!injectAudioFailure || plant.health() == -20 || plant.health() == -30)
                    && !plant.sampled() && level.getEntitiesOfClass(LooseItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(8), entity -> !entity.getItem().isEmpty()
                            && entity.getItem().is(ModItems.WHEAT_SEEDS.get())
                            && entity.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get())
                                == plant.seedReference()
                            && entity.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get())
                                .health() == rawHealth).size() == 1);
            if (injectAudioFailure) {
                injectedAudioFailure.set(true);
                throw new RuntimeException("intentional sampling audio observer failure");
            }
        };
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper, seedPacket, "sampling-full-hands",
                (h, fixture, epoch, emptyHand, tray, pos, world, finished) -> {
            trayPos.set(pos);
            BlockPos replantPos = pos.north();
            world.setBlock(replantPos, ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
            helper.runAfterDelay(2, () -> {
            try {
                NeoForge.EVENT_BUS.addListener(listener);
                var body = HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var plant = tray.plantState(); currentPlant.set(plant);
                plant.transitionSpecies(new PlantSeedReference(plant.genetics(), true));
                float endurance = plant.effectiveProfile().biology().endurance();
                float age = plant.effectiveProfile().lifecycle().maturation() * .8f;
                float beforeHealth = injectAudioFailure ? 10 : endurance;
                plant.state(age, beforeHealth, 0, 0, false, false);
                tray.setChanged();
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var otherHand = hands.handIds().stream().filter(id -> !id.equals(emptyHand)).findFirst().orElseThrow();
                var cutterEntity = LooseItemEntity.create(world, body.position().add(0, .1, .8), new ItemStack(ModItems.WIRECUTTER.get()));
                cutterEntity.setNoGravity(true); require(world.addFreshEntity(cutterEntity), "sampling wirecutter published");
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), cutterEntity.getUUID(), emptyHand, hands.revision(), ignored -> {})
                        == BodyHandItemTransfer.Result.SUCCESS, "wirecutter normally held in active hand");
                hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var filler = LooseItemEntity.create(world, body.position().add(0, .1, .8), new ItemStack(net.minecraft.world.item.Items.STICK));
                filler.setNoGravity(true); require(world.addFreshEntity(filler), "filler item published");
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), filler.getUUID(), otherHand, hands.revision(), ignored -> {})
                        == BodyHandItemTransfer.Result.SUCCESS, "other hand filled through trusted fixture pickup");
                hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                String toolToken = hands.token(emptyHand).orElseThrow();
                String fillerToken = hands.token(otherHand).orElseThrow();
                int toolCount = hands.stackCopy(emptyHand).orElseThrow().getCount();
                int fillerCount = hands.stackCopy(otherHand).orElseThrow().getCount();
                var reference = plant.seedReference();
                var genetics = reference.current();
                var beforeIds = world.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                        entity -> !entity.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                        .collect(java.util.stream.Collectors.toSet());
                var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 5));
                var hit = RegisteredPlayerReadyDriver.aimAt(body, world, pos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(), new BodyBlockUseRequest(epoch, 2, query.revision(),
                        emptyHand, toolToken, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE))
                        == BodyBlockUseResult.Outcome.PLACED, "non-ready plant sampled via authenticated ordinary USE");
                if (injectAudioFailure) {
                    require(injectedAudioFailure.get(), "matching sound observer injected the intended RuntimeException");
                    require(plant.health() == 0 && !plant.dead(), "audio failure still performs unconditional nonlethal holder clamp");
                } else require(plant.health() == endurance - 30 || plant.health() == endurance - 40,
                        "non-ready health loss is 30/40 and remains above clamp floor");
                 require(!reference.unique() && reference.current() == genetics, "published sample shares exact immutable seed reference");
                 require(genetics.profile().sourceName().equals(base.sourceName()), "sampled canonical genetics retain resolved source name");
                var afterHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(afterHands.token(emptyHand).filter(toolToken::equals).isPresent()
                                && afterHands.stackCopy(emptyHand).orElseThrow().getCount() == toolCount
                                && afterHands.token(otherHand).filter(fillerToken::equals).isPresent()
                                && afterHands.stackCopy(otherHand).orElseThrow().getCount() == fillerCount,
                        "sampling preserves wirecutter and other full-hand item ownership/count");
                var outputs = world.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                        entity -> !entity.getItem().isEmpty() && !beforeIds.contains(entity.getUUID())
                                && entity.getItem().is(ModItems.WHEAT_SEEDS.get()));
                 require(outputs.size() == 1 && outputs.getFirst().getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get()) == reference,
                         "one exact-reference packet falls back to world when hands are full");
                 require(outputs.getFirst().getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get())
                                 .current().profile().sourceName().equals(base.sourceName()),
                         "sample packet carrier initialization retains current genetics source name");
                require(outputs.getFirst().getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get())
                                .health() == healthAtSound.get(),
                        "packet preserves exact raw post-loss source health observed before clamping");
                require(soundCount.get() == 1 && soundObservedCommittedPacket.get(),
                        "scream observer sees the published packet and pre-finalization source state");

                if (!injectAudioFailure) {
                var sampleEntity = outputs.getFirst();
                ItemStack packetSnapshot = sampleEntity.getItem().copy();
                float packetHealth = packetSnapshot.get(com.juicyslew.moonstation14.component.ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get()).health();
                var targetTray = (HydroponicsTrayBlockEntity) world.getBlockEntity(replantPos);
                require(targetTray != null && targetTray.plantState() != null, "trusted pre-action empty tray initialized");
                var handsQuery = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 6));
                var dropHand = handsQuery.hands().stream().filter(value -> value.id().equals(emptyHand)).findFirst().orElseThrow();
                var droppedTool = BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.DROP, emptyHand, epoch, 3, handsQuery.revision(), null, toolToken));
                require(droppedTool.accepted(), "wirecutter intentionally dropped through authenticated hand action after sampling checks");
                var handsAfterDrop = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var packetHands = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 7));
                var activeAfterDrop = packetHands.hands().stream().filter(value -> value.id().equals(emptyHand)).findFirst().orElseThrow();
                require(activeAfterDrop.token() == null, "normal drop freed active hand for packet pickup");
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), sampleEntity.getUUID(), emptyHand,
                                handsAfterDrop.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS,
                        "sample packet picked up through normal authenticated world-item transfer");
                var plantQuery = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 8));
                var plantHand = plantQuery.hands().stream().filter(value -> value.id().equals(emptyHand)).findFirst().orElseThrow();
                require(plantHand.token() != null && plantHand.presentation().stack().getCount() == packetSnapshot.getCount(),
                        "packet ownership is present in a fresh active-hand snapshot");
                body.setPos(replantPos.getX() + .5, replantPos.getY() + 1, replantPos.getZ() - 1.5);
                var replantHit = RegisteredPlayerReadyDriver.aimAt(body, world, replantPos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(), new BodyBlockUseRequest(epoch, 4,
                                plantQuery.revision(), emptyHand, plantHand.token(), replantPos, replantHit.getDirection(),
                                false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.PLACED,
                        "sample packet replanted through ordinary authenticated USE");
                require(sampleEntity.isRemoved(), "normal packet planting consumes the world packet after hand pickup");
                 require(targetTray.plantState().seedReference() == reference
                                 && targetTray.plantState().genetics() == genetics
                                 && targetTray.plantState().genetics().profile().sourceName().equals(base.sourceName())
                                 && targetTray.plantState().health() == packetHealth,
                        "replant adopts exact current seed identity/genetics and packet health override");
                var finalHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(finalHands.stackCopy(emptyHand).isEmpty()
                                && finalHands.token(otherHand).filter(fillerToken::equals).isPresent()
                                && finalHands.stackCopy(otherHand).orElseThrow().getCount() == fillerCount,
                        "ordinary packet path leaves the active hand empty and preserves the other-hand filler");
                require(!reference.unique(), "replant does not reset source sharing state");
                }
                finish(h, fixture, finished, "full-hands non-ready sampling", null);
            } catch (Throwable failure) { finish(h, fixture, finished, "full-hands non-ready sampling", failure); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            });
        });
    }

    private static void denyCurrent(net.minecraft.server.level.ServerPlayer actor, long epoch, long actionSequence,
            long querySequence, String hand, String token, net.minecraft.world.entity.LivingEntity body,
            ServerLevel level, BlockPos pos) {
        var query = BodyHandStateService.query(actor, new BodyHandStateQuery(epoch, querySequence));
        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
        require(AuthenticatedBlockUseDriver.use(actor, new BodyBlockUseRequest(epoch, actionSequence, query.revision(),
                hand, token, pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE)) == BodyBlockUseResult.Outcome.DENIED,
                "sample guard denies without accepting current authenticated request");
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 200)
    @GameTestTags({"botany", "botany-sampling"})
    public static void traySamplingFinalizerClampsNegativeHealthWhenSampleRollIsFalse(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
        helper.runAfterDelay(2, () -> {
            try {
                var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(pos);
                require(tray != null && tray.plantState() != null, "real tray owner initialized before owner-boundary exercise");
                var species = ModSeedProfiles.seedId("wheat");
                var profile = ModSeedProfiles.serverLookup(species);
                var reference = new PlantSeedReference(new GeneticValue(species, profile), true);
                tray.commitPlant(reference, level.getGameTime(), null);
                var plant = tray.plantState();
                float endurance = profile.biology().endurance();
                float age = profile.lifecycle().maturation() * .8f;
                plant.state(age, 10, 0, 0, false, false);
                tray.setChanged();
                var plan = PlantSamplingPlan.prepare(plant, 3);
                require(plan != null && plan.packetHealthOverride() == -20f && plan.postCommitHealth() == 0,
                        "pure source plan retains raw negative packet health and separate clamp result");
                ItemStack packet = GeneticCarriers.createSeedPacket(reference, plan.packetHealthOverride());
                require(!packet.isEmpty(), "registered sample packet factory builds actual packet");
                UUID placement = tray.placementIdentity();
                long plantRevision = plant.revision();
                long[] committedRevision = {-1};
                var publication = FreshWorldOutputBatch.publishSystemHarvest(level, pos, java.util.List.of(packet),
                        () -> tray.isLoadedValid() && tray.placementIdentity().equals(placement)
                                && tray.plantState() == plant && plant.revision() == plantRevision,
                        () -> {
                            require(tray.commitSamplingHealth(placement, plant, plantRevision, reference,
                                    plant.health() - plan.healthLoss()), "tray source-health/share commit succeeds after packet publication");
                            committedRevision[0] = plant.revision();
                        });
                require(publication == BodyHandItemTransfer.Result.SUCCESS, "fresh world publication commits exact sample packet");
                var outputs = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                        entity -> !entity.getItem().isEmpty() && entity.getItem().is(ModItems.WHEAT_SEEDS.get())
                                && entity.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get()) == reference);
                require(outputs.size() == 1 && outputs.getFirst().getItem()
                                .get(com.juicyslew.moonstation14.component.ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get()).health() == -20f,
                        "published world packet retains negative raw health before holder clamp");
                require(plant.health() == -20f && !plant.dead() && !plant.sampled() && !reference.unique(),
                        "first tray commit retains negative alive state and marks exact reference shared");
                require(tray.finishSampling(placement, plant, committedRevision[0], reference, false, endurance),
                        "tray owner accepts explicit false sampled roll for finalization boundary");
                require(plant.health() == 0 && !plant.dead() && !plant.sampled(),
                        "false roll still unconditionally clamps negative holder health without immediate death");
                require(outputs.getFirst().getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.SEED_PACKET_HEALTH_OVERRIDE.get()).health() == -20f,
                        "holder clamp does not rewrite the already-published raw packet health");
                helper.succeed();
            } catch (Throwable failure) { helper.testInfo.fail(new AssertionError("tray sampling finalization boundary", failure)); }
        });
    }

    private static void assertUnchanged(net.minecraft.server.level.ServerPlayer actor, long epoch, long querySequence,
            String hand, String token, float health, PlantSeedReference reference, GeneticValue genetics,
            PlantInstance plant, java.util.Set<java.util.UUID> before, ServerLevel level, BlockPos pos, AtomicInteger sounds,
            boolean expectedSampled, boolean expectedDead, long expectedRevision) {
        require(plant.health() == health && plant.seedReference() == reference && reference.current() == genetics && reference.unique(),
                "denial preserves health and unique reference identity/value");
        require(plant.sampled() == expectedSampled && plant.dead() == expectedDead && plant.revision() == expectedRevision,
                "denial preserves sampled/dead flags and plant revision");
        var after = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8),
                entity -> !entity.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID)
                .collect(java.util.stream.Collectors.toSet());
        require(after.equals(before), "denial publishes no output");
        var query = BodyHandStateService.query(actor, new BodyHandStateQuery(epoch, querySequence));
        require(query.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow().token().equals(token),
                "denial leaves wirecutter token owned");
        require(sounds.get() == 0, "denial emits no scream");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void finish(GameTestHelper helper, com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture,
                               AtomicBoolean finished, String label, Throwable failure) {
        if (!finished.compareAndSet(false, true)) return;
        fixture.close();
        if (failure == null) helper.succeed(); else helper.testInfo.fail(new AssertionError(label, failure));
    }
}
