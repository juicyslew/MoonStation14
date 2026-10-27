package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsFixtureTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Authenticated loose-source slicing using the real registered KitchenKnife; profiles are fixture-scoped. */
@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "food-slicing"})
public final class LooseFoodSlicingAuthenticatedGameTests {
    private LooseFoodSlicingAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000, batch = "catalog-item-processing-slicing-output")
    @GameTestTags({"food-slicing", "food-slicing-authenticated", "merge-main-remaining", "merge-main-stage-two"})
    public static void registeredPlayerStartsTimedSliceAndPublishesFiveIndependentFoodItems(GameTestHelper helper) {
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()),
                "authenticated-food-slicing", (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            var manager = PrototypeRuntime.serverManager();
            var procType = ModItemProcessingProfiles.TYPE;
            var oldProcessing = new HashMap<>(manager.snapshot(procType).asMap());
            var toolType = ModToolProfiles.TYPE;
            var oldTools = new HashMap<>(manager.snapshot(toolType).asMap());
            var ingestionType = ModIngestionProfiles.TYPE;
            var oldIngestion = new HashMap<>(manager.snapshot(ingestionType).asMap());
            var cleanup = new ItemProcessingFixtureCleanup(h, fixture, finished, manager, procType, oldProcessing, toolType, oldTools,
                    ingestionType, oldIngestion, null);
            try {
                ResourceLocation tomato = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.TOMATO.get());
                ResourceLocation sliceOutput = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WHEAT_BUSHEL.get());
                installSliceOutputFoodProfile(h, manager, sliceOutput);
                var processing = new HashMap<>(oldProcessing);
                processing.put(tomato, new ItemProcessingProfile(null,
                        new ItemProcessingProfile.SliceableFood(sliceOutput, 5, 1f)));
                manager.publishDecoded(procType, processing);

                var ingestion = new HashMap<>(oldIngestion);
                ingestion.put(tomato, withTrash(ingestion.get(tomato), net.minecraft.world.item.Items.STICK));
                ingestion.put(sliceOutput, withTrash(ingestion.get(sliceOutput), net.minecraft.world.item.Items.PAPER));
                manager.publishDecoded(ingestionType, ingestion);

                ResourceLocation knifeId = BuiltinKeys.knife();
                var productionKnife = oldTools.get(knifeId);
                require(productionKnife != null && productionKnife.knife() != null && productionKnife.sharp() != null,
                        "resolved production KitchenKnife profile retains both Knife and Sharp capabilities");

                var body = HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var knife = LooseItemEntity.create(level, body.position().add(0, .1, .8), new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                knife.setNoGravity(true); require(level.addFreshEntity(knife), "registered KitchenKnife published");
                var hands = com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get();
                var liveHands = body.getExistingDataOrNull(hands);
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), knife.getUUID(), hand, liveHands.revision(), ignored -> {})
                        == BodyHandItemTransfer.Result.SUCCESS, "registered player acquires production knife through hand authority");
                liveHands = body.getExistingDataOrNull(hands);
                var token = liveHands.token(hand).orElseThrow();

                var tomatoStack = new ItemStack(ModItems.TOMATO.get());
                com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(tomatoStack, level);
                var source = LooseItemEntity.create(level, body.position().add(0, .1, 1.5), tomatoStack);
                require(level.addFreshEntity(source), "real registered tomato source published with native loose-item physics");
                Runnable beginSlice = () -> {
                var sounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
                drainFoodChop(fixture, sounds);
                var carrier = fixture.player();
                carrier.setPos(body.position().add(40, 0, 0));
                carrier.setCamera(body);
                require(carrier.getCamera() == body && carrier.position().distanceTo(body.position()) > 30,
                        "camera remains the nearby committed body while native carrier is remote");
                var sourceUnits = MS14Provider.getDetached(source.getItem(), MS14Bridges.REAGENT).snapshotUnits();
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source.getBoundingBox().getCenter());
                var settledHands = body.getExistingDataOrNull(hands);
                var otherHand = settledHands.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, otherHand, "fixture-stick",
                        new ItemStack(net.minecraft.world.item.Items.STICK)), "real Stick assigned through live hand ownership gateway");
                var query = fixture.queryHands(epoch);
                var selected = BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.SELECT_HAND, otherHand, epoch, 2, query.revision(), null, null));
                require(selected.accepted(), "authenticated selection activates Stick hand");
                var stickQuery = fixture.queryHands(epoch);
                var active = stickQuery.hands().stream().filter(value -> value.id().equals(otherHand)).findFirst().orElseThrow();
                var sharpOnly = new HashMap<>(oldTools);
                var fixtureToolId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.STICK);
                sharpOnly.put(fixtureToolId, new ToolProfile(null, null, null,
                        com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata.EMPTY,
                        Set.of(), null, null, null, new ToolProfile.Sharp(), null));
                manager.publishDecoded(toolType, sharpOnly);
                require(BodyHandRequestService.apply(fixture.player(), request(epoch, 3, stickQuery.revision(), otherHand, active.token(), source)).reason()
                        == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED
                        && !source.isRemoved(), "Sharp-only capability is not the slicing Knife utensil and preserves source");
                var cutterOnly = new HashMap<>(oldTools);
                cutterOnly.put(fixtureToolId, new ToolProfile(new ToolProfile.Tool(java.util.List.of(ToolProfile.Quality.CUTTING)),
                        null, null, com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata.EMPTY,
                        Set.of(), null, null, null, null, null));
                manager.publishDecoded(toolType, cutterOnly);
                require(BodyHandRequestService.apply(fixture.player(), request(epoch, 4, stickQuery.revision(), otherHand, active.token(), source)).reason()
                        == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED
                        && !source.isRemoved(), "CUTTING-only capability cannot slice and preserves source");
                manager.publishDecoded(toolType, oldTools);
                var restoreQuery = fixture.queryHands(epoch);
                require(BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.SELECT_HAND, hand, epoch, 5, restoreQuery.revision(), null, null)).accepted(),
                        "authenticated selection restores original KitchenKnife hand");
                var knifeQuery = fixture.queryHands(epoch);
                var knifeActive = knifeQuery.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                var accepted = BodyHandRequestService.apply(fixture.player(), request(epoch, 6, knifeQuery.revision(), hand, token, source));
                if (!accepted.accepted() || source.isRemoved()) {
                    var live = body.getExistingDataOrNull(hands);
                    var operations = body.getData(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get()).operations();
                    throw new AssertionError("slice start reason=" + accepted.reason() + " active=" + live.activeHand()
                            + " requested=" + hand + " rev=" + knifeQuery.revision() + "/" + live.revision()
                            + " ; authenticated USE_TARGET starts timed slicing without immediate deletion, hostTransition="
                            + accepted.hostTransition() + ", epoch=" + epoch
                            + ", sequence=6, requestedHand=" + hand + ", selectedHand=" + live.activeHand() + ", tool="
                            + live.stackCopy(hand).map(stack -> stack.getItem().toString()).orElse("empty")
                            + ", sourceRemoved=" + source.isRemoved() + ", pendingOperations=" + operations.stream()
                                    .filter(operation -> operation.kind().equals("food_slicing")).toList());
                }
                drainFoodChop(fixture, sounds);
                require(sounds.isEmpty(), "timed slice start emits no chop cue");
                var sourceOrigin = source.position();
                h.runAfterDelay(2, () -> guarded(cleanup, () -> {
                    TimedInteractionSystem.damage(body, 1.0);
                     require(!source.isRemoved(), "damage cancellation retains source");
                     drainFoodChop(fixture, sounds);
                     require(sounds.isEmpty(), "cancelled slice emits no chop cue");
                    require(hasCancelledSlice(body, source), "damage cancels the active slice session before restart");
                     require(BodyHandRequestService.apply(fixture.player(), request(epoch, 7, knifeQuery.revision(), hand,
                             knifeActive.token(), source)).accepted(), "authenticated request restarts after damage cancellation");
                }));
                h.runAfterDelay(4, () -> guarded(cleanup, () -> {
                    source.setPos(sourceOrigin.add(.1, 0, 0));
                    h.runAfterDelay(4, () -> guarded(cleanup, () -> {
                    source.setPos(sourceOrigin);
                    body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source.getBoundingBox().getCenter());
                        require(hasCancelledSlice(body, source), "moving nominated target cancels prior slice before restart");
                        require(BodyHandRequestService.apply(fixture.player(), request(epoch, 8, knifeQuery.revision(), hand,
                                knifeActive.token(), source)).accepted(), "target/source staleness cancels old session and fresh exact target starts anew");
                    }));
                }));
                var outputIdsBefore = level.getEntitiesOfClass(LooseItemEntity.class,
                        new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                        e -> e.getItem().is(ModItems.WHEAT_BUSHEL.get())).stream()
                        .map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
                h.runAfterDelay(10, () -> guarded(cleanup,
                        () -> require(!source.isRemoved(), "source remains through half of one-second slice deadline")));
                h.runAfterDelay(12, () -> guarded(cleanup, () -> {
                    try {
                        require(!source.isRemoved(), "source remains before completion deadline");
                        require(BodyHandRequestService.apply(fixture.player(), request(epoch, 9, knifeQuery.revision(), hand,
                                knifeActive.token(), source)).accepted() && !source.isRemoved(),
                                "duplicate USE_TARGET cancels the live timed slice without consuming source");
                        drainFoodChop(fixture, sounds);
                        require(sounds.isEmpty(), "cancel and replay emit no chop cue");
                        require(BodyHandRequestService.apply(fixture.player(), request(epoch, 10, knifeQuery.revision(), hand,
                                knifeActive.token(), source)).accepted() && !source.isRemoved(), "second authorized request restarts timed slicing");
                    } catch (Throwable failure) {
                        cleanup.finish(failure); return;
                    }
                }));
                h.runAfterDelay(40, () -> guarded(cleanup, () -> {
                    try {
                        require(source.isRemoved(), completionFailureDiagnostic(body, source, sourceOrigin,
                                body.getExistingDataOrNull(hands), hand,
                                knifeQuery.revision(), level));
                        drainFoodChop(fixture, sounds);
                        require(sounds.size() == 1, "successful committed slice emits exactly one chop cue");
                        var heard = sounds.getFirst();
                        require(heard.getSound().value() == com.juicyslew.moonstation14.sounds.ModSounds.FOOD_CHOP.get(), "native packet carries FOOD_CHOP");
                        require(heard.getSource() == net.minecraft.sounds.SoundSource.PLAYERS && Math.abs(heard.getVolume() - 0.7943282f) < 1e-6 && heard.getPitch() == 1f,
                                "chop packet preserves PLAYERS source, -2 dB gain and pitch");
                        var expectedSound = new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                                net.minecraft.core.Holder.direct(com.juicyslew.moonstation14.sounds.ModSounds.FOOD_CHOP.get()),
                                net.minecraft.sounds.SoundSource.PLAYERS, sourceOrigin.x, sourceOrigin.y, sourceOrigin.z, 0.7943282f, 1f, 0L);
                        require(heard.getX() == expectedSound.getX() && heard.getY() == expectedSound.getY() && heard.getZ() == expectedSound.getZ(),
                                "native chop packet encodes captured source origin");
                        var outputs = level.getEntitiesOfClass(LooseItemEntity.class,
                                new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                                e -> e.getItem().is(ModItems.WHEAT_BUSHEL.get()) && !outputIdsBefore.contains(e.getUUID()));
                        require(outputs.size() == 5, "five independently spawned slice owners");
                        require(outputs.stream().allMatch(e -> e.getItem().getCount() == 1), "slices are separate count-one stacks");
                        var childSolutions = outputs.stream().map(e -> MS14Provider.getDetached(
                                e.getItem(), MS14Bridges.REAGENT).snapshotUnits()).toList();
                        var expectedPlans = SlicingPlan.prepare(sourceUnits, 5, 150);
                        var expectedMultiset = expectedPlans.stream().collect(java.util.stream.Collectors.groupingBy(
                                java.util.function.Function.identity(), java.util.stream.Collectors.counting()));
                        var actualMultiset = childSolutions.stream().collect(java.util.stream.Collectors.groupingBy(
                                java.util.function.Function.identity(), java.util.stream.Collectors.counting()));
                        require(actualMultiset.equals(expectedMultiset),
                                "five children receive canonical per-output current-solution plan with exact multiplicity");
                        for (var reagent : sourceUnits.keySet()) {
                            long childTotal = childSolutions.stream().mapToLong(map -> map.getOrDefault(reagent, 0L)).sum();
                            require(childTotal <= sourceUnits.get(reagent), "child reagent outputs do not duplicate " + reagent);
                        }
                        require(childSolutions.stream().allMatch(map -> map.values().stream().mapToLong(Long::longValue).sum() <= 150),
                                "each child solution respects resolved output capacity");
                        require(childSolutions.stream().mapToLong(map -> map.values().stream().mapToLong(Long::longValue).sum()).sum() == 750,
                                "capacity overflow is lost per output and not redistributed");
                        require(sourceUnits.values().stream().mapToLong(Long::longValue).sum() == 1400,
                                "fixture source solution contains the resolved 1400-cent tomato contents");
                        var stickTrash = level.getEntitiesOfClass(LooseItemEntity.class,
                                new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                                e -> e.getItem().is(net.minecraft.world.item.Items.STICK));
                        require(stickTrash.size() == 1 && level.getEntitiesOfClass(LooseItemEntity.class,
                                new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                                e -> e.getItem().is(net.minecraft.world.item.Items.PAPER)).isEmpty(),
                                "source edible trash is emitted once; child edible trash is not copied");
                        require(body.getExistingDataOrNull(hands).token(hand).filter(token::equals).isPresent(),
                                "knife token remains in active hand after completion");
                    } catch (Throwable failure) {
                        cleanup.finish(failure); return;
                    }
                    cleanup.finish(null);
                }));
                };
                ItemProcessingFixtureSupport.awaitSettledSource(h, cleanup, source, beginSlice);
            } catch (Throwable failure) {
                cleanup.finish(failure);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000, batch = "catalog-item-processing-slicing-cancel")
    @GameTestTags({"food-slicing", "food-slicing-authenticated", "merge-main-remaining"})
    public static void activeHandChangeInvalidatesAdmittedSlice(GameTestHelper helper) {
        runCancellationFixture(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000, batch = "catalog-item-processing-slicing-blocked")
    @GameTestTags({"food-slicing", "food-slicing-authenticated", "merge-main-remaining"})
    public static void blockedOutputFootprintRetainsAdmittedSliceSource(GameTestHelper helper) {
        runCancellationFixture(helper, true);
    }

    private static void runCancellationFixture(GameTestHelper helper, boolean blockOutput) {
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()),
                blockOutput ? "slice-blocked-output" : "slice-hand-change", (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            var manager = PrototypeRuntime.serverManager();
            var procType = ModItemProcessingProfiles.TYPE;
            var oldProcessing = new HashMap<>(manager.snapshot(procType).asMap());
            var toolType = ModToolProfiles.TYPE;
            var oldTools = new HashMap<>(manager.snapshot(toolType).asMap());
            var ingestionType = ModIngestionProfiles.TYPE;
            var oldIngestion = new HashMap<>(manager.snapshot(ingestionType).asMap());
            var listenerRef = new AtomicReference<Consumer<EntityJoinLevelEvent>>();
            var cleanup = new ItemProcessingFixtureCleanup(h, fixture, finished, manager, procType, oldProcessing, toolType, oldTools,
                    ingestionType, oldIngestion, listenerRef);
            try {
                var tomato = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.TOMATO.get());
                var sliceOutput = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WHEAT_BUSHEL.get());
                installSliceOutputFoodProfile(h, manager, sliceOutput);
                var processing = new HashMap<>(oldProcessing);
                processing.put(tomato, new ItemProcessingProfile(null,
                        new ItemProcessingProfile.SliceableFood(sliceOutput, 5, 1f)));
                manager.publishDecoded(procType, processing);
                var ingestion = new HashMap<>(oldIngestion);
                ingestion.put(tomato, withTrash(ingestion.get(tomato), net.minecraft.world.item.Items.STICK));
                ingestion.put(sliceOutput, withTrash(ingestion.get(sliceOutput), net.minecraft.world.item.Items.PAPER));
                manager.publishDecoded(ingestionType, ingestion);
                var knifeId = BuiltinKeys.knife();
                var productionKnife = oldTools.get(knifeId);
                require(productionKnife != null && productionKnife.knife() != null && productionKnife.sharp() != null,
                        "resolved production KitchenKnife profile retains both Knife and Sharp capabilities");
                var body = HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var knife = LooseItemEntity.create(level, body.position().add(0, .1, .8), new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                knife.setNoGravity(true); require(level.addFreshEntity(knife), "registered KitchenKnife published");
                var attachment = com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get();
                var hands = body.getExistingDataOrNull(attachment);
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), knife.getUUID(), hand, hands.revision(), ignored -> {})
                        == BodyHandItemTransfer.Result.SUCCESS, "production knife acquired through hand authority");
                hands = body.getExistingDataOrNull(attachment);
                var token = hands.token(hand).orElseThrow();
                var sourceStack = new ItemStack(ModItems.TOMATO.get());
                com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(sourceStack, level);
                var source = LooseItemEntity.create(level, body.position().add(0, .1, 1.5), sourceStack);
                require(level.addFreshEntity(source), "slice source published with native loose-item physics");
                Runnable admitSlice = () -> {
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source.getBoundingBox().getCenter());
                var query = fixture.queryHands(epoch);
                var active = query.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                require(BodyHandRequestService.apply(fixture.player(), request(epoch, 2, query.revision(), hand, active.token(), source)).accepted(),
                        "authenticated slice admitted before changing hand/publication environment");
                var outputIds = Set.copyOf(level.getEntitiesOfClass(LooseItemEntity.class,
                        new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                        e -> e.getItem().is(ModItems.WHEAT_BUSHEL.get()) || e.getItem().is(net.minecraft.world.item.Items.STICK)
                                || e.getItem().is(net.minecraft.world.item.Items.PAPER)).stream().map(LooseItemEntity::getUUID).toList());
                AtomicInteger joinCount = new AtomicInteger();
                Consumer<EntityJoinLevelEvent> listener = event -> {
                    if (event.getLevel() == level && event.getEntity() instanceof LooseItemEntity loose
                            && loose.getItem().is(ModItems.WHEAT_BUSHEL.get())) {
                        int attempt = joinCount.incrementAndGet();
                        if (blockOutput && attempt == 2) event.setCanceled(true);
                    }
                };
                 cleanup.attachListener(listener);
                 if (!blockOutput) {
                      var currentHandsForSwitch = body.getExistingDataOrNull(attachment);
                      String other = currentHandsForSwitch.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
                      long revisionBeforeSelection = currentHandsForSwitch.revision();
                      require(BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                              BodyHandActionRequest.Action.SELECT_HAND, other, epoch, 3, currentHandsForSwitch.revision(), null, null)).accepted(),
                             "real authenticated SELECT_HAND changes active hand and revision");
                      var handsAfterSelection = body.getExistingDataOrNull(attachment);
                      require(handsAfterSelection.activeHand().equals(other)
                                      && handsAfterSelection.revision() > revisionBeforeSelection,
                              "hand selection commits the other active hand and advances revision");
                      require(body.getExistingDataOrNull(attachment).token(hand).filter(token::equals).isPresent(),
                              "knife remains owned in original slot after hand selection");
                      h.runAfterDelay(2, () -> guarded(cleanup, () -> require(hasCancelledSlice(body, source),
                             "active-hand change records cancelled admitted slice during terminal retention")));
                 }
                h.runAfterDelay(30, () -> guarded(cleanup, () -> {
                    try {
                        require(!source.isRemoved() && level.getEntity(source.getUUID()) == source,
                                "cancelled/blocked completion retains exact source owner");
                        var outputs = level.getEntitiesOfClass(LooseItemEntity.class,
                                new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                                e -> !outputIds.contains(e.getUUID()) && (e.getItem().is(ModItems.WHEAT_BUSHEL.get())
                                        || e.getItem().is(net.minecraft.world.item.Items.STICK)
                                        || e.getItem().is(net.minecraft.world.item.Items.PAPER)));
                        require(outputs.isEmpty(), "no slices or source/child trash leak from cancelled batch");
                        require(body.getExistingDataOrNull(attachment).token(hand).filter(token::equals).isPresent(),
                                "knife ownership is not duplicated or lost");
                        if (blockOutput) {
                            require(joinCount.get() >= 2, "publication failure injection canceled the second real slice spawn");
                        } else {
                            require(joinCount.get() == 0, "active-hand cancellation occurs before any output publication attempt");
                        }
                    } catch (Throwable failure) {
                        cleanup.finish(failure);
                        return;
                    }
                    cleanup.finish(null);
                }));
                };
                ItemProcessingFixtureSupport.awaitSettledSource(h, cleanup, source, admitSlice);
            } catch (Throwable failure) {
                cleanup.finish(failure);
            }
        });
    }

    private static BodyHandActionRequest request(long epoch, long sequence, long revision, String hand, String token, LooseItemEntity target) {
        return new BodyHandActionRequest(BodyHandActionRequest.Action.USE_TARGET, hand, epoch, sequence, revision, target.getUUID(), token);
    }
    private static void drainFoodChop(RegisteredServerPlayerFixture fixture,
            java.util.List<net.minecraft.network.protocol.game.ClientboundSoundPacket> sounds) {
        Object packet;
        while ((packet = fixture.pollOutbound()) != null)
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound
                    && sound.getSound().value() == com.juicyslew.moonstation14.sounds.ModSounds.FOOD_CHOP.get()) sounds.add(sound);
    }
    private static boolean hasCancelledSlice(net.minecraft.world.entity.LivingEntity body, LooseItemEntity source) {
        return body.getData(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get()).operations()
                .stream().anyMatch(operation -> operation.kind().equals("food_slicing")
                        && operation.sourceKey().equals(source.getUUID().toString())
                        && operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.CANCELLED);
    }
    private static String completionFailureDiagnostic(net.minecraft.world.entity.LivingEntity body, LooseItemEntity source,
            net.minecraft.world.phys.Vec3 origin, com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands,
            String hand, long expectedRevision, net.minecraft.server.level.ServerLevel level) {
        var operations = body.getData(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get())
                .operations().stream().filter(operation -> operation.kind().equals("food_slicing")
                        && operation.sourceKey().equals(source.getUUID().toString()))
                .map(operation -> "id=" + operation.id() + ",phase=" + operation.phase() + ",start="
                        + operation.startTick() + ",due=" + operation.dueTick() + ",terminal=" + operation.terminalTick())
                .limit(4).toList();
        long outputs = level.getEntitiesOfClass(LooseItemEntity.class,
                new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                entity -> entity.getItem().is(ModItems.WHEAT_BUSHEL.get())).size();
        String sourcePosition = source.isRemoved() ? "removed" : source.position().toString();
        return "source removed at +40 after sequence 10 restart: source=" + sourcePosition + ",origin=" + origin
                + ",body=" + body.position() + ",handRevision=" + hands.revision() + "/" + expectedRevision
                + ",outputs=" + outputs + ",matchingOperations=" + operations;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void guarded(ItemProcessingFixtureCleanup cleanup, Runnable callback) {
        if (!cleanup.isOpen()) return;
        try { callback.run(); } catch (Throwable failure) { cleanup.finish(failure); }
    }
    private static IngestionProfile withTrash(IngestionProfile profile, net.minecraft.world.item.Item trash) {
        var e = profile.edible();
        return new IngestionProfile(new IngestionProfile.Edible(e.doseCents(), e.delayTicks(), e.forceFeedDelayTicks(),
                e.destroyOnEmpty(), e.consumeSource(), e.completionSound(), e.volumeDb(), e.pitchVariation(),
                e.kind(), e.requiresSpecialDigestion(), java.util.List.of(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(trash))));
    }
    private static void installSliceOutputFoodProfile(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.prototype.PrototypeManager manager, ResourceLocation outputId) {
        var type = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE;
        var original = new HashMap<>(manager.snapshot(type).asMap());
        var overridden = new HashMap<>(original);
        overridden.put(outputId, new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(150,
                Map.of(ResourceLocation.fromNamespaceAndPath("moonstation14", "flour"), 150L)));
        manager.publishDecoded(type, overridden);
        var restored = new java.util.concurrent.atomic.AtomicBoolean();
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void restore() {
                if (restored.compareAndSet(false, true)) manager.publishDecoded(type, original);
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info,
                    net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info,
                    net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                    net.minecraft.gametest.framework.GameTestInfo newTest,
                    net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
        });
    }
    private static final class BuiltinKeys {
        private static ResourceLocation knife() { return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.KITCHEN_KNIFE.get()); }
    }
}
