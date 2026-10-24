package com.juicyslew.moonstation14.ms14.ingestion;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsFixtureTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Authenticated normal timed ingestion tests for full-consumption item outputs. */
@PrefixGameTestTemplate(false)
@GameTestTags({"ingestion", "ingestion-trash"})
public final class TimedTrashOutputAuthenticatedGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> NUTRIMENT =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, id("nutriment"));
    private TimedTrashOutputAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    @GameTestTags({"ingestion-trash", "ingestion-trash-held"})
    public static void heldTimedFoodEmitsDuplicateTrashOnlyOnFinalDoseAndPickupUsesHandOwnership(GameTestHelper helper) {
        var scope = installTrashProfile(1000, 500);
        var fixture = register(helper, "trash-held");
        var finished = new AtomicBoolean();
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                var hands = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                String hand = hands.activeHand(), token = "held-trash-food";
                ItemStack source = foodStack(body.level());
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, hand, token, source),
                        "resolved edible source receives normal registered hand ownership");
                String otherHand = hands(body).handIds().stream().filter(value -> !value.equals(hand)).findFirst().orElseThrow();
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, otherHand, "other-held-item",
                        new ItemStack(net.minecraft.world.item.Items.PAPER)), "second hand filled through the same live owner");
                var before = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(active.epoch(), 2));
                var held = before.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                require(BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.USE_IN_HAND, hand, active.epoch(), 3, before.revision(), null, held.token())).accepted(),
                        "normal authenticated USE_IN_HAND starts timed food ingestion");
                require(sourceNutriment(hands(body).stackCopy(hand).orElseThrow()) == 1000
                                && stomach(body) == 0 && trashInHands(body) == 0,
                        "starting timed ingestion has no reagent, stomach, or trash side effects");
                helper.runAfterDelay(4, () -> {
                    try {
                        require(sourceNutriment(hands(body).stackCopy(hand).orElseThrow()) == 500
                                        && stomach(body) == 500 && trashInHands(body) == 0,
                                "partial bite transfers one dose but emits no trash");
                        require(((ServerLevel) body.level()).getEntitiesOfClass(LooseItemEntity.class,
                                        new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(3),
                                        entity -> entity.getItem().is(net.minecraft.world.item.Items.STICK)).isEmpty(),
                                "partial bite emits no world-owned trash either");
                    } catch (Throwable failure) { finish(helper, fixture, scope, finished, failure); }
                });
                helper.runAfterDelay(8, () -> {
                    finishAfter(helper, fixture, scope, finished, () -> {
                        require(stomach(body) == 1000, "final dose commits through the existing ingestion owner");
                        var after = hands(body);
                        require(after.stackCopy(hand).filter(stack -> stack.is(net.minecraft.world.item.Items.STICK)
                                        && stack.getCount() == 1).isPresent(),
                                "held source's first trash output uses the now-free original hand");
                        require(after.stackCopy(otherHand).filter(stack -> stack.is(net.minecraft.world.item.Items.PAPER)
                                        && stack.getCount() == 1).isPresent(), "occupied other hand remains unchanged");
                        require(after.token(hand).isPresent() && after.token(otherHand).isPresent(),
                                "source hand pickup commits normal ownership without displacing the other item");
                        require(trashInHands(body) == 1, "only one duplicate output fits the now-free hand");
                        var worldTrash = ((ServerLevel) body.level()).getEntitiesOfClass(LooseItemEntity.class,
                                new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(3),
                                entity -> entity.getItem().is(net.minecraft.world.item.Items.STICK));
                        require(worldTrash.size() == 1 && worldTrash.getFirst().getItem().getCount() == 1,
                                "failed second trusted pickup leaves its separate output world-owned");
                    });
                });
            } catch (Throwable failure) { finish(helper, fixture, scope, finished, failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 60)
    @GameTestTags({"ingestion-trash", "ingestion-trash-world"})
    public static void worldTimedFoodKeepsTrashInWorldInsteadOfAutoPickup(GameTestHelper helper) {
        var scope = installTrashProfile(500, 500);
        var fixture = register(helper, "trash-world");
        var finished = new AtomicBoolean();
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body(); var level = (ServerLevel) body.level();
                var hands = hands(body); String hand = hands.activeHand();
                var source = LooseItemEntity.create(level, body.position().add(0, .1, .8), foodStack(level));
                source.setNoGravity(true); require(level.addFreshEntity(source), "loose edible source published");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source.getBoundingBox().getCenter());
                var before = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(active.epoch(), 2));
                var activeHand = before.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                require(activeHand.token() == null, "world ingestion begins with an empty active hand");
                var request = new BodyHandActionRequest(BodyHandActionRequest.Action.ALTERNATE_TARGET,
                        hand, active.epoch(), 3, before.revision(), source.getUUID(), null);
                require(BodyHandRequestService.apply(fixture.player(), request).accepted(),
                        "authenticated exact-target world action starts timed ingestion");
                var origin = source.position();
                helper.runAfterDelay(4, () -> finishAfter(helper, fixture, scope, finished, () -> {
                    require(source.isRemoved() && level.getEntity(source.getUUID()) == null,
                            "final world-source bite removes the depleted food entity");
                    require(stomach(body) == 500, "world source commits the dose once");
                    var outputs = level.getEntitiesOfClass(LooseItemEntity.class,
                            new net.minecraft.world.phys.AABB(origin, origin).inflate(2),
                            entity -> entity.getItem().is(net.minecraft.world.item.Items.STICK));
                    require(outputs.size() == 2 && outputs.stream().allMatch(entity -> entity.getItem().getCount() == 1),
                            "world full-eat creates two separate count-one trash entities");
                    require(outputs.stream().allMatch(entity -> entity.position().distanceToSqr(origin) <= .5),
                            "world trash uses the source-origin collision-safe placement adaptation");
                    require(hands(body).stackCopy(hand).isEmpty()
                                    && hands(body).handIds().stream().allMatch(id -> hands(body).stackCopy(id).isEmpty()),
                            "world-source outputs are not automatically picked up even though hands are free");
                }));
            } catch (Throwable failure) { finish(helper, fixture, scope, finished, failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    @GameTestTags({"ingestion-trash", "ingestion-trash-cancelled"})
    public static void cancelledTimedFoodDoesNotPublishTrash(GameTestHelper helper) {
        var scope = installTrashProfile(500, 500);
        var fixture = register(helper, "trash-cancelled");
        var finished = new AtomicBoolean();
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body(); var live = hands(body); String hand = live.activeHand(), token = "cancelled-trash-food";
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, hand, token, foodStack(body.level())),
                        "food owned through registered actor");
                var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(active.epoch(), 2));
                var held = query.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                var first = new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,
                        hand, active.epoch(), 3, query.revision(), null, held.token());
                require(BodyHandRequestService.apply(fixture.player(), first).accepted(), "timed ingestion admitted normally");
                require(BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.USE_IN_HAND, hand, active.epoch(), 4,
                        query.revision(), null, held.token())).accepted(), "same authenticated action cancels its operation");
                helper.runAfterDelay(5, () -> finishAfter(helper, fixture, scope, finished, () -> {
                    require(sourceNutriment(hands(body).stackCopy(hand).orElseThrow()) == 500 && stomach(body) == 0,
                            "cancellation leaves food and stomach unchanged");
                    require(trashInHands(body) == 0
                                    && ((ServerLevel) body.level()).getEntitiesOfClass(LooseItemEntity.class,
                                    new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(3),
                                    entity -> entity.getItem().is(net.minecraft.world.item.Items.STICK)).isEmpty(),
                            "cancelled session emits neither held nor world trash outputs");
                }));
            } catch (Throwable failure) { finish(helper, fixture, scope, finished, failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 60)
    @GameTestTags({"ingestion-trash", "ingestion-trash-publication-failure"})
    public static void partialTrashPublicationFailurePreservesFoodAndStomach(GameTestHelper helper) {
        var scope = installTrashProfile(500, 500);
        var fixture = register(helper, "trash-failure");
        var finished = new AtomicBoolean();
        ServerLevel level = (ServerLevel) helper.getLevel();
        var joinCount = new AtomicInteger();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof LooseItemEntity loose
                    && loose.getItem().is(net.minecraft.world.item.Items.STICK)
                    && joinCount.incrementAndGet() == 2) event.setCanceled(true);
        };
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body(); var live = hands(body); String hand = live.activeHand(), token = "trash-failure-food";
                ItemStack food = foodStack(level);
                require(LiveHandsFixtureTransfer.put(fixture.player(), body, hand, token, food), "held edible source owned");
                long stomachBefore = stomach(body);
                var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(active.epoch(), 2));
                var held = query.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                NeoForge.EVENT_BUS.addListener(listener);
                require(BodyHandRequestService.apply(fixture.player(), new BodyHandActionRequest(
                        BodyHandActionRequest.Action.USE_IN_HAND, hand, active.epoch(), 3,
                        query.revision(), null, held.token())).accepted(), "normal timed ingestion admitted");
                helper.runAfterDelay(4, () -> {
                    try {
                        var retained = hands(body).stackCopy(hand).orElseThrow();
                        require(sourceNutriment(retained) == 500 && stomach(body) == stomachBefore,
                                "failed batch leaves original food reagents and stomach unchanged");
                        require(hands(body).token(hand).filter(held.token()::equals).isPresent(),
                                "failed batch preserves source owner token");
                        require(joinCount.get() == 2, "failure injected only after one output entity was published");
                        require(level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(4),
                                entity -> entity.getItem().is(net.minecraft.world.item.Items.STICK)).isEmpty(),
                                "first published output is compensated and cancelled second output is absent");
                        finish(helper, fixture, scope, finished, (Throwable) null);
                    } catch (Throwable failure) {
                        finish(helper, fixture, scope, finished, failure);
                    } finally { NeoForge.EVENT_BUS.unregister(listener); }
                });
            } catch (Throwable failure) {
                NeoForge.EVENT_BUS.unregister(listener);
                finish(helper, fixture, scope, finished, failure);
            }
        });
    }

    private static ProfileScope installTrashProfile(int sourceCents, int doseCents) {
        var manager = PrototypeRuntime.serverManager(); var type = ModIngestionProfiles.TYPE;
        var old = new HashMap<>(manager.snapshot(type).asMap());
        var foodType = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE;
        var oldFood = new HashMap<>(manager.snapshot(foodType).asMap());
        var profiles = new HashMap<>(old);
        var sourceId = BuiltInRegistries.ITEM.getKey(ModItems.CHEESE_SLICE.get());
        var nutriment = id("nutriment");
        var edible = new IngestionProfile.Edible(doseCents, 3, 40, true, false,
                id("ss14/items/eat"), -2f, .25f, IngestionProfile.Kind.FOOD, false,
                List.of(ResourceLocation.withDefaultNamespace("stick"), ResourceLocation.withDefaultNamespace("stick")));
        profiles.put(sourceId, new IngestionProfile(edible));
        manager.publishDecoded(type, profiles);
        var foods = new HashMap<>(oldFood);
        foods.put(sourceId, new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(
                sourceCents, Map.of(nutriment, (long) sourceCents)));
        manager.publishDecoded(foodType, foods);
        return new ProfileScope(manager, type, old, foodType, oldFood);
    }

    private static RegisteredServerPlayerFixture register(GameTestHelper helper, String name) {
        return RegisteredServerPlayerFixture.register((ServerLevel) helper.getLevel(), name);
    }
    private static ItemStack foodStack(net.minecraft.world.level.Level level) {
        ItemStack stack = new ItemStack(ModItems.CHEESE_SLICE.get());
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(stack, level);
        return stack;
    }
    private static LiveHands hands(net.minecraft.world.entity.LivingEntity body) {
        return (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
    }
    private static long sourceNutriment(ItemStack stack) {
        var component = stack.get(ModDataComponents.REAGENT.get());
        return component == null ? 0 : ReagentUnits.fromFloat(component.contents().getOrDefault(NUTRIMENT, 0f));
    }
    private static long stomach(net.minecraft.world.entity.LivingEntity body) {
        return ReagentUnits.fromFloat(MS14Provider.get(body, MS14Bridges.STOMACH).getMap().getOrDefault(NUTRIMENT, 0f));
    }
    private static int trashInHands(net.minecraft.world.entity.LivingEntity body) {
        return hands(body).handIds().stream().map(hands(body)::stackCopy).flatMap(java.util.Optional::stream)
                .filter(stack -> stack.is(net.minecraft.world.item.Items.STICK)).mapToInt(ItemStack::getCount).sum();
    }
    private static void finish(GameTestHelper helper, RegisteredServerPlayerFixture fixture, ProfileScope scope,
                               AtomicBoolean finished, Throwable failure) {
        if (!finished.compareAndSet(false, true)) return;
        try { scope.restore(); } catch (Throwable restore) { if (failure == null) failure = restore; else failure.addSuppressed(restore); }
        try { fixture.close(); } catch (Throwable close) { if (failure == null) failure = close; else failure.addSuppressed(close); }
        if (failure == null) helper.succeed(); else helper.testInfo.fail(failure);
    }
    private static void finishAfter(GameTestHelper helper, RegisteredServerPlayerFixture fixture, ProfileScope scope,
                                    AtomicBoolean finished, Runnable assertions) {
        try { assertions.run(); finish(helper, fixture, scope, finished, null); }
        catch (Throwable failure) { finish(helper, fixture, scope, finished, failure); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private record ProfileScope(com.juicyslew.moonstation14.ms14.prototype.PrototypeManager manager,
                                com.juicyslew.moonstation14.ms14.prototype.PrototypeType<IngestionProfile> type,
                                Map<ResourceLocation, IngestionProfile> previous,
                                com.juicyslew.moonstation14.ms14.prototype.PrototypeType<com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile> foodType,
                                Map<ResourceLocation, com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile> previousFood) {
        void restore() { manager.publishDecoded(type, previous); manager.publishDecoded(foodType, previousFood); }
    }
}
