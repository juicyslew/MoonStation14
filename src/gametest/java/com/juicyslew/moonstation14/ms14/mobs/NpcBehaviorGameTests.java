package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestTags({"mouse-ai", "npc"})
public final class NpcBehaviorGameTests {
    private NpcBehaviorGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 140)
    @GameTestTags({"mouse-ai", "npc", "portable-escape"})
    public static void autonomousContainedMouseAttemptsEscapeAndFleesAway(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, origin, 12);
        clearNpcHeadroom(helper, origin, 12);
        var mouse = createMouse(helper, origin);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        var owner = new com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner() {
            public java.util.UUID identity() { return mouse.getUUID(); }
            public net.minecraft.server.level.ServerLevel level() { return level; }
            public Vec3 position() { return origin.getCenter(); }
            public Stage stage() { return Stage.STATIONARY; }
            public boolean isValid() { return true; }
            public boolean tryEscape(net.minecraft.world.entity.LivingEntity body) { attempts.incrementAndGet(); return true; }
        };
        require(containment.bind(mouse, owner), "contained autonomous mouse registered");
        NpcBehaviorSystem.tick(mouse, level);
        require(attempts.get() == 1 && mouse.position().equals(origin.getCenter()),
                "composed autonomous mouse requests owner escape without moving while contained");
        require(containment.unbind(mouse, owner), "synthetic owner released after dispatch evidence");

        Vec3 formerRoot = mouse.position().add(2, 0, 0);
        require(NpcBehaviorSystem.requestPostEscapeFlee(mouse, formerRoot), "autonomous post-release flee accepted");
        Vec3 goal = NpcBehaviorSystem.debugFleePoint(mouse);
        require(goal != null && goal.x < mouse.getX() && NpcBehaviorSystem.debugFleeUntil(mouse) > level.getGameTime(),
                "flee goal is supported and away from former root with finite phase");
        var planner = new GroundNpcRoutePlanner();
        while (GroundNpcRoutePlanner.tickBudget(level).routeCalls() < 12)
            planner.routeResult(mouse, mouse.position().add(1, 0, 0));
        NpcBehaviorSystem.tick(mouse, level);
        require(NpcBehaviorSystem.debugSnapshot(mouse).routeResult().contains("DEFERRED"),
                "exhausted shared route budget defers flee rather than stepping directly");
        Vec3 fleeStart = mouse.position();
        helper.runAfterDelay(35, () -> require(mouse.position().x < fleeStart.x - .1,
                "deferred flee goal is retried on NPC cadence and moves away once budget returns"));
        helper.runAfterDelay(110, () -> {
            require(NpcBehaviorSystem.debugFleeUntil(mouse) == 0, "flee phase expires");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 120)
    @GameTestTags({"mouse-ai", "npc", "portable-escape", "portable-escape-failure"})
    public static void enclosedMouseFleeIsBoundedAndRoleTakeoverClearsFlee(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++)
            if (dx != 0 || dz != 0) for (int dy = -1; dy >= -4; dy--)
                helper.getLevel().setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        clearNpcHeadroom(helper, origin, 4);
        var mouse = createMouse(helper, origin);
        // A three-block-high solid ring leaves no adjacent supported escape route.
        for (int y = 1; y <= 3; y++) for (int offset = -1; offset <= 1; offset++) {
            helper.getLevel().setBlock(origin.offset(-1, y, offset), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(1, y, offset), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(offset, y, -1), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(offset, y, 1), Blocks.STONE.defaultBlockState(), 3);
        }
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        mouse.setNoGravity(true);
        mouse.setDeltaMovement(Vec3.ZERO);
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.cancel(mouse);
        Vec3 start = mouse.position();
        require(NpcBehaviorSystem.requestPostEscapeFlee(mouse, start.add(2, 0, 0)),
                "flee phase remains accepted even when no away route is currently supported");
        helper.runAfterDelay(35, () -> {
            require(mouse.position().distanceTo(start) < .1,
                    "blocked flee never substitutes direct unsupported movement start=" + start
                            + " now=" + mouse.position() + " flee=" + NpcBehaviorSystem.debugFleePoint(mouse)
                            + " brain=" + NpcBehaviorSystem.debugSnapshot(mouse));
            require(NpcBehaviorSystem.debugFleeUntil(mouse) > level.getGameTime(),
                    "blocked route attempts remain inside finite flee window");
            var takeover = com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease
                    .tryAcquireRoleTarget(mouse).orElseThrow();
            NpcBehaviorSystem.tick(mouse, level);
            require(NpcBehaviorSystem.debugFleeUntil(mouse) == 0,
                    "actual role movement lease takeover clears autonomous flee");
            takeover.close();
            helper.runAfterDelay(75, () -> {
                require(NpcBehaviorSystem.debugFleeUntil(mouse) == 0, "flee remains cleared after bounded phase deadline");
                helper.succeed();
            });
        });
    }


    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 150)
    @GameTestTags({"mouse-ai", "npc"})
    public static void freshSummonMouseMovesFromCornerCoordinates(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, origin, 20);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        double[] offsets = {0, .01, .99, .5};
        var mice = new java.util.ArrayList<com.juicyslew.moonstation14.entities.MouseMob>();
        var starts = new java.util.ArrayList<Vec3>();
        for (int i = 0; i < offsets.length; i++) {
            var spawn = origin.offset(i * 3, 0, 0);
            var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
            require(mouse != null, "normal summon constructor creates mouse");
            mouse.setPos(spawn.getX() + offsets[i], spawn.getY(), spawn.getZ() + offsets[i]);
            require(level.addFreshEntity(mouse), "fresh summon mouse joins world");
            mice.add(mouse);
            starts.add(mouse.position());
        }
        helper.runAtTickTime(100, () -> {
            for (int i = 0; i < mice.size(); i++) {
                var mouse = mice.get(i);
                var resolved = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem
                        .resolveForActor(mouse).flatMap(c -> c.component(NpcBehaviorComponent.class));
                require(resolved.isPresent(), "ordinary entity-join path enrolled resolved NPC behavior");
                require(mouse.position().distanceTo(starts.get(i)) > .15,
                        "fresh ordinary mouse moved through production idle AI from offset=" + offsets[i]
                                + " start=" + starts.get(i) + " now=" + mouse.position()
                                + " brain=" + NpcBehaviorSystem.debugSnapshot(mouse));
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 120)
    @GameTestTags({"mouse-ai", "npc", "mouse-summon-nbt"})
    public static void literalDefaultSummonWithoutNoAiTagMoves(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, origin, 12);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mice = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.MouseMob.class,
                new net.minecraft.world.phys.AABB(origin).inflate(16));
        var starts = new java.util.ArrayList<Vec3>();
        for (int i = 0; i < 4; i++) {
            var spawn = origin.offset(i * 3, 0, 0);
            var source = level.getServer().createCommandSourceStack().withLevel(level)
                    .withPosition(new Vec3(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5));
            level.getServer().getCommands().performPrefixedCommand(source, "summon moonstation14:mouse ~ ~ ~");
        }
        mice = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.MouseMob.class,
                new net.minecraft.world.phys.AABB(origin).inflate(16));
        require(mice.size() == 4, "literal commands created four mice: " + mice.size());
        var summonedMice = List.copyOf(mice);
        for (var mouse : summonedMice) starts.add(mouse.position());
        for (var mouse : summonedMice) {
            require(com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mouse)
                            .flatMap(character -> character.component(NpcBehaviorComponent.class)).isPresent(),
                    "literal summoned body has resolved NPC component");
            require(!mouse.isNoAi(), "summon absent NoAI tag has vanilla false default; pos=" + mouse.position());
        }
        helper.runAtTickTime(100, () -> {
            var diagnostics = new java.util.ArrayList<String>();
            boolean passed = true;
            for (int i = 0; i < summonedMice.size(); i++) {
                var mouse = summonedMice.get(i);
                boolean driven = ((com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven)(Object)mouse)
                        .moonstation14$isNpcMovementDriven();
                double distance = mouse.position().distanceTo(starts.get(i));
                passed &= mouse.isNoAi() && driven && distance > .15;
                diagnostics.add("NoAI=" + mouse.isNoAi() + " driven=" + driven + " start=" + starts.get(i)
                        + " current=" + mouse.position() + " delta=" + distance
                        + " brain=" + NpcBehaviorSystem.debugSnapshot(mouse));
            }
            require(passed, "literal summon production results: " + diagnostics);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 120)
    public static void productionTickConsumesResolvedPolicyAndMovesOncePerTick(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++)
            helper.getLevel().setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed");
        mouse.setNoAi(true);
        mouse.setNoGravity(true);
        mouse.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        require(helper.getLevel().addFreshEntity(mouse), "mouse loaded in fixture");
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse, level);
        require(com.juicyslew.moonstation14.ms14.organ.BodySystem.reconcile(mouse), "mouse prototype composition reconciled");
        var policy = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mouse)
                .flatMap(character -> character.component(NpcBehaviorComponent.class)).orElseThrow();
        require(policy.tasks().equals(java.util.List.of(NpcBehaviorComponent.Task.FOOD,
                NpcBehaviorComponent.Task.DRINK, NpcBehaviorComponent.Task.IDLE)), "resolved policy branch order");
        var start = mouse.position();
        helper.runAfterDelay(15, () -> pollProductionMovement(helper, mouse, start, 0));
    }

    private static void pollProductionMovement(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse, Vec3 start, int elapsed) {
        double distance = start.distanceTo(mouse.position());
        if (distance > .2) {
            var driven = (com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven) (Object) mouse;
            require(driven.moonstation14$isNpcMovementDriven(), "production entity PRE tick claims only NPC movement");
            require(distance > .2 && distance < 6, "NPC production loop moved via one bounded shared step per tick; delta="
                    + distance + ", start=" + start + ", current=" + mouse.position());
            helper.succeed();
            return;
        }
        if (elapsed >= 80) throw new GameTestAssertException("NPC production loop did not move within a deferred route budget; start="
                + start + " current=" + mouse.position() + " brain=" + NpcBehaviorSystem.debugSnapshot(mouse));
        helper.runAfterDelay(2, () -> pollProductionMovement(helper, mouse, start, elapsed + 2));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 400, batch = "npc_food_meal")
    public static void hungryMouseSeeksAndEatsUsingTimedWorldIngestion(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        floor(helper, origin, 8);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = createMouse(helper, origin);
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get(),
                new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                        new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(0)));
        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.THIRST.get(),
                new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                        new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(35)));
        mouse.setYRot(180);
        mouse.setYHeadRot(180);
        mouse.setYBodyRot(180);
        var item = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                mouse.position().add(2.2, 0, 2.2), new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(item), "prototype cheese placed in world");
        helper.runAfterDelay(30, () -> awaitNpcIngestionCompletion(helper, mouse, 0));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 500, batch = "npc_food_fallback")
    @GameTestTags({"mouse-ai", "npc-physics", "ingestion", "npc-route-regression"})
    public static void unreachablePinnedMealReleasesToReachableAlternate(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, origin, 12);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = createMouse(helper, origin);
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        hungerAndThirst(mouse, 0, 35);
        var blocked = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                new Vec3(origin.getX() + 3.5, origin.getY(), origin.getZ() + .5),
                new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(blocked), "initial edible world source exists");
        awaitPinnedMealThenBlock(helper, mouse, blocked, origin, 0);
    }

    private static void awaitPinnedMealThenBlock(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse,
            com.juicyslew.moonstation14.entities.LooseItemEntity blocked,
            BlockPos origin, int elapsed) {
        var brain = NpcBehaviorSystem.debugSnapshot(mouse);
        if (brain != null && blocked.getUUID().equals(brain.itemTarget())) {
            var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
            for (int z = -19; z <= 19; z++) for (int y = 0; y <= 3; y++)
                level.setBlock(origin.offset(2, y, z), Blocks.STONE.defaultBlockState(), 3);
            blocked.setPos(origin.getX() + 4.0, origin.getY(), origin.getZ() + .5);
            var alternate = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                    new Vec3(origin.getX() + .5, origin.getY(), origin.getZ() + 4.5),
                    new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
            require(level.addFreshEntity(alternate), "reachable alternate meal added after initial target is pinned");
            awaitAlternateMeal(helper, mouse, blocked, alternate, 0);
            return;
        }
        if (elapsed >= 100) throw new GameTestAssertException("NPC never pinned the initially reachable meal: " + brain);
        helper.runAfterDelay(2, () -> awaitPinnedMealThenBlock(helper, mouse, blocked, origin, elapsed + 2));
    }

    private static void awaitAlternateMeal(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse,
            com.juicyslew.moonstation14.entities.LooseItemEntity blocked,
            com.juicyslew.moonstation14.entities.LooseItemEntity alternate, int elapsed) {
        var brain = NpcBehaviorSystem.debugSnapshot(mouse);
        if (brain != null && alternate.getUUID().equals(brain.itemTarget())) {
            helper.runAfterDelay(220, () -> {
                require(alternate.isRemoved() && blocked.isAlive(),
                        "NPC completes reachable alternate without user pickup of the rejected source; alternateRemoved="
                                + alternate.isRemoved() + " blockedAlive=" + blocked.isAlive() + " brain="
                                + NpcBehaviorSystem.debugSnapshot(mouse));
                helper.succeed();
            });
            return;
        }
        if (elapsed >= 300) throw new GameTestAssertException("NPC remained pinned to path-invalid meal instead of alternate: "
                + brain + " position=" + mouse.position());
        helper.runAfterDelay(2, () -> awaitAlternateMeal(helper, mouse, blocked, alternate, elapsed + 2));
    }

    private static void awaitNpcIngestionCompletion(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse, int elapsed) {
        var operations = mouse.getExistingDataOrNull(
                com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
        boolean completed = operations != null && operations.operations().stream().anyMatch(operation ->
                operation.kind().equals("ingestion")
                        && operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.COMPLETED);
        if (completed) { helper.succeed(); return; }
        if (elapsed >= 90) throw new GameTestAssertException("NPC's authoritative cheese transaction did not complete; operations="
                + operations + " position=" + mouse.position() + " brain=" + NpcBehaviorSystem.debugSnapshot(mouse));
        helper.runAfterDelay(5, () -> awaitNpcIngestionCompletion(helper, mouse, elapsed + 5));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70, batch = "npc_idle_directions")
    @GameTestTags({"mouse-ai", "npc-physics"})
    public static void idleMiceChooseIndependentDirectionsAndMoveTowardTheirDestinations(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, origin, 32);
        clearNpcHeadroom(helper, origin, 32);
        var tickets = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.around(helper,
                origin.offset(-8, 0, -8), origin.offset(16, 0, 16), 1);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mice = new java.util.ArrayList<com.juicyslew.moonstation14.entities.MouseMob>();
        var starts = new java.util.ArrayList<Vec3>();
        for (int i = 0; i < 16; i++) {
            var start = origin.offset((i % 4) * 3, 0, (i / 4) * 3);
            var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
            require(mouse != null, "ambient mouse constructed");
            mouse.setPos(start.getX() + .5, start.getY(), start.getZ() + .5);
            require(level.addFreshEntity(mouse), "ordinary idle mouse joins world");
            mice.add(mouse);
            starts.add(mouse.position());
        }
        var tickingPositions = mice.stream().map(mouse -> mouse.blockPosition()).toArray(BlockPos[]::new);
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(helper, tickets, 20,
                () -> observeIdleDirections(helper, mice, starts, new boolean[mice.size()], new Vec3[mice.size()],
                        new boolean[mice.size()], new int[4], 0), tickingPositions);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    @GameTestTags({"npc-physics"})
    public static void routeBudgetIsSharedAcrossDimensionsAndResetsOnServerTick(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, origin, 5);
        var overworld = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var nether = overworld.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        require(nether != null, "vanilla nether level exists on the same server");
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
            nether.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        overworld.setBlock(origin.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        nether.setBlock(origin.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        var overworldMob = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(overworld);
        var netherMob = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(nether);
        require(overworldMob != null && netherMob != null, "route probes construct in each dimension");
        overworldMob.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        netherMob.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        GroundNpcRoutePlanner planner = new GroundNpcRoutePlanner();
        Vec3 target = overworldMob.position().add(3, 0, 0);
        var result = planner.routeResult(overworldMob, target);
        require(result.status() != GroundNpcRoutePlanner.RouteStatus.DEFERRED,
                "initial overworld probe has shared budget");
        var budget = GroundNpcRoutePlanner.tickBudget(overworld);
        while (budget.routeCalls() < 12) {
            result = planner.routeResult(budget.routeCalls() % 2 == 0 ? netherMob : overworldMob,
                    target);
            require(result.status() != GroundNpcRoutePlanner.RouteStatus.DEFERRED,
                    "shared calls remain available until the common cap");
            budget = GroundNpcRoutePlanner.tickBudget(overworld);
        }
        result = planner.routeResult(netherMob, netherMob.position());
        require(result.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED,
                "switching dimensions cannot reset this server tick's route-call budget");
        var netherBudget = GroundNpcRoutePlanner.tickBudget(nether);
        budget = GroundNpcRoutePlanner.tickBudget(overworld);
        require(netherBudget.equals(budget) && budget.routeCalls() == 12 && budget.expansions() > 0
                        && budget.expansions() <= 7_000,
                "both dimensions observe the same bounded route-call and node counters: " + budget + " / " + netherBudget);
        helper.runAfterDelay(1, () -> {
            var next = planner.routeResult(netherMob, target);
            var reset = GroundNpcRoutePlanner.tickBudget(overworld);
            var netherReset = GroundNpcRoutePlanner.tickBudget(nether);
            require(next.status() != GroundNpcRoutePlanner.RouteStatus.DEFERRED
                            && reset.routeCalls() > 0 && reset.routeCalls() <= 12 && reset.equals(netherReset)
                            && reset.expansions() <= 7_000,
                    "one server tick later the shared counters reset exactly once: " + reset + " / " + netherReset);
            helper.succeed();
        });
    }

    private static void observeIdleDirections(GameTestHelper helper,
            List<com.juicyslew.moonstation14.entities.MouseMob> mice, List<Vec3> starts,
            boolean[] sampled, Vec3[] goals, boolean[] progressing, int[] quadrants, int elapsed) {
        int observed = 0;
        int moved = 0;
        for (int i = 0; i < mice.size(); i++) {
            var mouse = mice.get(i);
            var brain = NpcBehaviorSystem.debugSnapshot(mouse);
            if (!sampled[i] && brain != null && brain.idlePoint() != null) {
                sampled[i] = true;
                goals[i] = brain.idlePoint();
                double dx = goals[i].x - starts.get(i).x, dz = goals[i].z - starts.get(i).z;
                quadrants[(dx >= 0 ? 1 : 0) + (dz >= 0 ? 2 : 0)]++;
            }
            if (sampled[i]) {
                observed++;
                double dx = goals[i].x - starts.get(i).x, dz = goals[i].z - starts.get(i).z;
                double movedX = mouse.getX() - starts.get(i).x, movedZ = mouse.getZ() - starts.get(i).z;
                if (movedX * dx + movedZ * dz > .02) progressing[i] = true;
                if (progressing[i]) moved++;
            }
        }
        if (observed >= 12 || elapsed >= 35) {
            int represented = 0;
            for (int count : quadrants) if (count > 0) represented++;
            require(observed >= 12, "staggered mice supplied enough independent destinations: " + observed);
            require(represented >= 3, "per-entity random goals cover cardinal and diagonal world sectors: "
                    + java.util.Arrays.toString(quadrants));
            require(moved >= 8, "actual locomotion progresses toward sampled goals: " + moved + "/" + observed);
            helper.succeed();
        } else helper.runAfterDelay(1, () -> observeIdleDirections(helper, mice, starts, sampled, goals,
                progressing, quadrants, elapsed + 1));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 150)
    public static void parchedMouseSeeksAndDrinksResolvedWorldSource(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        floor(helper, origin, 8);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = createMouse(helper, origin);
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get(),
                new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                        new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(35)));
        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.THIRST.get(),
                new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                        new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(0)));
        var stack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.BOTTLE.get());
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(java.util.Map.of(
                        com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water"), 10f)));
        var item = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                mouse.position().add(2.2, 0, 0), stack);
        require(level.addFreshEntity(item), "water bottle placed in world");
        helper.runAtTickTime(120, () -> {
            float current = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.THIRST.get()).thirst();
            require(current > 0, "parched NPC gained hydration through the timed drink path; pos=" + mouse.position()
                    + " item=" + item.position() + " operations="
                    + mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get()));
            require(com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isOverHydrated(mouse)
                            || !item.isRemoved(), "NPC stops seeking once resolved hydration recovers/caps");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260)
    public static void parchedMouseIgnoresPuddleAndDrinksEligibleWorldSource(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        floor(helper, origin, 8);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = createMouse(helper, origin);
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        hungerAndThirst(mouse, 35, 0);
        BlockPos puddlePos = origin.east().east();
        level.setBlock(puddlePos, com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        require(level.getBlockEntity(puddlePos) instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity,
                "world puddle exists");
        var puddle = (com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity) level.getBlockEntity(puddlePos);
        com.juicyslew.moonstation14.ms14.MS14Provider.update(puddle,
                com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT,
                new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(java.util.Map.of(
                        com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water"), 10f)));
        var stack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.BOTTLE.get());
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(java.util.Map.of(
                        com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water"), 10f)));
        var drink = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                mouse.position().add(-.7, 0, 0), stack);
        require(level.addFreshEntity(drink), "eligible world drink placed beyond forbidden puddle candidate");
        helper.runAtTickTime(12, () -> {
            var state = NpcBehaviorSystem.debugSnapshot(mouse);
            require(state != null && state.task() == NpcBehaviorComponent.Task.DRINK
                            && drink.getUUID().equals(state.itemTarget()),
                    "NPC drink utility selects the eligible bottle rather than the adjacent puddle: "+state);
        });
        helper.runAtTickTime(80, () -> {
            var contents = drink.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            long remaining = contents == null ? 0 : contents.centContents().values().stream().mapToLong(Long::longValue).sum();
            require(drink.isRemoved() || drink.getItem().isEmpty() || remaining < 1000,
                    "eligible world drink was consumed, not the puddle: remainingCents="+remaining
                            +" brain="+NpcBehaviorSystem.debugSnapshot(mouse));
            require(puddle.getData(com.juicyslew.moonstation14.component.ModDataAttachments.REAGENT.get())
                            .snapshotUnits().values().stream().anyMatch(value -> value > 0),
                    "nearby bad-drink puddle remains untouched by NPC utility selection");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    public static void fullMouseDoesNotStartRepeatedWorldIngestion(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        floor(helper, origin, 8);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = createMouse(helper, origin);
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        hungerAndThirst(mouse, 35, 35);
        var stack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.BOTTLE.get());
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(java.util.Map.of(
                        com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water"), 10f)));
        var item = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                mouse.position().add(.8, 0, 0), stack);
        require(level.addFreshEntity(item), "full-body drink candidate placed");
        helper.runAtTickTime(55, () -> {
            var operations = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
            require(operations == null || operations.operations().stream().noneMatch(value ->
                            value.kind().equals("ingestion") && value.phase()
                                    == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE),
                    "full resolved stomach/hydration never starts or restarts a timed transaction");
            require(!item.isRemoved() && item.getItem().getCount() == 1,
                    "capped body leaves its untouched water source in the world");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    @GameTestTags({"npc-physics", "npc-slab-regression"})
    public static void productionNpcTraversesBottomSlabAndDescendingOneBlock(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        for (int x = -4; x <= 9; x++) for (int z = -3; z <= 3; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                            net.minecraft.world.level.block.state.properties.SlabType.BOTTOM), 3);
        var mouse = createMouse(helper, origin);
        mouse.setPos(origin.getX() + .5, origin.getY() - .5, origin.getZ() + .5);
        mouse.setNoGravity(false);
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        hungerAndThirst(mouse, 0, 35);
        var food = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                mouse.position().add(4.2, 0, 0), new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(food), "meal placed on slab surface");
        helper.runAfterDelay(1, () -> pollSlabTraversal(helper, mouse, origin, 0));
    }

    private static void pollSlabTraversal(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse, BlockPos origin, int elapsed) {
        double dx = mouse.getX() - (origin.getX() + .5);
        if (dx > 1.5) {
            require(dx < 6.25 && mouse.onGround()
                            && new GroundNpcRoutePlanner().hasSupportedSurface(mouse, mouse.position()),
                    "NPC traversed the partial-height slab corridor and remained supported within path bounds: dx="
                            + dx + " pos=" + mouse.position() + " brain=" + NpcBehaviorSystem.debugSnapshot(mouse));
            helper.succeed();
            return;
        }
        if (elapsed >= 60) throw new GameTestAssertException("production NPC did not cross partial-height support: dx="
                + dx + " pos=" + mouse.position() + " brain=" + NpcBehaviorSystem.debugSnapshot(mouse));
        helper.runAfterDelay(1, () -> pollSlabTraversal(helper, mouse, origin, elapsed + 1));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 180)
    @GameTestTags({"npc-physics", "npc-step-drop-regression"})
    public static void productionNpcTraversesOneBlockStepAndDrop(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        for (int x = -20; x <= 20; x++) for (int z = -3; z <= 3; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        for (int x = -20; x <= 20; x++) for (int z : new int[]{-1, 1}) {
            level.setBlock(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(origin.offset(x, 1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        // A one-block stair-up followed by a one-block descent; destination stays supported.
        level.setBlock(origin.offset(2, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin.offset(4, -1, 0), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(origin.offset(4, -2, 0), Blocks.STONE.defaultBlockState(), 3);
        var mouse = createMouse(helper, origin);
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        hungerAndThirst(mouse, 0, 35);
        var food = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                mouse.position().add(5.2, 0, 0), new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(food), "meal beyond one-block step and drop");
        require(NpcBehaviorSystem.debugSupportedItem(food) && NpcBehaviorSystem.debugItemValue(level, food) > 0,
                "world target passes the same supported-profile/value gates used by production planner");
        assertStepDropRouteWhenBudgetAvailable(helper, mouse, food, origin, 0);
    }

    private static void assertStepDropRouteWhenBudgetAvailable(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse,
            com.juicyslew.moonstation14.entities.LooseItemEntity food, BlockPos origin, int retries) {
        var result = new GroundNpcRoutePlanner().routeResult(mouse, food.position());
        if (result.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED) {
            if (retries >= 40) throw new GameTestAssertException("step/drop search remained budget-deferred: " + result);
            helper.runAfterDelay(1, () -> assertStepDropRouteWhenBudgetAvailable(helper, mouse, food, origin, retries + 1));
            return;
        }
        require(result.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE && !result.waypoints().isEmpty(),
                "route exists over constrained step/drop corridor: " + result);
        var planned = result.waypoints();
        Vec3 previous = mouse.position();
        for (var waypoint : planned) {
            require(Math.abs(waypoint.y() - previous.y) <= 1.0001,
                    "route never schedules a jump/drop exceeding the supported one-block motor edge");
            previous = waypoint.position();
        }
        for (int x : new int[]{7, 8}) for (int y : new int[]{-1, -2, -3})
            helper.getLevel().setBlock(origin.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
        assertDeepPitRejectedWhenBudgetAvailable(helper, mouse, food, origin, 0);
    }

    private static void assertDeepPitRejectedWhenBudgetAvailable(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse,
            com.juicyslew.moonstation14.entities.LooseItemEntity food, BlockPos origin, int retries) {
        var result = new GroundNpcRoutePlanner().routeResult(mouse,
                new Vec3(origin.getX() + 10.5, origin.getY(), origin.getZ() + .5));
        if (result.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED) {
            if (retries >= 40) throw new GameTestAssertException("deep-pit search remained budget-deferred: " + result);
            helper.runAfterDelay(1, () -> assertDeepPitRejectedWhenBudgetAvailable(helper, mouse, food,
                    origin, retries + 1));
            return;
        }
        require(result.status() == GroundNpcRoutePlanner.RouteStatus.NO_PATH,
                "bounded one-block edge planner refuses deep corridor pit: " + result);
        helper.runAfterDelay(12, () -> {
            var state = NpcBehaviorSystem.debugSnapshot(mouse);
            require(state != null && state.task() == NpcBehaviorComponent.Task.FOOD && food.getUUID().equals(state.itemTarget())
                            && !state.route().isEmpty() && mouse.getX() > origin.getX() + .55,
                    "production planner retained edible candidate and routes after staggered admission; position="+mouse.position()+" state="+state);
        });
        pollStepAndDrop(helper, mouse, origin, 0, origin.getY(), origin.getX() + .5, false);
    }

    private static void pollStepAndDrop(GameTestHelper helper, com.juicyslew.moonstation14.entities.MouseMob mouse,
                                        BlockPos origin, int elapsed, double highestY, double furthestX, boolean crossedStep) {
        double maxY = Math.max(highestY, mouse.getY());
        double x = mouse.getX() - (origin.getX() + .5);
        double maxX = Math.max(furthestX, mouse.getX());
        boolean crossed = crossedStep || x > 1.5 && mouse.getY() > origin.getY() + .75;
        if (crossed && x > 4 && mouse.getY() <= origin.getY() + .05) {
            require(x < 6.25, "NPC path remained below six-block displacement after step/drop: " + x);
            helper.succeed();
            return;
        }
        if (elapsed >= 120) throw new GameTestAssertException("production NPC failed one-block step/drop trace: x="+x+" maxX="
                +(maxX-(origin.getX()+.5))+" crossed="+crossed
                +" highestY="+maxY+" current="+mouse.position()+" velocity="+mouse.getDeltaMovement()
                +" onGround="+mouse.onGround()+" noGravity="+mouse.isNoGravity()+" yaw="+mouse.getYRot()
                +" brain="+NpcBehaviorSystem.debugSnapshot(mouse));
        helper.runAfterDelay(1, () -> pollStepAndDrop(helper, mouse, origin, elapsed + 1, maxY, maxX, crossed));
    }

    private static void hungerAndThirst(com.juicyslew.moonstation14.entities.MouseMob mouse, float hunger, float thirst) {
        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get(),
                new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(hunger)));
        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.THIRST.get(),
                new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(thirst)));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void playerAuthorityPausesNpcAndUnclaimedBodyResumes(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        floor(helper, origin, 10);
        var mouse = createMouse(helper, origin);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get(),
                new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                        new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(0)));
        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.THIRST.get(),
                new com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment(
                        new com.juicyslew.moonstation14.ms14.thirst.ThirstComponent(35)));
        var meal = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                mouse.position().add(5.2, 0, 0), new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(meal), "food target for pause/release test");
        var driven = (com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven) (Object) mouse;
        helper.runAtTickTime(12, () -> {
            var lease = com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease
                    .tryAcquireRoleTarget(mouse).orElseThrow();
            helper.runAtTickTime(14, () -> {
                require(!driven.moonstation14$isNpcMovementDriven(), "claimed actor clears NPC movement flag");
                var parked = mouse.position();
                lease.close();
                helper.runAtTickTime(38, () -> {
                    require(driven.moonstation14$isNpcMovementDriven() && mouse.position().distanceTo(parked) > .2,
                            "released unclaimed actor resumes production NPC locomotion; pos=" + mouse.position()
                                    + " meal=" + meal.position());
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80, batch = "npc_food_takeover")
    @GameTestTags({"mouse-ai", "ingestion", "npc-player-takeover-regression"})
    public static void playerTakeoverIngestionSurvivesNpcEligibilityCleanup(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        floor(helper, origin, 8);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = createMouse(helper, origin);
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        hungerAndThirst(mouse, 0, 35);
        var npcMeal = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                mouse.position().add(.55, 0, 0), new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(npcMeal), "NPC meal loaded before acquisition");
        helper.runAtTickTime(12, () -> {
            var before = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
            require(before != null && before.operations().stream().anyMatch(operation -> operation.kind().equals("ingestion")
                            && operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE),
                    "NPC starts its own timed meal before authority takeover");
            var lease = com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease
                    .tryAcquireRoleTarget(mouse).orElseThrow();
            var playerMeal = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                    mouse.position().add(.6, 0, 0), new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
            require(level.addFreshEntity(playerMeal), "independent owner takeover meal loaded after NPC acquisition");
            playerMeal.setPos(mouse.position().add(.6, 0, 0));
            var towardMeal = playerMeal.position().subtract(mouse.position());
            float yaw = (float) Math.toDegrees(Math.atan2(-towardMeal.x, towardMeal.z));
            mouse.setYRot(yaw);
            mouse.setYHeadRot(yaw);
            mouse.setYBodyRot(yaw);
            var start = com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService.startWorldOwned(
                    mouse, playerMeal.getUUID(), null, 0, helper.getLevel().getGameTime(),
                    () -> com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease
                            .isOwnedRoleTargetEligible(mouse));
            require(start == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                    "claimed owner starts an independent authoritative meal operation: " + start);
            helper.runAtTickTime(42, () -> {
                var after = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
                require(after != null && after.operations().stream().anyMatch(operation -> operation.kind().equals("ingestion")
                                && operation.sourceKey().equals("world:" + playerMeal.getUUID())
                                && operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.COMPLETED),
                        "NPC eligibility cleanup never cancels the owner-started operation; operations=" + after);
                lease.close();
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 90, batch = "npc_food_pile")
    @GameTestTags({"mouse-ai", "food-pile"})
    public static void miceDistributeAcrossDefaultFoodPileAndRetainTargets(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, origin, 8);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var sources = new java.util.ArrayList<java.util.UUID>();
        for (int i = 0; i < 8; i++) {
            var item = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                    new Vec3(origin.getX() + .2 + (i % 4) * .18, origin.getY(), origin.getZ() + .2 + (i / 4) * .18),
                    new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
            require(level.addFreshEntity(item) && NpcBehaviorSystem.debugSupportedItem(item)
                            && NpcBehaviorSystem.debugItemValue(level, item) > 0,
                    "normally initialized default cheese is an eligible NPC food source");
            sources.add(item.getUUID());
        }
        var mice = new java.util.ArrayList<com.juicyslew.moonstation14.entities.MouseMob>();
        int[][] starts = {{-3, 0}, {-2, -2}, {0, -3}, {2, -2}, {3, 0}, {2, 2}, {0, 3}, {-2, 2}};
        for (int i = 0; i < 8; i++) {
            var mouse = createMouse(helper, origin, new java.util.UUID(0xF00DL, i + 1));
            mouse.setPos(origin.getX() + .5 + starts[i][0], origin.getY(), origin.getZ() + .5 + starts[i][1]);
            com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
            com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
            hungerAndThirst(mouse, 0, 35);
            mice.add(mouse);
        }
        helper.runAtTickTime(24, () -> {
            var targets = new java.util.ArrayList<java.util.UUID>();
            for (var mouse : mice) {
                var state = NpcBehaviorSystem.debugSnapshot(mouse);
                require(state != null && state.itemTarget() != null && sources.contains(state.itemTarget()),
                        "mouse has acquired one of the default cheese sources: " + state);
                targets.add(state.itemTarget());
            }
            require(targets.stream().distinct().count() > 1, "seeded per-mob PRNG distributes the pile across sources: " + targets);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 800, batch = "npc_idle_arrival")
    @GameTestTags({"mouse-ai", "npc-physics", "npc-idle-arrival-regression"})
    public static void idleDestinationPersistsAcrossReplansAndWaitsAfterArrival(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        floor(helper, origin, 10);
        clearNpcHeadroom(helper, origin, 10);
        var tickets = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.around(helper,
                origin.offset(-8, 0, -8), origin.offset(8, 0, 8), 1);
        long nextPlanBucket = (helper.getLevel().getGameTime() + 1) / 9;
        var mouse = createMouse(helper, origin, new java.util.UUID(0, nextPlanBucket));
        Vec3 spawnedAt = mouse.position();
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(helper, tickets, 8,
                () -> checkIdleSpawnProjections(helper, mouse, origin, spawnedAt,
                        new double[]{0, .01, .99, .5}, 0, 0), mouse.blockPosition());
    }

    private static void checkIdleSpawnProjections(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse, BlockPos origin, Vec3 spawnedAt,
            double[] offsets, int index, int retries) {
        if (index >= offsets.length) {
            mouse.setPos(spawnedAt);
            checkIdleFlatRoute(helper, mouse, spawnedAt, 0);
            return;
        }
        double offset = offsets[index];
        mouse.setPos(origin.getX() + offset, origin.getY(), origin.getZ() + offset);
        var result = new GroundNpcRoutePlanner().routeResult(mouse, mouse.position().add(3, 0, 0));
        if (result.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED) {
            if (retries >= 40) throw new GameTestAssertException("spawn projection route stayed budget-deferred: " + result);
            helper.runAfterDelay(1, () -> checkIdleSpawnProjections(helper, mouse, origin, spawnedAt, offsets,
                    index, retries + 1));
            return;
        }
        require(result.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE && !result.waypoints().isEmpty(),
                "planner projects supported off-center spawn onto its cell graph: offset=" + offset
                        + " position=" + mouse.position() + " result=" + result);
        helper.runAfterDelay(1, () -> checkIdleSpawnProjections(helper, mouse, origin, spawnedAt,
                offsets, index + 1, 0));
    }

    private static void checkIdleFlatRoute(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse, Vec3 spawnedAt, int retries) {
        var result = new GroundNpcRoutePlanner().routeResult(mouse, mouse.position().add(3, 0, 0));
        if (result.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED) {
            if (retries >= 40) throw new GameTestAssertException("spawn flat route stayed budget-deferred: " + result);
            helper.runAfterDelay(1, () -> checkIdleFlatRoute(helper, mouse, spawnedAt, retries + 1));
            return;
        }
        require(result.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE && !result.waypoints().isEmpty(),
                "surface planner resolves a supported flat-floor path from spawned mouse: " + result
                        + " position=" + mouse.position() + " foot=" + helper.getLevel().getBlockState(mouse.blockPosition().below()));
        awaitChosenIdleRoute(helper, mouse, spawnedAt, 0);
    }

    private static void awaitChosenIdleRoute(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mouse, Vec3 spawnedAt, int retries) {
        var first = NpcBehaviorSystem.debugSnapshot(mouse);
        if (first == null || first.task() != NpcBehaviorComponent.Task.IDLE || first.idlePoint() == null) {
            if (retries >= 300) throw new GameTestAssertException("NPC did not select an idle destination: " + first);
            helper.runAfterDelay(1, () -> awaitChosenIdleRoute(helper, mouse, spawnedAt, retries + 1));
            return;
        }
        long selectedAt = helper.getLevel().getGameTime();
        Vec3 selectedFrom = mouse.position();
        double range = first.idlePoint().distanceTo(selectedFrom);
        // The regression needs time to observe one planning interval before this correctly fast
        // NPC arrives. Leave short random walks alone and wait for the next natural idle choice.
        if (range < 4) {
            if (retries >= 300) throw new GameTestAssertException("No sufficiently long natural idle route selected: " + first);
            helper.runAfterDelay(1, () -> awaitChosenIdleRoute(helper, mouse, spawnedAt, retries + 1));
            return;
        }
        var chosen = new GroundNpcRoutePlanner().routeResult(mouse, first.idlePoint());
        if (chosen.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED) {
            if (retries >= 40) throw new GameTestAssertException("chosen idle route stayed budget-deferred: " + chosen);
            helper.runAfterDelay(1, () -> awaitChosenIdleRoute(helper, mouse, spawnedAt, retries + 1));
            return;
        }
        require(chosen.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE && !chosen.waypoints().isEmpty(),
                "selected idle destination has a route: " + chosen + " brain=" + first);
        Vec3 destination = first.idlePoint();
        require(range > 0 && range <= 7, "idle destination is bounded by resolved range 7: range="+range
                +" idle="+destination+" selectedFrom="+selectedFrom+" body="+mouse.position());
        double routeLength = polylineLength(selectedFrom, chosen.waypoints());
        var resolvedMovement = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mouse)
                .orElseThrow().component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class)
                .orElseThrow();
        double autonomousSpeed = NpcBehaviorSystem.autonomousSpeedPerSecond(resolvedMovement);
        // Use the same resolved absolute NPC sprint speed as the live motor. Route steering tapers wish
        // linearly inside .75 blocks; each waypoint's .4-block acceptance adds at most two ticks
        // beyond constant-speed travel, and the final .5-block arrival check adds one sample tick.
        int waypointTaperTicks = 2 * chosen.waypoints().size();
        int finalArrivalSampleTicks = 1;
        // Preserve the fixture's existing scheduling cushion unchanged: configured idle wait,
        // two 18-tick plan windows, and a fixed 40-tick test margin. These are not physics rules.
        int routeTicks = (int) Math.ceil(routeLength / (autonomousSpeed / 20d)) + waypointTaperTicks
                + finalArrivalSampleTicks + first.idleWait() + 2 * 18 + 40;
        helper.runAfterDelay(10, () -> {
            var replanned = NpcBehaviorSystem.debugSnapshot(mouse);
            require(replanned != null && (destination.equals(replanned.idlePoint())
                            || replanned.idlePoint() == null && replanned.idleUntil() > helper.getLevel().getGameTime()),
                    "replan interval preserves the active destination or recognizes its arrival wait: first=" + first
                            + " replanned=" + replanned + " tick=" + helper.getLevel().getGameTime()
                            + " position=" + mouse.position());
        pollForIdleArrival(helper, mouse, destination, (int) (helper.getLevel().getGameTime() - selectedAt),
                routeTicks, routeLength, autonomousSpeed, selectedAt,
                new java.util.ArrayDeque<>());
        });
    }

    private static void pollForIdleArrival(GameTestHelper helper, com.juicyslew.moonstation14.entities.MouseMob mouse,
                                           Vec3 destination, int elapsed, int routeTicks, double routeLength, double autonomousSpeed,
                                           long pollStartedAt, java.util.ArrayDeque<String> recentSamples) {
        var snapshot = NpcBehaviorSystem.debugSnapshot(mouse);
        recentSamples.addLast("t=" + helper.getLevel().getGameTime() + " pos=" + mouse.position()
                + " entityTick=" + helper.getLevel().isPositionEntityTicking(mouse.blockPosition())
                + " tickCount=" + mouse.tickCount
                + " vel=" + mouse.getDeltaMovement() + " grounded=" + mouse.onGround()
                + " distance=" + Math.hypot(mouse.getX() - destination.x, mouse.getZ() - destination.z)
                + " target=" + (snapshot == null ? null : snapshot.idlePoint())
                + " waypoint=" + (snapshot == null || snapshot.route().isEmpty() ? null : snapshot.route().get(0))
                + " motor=" + (snapshot == null ? null : snapshot.motorStep()));
        while (recentSamples.size() > 8) recentSamples.removeFirst();
        if (snapshot != null && snapshot.idlePoint() == null && snapshot.idleUntil() > helper.getLevel().getGameTime()) {
            Vec3 arrived = mouse.position();
            long observedAt = helper.getLevel().getGameTime();
            long arrivalTick = snapshot.idleUntil() - snapshot.idleWait();
            require(snapshot.idleWait() >= 40 && snapshot.idleWait() <= 140
                            && observedAt >= arrivalTick && arrivalTick >= pollStartedAt && snapshot.idleUntil() > observedAt
                            && mouse.onGround() && new GroundNpcRoutePlanner().hasSupportedSurface(mouse, mouse.position())
                            && Math.hypot(mouse.getX() - destination.x, mouse.getZ() - destination.z) <= .5
                            && Math.abs(mouse.getY() - destination.y) <= .75,
                    "configured 40–140 tick wait starts at actual grounded arrival: arrivalTick=" + arrivalTick
                            + " observedTick=" + observedAt + " pollStartedAt=" + pollStartedAt
                            + " idleUntil=" + snapshot.idleUntil() + " wait=" + snapshot.idleWait()
                            + " onGround=" + mouse.onGround() + " supported="
                            + new GroundNpcRoutePlanner().hasSupportedSurface(mouse, mouse.position())
                            + " destination=" + destination + " position=" + mouse.position());
            helper.runAfterDelay(20, () -> {
                var waiting = NpcBehaviorSystem.debugSnapshot(mouse);
                require(waiting != null && waiting.idlePoint() == null
                                && mouse.position().distanceTo(arrived) < .05,
                        "body remains stationary after arriving during the configured idle wait: arrival=" + arrived
                                + " current=" + mouse.position() + " displacement=" + mouse.position().distanceTo(arrived)
                                + " velocity=" + mouse.getDeltaMovement() + " state=" + waiting);
                helper.succeed();
            });
            return;
        }
        if (elapsed >= routeTicks) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("Idle route last-eight-step trace: {}", recentSamples);
            throw new GameTestAssertException("idle arrival timeout elapsed=" + elapsed + "/" + routeTicks
                    + " routeLength=" + routeLength + " autonomousSpeed=" + autonomousSpeed + " targetDistance="
                    + Math.hypot(mouse.getX() - destination.x, mouse.getZ() - destination.z)
                    + " pos=" + mouse.position() + " vel=" + mouse.getDeltaMovement() + " target="
                    + (snapshot == null ? null : snapshot.idlePoint()) + " motor="
                    + (snapshot == null ? null : snapshot.motorStep()));
        }
        helper.runAfterDelay(1, () -> pollForIdleArrival(helper, mouse, destination, elapsed + 1,
                routeTicks, routeLength, autonomousSpeed, pollStartedAt, recentSamples));
    }

    private static double polylineLength(Vec3 start, List<GroundNpcRoutePlanner.Waypoint> route) {
        double length = 0;
        Vec3 previous = start;
        for (var waypoint : route) {
            Vec3 next = waypoint.position();
            length += previous.distanceTo(next);
            previous = next;
        }
        return length;
    }

    private static com.juicyslew.moonstation14.entities.MouseMob createMouse(GameTestHelper helper, BlockPos origin) {
        return createMouse(helper, origin, null);
    }

    private static com.juicyslew.moonstation14.entities.MouseMob createMouse(GameTestHelper helper, BlockPos origin,
                                                                              java.util.UUID testUuid) {
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(helper.getLevel());
        require(mouse != null, "mouse constructed");
        if (testUuid != null) mouse.setUUID(testUuid);
        mouse.setNoAi(true);
        mouse.setNoGravity(true);
        mouse.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        require(helper.getLevel().addFreshEntity(mouse), "mouse added");
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse, level);
        require(com.juicyslew.moonstation14.ms14.organ.BodySystem.reconcile(mouse), "mouse body reconciled");
        return mouse;
    }

    private static void floor(GameTestHelper helper, BlockPos origin, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            helper.getLevel().setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
    }

    private static void clearNpcHeadroom(GameTestHelper helper, BlockPos origin, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            for (int y = 0; y <= 3; y++) helper.getLevel().setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
