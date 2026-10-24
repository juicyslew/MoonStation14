package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperationState;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.loose_objects.interaction.LiquidInteractionCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Map;

@GameTestTags({"timed-interaction", "ingestion", "hands", "chemistry"})
@PrefixGameTestTemplate(false)
public final class BodyTimedIngestionGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> NUTRIENT =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "nutriment"));

    private BodyTimedIngestionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void humanCannotStartTimedWheatIngestion(GameTestHelper helper) {
        var level = helper.getLevel();
        Villager body = makeBody(helper, new BlockPos(2, 1, 2));
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var wheat = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                new Vec3(pos.getX() + .5, pos.getY(), pos.getZ() + .5),
                new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WHEAT_BUSHEL.get()));
        require(level.addFreshEntity(wheat), "wheat source spawned");
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(wheat.getItem(), level);
        aimAtItem(body, wheat.position());
        var profile = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.serverLookup(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(wheat.getItem().getItem()));
        require(profile != null && profile.edible().requiresSpecialDigestion(), "resolved wheat profile requires special digestion");
        var before = MS14Provider.getDetached(body, MS14Bridges.STOMACH).getMap();
        require(BodyTimedIngestionService.startWorldOwned(body, wheat.getUUID(), null, 0, 991, () -> true)
                == BodyHandActionResult.Reason.DENIED, "ordinary human is denied wheat admission");
        require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().noneMatch(o -> o.phase() == TimedOperation.Phase.ACTIVE),
                "denial creates no active timed operation");
        require(before.equals(MS14Provider.getDetached(body, MS14Bridges.STOMACH).getMap()), "denial does not drain source into stomach");
        // Exercise the public completion transaction directly: food=false must not bypass
        // the source's current special-digestion profile. The source is valid and reachable.
        var wheatBefore = wheat.getItem().copy();
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var direct = BodyLiquidInteraction.sipTimedOwned(body, wheat.getUUID(), hands.activeHand(), null,
                hands.revision(), 500, false, false, () -> true);
        require(direct == BodyLiquidInteraction.Result.DENIED, "final transaction guard denies wheat even when caller says drink");
        require(wheatBefore.equals(wheat.getItem()), "final guard leaves actual world source unchanged");
        require(before.equals(MS14Provider.getDetached(body, MS14Bridges.STOMACH).getMap()), "final guard leaves stomach unchanged");

        // Positive control through the same transaction path: ordinary resolved food is allowed.
        BlockPos tomatoPos = pos.east(); level.setBlock(tomatoPos.below(), Blocks.STONE.defaultBlockState(), 3);
        var tomato = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                new Vec3(tomatoPos.getX() + .5, tomatoPos.getY(), tomatoPos.getZ() + .5),
                new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.TOMATO.get()));
        require(level.addFreshEntity(tomato), "tomato control source spawned");
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(tomato.getItem(), level);
        aimAtItem(body, tomato.position());
        var tomatoProfile = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.serverLookup(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(tomato.getItem().getItem()));
        require(tomatoProfile != null && !tomatoProfile.edible().requiresSpecialDigestion(), "control uses ordinary resolved tomato profile");
        var allowed = BodyLiquidInteraction.sipTimedOwned(body, tomato.getUUID(), hands.activeHand(), null,
                hands.revision(), 500, false, false, () -> true);
        require(allowed == BodyLiquidInteraction.Result.SUCCESS, "ordinary food succeeds through guarded transaction");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 160)
    public static void puddleConsumerWaitsForResolvedProfileDeadlineAndRepeats(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bodyPos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(bodyPos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setPos(bodyPos.getX() + .5, bodyPos.getY(), bodyPos.getZ() + .5);
        body.setYRot(-90f);
        body.setXRot(0f);
        require(level.addFreshEntity(body), "body spawned");
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "eligible body and hands initialized");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        BlockPos pos = bodyPos.offset(1, 0, 0);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof PuddleBlockEntity, "puddle entity created");
        PuddleBlockEntity puddle = (PuddleBlockEntity) level.getBlockEntity(pos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(WATER, 10f)));
        aimAtPuddle(body, pos);
        require(PuddleTargetProbe.selected(level, body, pos), probeDiagnostic(level, body, pos));
        long start = level.getGameTime();
        require(BodyTimedIngestionService.startPuddleOwned(body, pos, hands.activeHand(), hands.revision(), 77, () -> true)
                .name().equals("OK"), "production timed ingestion consumer accepts request");
        require(water(puddle) == 1000 && water(body) == 0, "starting operation performs no immediate transfer");
        helper.runAfterDelay(30, () -> {
            require(level.getGameTime() - start >= 30 && water(puddle) == 1000 && water(body) == 0,
                    "puddle profile does not complete before its 60 tick deadline");
        });
        helper.runAfterDelay(63, () -> {
            require(water(puddle) == 900, "first timed 100-cent dose committed");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 60, () -> {
                require(water(puddle) == 800, "repeat waits one full resolved interval and commits once");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void duplicateGestureCancelsAndStaleRevisionCannotCancel(GameTestHelper helper) {
        var level = helper.getLevel();
        Villager body = makeBody(helper, new BlockPos(2, 1, 2));
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        BlockPos pos = puddle(level, helper.absolutePos(new BlockPos(3, 1, 2)), 10f);
        aimAtPuddle(body,pos);
        require(PuddleTargetProbe.selected(level,body,pos),probeDiagnostic(level,body,pos));
        require(BodyTimedIngestionService.startPuddleOwned(body,pos,hands.activeHand(),hands.revision(),1,()->true).name().equals("OK"), "first request starts");
        TimedOperationState state = body.getData(ModDataAttachments.TIMED_OPERATIONS.get());
        long id = state.operations().get(0).id();
        require(BodyTimedIngestionService.startPuddleOwned(body,pos,hands.activeHand(),hands.revision()-1,2,()->true)==BodyHandActionResult.Reason.DENIED,
                "stale hand revision denied");
        require(active(body,id), "stale duplicate leaves original operation active");
        MS14Provider.update(body, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(WATER, LiquidInteractionCapability.STOMACH_CAPACITY_CENTS / 100f)));
        require(BodyTimedIngestionService.startPuddleOwned(body,pos,hands.activeHand(),hands.revision(),3,()->true).name().equals("OK"), "authorized duplicate cancels");
        require(water((PuddleBlockEntity)level.getBlockEntity(pos))==1000
                        && water(body)==LiquidInteractionCapability.STOMACH_CAPACITY_CENTS && cancelled(body,id),
                "authorized duplicate cancels despite full stomach without transferring anything");
        MS14Provider.update(body, MS14Bridges.STOMACH, new ReagentAttachment(Map.of()));
        helper.runAfterDelay(1, () -> {
            require(BodyTimedIngestionService.startPuddleOwned(body,pos,hands.activeHand(),hands.revision(),4,()->true).name().equals("OK"), "fresh gesture starts new operation");
            TimedOperation repeat = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                    .filter(o -> o.phase() == TimedOperation.Phase.ACTIVE).findFirst().orElseThrow();
            require(repeat.id() != id && repeat.startTick() > state.operations().get(0).startTick(), "restart creates a fresh repeat operation");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 30, () -> {
                require(water((PuddleBlockEntity)level.getBlockEntity(pos))==1000 && active(body, repeat.id()), "fresh repeat remains active before its full interval");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 31, () -> {
                    TimedOperation current = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                            .filter(o -> o.id() == repeat.id()).findFirst().orElseThrow();
                    require(water((PuddleBlockEntity)level.getBlockEntity(pos))==900 && current.phase() == TimedOperation.Phase.ACTIVE
                                    && current.startTick() > repeat.startTick(), "first repeat dose commits and next interval starts");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 70)
    public static void twoActorsSharePuddleAndSecondCommitsRemainder(GameTestHelper helper) {
        var level=helper.getLevel();
        Villager first=makeBody(helper,new BlockPos(2,1,2));
        Villager second=makeBody(helper,new BlockPos(2,1,4));
        BlockPos pos=puddle(level,helper.absolutePos(new BlockPos(3,1,2)),1.5f);
        aimAtPuddle(first,pos); aimAtPuddle(second,pos);
        require(PuddleTargetProbe.selected(level,first,pos),probeDiagnostic(level,first,pos));
        require(PuddleTargetProbe.selected(level,second,pos),probeDiagnostic(level,second,pos));
        LiveHands h1=first.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()), h2=second.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyTimedIngestionService.startPuddleOwned(first,pos,h1.activeHand(),h1.revision(),11,()->true).name().equals("OK"), "first actor starts");
        require(BodyTimedIngestionService.startPuddleOwned(second,pos,h2.activeHand(),h2.revision(),12,()->true).name().equals("OK"), "second actor starts against same source");
        require(water((PuddleBlockEntity)level.getBlockEntity(pos))==150, "both operations begin before source changes");
        helper.runAfterDelay(62,()->{
            require(level.getBlockState(pos).isAir(), "remaining puddle is removed only after second admission");
            int completed = (int) java.util.stream.Stream.of(first, second).filter(b -> hasTerminal(b, TimedOperation.Phase.COMPLETED)).count();
            int cancelled = (int) java.util.stream.Stream.of(first, second).filter(b -> hasTerminal(b, TimedOperation.Phase.CANCELLED)).count();
            require(completed == 1 && cancelled == 1, "one actor commits the final 50-cent slice; the missing-source repeat cancels");
            require(!hasTerminal(first, TimedOperation.Phase.ACTIVE) && !hasTerminal(second, TimedOperation.Phase.ACTIVE), "neither operation remains active after source depletion");
            require(h1.compatible(first)&&h2.compatible(second), "hands remain valid after commits");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 1400)
    public static void finalPartialCapacityDoseStopsTimedPuddleOperation(GameTestHelper helper) {
        Villager body = makeBody(helper, new BlockPos(2, 1, 2));
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        BlockPos pos = puddle(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 2)), 5f);
        aimAtPuddle(body, pos);
        long capacity = LiquidInteractionCapability.STOMACH_CAPACITY_CENTS;
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(body, helper.getLevel());
        waitForCapacitySafeStart(helper, body, () -> runPartialCapacityDoseFixture(helper, body, hands, pos, capacity));
    }

    private static void runPartialCapacityDoseFixture(GameTestHelper helper, Villager body, LiveHands hands,
                                                       BlockPos pos, long capacity) {
        long startTime = helper.getLevel().getGameTime();
        require(!com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM
                        .isDue(startTime, body.getId()), "capacity fixture starts outside the body's metabolism bucket");
        setThirst(body, 0f);
        MS14Provider.update(body, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(WATER, (capacity - 50) / 100f)));
        require(!com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(body),
                "partial-capacity drink fixture starts below resolved hydration cap");
        require(BodyTimedIngestionService.startPuddleOwned(body, pos, hands.activeHand(), hands.revision(), 31, () -> true)
                == BodyHandActionResult.Reason.OK, "operation starts with less room than one configured dose");
        TimedOperation started = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .filter(o -> o.phase() == TimedOperation.Phase.ACTIVE).findFirst().orElseThrow();
        require(started.dueTick() == startTime + 60, "the fixture aligns against the resolved 60-tick puddle dose");
        waitForGameTime(helper, started.dueTick() - 1, () -> {
            require(water((PuddleBlockEntity) helper.getLevel().getBlockEntity(pos)) == 500,
                    "source remains untouched until the actual timed completion edge");
            MS14Provider.update(body, MS14Bridges.STOMACH,
                    new ReagentAttachment(Map.of(WATER, (capacity - 50) / 100f)));
            waitForGameTime(helper, started.dueTick() + 1, () -> {
                require(!com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM
                                .isDue(started.dueTick() - 1, body.getId())
                                && !com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM
                                .isDue(started.dueTick(), body.getId())
                                && !com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM
                                .isDue(started.dueTick() + 1, body.getId()),
                        "the capacity setup and completion window avoids this body's metabolism bucket");
                TimedOperation operation = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .filter(o -> o.id() == started.id()).findFirst().orElse(null);
                require(operation != null && operation.phase() == TimedOperation.Phase.COMPLETED,
                        "timed operation completed at the due edge; state="
                                + body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations());
                require(water(body) == capacity
                                && water((PuddleBlockEntity) helper.getLevel().getBlockEntity(pos)) == 450,
                        "exact final 50-cent capacity slice moves; stomach=" + water(body)
                                + " source=" + water((PuddleBlockEntity) helper.getLevel().getBlockEntity(pos)));
                require(!active(body, operation.id()), "capacity-limited operation does not repeat");
                waitForGameTime(helper, started.dueTick() + 70, () -> {
                    require(water((PuddleBlockEntity) helper.getLevel().getBlockEntity(pos)) == 450
                                    && !active(body, operation.id()),
                            "a later natural tick does not restart the completed operation or consume source");
                    MS14Provider.update(body, MS14Bridges.STOMACH, new ReagentAttachment(Map.of()));
                    setThirst(body, 0f);
                    require(BodyTimedIngestionService.startPuddleOwned(body, pos, hands.activeHand(), hands.revision(), 32, () -> true)
                                    == BodyHandActionResult.Reason.OK,
                            "a distinct new gesture can start a fresh operation after capacity is available");
                    helper.succeed();
                });
            });
        });
    }

    private static void waitForCapacitySafeStart(GameTestHelper helper, Villager body, Runnable action) {
        long now = helper.getLevel().getGameTime();
        long start = now;
        var metabolism = com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM;
        while (metabolism.isDue(start, body.getId())
                || metabolism.isDue(start + 59, body.getId())
                || metabolism.isDue(start + 60, body.getId())
                || metabolism.isDue(start + 61, body.getId())) start++;
        if (start <= now) action.run();
        else helper.runAfterDelay(1, () -> waitForCapacitySafeStart(helper, body, action));
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void hydrationCapReachedInMetabolismBucketStopsDrinkBeforeThirstDecay(GameTestHelper helper) {
        var level = helper.getLevel();
        var body = makeBody(helper, new BlockPos(2, 1, 2));
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        BlockPos pos = puddle(level, helper.absolutePos(new BlockPos(3, 1, 2)), 5f);
        aimAtPuddle(body, pos);
        require(BodyTimedIngestionService.startPuddleOwned(body, pos, hands.activeHand(), hands.revision(), 41, () -> true)
                == BodyHandActionResult.Reason.OK, "human voluntary drink starts");
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(body, level);
        setThirst(body, 599.99f);
        MS14Provider.update(body, MS14Bridges.STOMACH, new ReagentAttachment(Map.of(WATER, 1f)));
        var due = java.util.EnumSet.of(com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM,
                com.juicyslew.moonstation14.ms14.activity.EntityActivity.THIRST);
        require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .anyMatch(o -> o.phase() == TimedOperation.Phase.ACTIVE),
                "human drink is active immediately before the metabolism edge");
        require(!com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(body),
                "human begins metabolism below the resolved hydration cap");
        com.juicyslew.moonstation14.eventhooks.TickHooks.runDueActivities(body, level, due);
        require(!com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(body),
                "same-bucket thirst decay follows metabolism that reached the hydration cap");
        require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .anyMatch(o -> o.phase() == TimedOperation.Phase.CANCELLED),
                "drink is terminally cancelled before same-bucket thirst decay");
        long remaining = water((PuddleBlockEntity) level.getBlockEntity(pos));
        helper.runAfterDelay(65, () -> {
            require(water((PuddleBlockEntity) level.getBlockEntity(pos)) == remaining,
                    "natural hydration decay below cap does not restart the cancelled drink");
            require(BodyTimedIngestionService.startPuddleOwned(body, pos, hands.activeHand(), hands.revision(), 43, () -> true)
                            == BodyHandActionResult.Reason.OK,
                    "new human gesture can start drinking after natural hydration decay");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void mouseHydrationCapBucketStopsHandlessPuddleDrink(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bodyPos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(bodyPos.below(), Blocks.STONE.defaultBlockState(), 3);
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "mouse created");
        mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPos(bodyPos.getX() + .5, bodyPos.getY(), bodyPos.getZ() + .5);
        require(level.addFreshEntity(mouse) && CharacterIdentitySystem.enroll(mouse, level,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse")) && BodySystem.reconcile(mouse),
                "mouse enrolled with inherited stomach and thirst");
        BlockPos pos = puddle(level, helper.absolutePos(new BlockPos(3, 1, 2)), 5f);
        aimAtPuddle(mouse, pos);
        require(BodyTimedIngestionService.startPuddleOwned(mouse, pos, null, 0, 42, () -> true)
                == BodyHandActionResult.Reason.OK, "handless mouse voluntary drink starts");
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        setThirst(mouse, 34.99f);
        BlockPos foodPos = helper.absolutePos(new BlockPos(5, 1, 2));
        level.setBlock(foodPos.below(), Blocks.STONE.defaultBlockState(), 3);
        var cheese = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                new Vec3(foodPos.getX() + .5, foodPos.getY(), foodPos.getZ() + .5),
                new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(cheese), "resolved cheese food source spawned");
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(cheese.getItem(), level);
        Vec3 foodDelta = cheese.position().subtract(mouse.getEyePosition());
        mouse.setYRot((float) (Math.toDegrees(Math.atan2(foodDelta.z, foodDelta.x)) - 90));
        mouse.setXRot((float) -Math.toDegrees(Math.atan2(foodDelta.y, Math.hypot(foodDelta.x, foodDelta.z))));
        mouse.setYHeadRot(mouse.getYRot());
        require(BodyTimedIngestionService.startWorldOwned(mouse, cheese.getUUID(), null, 0, 45, () -> true)
                        == BodyHandActionResult.Reason.OK,
                "voluntary food ingestion remains startable alongside the drink");
        MS14Provider.update(mouse, MS14Bridges.STOMACH, new ReagentAttachment(Map.of(WATER, 1f)));
        require(mouse.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .anyMatch(o -> o.phase() == TimedOperation.Phase.ACTIVE),
                "mouse drink is active immediately before the metabolism edge");
        require(!com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(mouse),
                "mouse begins metabolism below the resolved hydration cap");
        com.juicyslew.moonstation14.eventhooks.TickHooks.runDueActivities(mouse, level,
                java.util.EnumSet.of(com.juicyslew.moonstation14.ms14.activity.EntityActivity.REAGENT_METABOLISM,
                        com.juicyslew.moonstation14.ms14.activity.EntityActivity.THIRST));
        require(!com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(mouse),
                "mouse water metabolism reaches 35-unit cap before same-bucket thirst decay");
        require(mouse.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .anyMatch(o -> o.phase() == TimedOperation.Phase.CANCELLED),
                "mouse drink is cancelled before same-bucket thirst decay");
        require(mouse.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .anyMatch(o -> o.kind().equals("ingestion") && o.sourceKey().startsWith("world:")
                                && o.phase() == TimedOperation.Phase.ACTIVE),
                "hydration-cap drink cancellation preserves a concurrent voluntary FOOD operation");
        long remaining = water((PuddleBlockEntity) level.getBlockEntity(pos));
        helper.runAfterDelay(65, () -> {
            require(water((PuddleBlockEntity) level.getBlockEntity(pos)) == remaining,
                    "mouse hydration decay cannot restart a stopped drink");
            require(BodyTimedIngestionService.startPuddleOwned(mouse, pos, null, 0, 44, () -> true)
                            == BodyHandActionResult.Reason.OK,
                    "new mouse gesture can start drinking after natural hydration decay");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 90)
    public static void foodItemOnThinPuddleIsSelectedAndStartsAsWorldFood(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bodyPos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(bodyPos.below(), Blocks.STONE.defaultBlockState(), 3);
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "mouse created");
        mouse.setNoAi(true); mouse.setNoGravity(true); mouse.setPos(bodyPos.getX() + .5, bodyPos.getY(), bodyPos.getZ() + .5);
        require(level.addFreshEntity(mouse) && CharacterIdentitySystem.enroll(mouse, level,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse")) && BodySystem.reconcile(mouse),
                "mouse enrolled with oral capability");
        BlockPos puddlePos = helper.absolutePos(new BlockPos(5, 1, 2));
        BlockPos puddle = puddle(level, puddlePos, 5f);
        var cheese = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                new Vec3(puddlePos.getX() + .5, puddlePos.getY() + .25, puddlePos.getZ() + .5),
                new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(cheese), "food item placed over puddle");
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(cheese.getItem(), level);
        Vec3 delta = cheese.position().subtract(mouse.getEyePosition());
        mouse.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
        mouse.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        mouse.setYHeadRot(mouse.getYRot());
        var eye = mouse.getEyePosition();
        var colliderHit = level.clip(new net.minecraft.world.level.ClipContext(eye,
                eye.add(mouse.getLookAngle().scale(BodyItemTargetProbe.MAX_REACH)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mouse));
        require(colliderHit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
                        || !colliderHit.getBlockPos().equals(puddle),
                "noCollission puddle shape does not occlude authoritative item collider ray");
        require(BodyItemTargetProbe.probeSelected(level, mouse, cheese.getUUID(), BodyItemTargetProbe.MAX_REACH) != null,
                "loaded item ray accepts food above the non-colliding puddle");
        long puddleBefore = water((PuddleBlockEntity) level.getBlockEntity(puddle));
        require(BodyTimedIngestionService.startWorldOwned(mouse, cheese.getUUID(), null, 0, 87, () -> true)
                        == BodyHandActionResult.Reason.OK,
                "world item admission starts for the ray-selected food");
        require(water((PuddleBlockEntity) level.getBlockEntity(puddle)) == puddleBefore,
                "food selection does not consume the underlying puddle");
        require(mouse.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                        .anyMatch(op -> op.sourceKey().equals("world:" + cheese.getUUID()) && op.phase() == TimedOperation.Phase.ACTIVE),
                "active operation retains selected world-food identity");
        helper.runAfterDelay(22, () -> {
            require(ReagentUnits.fromFloat(MS14Provider.get(mouse, MS14Bridges.STOMACH).getMap()
                            .getOrDefault(NUTRIENT, 0f)) > 0,
                    "timed world FOOD completion transfers nutriment to stomach");
            require(water((PuddleBlockEntity) level.getBlockEntity(puddle)) == puddleBefore,
                    "food completion leaves colocated water puddle unchanged");
            helper.succeed();
        });
    }

    private static Villager makeBody(GameTestHelper helper, BlockPos pos) {
        pos = helper.absolutePos(pos);
        var level=helper.getLevel(); level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        Villager body=EntityType.VILLAGER.create(level); require(body!=null,"body created"); body.setNoAi(true); body.setNoGravity(true);
        body.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(-90f); body.setXRot(0f);
        require(level.addFreshEntity(body)&&CharacterIdentitySystem.enroll(body,level,ModCharacters.HUMAN_ID),"body enrolled");
        require(LiveHands.initialize(body).isPresent()&&BodySystem.reconcile(body),"body initialized"); return body;
    }

    private static BlockPos puddle(net.minecraft.world.level.Level level,BlockPos pos,float units) {
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3); level.setBlock(pos,ModBlocks.PUDDLE.get().defaultBlockState(),3);
        require(level.getBlockEntity(pos) instanceof PuddleBlockEntity,"puddle created");
        MS14Provider.update(level.getBlockEntity(pos),MS14Bridges.REAGENT,new ReagentAttachment(Map.of(WATER,units)));
        return pos;
    }

    private static void aimAtPuddle(net.minecraft.world.entity.LivingEntity body, BlockPos pos) {
        var level=body.level();
        var bounds=level.getBlockState(pos).getShape(level,pos,CollisionContext.of(body)).bounds();
        Vec3 target=new Vec3(pos.getX()+(bounds.minX+bounds.maxX)/2,
                pos.getY()+(bounds.minY+bounds.maxY)/2,pos.getZ()+(bounds.minZ+bounds.maxZ)/2);
        Vec3 delta=target.subtract(body.getEyePosition());
        body.setYRot((float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90));
        body.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        body.setYHeadRot(body.getYRot()); body.setYBodyRot(body.getYRot());
    }

    private static void aimAtItem(net.minecraft.world.entity.LivingEntity body, Vec3 target) {
        Vec3 delta = target.subtract(body.getEyePosition());
        body.setYRot((float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
        body.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        body.setYHeadRot(body.getYRot()); body.setYBodyRot(body.getYRot());
    }

    private static String probeDiagnostic(net.minecraft.world.level.Level level,Villager body,BlockPos pos) {
        return "puddle target not selected: eyes="+body.getEyePosition()+" look="+body.getLookAngle()+" pos="+pos;
    }

    private static boolean active(Villager b,long id) { return b.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o->o.id()==id&&o.phase()==TimedOperation.Phase.ACTIVE); }
    private static boolean cancelled(Villager b,long id) { return b.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o->o.id()==id&&o.phase()==TimedOperation.Phase.CANCELLED); }
    private static boolean hasTerminal(Villager b,TimedOperation.Phase p) { return b.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o->o.phase()==p); }
    private static boolean hasPhase(Villager b,long id,TimedOperation.Phase p) { return b.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o->o.id()==id&&o.phase()==p); }

    private static long water(PuddleBlockEntity entity) {
        return ReagentUnits.fromFloat(MS14Provider.get(entity, MS14Bridges.REAGENT).getMap().getOrDefault(WATER, 0f));
    }

    private static void setThirst(net.minecraft.world.entity.LivingEntity entity, float value) {
        float before = MS14Provider.get(entity, com.juicyslew.moonstation14.ms14.MS14Bridges.THIRST).thirst();
        MS14Provider.updateIfChanged(entity, com.juicyslew.moonstation14.ms14.MS14Bridges.THIRST,
                new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(before),
                new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                        new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(value)));
    }

    private static void waitForGameTime(GameTestHelper helper, long target, Runnable action) {
        if (helper.getLevel().getGameTime() >= target) {
            action.run();
        } else {
            helper.runAfterDelay(1, () -> waitForGameTime(helper, target, action));
        }
    }

    private static long water(Villager entity) {
        return ReagentUnits.fromFloat(MS14Provider.get(entity, MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
