package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.animals.AbsorbentCleaningAttachment;
import com.juicyslew.moonstation14.ms14.animals.AbsorbentCleaningService;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.AnimalMobRegistration;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTestAssertException;

@PrefixGameTestTemplate(false)
@GameTestTags({"animals", "moproach-cleaning", "puddle"})
public final class AbsorbentCleaningGameTests {
    private AbsorbentCleaningGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"animals", "moproach-cleaning", "puddle"})
    public static void cleaningConservesAbsorberWaterAndMovesPuddleContaminants(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        for (int x = -2; x <= 4; x++) for (int z = -2; z <= 2; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var type = AnimalMobRegistration.types().values().stream().map(net.neoforged.neoforge.registries.DeferredHolder::get)
                .filter(candidate -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(candidate)
                        .equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "moproach")))
                .findFirst().orElseThrow();
        var mop = type.create(level);
        require(mop instanceof net.minecraft.world.entity.Mob, "moproach host exists");
        var mob = (net.minecraft.world.entity.Mob) mop;
        mob.setPos(origin.getX()+.5, origin.getY(), origin.getZ()+.5);
        mob.setNoAi(true);
        require(level.addFreshEntity(mob), "moproach added to level");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mob, level);
        require(com.juicyslew.moonstation14.ms14.organ.BodySystem.reconcile(mob), "moproach profile/body resolved");
        var water = ModReagents.createKey("water");
        var blood = ModReagents.createKey("blood");
        MS14Provider.update(mob, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(water, 10f)));
        BlockPos puddlePos = origin.east().east();
        level.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        require(level.getBlockEntity(puddlePos) instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity,
                "puddle block entity exists");
        var puddle = (com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity) level.getBlockEntity(puddlePos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(water, 2f, blood, 5f)));
        long before = MS14Provider.getDetached(mob, MS14Bridges.REAGENT).totalUnits()
                + MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).totalUnits();
        var result = AbsorbentCleaningService.cleanAt(mob, puddlePos, level.getGameTime(), false);
        require(result == AbsorbentCleaningService.Result.SUCCESS, "visible puddle accepted by shared cleaning API");
        var absorbed = MS14Provider.getDetached(mob, MS14Bridges.REAGENT);
        var residue = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
        require(absorbed.snapshotUnits().getOrDefault(blood, 0L) == 200, "2-unit bounded pickup transfers contaminant");
        require(absorbed.snapshotUnits().getOrDefault(water, 0L) == 800, "absorbed water replaces transferred water");
        require(absorbed.totalUnits() + residue.totalUnits() == before, "no reagent duplication or loss");
        require(residue.snapshotUnits().getOrDefault(blood, 0L) == 300, "unpicked production puddle residue remains");
        require(level.getBlockEntity(puddlePos) == puddle, "live source puddle is not arbitrarily removed");
        require(mob.getData(ModDataAttachments.ABSORBENT_CLEANING.get()).nextUseTick() == level.getGameTime() + 10,
                "0.5-second use delay is persisted");
        require(AbsorbentCleaningService.cleanAt(mob, puddlePos, level.getGameTime(), false)
                        == AbsorbentCleaningService.Result.DENIED,
                "cooldown denies an immediate duplicate cleaning transaction");
        mob.discard(); helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 75)
    @GameTestTags({"animals", "moproach-cleaning", "puddle", "npc"})
    public static void resolvedMoproachRegeneratesPurgesAndRetainsCycleDeadline(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mob = createMoproach(level, helper.absolutePos(new BlockPos(3, 1, 3)));
        var water = ModReagents.createKey("water"); var blood = ModReagents.createKey("blood");
        MS14Provider.update(mob, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(blood, 15f)));
        long initialGameTime = level.getGameTime();
        var initialChemistry = MS14Provider.getDetached(mob, MS14Bridges.REAGENT);
        require(initialChemistry.snapshotUnits().getOrDefault(blood, 0L) == 1500
                        && initialChemistry.totalUnits() == 1500,
                "cycle fixture starts with exactly 15 units blood; "
                        + cycleDiagnostic(mob, level, initialGameTime, 0, initialChemistry));
        var resolved = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mob).orElseThrow();
        var profile = resolved.component(com.juicyslew.moonstation14.ms14.character.components.AbsorbentCleaningComponent.class).orElseThrow();
        require(profile.pickupCents() == 200 && profile.capacityCents() == 5000 && profile.regenerationCents() == 1000
                        && profile.purgeCents() == 1000 && profile.cycleTicks() == 20,
                "moproach runtime consumes resolved upstream cleaning parameters");
        com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.tick(mob, level);
        long deadline = mob.getData(ModDataAttachments.ABSORBENT_CLEANING.get()).nextCycleTick();
        require(deadline == level.getGameTime() + 20, "initial fixed-duration deadline is persisted; "
                + cycleDiagnostic(mob, level, initialGameTime, deadline, initialChemistry));
        helper.runAtTickTime(21, () -> {
            var first = MS14Provider.getDetached(mob, MS14Bridges.REAGENT);
            require(first.snapshotUnits().getOrDefault(water, 0L) == 1000
                            && first.snapshotUnits().getOrDefault(blood, 0L) == 500,
                    "first production cycle chemistry; observed=" + first.snapshotUnits() + "; "
                            + cycleDiagnostic(mob, level, initialGameTime, deadline, first));
            var cycleState = mob.getData(ModDataAttachments.ABSORBENT_CLEANING.get());
            long firstExpectedDeadline = deadline + profile.cycleTicks();
            require(cycleState.nextCycleTick() == firstExpectedDeadline,
                    "cycle advances once by its configured interval; "
                            + cycleDiagnostic(mob, level, initialGameTime, firstExpectedDeadline, first));
            com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.tick(mob, level);
            require(MS14Provider.getDetached(mob, MS14Bridges.REAGENT).equals(first),
                    "same-tick second system pass cannot duplicate regeneration or purge");
            helper.runAtTickTime(41, () -> {
                var second = MS14Provider.getDetached(mob, MS14Bridges.REAGENT);
                var secondCycleState = mob.getData(ModDataAttachments.ABSORBENT_CLEANING.get());
                long secondExpectedDeadline = deadline + 2L * profile.cycleTicks();
                require(secondCycleState.nextCycleTick() == secondExpectedDeadline,
                        "second cycle advances deadline by exactly one configured interval; "
                                + cycleDiagnostic(mob, level, initialGameTime, secondExpectedDeadline, second));
                require(second.snapshotUnits().getOrDefault(water, 0L) == 2000
                                && second.snapshotUnits().getOrDefault(blood, 0L) == 0,
                        "second production cycle applies one further 10-unit regen/purge; "
                                + cycleDiagnostic(mob, level, initialGameTime, secondCycleState.nextCycleTick(), second));
                MS14Provider.update(mob, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(blood, 50f)));
                mob.setData(ModDataAttachments.ABSORBENT_CLEANING.get(), new AbsorbentCleaningAttachment(
                        secondCycleState.nextUseTick(), level.getGameTime()));
                com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.tick(mob, level);
                var fullCycle = MS14Provider.getDetached(mob, MS14Bridges.REAGENT);
                require(fullCycle.totalUnits() == 4000 && fullCycle.snapshotUnits().getOrDefault(water, 0L) == 0,
                        "full solution remains capacity-bounded while non-water purge runs");
                mob.discard(); helper.succeed();
            });
        });
    }

    private static String cycleDiagnostic(net.minecraft.world.entity.Mob mob,
                                         net.minecraft.server.level.ServerLevel level,
                                         long initialGameTime, long expectedDeadline,
                                         ReagentAttachment observed) {
        var state = mob.getExistingDataOrNull(ModDataAttachments.ABSORBENT_CLEANING.get());
        return "initialGameTime=" + initialGameTime + ", now=" + level.getGameTime()
                + ", expectedDeadline=" + expectedDeadline
                + ", actualDeadline=" + (state == null ? "<missing>" : state.nextCycleTick())
                + ", alive=" + mob.isAlive() + ", removed=" + mob.isRemoved()
                + ", tickCount=" + mob.tickCount + ", pos=" + mob.position()
                + ", reagents=" + observed.snapshotUnits();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 110)
    @GameTestTags({"animals", "moproach-cleaning", "puddle", "npc"})
    public static void unclaimedMoproachFindsAndCleansPuddleThroughSharedNpcRoute(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        for (int x = -2; x <= 7; x++) for (int z = -2; z <= 2; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var mob = createMoproach(level, origin);
        var water = ModReagents.createKey("water"); var blood = ModReagents.createKey("blood");
        MS14Provider.update(mob, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(water, 10f)));
        BlockPos target = origin.east().east().east();
        level.setBlock(target, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        var puddle = (com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity) level.getBlockEntity(target);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(water, 2f, blood, 5f)));
        long startedAt = level.getGameTime();
        long initialBlood = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT)
                .snapshotUnits().getOrDefault(blood, 0L);
        var plannerSelectedClean = new java.util.concurrent.atomic.AtomicBoolean();
        observeNpcClean(helper, mob, level, startedAt, 90, plannerSelectedClean);
        helper.runAtTickTime(90, () -> {
            var state = mob.getExistingDataOrNull(ModDataAttachments.ABSORBENT_CLEANING.get());
            var remaining = level.getBlockEntity(target) instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity livePuddle
                    ? MS14Provider.getDetached(livePuddle, MS14Bridges.REAGENT) : null;
            long remainingBlood = remaining == null ? 0 : remaining.snapshotUnits().getOrDefault(blood, 0L);
            require(plannerSelectedClean.get(), "existing NPC planner selected CLEAN during bounded observation");
            if (!(state != null && state.nextUseTick() > startedAt && remainingBlood < initialBlood)) {
                var npc = NpcBehaviorSystem.debugSnapshot(mob);
                String diagnostic = "initialBlood=" + initialBlood + ", remainingBlood=" + remainingBlood
                        + ", puddle=" + (remaining != null) + ", use=" + (state == null ? "missing" : state.nextUseTick())
                        + ", now=" + level.getGameTime() + ", pos=" + mob.position() + ", alive=" + mob.isAlive()
                        + ", onGround=" + mob.onGround() + ", noAi=" + mob.isNoAi()
                        + ", npc=" + npc + ", mobReagents=" + MS14Provider.getDetached(mob, MS14Bridges.REAGENT).snapshotUnits();
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error("Unclaimed Moproach cleaning route failure: {}", diagnostic);
                require(false, "route clean failed; blood=" + initialBlood + "->" + remainingBlood
                        + ", use=" + (state == null ? "missing" : state.nextUseTick())
                        + ", pos=" + mob.blockPosition() + ", ground=" + mob.onGround()
                        + ", alive=" + mob.isAlive() + ", noAi=" + mob.isNoAi()
                        + ", task=" + (npc == null ? "missing" : npc.task())
                        + ", route=" + (npc == null ? "missing" : npc.routeResult())
                        + ", motor=" + (npc == null ? "missing" : npc.motorStep()));
            }
            mob.discard(); helper.succeed();
        });
    }

    private static void observeNpcClean(GameTestHelper helper, net.minecraft.world.entity.Mob mob,
                                        net.minecraft.server.level.ServerLevel level, long startedAt, int deadline,
                                        java.util.concurrent.atomic.AtomicBoolean observed) {
        long elapsed = level.getGameTime() - startedAt;
        if (elapsed >= deadline) return;
        var snapshot = NpcBehaviorSystem.debugSnapshot(mob);
        if (snapshot != null && snapshot.task()
                == com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent.Task.CLEAN) observed.set(true);
        if (!observed.get()) helper.runAfterDelay(1, () -> observeNpcClean(helper, mob, level, startedAt, deadline, observed));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 150)
    @GameTestTags({"animals", "moproach-cleaning", "puddle", "interaction", "lifecycle"})
    public static void cleanCommandRequiresCommittedBodyGazeAndLoadedVisiblePuddle(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        for (int x = -2; x <= 8; x++) for (int z = -2; z <= 3; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        // RegisteredServerPlayerFixture is an embedded registered peer; this validates server admission/commit, not an actual network handshake.
        var fixture = RegisteredServerPlayerFixture.register(level, "mopepoch");
        var wrongFixture = RegisteredServerPlayerFixture.register(level, "mopwrong");
        var mob = createMoproach(level, origin);
        var water = ModReagents.createKey("water"); var blood = ModReagents.createKey("blood");
        BlockPos puddlePos = origin.east().east();
        level.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        var puddle = (com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity) level.getBlockEntity(puddlePos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(water, 2f, blood, 5f)));
        var sourceBefore = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
        try {
            int wrong = dispatchClean(wrongFixture);
            require(wrong == 0 && MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).equals(sourceBefore),
                    "different uncommitted player cannot act on another body's puddle");
            wrongFixture.close(); // It is not captured by the async lifecycle/role-offer continuation.
            // The lifecycle sends its initial CHARACTER begin from ServerTickEvent.Post, not register().
            helper.runAfterDelay(2, () -> readyPlayerForMoproachTakeover(helper, fixture, mob, origin,
                    puddlePos, puddle, sourceBefore, water, blood));
        } catch (RuntimeException | Error failure) {
            fixture.close(); wrongFixture.close(); mob.discard(); throw failure;
        }
    }

    private static void readyPlayerForMoproachTakeover(GameTestHelper helper,
                                                        RegisteredServerPlayerFixture fixture,
                                                        net.minecraft.world.entity.Mob mob, BlockPos origin,
                                                        BlockPos puddlePos,
                                                        com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity puddle,
                                                        ReagentAttachment sourceBefore,
                                                        net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water,
                                                        net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> blood) {
        try {
            var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            require(com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(active.body(),
                    java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 300f), 1f, true)
                    == com.juicyslew.moonstation14.ms14.damage.DamageSystem.Result.APPLIED,
                    "canonical player lifecycle enters corpse before moproach takeover");
            helper.runAfterDelay(3, () -> beginMoproachTakeover(helper, fixture, mob, origin, puddlePos,
                    puddle, sourceBefore, water, blood, 0));
        } catch (RuntimeException | Error failure) {
            fixture.close(); mob.discard();
            throw failure instanceof GameTestAssertException assertion ? assertion
                    : new GameTestAssertException("registered-player readiness failed before Moproach takeover: "
                    + failure.getMessage());
        }
    }

    private static void beginMoproachTakeover(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                               net.minecraft.world.entity.Mob mob, BlockPos origin, BlockPos puddlePos,
                                               com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity puddle,
                                               ReagentAttachment sourceBefore,
                                               net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water,
                                               net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> blood,
                                               int retries) {
        try {
            var player = fixture.player();
            var corpse = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
            if (corpse == null || corpse.mode() != com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.CORPSE) {
                if (retries >= 30) throw new GameTestAssertException("registered-player lifecycle did not reach corpse");
                helper.runAfterDelay(1, () -> beginMoproachTakeover(helper, fixture, mob, origin, puddlePos, puddle,
                        sourceBefore, water, blood, retries + 1));
                return;
            }
            require(com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.requestGhost(player, corpse.epoch()),
                    "corpse transitions through canonical ghost lifecycle");
            var ghost = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
            require(ghost != null && RegisteredPlayerReadyDriver.readyGhost(fixture, ghost.authorityGeneration()),
                    "registered player readies current GHOST epoch");
            require(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl
                    .offerRoleTarget(player, mob), "current ghost receives actual Moproach role offer");
            com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer offer = null;
            Object packet;
            while ((packet = fixture.pollOutbound()) != null)
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket payload
                        && payload.payload() instanceof com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer found)
                    offer = found;
            require(offer != null, "server issues role offer for Moproach");
            RegisteredPlayerReadyDriver.acceptRoleOffer(fixture, offer);
            var ownership = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl
                    .activeCharacterBody(player).orElseThrow();
            require(ownership.body() == mob && player.getCamera() == mob,
                    "authenticated committed canonical body is the original Moproach");
            // Keep the unclaimed NPC from consuming the fixture puddle before its controlled takeover.
            MS14Provider.update(mob, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(water, 10f)));

            // Actual command dispatcher rejects a gaze that does not nominate a puddle and leaves chemistry untouched.
            RegisteredPlayerReadyDriver.aimAt(mob, (net.minecraft.server.level.ServerLevel) helper.getLevel(), puddlePos);
            require(dispatchClean(fixture) == 1, "clean command commits against authenticated body's actual gaze target");
            var afterClean = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
            require(afterClean.snapshotUnits().getOrDefault(blood, 0L) == 300,
                    "dispatcher command uses same bounded production transaction");
            // The player body cannot nominate a puddle through a wall, and no source is changed on denial.
            mob.setData(ModDataAttachments.ABSORBENT_CLEANING.get(), AbsorbentCleaningAttachment.EMPTY);
            BlockPos wall = puddlePos.west();
            ((net.minecraft.server.level.ServerLevel) helper.getLevel()).setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            RegisteredPlayerReadyDriver.aimAt(mob, (net.minecraft.server.level.ServerLevel) helper.getLevel(), wall);
            var beforeDenied = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
            require(dispatchClean(fixture) == 0 && MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).equals(beforeDenied),
                    "blocked/wrong gaze is rejected with no fluid mutation");
            ((net.minecraft.server.level.ServerLevel) helper.getLevel()).setBlock(wall, Blocks.AIR.defaultBlockState(), 3);

            BlockPos farPuddlePos = origin.south().south().south().south().south().south().south();
            ((net.minecraft.server.level.ServerLevel) helper.getLevel()).setBlock(farPuddlePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
            var farPuddle = (com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity)
                    ((net.minecraft.server.level.ServerLevel) helper.getLevel()).getBlockEntity(farPuddlePos);
            MS14Provider.update(farPuddle, MS14Bridges.REAGENT, new ReagentAttachment(java.util.Map.of(water, 2f, blood, 5f)));
            mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, farPuddlePos.getCenter());
            var beforeFar = MS14Provider.getDetached(farPuddle, MS14Bridges.REAGENT);
            require(dispatchClean(fixture) == 0 && MS14Provider.getDetached(farPuddle, MS14Bridges.REAGENT).equals(beforeFar),
                    "out-of-range loaded puddle cannot be cleaned by gaze command");

            // Containment is an explicit controlled-action denial even when body, gaze, and session are otherwise valid.
            mob.setData(ModDataAttachments.ABSORBENT_CLEANING.get(), AbsorbentCleaningAttachment.EMPTY);
            RegisteredPlayerReadyDriver.aimAt(mob, (net.minecraft.server.level.ServerLevel) helper.getLevel(), puddlePos);
            var owner = new com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner() {
                public java.util.UUID identity() { return mob.getUUID(); }
                public net.minecraft.server.level.ServerLevel level() { return (net.minecraft.server.level.ServerLevel) helper.getLevel(); }
                public net.minecraft.world.phys.Vec3 position() { return mob.position(); }
                public Stage stage() { return Stage.STATIONARY; }
                public boolean isValid() { return true; }
                public boolean tryEscape(net.minecraft.world.entity.LivingEntity body) { return true; }
            };
            var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(owner.level());
            require(containment.bind(mob, owner), "test establishes real containment owner ledger");
            var beforeContained = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
            require(dispatchClean(fixture) == 0 && MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).equals(beforeContained),
                    "contained controlled Moproach cannot clean or mutate chemistry");
            require(containment.unbind(mob, owner), "test removes exact containment owner");

            com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(mob,
                    java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 300f), 1f, true);
            var beforeDead = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
            require(dispatchClean(fixture) == 0 && MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).equals(beforeDead),
                    "dead canonical body cannot clean or mutate puddle");
            fixture.close(); mob.discard(); helper.succeed();
        } catch (RuntimeException | Error failure) { fixture.close(); mob.discard(); throw failure; }
    }

    private static int dispatchClean(RegisteredServerPlayerFixture fixture) {
        var dispatcher = fixture.player().getServer().getCommands().getDispatcher();
        try { return dispatcher.execute(dispatcher.parse("cleanpuddle", fixture.player().createCommandSourceStack())); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("cleanpuddle failed to parse: " + failure);
        }
    }

    private static net.minecraft.world.entity.Mob createMoproach(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var type = AnimalMobRegistration.types().values().stream().map(net.neoforged.neoforge.registries.DeferredHolder::get)
                .filter(candidate -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(candidate)
                        .equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "moproach")))
                .findFirst().orElseThrow(() -> new GameTestAssertException("moproach host type unavailable"));
        var entity = type.create(level);
        if (!(entity instanceof net.minecraft.world.entity.Mob mob)) throw new GameTestAssertException("moproach must be a Mob");
        mob.setPos(pos.getX()+.5, pos.getY(), pos.getZ()+.5);
        mob.setNoAi(true);
        if (!level.addFreshEntity(mob)) throw new GameTestAssertException("moproach failed to join level");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mob, level);
        if (!com.juicyslew.moonstation14.ms14.organ.BodySystem.reconcile(mob)) throw new GameTestAssertException("moproach body failed reconcile");
        return mob;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
