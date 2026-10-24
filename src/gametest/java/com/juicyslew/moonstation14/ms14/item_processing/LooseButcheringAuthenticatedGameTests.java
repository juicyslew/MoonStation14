package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated explicit BUTCHER action against resolved, fixture-scoped source composition. */
@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "butchering"})
public final class LooseButcheringAuthenticatedGameTests {
    private LooseButcheringAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"butchering", "butchering-authenticated", "food-slicing-authenticated"})
    public static void explicitButcherUsesSharpAndPublishesOrderedIndependentOutputs(GameTestHelper helper) {
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()),
                "authenticated-butcher", (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            var manager = PrototypeRuntime.serverManager();
            var procType = ModItemProcessingProfiles.TYPE;
            var toolType = ModToolProfiles.TYPE;
            var oldProcessing = new HashMap<>(manager.snapshot(procType).asMap());
            var oldTools = new HashMap<>(manager.snapshot(toolType).asMap());
            var ingestionType = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE;
            var oldIngestion = new HashMap<>(manager.snapshot(ingestionType).asMap());
            var done = new AtomicBoolean();
            var cleanup = new ItemProcessingFixtureCleanup(h, fixture, done, manager, procType, oldProcessing,
                    toolType, oldTools, ingestionType, oldIngestion, null);
            try {
                ResourceLocation tomato = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.TOMATO.get());
                ResourceLocation wood = ResourceLocation.fromNamespaceAndPath("moonstation14", "wood");
                ResourceLocation paper = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.PAPER);
                var processing = new HashMap<>(oldProcessing);
                var resolvedTomato = oldProcessing.get(tomato);
                require(resolvedTomato != null && resolvedTomato.butcherable() != null
                        && resolvedTomato.butcherable().delaySeconds() == 8f,
                        "resolved tomato butcher profile has default eight-second delay");
                var defaultKnife = oldTools.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.KITCHEN_KNIFE.get()));
                require(defaultKnife.sharp().butcherDelayModifier() == 1f, "resolved production Sharp modifier defaults to one");
                processing.put(tomato, new ItemProcessingProfile(null, null, new ItemProcessingProfile.Butcherable(
                        java.util.List.of(new ItemProcessingProfile.ItemOutput(wood, 2), new ItemProcessingProfile.ItemOutput(paper, 1)), 0.25f,
                        ItemProcessingProfile.Butcherable.Type.KNIFE)));
                manager.publishDecoded(procType, processing);
                var originalIngestion = oldIngestion.get(tomato);
                var edible = originalIngestion.edible();
                var edibleWithTrash = new com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile.Edible(
                        edible.doseCents(), edible.delayTicks(), edible.forceFeedDelayTicks(), edible.destroyOnEmpty(), edible.consumeSource(),
                        edible.completionSound(), edible.volumeDb(), edible.pitchVariation(), edible.kind(),
                        edible.requiresSpecialDigestion(), java.util.List.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.STICK)));
                var ingestion = new HashMap<>(oldIngestion);
                ingestion.put(tomato, new com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile(edibleWithTrash));
                manager.publishDecoded(ingestionType, ingestion);
                require(defaultKnife.sharp() != null, "resolved KitchenKnife has Sharp capability");
                var body = HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var knife = LooseItemEntity.create(level, body.position().add(0, .1, .8), new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                knife.setNoGravity(true); require(level.addFreshEntity(knife), "registered production knife spawned");
                var attachment = com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get();
                var hands = body.getExistingDataOrNull(attachment);
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), knife.getUUID(), hand, hands.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS, "player acquires production knife");
                hands = body.getExistingDataOrNull(attachment);
                var seedEntry = com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.serverCatalog().asMap().entrySet().stream()
                        .filter(entry -> entry.getValue().product().equals(tomato)).findFirst().orElseThrow();
                var seedReference = new com.juicyslew.moonstation14.ms14.botany.PlantSeedReference(
                        new com.juicyslew.moonstation14.ms14.botany.GeneticValue(seedEntry.getKey(), seedEntry.getValue()), true);
                var sourceStack = com.juicyslew.moonstation14.ms14.botany.ProduceFactory.createHarvest(seedReference,
                        seedEntry.getValue().lifecycle().potency());
                sourceStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("butcher-source-marker"));
                var sourceUnits = com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(sourceStack,
                        com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT).snapshotUnits();
                require(!sourceUnits.isEmpty(), "butcher source has initialized prototype chemistry");
                require(sourceStack.get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()) != null
                        && sourceStack.get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get()) != null
                        && com.juicyslew.moonstation14.ms14.botany.GeneticCarriers.resolve(sourceStack, () -> null) != null,
                        "canonical harvested source has produce and genetic carriers");
                var source = LooseItemEntity.create(level, body.position().add(0, .1, 1.5), sourceStack);
                source.setNoGravity(true); require(level.addFreshEntity(source), "registered tomato source spawned");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source.getBoundingBox().getCenter());
                var q = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 1));
                var active = q.hands().stream().filter(x -> x.id().equals(hand)).findFirst().orElseThrow();
                var knifeId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.KITCHEN_KNIFE.get());
                var knifeOnly = new HashMap<>(oldTools);
                knifeOnly.put(knifeId, new com.juicyslew.moonstation14.ms14.tools.ToolProfile(
                        null,
                        null, null, com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata.EMPTY,
                        java.util.Set.of(), null, null, null, null, new com.juicyslew.moonstation14.ms14.tools.ToolProfile.Knife()));
                manager.publishDecoded(toolType, knifeOnly);
                require(!BodyHandRequestService.apply(fixture.player(), butcher(epoch, 2, q.revision(), hand, active.token(), source)).accepted()
                        && !source.isRemoved(), "Knife-only tool is denied without Sharp and preserves source");
                var cuttingOnly = new HashMap<>(oldTools);
                cuttingOnly.put(knifeId, new com.juicyslew.moonstation14.ms14.tools.ToolProfile(
                        new com.juicyslew.moonstation14.ms14.tools.ToolProfile.Tool(java.util.List.of(com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.CUTTING)),
                        null, null, com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata.EMPTY,
                        java.util.Set.of(), null, null, null, null, null));
                manager.publishDecoded(toolType, cuttingOnly);
                require(!BodyHandRequestService.apply(fixture.player(), butcher(epoch, 3, q.revision(), hand, active.token(), source)).accepted()
                        && !source.isRemoved(), "CUTTING-only tool is denied and preserves source");
                manager.publishDecoded(toolType, oldTools);
                var outputOrder = new java.util.ArrayList<net.minecraft.world.item.Item>();
                java.util.function.Consumer<EntityJoinLevelEvent> outputObserver = event -> {
                    if (event.getLevel() == level && event.getEntity() instanceof LooseItemEntity loose
                            && (loose.getItem().is(com.juicyslew.moonstation14.item.ModItems.WOOD.get())
                                || loose.getItem().is(net.minecraft.world.item.Items.PAPER))) outputOrder.add(loose.getItem().getItem());
                };
                cleanup.attachListener(outputObserver);
                require(BodyHandRequestService.apply(fixture.player(), butcher(epoch, 4, q.revision(), hand, active.token(), source)).accepted(), "explicit authenticated BUTCHER admitted");
                var operation = body.getData(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get()).operations()
                        .stream().filter(op -> op.kind().equals("loose_butchering") && op.sourceKey().equals(source.getUUID().toString())).findFirst().orElseThrow();
                require(operation.durationTicks() == 5, "fixture delay .25 seconds times production Sharp modifier 1 has five-tick deadline");
                h.runAfterDelay(3, () -> guarded(cleanup, () -> require(!source.isRemoved(), "source remains before deadline")));
                h.runAfterDelay(12, () -> {
                    if (!cleanup.isOpen()) return;
                    try {
                        require(source.isRemoved(), "source consumed at completion");
                        var area = new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5);
                        var out = level.getEntitiesOfClass(LooseItemEntity.class, area, e -> e.getItem().is(com.juicyslew.moonstation14.item.ModItems.WOOD.get()) || e.getItem().is(net.minecraft.world.item.Items.PAPER));
                        require(out.size() == 3, "configured output count published independently");
                        require(out.stream().allMatch(e -> e.getItem().getCount() == 1), "outputs are separate count-one items");
                        require(outputOrder.equals(java.util.List.of(com.juicyslew.moonstation14.item.ModItems.WOOD.get(),
                                com.juicyslew.moonstation14.item.ModItems.WOOD.get(), net.minecraft.world.item.Items.PAPER)),
                                "output publication follows configured A,A,B plan order");
                        require(out.stream().filter(e -> e.getItem().is(com.juicyslew.moonstation14.item.ModItems.WOOD.get())).count() == 2
                                && out.stream().filter(e -> e.getItem().is(net.minecraft.world.item.Items.PAPER)).count() == 1, "wood count and paper output match composition");
                        require(out.stream().noneMatch(e -> e.getItem().is(net.minecraft.world.item.Items.STICK)),
                                "butchering never publishes source ingestion trash marker");
                        var expectedWood = new ItemStack(com.juicyslew.moonstation14.item.ModItems.WOOD.get());
                        var expectedPaper = new ItemStack(net.minecraft.world.item.Items.PAPER);
                        var outputDefaultsMatch = out.stream().allMatch(e -> {
                            var expected = e.getItem().is(com.juicyslew.moonstation14.item.ModItems.WOOD.get()) ? expectedWood : expectedPaper;
                            return ItemStack.isSameItemSameComponents(expected, e.getItem());
                        });
                        require(outputDefaultsMatch, "wood and paper butcher outputs retain their exact fresh prototype components");
                        var sourceReagents = sourceUnits;
                        require(sourceReagents.keySet().stream().noneMatch(reagent -> out.stream().anyMatch(e ->
                                com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(e.getItem(),
                                        com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT).snapshotUnits().getOrDefault(reagent, 0L) > 0)),
                                "no source reagent identity or quantity is transferred to non-food butcher outputs");
                        require(out.stream().allMatch(e -> com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(
                                e.getItem(), com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT).snapshotUnits().isEmpty()),
                                "raw wood and paper prototype outputs have no reagent chemistry");
                        require(out.stream().allMatch(e -> e.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get()) == null
                                && e.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get()) == null
                                && e.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()) == null
                                && e.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) == null),
                                "butcher outputs do not inherit source genetics, reference, produce instance, or custom name");
                        require(sourceReagents.values().stream().mapToLong(Long::longValue).sum() > 0,
                                "source chemistry was nonempty for output isolation assertion");
                        require(body.getExistingDataOrNull(attachment).token(hand).isPresent(), "held knife retained");
                        cleanup.finish(null);
                    } catch (Throwable e) { cleanup.finish(e); }
                });
            } catch (Throwable e) { cleanup.finish(e); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"butchering", "butchering-authenticated", "food-slicing-authenticated"})
    public static void useTargetSlicesDualCompositionAndExplicitButcherRoutesToButcherPlan(GameTestHelper helper) {
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()),
                "dual-composition-routing", (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            var manager = PrototypeRuntime.serverManager();
            var procType = ModItemProcessingProfiles.TYPE;
            var toolType = ModToolProfiles.TYPE;
            var ingestionType = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE;
            var oldProcessing = new HashMap<>(manager.snapshot(procType).asMap());
            var oldTools = new HashMap<>(manager.snapshot(toolType).asMap());
            var oldIngestion = new HashMap<>(manager.snapshot(ingestionType).asMap());
            var cleanup = new ItemProcessingFixtureCleanup(h, fixture, finished, manager, procType, oldProcessing,
                    toolType, oldTools, ingestionType, oldIngestion, null);
            try {
                var tomato = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.TOMATO.get());
                var wheat = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WHEAT_BUSHEL.get());
                var wood = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WOOD.get());
                var paper = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.PAPER);
                require(oldProcessing.get(tomato) != null && oldProcessing.get(tomato).sliceableFood() != null
                        && oldProcessing.get(tomato).butcherable() != null, "resolved tomato has both processing routes");
                var processing = new HashMap<>(oldProcessing);
                processing.put(tomato, new ItemProcessingProfile(null,
                        new ItemProcessingProfile.SliceableFood(wheat, 5, 1f),
                        new ItemProcessingProfile.Butcherable(java.util.List.of(
                                new ItemProcessingProfile.ItemOutput(wood, 2),
                                new ItemProcessingProfile.ItemOutput(paper, 1)), .25f,
                                ItemProcessingProfile.Butcherable.Type.KNIFE)));
                manager.publishDecoded(procType, processing);
                var resolved = manager.snapshot(procType).asMap().get(tomato);
                require(resolved.sliceableFood().output().equals(wheat) && resolved.butcherable().outputs().size() == 2,
                        "published resolved source composition contains slice and butcher plans");
                var knifeId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.KITCHEN_KNIFE.get());
                var knifeProfile = oldTools.get(knifeId);
                require(knifeProfile != null && knifeProfile.sharp() != null && knifeProfile.knife() != null,
                        "production KitchenKnife retains Sharp and Knife capabilities");
                var body = HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var attachment = com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get();
                var knife = LooseItemEntity.create(level, body.position().add(0, .1, .8), new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                knife.setNoGravity(true); require(level.addFreshEntity(knife), "registered production knife spawned");
                var hands = body.getExistingDataOrNull(attachment);
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), knife.getUUID(), hand, hands.revision(), ignored -> {})
                        == BodyHandItemTransfer.Result.SUCCESS, "registered player acquires production knife");
                var token = body.getExistingDataOrNull(attachment).token(hand).orElseThrow();
                var area = new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5);
                var wheatBefore = level.getEntitiesOfClass(LooseItemEntity.class, area, e -> e.getItem().is(ModItems.WHEAT_BUSHEL.get()))
                        .stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
                var rawBefore = level.getEntitiesOfClass(LooseItemEntity.class, area, e -> e.getItem().is(ModItems.WOOD.get())
                        || e.getItem().is(net.minecraft.world.item.Items.PAPER)).stream().map(LooseItemEntity::getUUID)
                        .collect(java.util.stream.Collectors.toSet());
                var source1 = dualSource(level, body.position().add(0, .1, 1.5));
                require(level.addFreshEntity(source1), "first actual tomato source spawned");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source1.getBoundingBox().getCenter());
                var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 1));
                var active = query.hands().stream().filter(v -> v.id().equals(hand)).findFirst().orElseThrow();
                require(BodyHandRequestService.apply(fixture.player(), sliceRequest(epoch, 2, query.revision(), hand, active.token(), source1)).accepted(),
                        "normal authenticated USE_TARGET admitted");
                require(!source1.isRemoved(), "slice admission does not immediately delete source");
                h.runAfterDelay(8, () -> guarded(cleanup, () -> require(!source1.isRemoved(), "slice source remains before one-second deadline")));
                h.runAfterDelay(24, () -> guarded(cleanup, () -> {
                    try {
                        require(source1.isRemoved(), "USE_TARGET completed timed slicing");
                        var wheatOut = level.getEntitiesOfClass(LooseItemEntity.class, area, e -> e.getItem().is(ModItems.WHEAT_BUSHEL.get())
                                && !wheatBefore.contains(e.getUUID()));
                        require(wheatOut.size() == 5 && wheatOut.stream().allMatch(e -> e.getItem().getCount() == 1),
                                "slice route publishes five separate wheat items");
                        var rawOut = level.getEntitiesOfClass(LooseItemEntity.class, area, e -> (e.getItem().is(ModItems.WOOD.get())
                                || e.getItem().is(net.minecraft.world.item.Items.PAPER)) && !rawBefore.contains(e.getUUID()));
                        require(rawOut.isEmpty(), "slice route publishes no raw butcher outputs");
                        var source2 = dualSource(level, body.position().add(0, .1, 1.5));
                        require(level.addFreshEntity(source2), "fresh second actual tomato source spawned");
                        body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source2.getBoundingBox().getCenter());
                        var q2 = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 3));
                        var active2 = q2.hands().stream().filter(v -> v.id().equals(hand)).findFirst().orElseThrow();
                        require(BodyHandRequestService.apply(fixture.player(), butcher(epoch, 4, q2.revision(), hand, active2.token(), source2)).accepted(),
                                "fresh monotonic sequence explicit BUTCHER admitted");
                        require(!source2.isRemoved(), "butcher admission does not immediately delete source");
                        h.runAfterDelay(2, () -> guarded(cleanup, () -> require(!source2.isRemoved(), "butcher source remains before fixture deadline")));
                        h.runAfterDelay(10, () -> guarded(cleanup, () -> {
                            try {
                                require(source2.isRemoved(), "explicit BUTCHER completed its short timed route");
                                var finalWheat = level.getEntitiesOfClass(LooseItemEntity.class, area, e -> e.getItem().is(ModItems.WHEAT_BUSHEL.get()))
                                        .stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
                                require(finalWheat.equals(union(wheatBefore, wheatOut.stream().map(LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet()))),
                                        "explicit butcher adds no wheat outputs");
                                var rawOut2 = level.getEntitiesOfClass(LooseItemEntity.class, area, e -> (e.getItem().is(ModItems.WOOD.get())
                                        || e.getItem().is(net.minecraft.world.item.Items.PAPER)) && !rawBefore.contains(e.getUUID()));
                                require(rawOut2.size() == 3 && rawOut2.stream().filter(e -> e.getItem().is(ModItems.WOOD.get())).count() == 2
                                        && rawOut2.stream().filter(e -> e.getItem().is(net.minecraft.world.item.Items.PAPER)).count() == 1,
                                        "explicit BUTCHER publishes exactly configured raw-material plan");
                                require(body.getExistingDataOrNull(attachment).token(hand).filter(token::equals).isPresent(), "knife retained after both routes");
                            } catch (Throwable failure) { cleanup.finish(failure); return; }
                            cleanup.finish(null);
                        }));
                    } catch (Throwable failure) { cleanup.finish(failure); }
                }));
            } catch (Throwable failure) { cleanup.finish(failure); }
        });
    }

    private static LooseItemEntity dualSource(net.minecraft.world.level.Level level, net.minecraft.world.phys.Vec3 pos) {
        var stack = new ItemStack(ModItems.TOMATO.get());
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(stack, level);
        var entity = LooseItemEntity.create(level, pos, stack); entity.setNoGravity(true); return entity;
    }
    private static BodyHandActionRequest sliceRequest(long epoch, long sequence, long revision, String hand, String token, LooseItemEntity source) {
        return new BodyHandActionRequest(BodyHandActionRequest.Action.USE_TARGET, hand, epoch, sequence, revision, source.getUUID(), token);
    }
    private static <T> java.util.Set<T> union(java.util.Set<T> a, java.util.Set<T> b) {
        var result = new java.util.HashSet<>(a); result.addAll(b); return result;
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"butchering", "butchering-authenticated"})
    public static void activeHandChangeCancelsButcherAndRetainsExactSourceAndKnife(GameTestHelper helper) {
        runCancellation(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"butchering", "butchering-authenticated"})
    public static void rejectedSecondOutputJoinRollsBackButcherBatch(GameTestHelper helper) {
        runCancellation(helper, true);
    }

    private static void runCancellation(GameTestHelper helper, boolean rejectSecondJoin) {
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper, new ItemStack(ModItems.WHEAT_SEEDS.get()),
                rejectSecondJoin ? "butcher-rejected-publication" : "butcher-hand-change",
                (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            var manager = PrototypeRuntime.serverManager();
            var procType = ModItemProcessingProfiles.TYPE;
            var toolType = ModToolProfiles.TYPE;
            var ingestionType = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE;
            var oldProcessing = new HashMap<>(manager.snapshot(procType).asMap());
            var oldTools = new HashMap<>(manager.snapshot(toolType).asMap());
            var oldIngestion = new HashMap<>(manager.snapshot(ingestionType).asMap());
            var listenerRef = new AtomicReference<Consumer<EntityJoinLevelEvent>>();
            var done = new AtomicBoolean();
            var cleanup = new ItemProcessingFixtureCleanup(h, fixture, done, manager, procType, oldProcessing,
                    toolType, oldTools, ingestionType, oldIngestion, listenerRef);
            try {
                var tomato = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.TOMATO.get());
                var wood = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WOOD.get());
                var paper = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.PAPER);
                var processing = new HashMap<>(oldProcessing);
                processing.put(tomato, new ItemProcessingProfile(null, null, new ItemProcessingProfile.Butcherable(
                        java.util.List.of(new ItemProcessingProfile.ItemOutput(wood, 2), new ItemProcessingProfile.ItemOutput(paper, 1)), .25f,
                        ItemProcessingProfile.Butcherable.Type.KNIFE)));
                manager.publishDecoded(procType, processing);
                var body = HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var attachment = com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get();
                var knife = LooseItemEntity.create(level, body.position().add(0, .1, .8), new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                knife.setNoGravity(true); require(level.addFreshEntity(knife), "production knife published");
                var hands = body.getExistingDataOrNull(attachment);
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), knife.getUUID(), hand, hands.revision(), ignored -> {}) == BodyHandItemTransfer.Result.SUCCESS, "knife acquired in nominated slot");
                hands = body.getExistingDataOrNull(attachment);
                var token = hands.token(hand).orElseThrow();
                var source = LooseItemEntity.create(level, body.position().add(0, .1, 1.5), new ItemStack(ModItems.TOMATO.get()));
                source.setNoGravity(true); require(level.addFreshEntity(source), "tomato source published");
                var sourceId = source.getUUID();
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source.getBoundingBox().getCenter());
                var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 1));
                var active = query.hands().stream().filter(v -> v.id().equals(hand)).findFirst().orElseThrow();
                require(BodyHandRequestService.apply(fixture.player(), butcher(epoch, 2, query.revision(), hand, active.token(), source)).accepted(), "authenticated BUTCHER starts timed session");
                var before = java.util.Set.copyOf(level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                        e -> e.getItem().is(ModItems.WOOD.get()) || e.getItem().is(net.minecraft.world.item.Items.PAPER)).stream().map(LooseItemEntity::getUUID).toList());
                AtomicInteger joins = new AtomicInteger();
                if (rejectSecondJoin) {
                    Consumer<EntityJoinLevelEvent> listener = event -> {
                        if (event.getLevel() == level && event.getEntity() instanceof LooseItemEntity loose
                                && (loose.getItem().is(ModItems.WOOD.get()) || loose.getItem().is(net.minecraft.world.item.Items.PAPER))
                                && joins.incrementAndGet() == 2) event.setCanceled(true);
                    };
                    cleanup.attachListener(listener);
                } else {
                    String other = hands.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
                    require(BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,
                            other, epoch, 3, hands.revision(), null, null)).accepted(), "authenticated SELECT_HAND changes active hand");
                }
                h.runAfterDelay(20, () -> {
                    if (!cleanup.isOpen()) return;
                    try {
                        require(!source.isRemoved() && level.getEntity(sourceId) == source, "cancelled/failed operation retains exact source entity");
                        var outputs = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                                e -> !before.contains(e.getUUID()) && (e.getItem().is(ModItems.WOOD.get()) || e.getItem().is(net.minecraft.world.item.Items.PAPER)));
                        require(outputs.isEmpty(), "no partial butcher outputs remain");
                        require(body.getExistingDataOrNull(attachment).token(hand).filter(token::equals).isPresent(), "knife remains in original hand slot");
                        if (rejectSecondJoin) require(joins.get() >= 2, "second output join was observed and rejected");
                        cleanup.finish(null);
                    } catch (Throwable failure) { cleanup.finish(failure); }
                });
            } catch (Throwable failure) { cleanup.finish(failure); }
        });
    }

    private static BodyHandActionRequest butcher(long epoch, long sequence, long revision, String hand, String token, LooseItemEntity source) {
        return new BodyHandActionRequest(BodyHandActionRequest.Action.BUTCHER, hand, epoch, sequence, revision, source.getUUID(), token);
    }
    private static void guarded(ItemProcessingFixtureCleanup cleanup, Runnable callback) {
        if (!cleanup.isOpen()) return;
        try { callback.run(); } catch (Throwable failure) { cleanup.finish(failure); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
