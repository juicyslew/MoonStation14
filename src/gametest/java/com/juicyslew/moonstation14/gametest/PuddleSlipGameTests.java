package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PuddleSlipGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(PuddleSlipGameTests.class);

    private PuddleSlipGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void realBlockContactSlipsBoundFakePlayerAndVillagerOnlyOnce(GameTestHelper helper) {
        java.util.concurrent.ConcurrentHashMap<java.util.UUID, AtomicInteger> counts = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> observer = event ->
                counts.computeIfAbsent(event.target().getUUID(), ignored -> new AtomicInteger()).incrementAndGet();
        require(SlipSystem.addListener(observer), "test slip observer registration");
        helper.runAfterDelay(59, () -> SlipSystem.removeListener(observer));
        BlockPos floor = new BlockPos(2, 0, 2);
        BlockPos puddlePos = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(puddlePos));
        ReagentAttachment contents = new ReagentAttachment();
        // Touch can legitimately remove 15% on either admitted slip. Keep enough
        // slippery volume after the first actor's reaction for the re-entry proof.
        contents.specificAdd(ModReagents.createKey("spacelube"), 20f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);

        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 2));
        villager.setNoAi(true);
        FakePlayer fake = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "puddle-slip"));
        putAt(fake, helper, new BlockPos(1, 1, 2));
        require(helper.getLevel().addFreshEntity(fake), "FakePlayer/ServerPlayer must join the GameTest level");
        var unbound = helper.spawn(EntityType.COW, new BlockPos(4, 1, 2));
        unbound.setNoAi(true);
        require(CharacterControlSystem.canAct(villager) && CharacterControlSystem.canAct(fake),
                "both positive actors must resolve as eligible human characters");

        helper.runAfterDelay(2, () -> {
            villager.setDeltaMovement(.12, 0, 0);
            fake.setDeltaMovement(.12, 0, 0);
            villager.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(1, 0, 0));
            fake.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(1, 0, 0));
            require(slipCount(counts, fake) == 0 && !CharacterControlSystem.isStunned(fake),
                    "entityInside alone must not admit a player from stale/nonzero velocity");
            SlipSystem.onAcceptedPlayerMovement(fake, net.minecraft.world.phys.Vec3.ZERO);
            require(slipCount(counts, fake) == 0, "zero accepted displacement must remain inert");
            fake.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            SlipSystem.onAcceptedPlayerMovement(fake, new net.minecraft.world.phys.Vec3(.12, 0, 0));
            helper.runAfterDelay(1, () -> {
                require(CharacterControlSystem.isStunned(villager), "villager must slip through actual entityInside; count="
                        + slipCount(counts, villager) + ", pos=" + villager.position() + ", velocity=" + villager.getDeltaMovement());
                require(CharacterControlSystem.isStunned(fake), "FakePlayer must slip through actual entityInside");
                require(slipCount(counts, villager) == 1 && slipCount(counts, fake) == 1,
                        "actual movement through the puddle must invoke each slip hook exactly once");
                require(villager.hasData(ModDataAttachments.STATUS_EFFECT.get())
                                && fake.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                        "slip must apply timed server status to both actors");
                require(SlipSystem.isSliding(villager) == SlidingFrictionSystem.hasQualifyingContact(villager)
                                && SlipSystem.isSliding(fake) == SlidingFrictionSystem.hasQualifyingContact(fake),
                        "sliding projection must remain only while each actor's feet contact a qualifying source");
                require(!unbound.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                        "unconfigured Cow remains inert despite physical puddle contact");
                require(!SlipSystem.isSliding(unbound) && !unbound.hasData(ModDataAttachments.SLIDING.get()),
                        "absent sliding read must not materialize state on an unrelated entity");
                villager.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(.1, 0, 0));
                fake.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(.1, 0, 0));
                SlipSystem.onAcceptedPlayerMovement(fake, new net.minecraft.world.phys.Vec3(.1, 0, 0));
                require(slipCount(counts, villager) == 1 && slipCount(counts, fake) == 1,
                        "repeated movement callbacks during one puddle contact must not slip twice");
                helper.runAfterDelay(2, () -> {
                    villager.setDeltaMovement(-.12, 0, 0);
                    fake.setDeltaMovement(-.12, 0, 0);
                    villager.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(-1, 0, 0));
                    fake.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(-1, 0, 0));
                    SlipSystem.onAcceptedPlayerMovement(fake, new net.minecraft.world.phys.Vec3(-1, 0, 0));
                    helper.runAfterDelay(2, () -> {
                        // Reactive Touch consumes solution on admitted slips. Restore and
                        // verify the source so the re-entry behavior is tested with a
                        // qualifying puddle, not an exhausted one.
                        ReagentAttachment refilled = new ReagentAttachment();
                        refilled.specificAdd(ModReagents.createKey("spacelube"), 20f, puddle.getCapacity());
                        MS14Provider.update(puddle, MS14Bridges.REAGENT, refilled);
                        require(MS14Provider.get(puddle, MS14Bridges.REAGENT)
                                        .snapshotUnits().getOrDefault(ModReagents.createKey("spacelube"), 0L) == 2000L,
                                "re-entry source must contain exactly 20 Space Lube units");
                        villager.setDeltaMovement(.12, 0, 0);
                        villager.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(1, 0, 0));
                        require(slipCount(counts, villager) == 2,
                                "Villager re-entry must admit exactly once; villagerCount="
                                        + slipCount(counts, villager) + ", fakeCount=" + slipCount(counts, fake)
                                        + ", villagerPos=" + villager.position() + ", fakePos=" + fake.position()
                                        + ", villagerStunned=" + CharacterControlSystem.isStunned(villager)
                                        + ", fakeStunned=" + CharacterControlSystem.isStunned(fake)
                                        + ", source=" + MS14Provider.get(puddle, MS14Bridges.REAGENT).snapshotUnits());
                        SlipSystem.removeListener(observer);
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void acceptedPlayerEndpointIgnoresCrossingsAndNearbyMisses(GameTestHelper helper) {
        AtomicInteger events = new AtomicInteger();
        AtomicReference<UUID> crossingId = new AtomicReference<>();
        java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> observer = event ->
                {
                    if (event.target().getUUID().equals(crossingId.get())) events.incrementAndGet();
                };
        require(SlipSystem.addListener(observer), "endpoint-contact observer registration");
        helper.runAfterDelay(29, () -> SlipSystem.removeListener(observer));

        BlockPos floor = new BlockPos(2, 0, 2);
        BlockPos puddlePos = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
        fillSpaceLube(helper, puddlePos, 20f);
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) helper.getLevel();

        FakePlayer crossing = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "swept-crossing"));
        putAt(crossing, helper, new BlockPos(3, 1, 2));
        require(level.addFreshEntity(crossing), "crossing FakePlayer must join the GameTest level");
        crossingId.set(crossing.getUUID());
        FakePlayer parallel = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "swept-parallel"));
        putAt(parallel, helper, new BlockPos(3, 1, 3));
        require(level.addFreshEntity(parallel), "parallel FakePlayer must join the GameTest level");
        FakePlayer above = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "swept-above"));
        putAt(above, helper, new BlockPos(3, 2, 2));
        require(level.addFreshEntity(above), "above FakePlayer must join the GameTest level");

        helper.runAfterDelay(2, () -> {
            // End at x=3.5, wholly beyond the source, after an accepted move whose
            // path crosses it. Endpoint policy intentionally does not sweep the path.
            Vec3 accepted = new Vec3(1.4d, 0d, 0d);
            SlipSystem.onAcceptedPlayerMovement(crossing, accepted);
            SlipSystem.onAcceptedPlayerMovement(parallel, accepted);
            SlipSystem.onAcceptedPlayerMovement(above, accepted);
            require(events.get() == 0 && !CharacterControlSystem.isStunned(crossing),
                    "cross-only movement must remain inert when the endpoint is outside; events=" + events.get());
            require(!CharacterControlSystem.isStunned(parallel) && !CharacterControlSystem.isStunned(above),
                    "parallel z miss and vertically separated sweep must remain inert");
            SlipSystem.removeListener(observer);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void acceptedPlayerEndpointDoesNotRelatchOnPuddleExit(GameTestHelper helper) {
        AtomicInteger events = new AtomicInteger();
        AtomicReference<UUID> targetId = new AtomicReference<>();
        java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> observer = event -> {
            if (event.target().getUUID().equals(targetId.get())) events.incrementAndGet();
        };
        require(SlipSystem.addListener(observer), "exit-latch observer registration");
        helper.runAfterDelay(29, () -> SlipSystem.removeListener(observer));

        BlockPos floor = new BlockPos(2, 0, 2);
        BlockPos puddlePos = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
        fillSpaceLube(helper, puddlePos, 20f);
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "swept-exit"));
        putAt(player, helper, new BlockPos(1, 1, 2));
        require(level.addFreshEntity(player), "exit-latch FakePlayer must join the GameTest level");
        targetId.set(player.getUUID());

        helper.runAfterDelay(2, () -> {
            // Move to each endpoint before admission; only endpoint overlap matters.
            putAt(player, helper, new BlockPos(2, 1, 2));
            SlipSystem.onAcceptedPlayerMovement(player, new Vec3(1d, 0d, 0d));
            require(events.get() == 1, "accepted entry must emit exactly one event; count=" + events.get());

            putAt(player, helper, new BlockPos(3, 1, 2));
            SlipSystem.onAcceptedPlayerMovement(player, new Vec3(1d, 0d, 0d));
            require(events.get() == 1,
                    "endpoint EXIT of a latched source must not emit a second event; count=" + events.get());

            com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem.clearAll(
                    ((com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait) player).toHandleSelf(), level);
            fillSpaceLube(helper, puddlePos, 20f);
            putAt(player, helper, new BlockPos(2, 1, 2));
            SlipSystem.onAcceptedPlayerMovement(player, new Vec3(-1d, 0d, 0d));
            require(events.get() == 2,
                    "a separate entry after the exit must admit once; count=" + events.get());

            // The virtual admission ratio is now below 30%, but the player's AABB
            // still intersects the physical source. This is not an SS14 EndCollide.
            BlockPos absoluteSource = helper.absolutePos(puddlePos);
            player.setPos(absoluteSource.getX() + 1.1d, absoluteSource.getY(), absoluteSource.getZ() + .5d);
            net.minecraft.world.phys.AABB sourceShape = helper.getLevel().getBlockState(absoluteSource)
                    .getShape(helper.getLevel(), absoluteSource,
                            net.minecraft.world.phys.shapes.CollisionContext.empty()).bounds().move(absoluteSource);
            require(SlipSystem.intersectionRatio(player.getBoundingBox(),
                            sourceShape, absoluteSource) < .3d, "near-edge fixture must fall below the admission ratio");
            require(player.getBoundingBox().intersects(sourceShape),
                    "near-edge fixture must still intersect the puddle shape");
            SlipSystem.reconcileTarget(player);
            putAt(player, helper, new BlockPos(2, 1, 2));
            SlipSystem.onAcceptedPlayerMovement(player, new Vec3(-.1d, 0d, 0d));
            require(events.get() == 2, "near-edge overlap must remain latched and not reboost");

            putAt(player, helper, new BlockPos(3, 1, 2));
            SlipSystem.reconcileTarget(player);
            fillSpaceLube(helper, puddlePos, 20f);
            putAt(player, helper, new BlockPos(2, 1, 2));
            SlipSystem.onAcceptedPlayerMovement(player, new Vec3(-1d, 0d, 0d));
            require(events.get() == 3, "full AABB exit must rearm the source; count=" + events.get());
            SlipSystem.removeListener(observer);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void soapReagentAdmitsEndpointContactWithoutSpaceLube(GameTestHelper helper) {
        AtomicInteger events = new AtomicInteger();
        AtomicInteger totalEvents = new AtomicInteger();
        AtomicReference<UUID> targetId = new AtomicReference<>();
        java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> observer = event -> {
            totalEvents.incrementAndGet();
            if (event.target().getUUID().equals(targetId.get())) events.incrementAndGet();
        };
        require(SlipSystem.addListener(observer), "soap-source observer registration");
        helper.runAfterDelay(29, () -> SlipSystem.removeListener(observer));

        BlockPos floor = new BlockPos(2, 0, 2);
        BlockPos puddlePos = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(puddlePos));
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(ModReagents.createKey("soapreagent"), 20f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);

        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "soap-slip"));
        putAt(player, helper, new BlockPos(1, 1, 2));
        require(level.addFreshEntity(player), "soap-contact FakePlayer must join the GameTest level");
        targetId.set(player.getUUID());
        var unbound = helper.spawn(EntityType.COW, new BlockPos(4, 1, 2));
        unbound.setNoAi(true);
        com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment invalidIdentity =
                new com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment();
        invalidIdentity.bind(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "unbound-slip"));
        MS14Provider.update(unbound, MS14Bridges.CHARACTER_IDENTITY, invalidIdentity);

        helper.runAfterDelay(2, () -> {
            BlockPos endpoint = helper.absolutePos(puddlePos);
            player.setPos(endpoint.getX() + .5d, endpoint.getY(), endpoint.getZ() + .5d);
            SlipSystem.onAcceptedPlayerMovement(player, new Vec3(.1d, 0d, 0d));
            require(events.get() == 0 && !player.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                    "soap below its required speed must not admit or apply status");
            SlipSystem.onAcceptedPlayerMovement(player, new Vec3(.25d, 0d, 0d));
            require(events.get() == 1 && CharacterControlSystem.isStunned(player),
                    "20u soap must admit at accepted speed 5 blocks/second without Space Lube");
            require(player.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                    "admitted soap slip must apply timed status");
            putAt(unbound, helper, puddlePos);
            require(com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolve(unbound).isEmpty(),
                    "unconfigured Cow must remain unresolved before source contact");
            int beforeUnboundContact = totalEvents.get();
            SlipSystem.onPuddleContact(level, helper.absolutePos(puddlePos), unbound);
            require(totalEvents.get() == beforeUnboundContact,
                    "unconfigured Cow must not emit a slip event for soap source contact");
            SlipSystem.removeListener(observer);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void stunnedVillagerRetainsInstalledImpulseOnNaturalServerTicks(GameTestHelper helper) {
        // Four independent, otherwise-identical lanes: the x=1..12 stone runs keep
        // every actor grounded throughout the observation; only the stunned lanes
        // have a source at x=1. Villagers retain their normal AI in both conditions.
        for (int z : new int[]{2, 4, 6, 8}) {
            for (int x = 1; x <= 12; x++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        BlockPos playerPuddlePos = new BlockPos(2, 1, 4);
        BlockPos villagerPuddlePos = new BlockPos(2, 1, 8);
        for (BlockPos puddlePos : new BlockPos[]{playerPuddlePos, villagerPuddlePos}) {
            helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
            fillSpaceLube(helper, puddlePos, 20f);
        }

        FakePlayer playerBaseline = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "unstunned-slip-tick-baseline"));
        putAt(playerBaseline, helper, new BlockPos(1, 1, 2));
        require(helper.getLevel().addFreshEntity(playerBaseline), "baseline FakePlayer must join the GameTest level");
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "stunned-slip-tick"));
        putAt(player, helper, playerPuddlePos);
        require(helper.getLevel().addFreshEntity(player), "diagnostic FakePlayer must join the GameTest level");
        Villager villagerBaseline = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 6));
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 8));
        // Keep the setup ticks deterministic: wandering must not move the actor
        // onto the source before the controlled stun/impulse observation below.
        villager.setNoAi(true);
        require(CharacterControlSystem.canAct(player) && CharacterControlSystem.canAct(villager),
                "FakePlayer and Villager must resolve as eligible control actors");

        helper.runAfterDelay(2, () -> {
            // Identical baseline impulses; neither baseline lane contains a puddle.
            playerBaseline.setDeltaMovement(.375d, 0d, 0d);
            villagerBaseline.setDeltaMovement(.375d, 0d, 0d);

            // The accepted displacement is large enough to meet Space Lube's speed gate;
            // this player trial exercises real accepted-movement source admission.
            Vec3 acceptedDisplacement = new Vec3(.25d, 0d, 0d);
            SlipSystem.onAcceptedPlayerMovement(player, acceptedDisplacement);
            require(CharacterControlSystem.isStunned(player), "accepted source contact must stun FakePlayer");
            Vec3 playerLaunch = player.getDeltaMovement();
            require(playerLaunch.lengthSqr() > 0d,
                    "accepted source contact must install a nonzero FakePlayer launch velocity");
            Vec3 playerStart = player.position();

            // This test isolates natural Villager momentum under controlled stun. The
            // previous ordinary Entity.move fixture did not establish a real contact;
            // do not describe this installed impulse as a puddle slip. Real Villager
            // entityInside admission is covered by the positive test above.
            putAt(villager, helper, villagerPuddlePos);
            CharacterControlSystem.applyStun(villager, 2f);
            villager.setDeltaMovement(.9d, 0d, 0d);
            require(CharacterControlSystem.isStunned(villager),
                    "controlled stun must be active before natural-tick observation");
            villager.setNoAi(false);
            Vec3 villagerLaunch = villager.getDeltaMovement();
            require(villagerLaunch.lengthSqr() > 0d, "AI-enabled Villager must receive nonzero launch velocity");
            Vec3 villagerStart = villager.position();
            Vec3 playerBaselineStart = playerBaseline.position();
            Vec3 villagerBaselineStart = villagerBaseline.position();
            BlockPos laneStart = helper.absolutePos(new BlockPos(1, 0, 8));
            BlockPos laneEnd = helper.absolutePos(new BlockPos(12, 0, 8));
            double floorY = laneStart.getY() + 1d;
            Vec3[] lastVillagerPosition = {villagerStart};

            // onGround is a transient collision/tick flag, not a reliable assertion
            // of floor contact between natural entity ticks. Check the actual feet
            // height and lane footprint on every observed tick instead, and reject
            // a discontinuous position jump that could otherwise mask a teleport.
            for (int tick = 1; tick <= 3; tick++) {
                final int observedTick = tick;
                helper.runAfterDelay(observedTick, () -> {
                    Vec3 position = villager.position();
                    require(Math.abs(position.y - floorY) <= .08d,
                            "stunned Villager feet must stay at the stone floor top on natural tick "
                                    + observedTick + "; floorY=" + floorY + ", position=" + position);
                    require(position.x >= laneStart.getX() && position.x < laneEnd.getX() + 1d
                                    && position.z >= laneStart.getZ() && position.z < laneStart.getZ() + 1d,
                            "stunned Villager must remain inside its stone-lane footprint on natural tick "
                                    + observedTick + "; position=" + position);
                    require(position.distanceToSqr(lastVillagerPosition[0]) <= 1.25d * 1.25d,
                            "stunned Villager position must advance continuously, not teleport, on natural tick "
                                    + observedTick + "; previous=" + lastVillagerPosition[0] + ", position=" + position);
                    lastVillagerPosition[0] = position;
                });
            }

            // No travel(), explicit move(), or client movement packets occur during these
            // three ticks: only the server's normal entity tick can advance the actors.
            helper.runAfterDelay(3, () -> {
                Vec3 playerDelta = player.position().subtract(playerStart);
                Vec3 villagerDelta = villager.position().subtract(villagerStart);
                Vec3 playerBaselineDelta = playerBaseline.position().subtract(playerBaselineStart);
                Vec3 villagerBaselineDelta = villagerBaseline.position().subtract(villagerBaselineStart);
                Vec3 playerVelocity = player.getDeltaMovement();
                Vec3 villagerVelocity = villager.getDeltaMovement();
                Vec3 playerBaselineVelocity = playerBaseline.getDeltaMovement();
                Vec3 villagerBaselineVelocity = villagerBaseline.getDeltaMovement();
                LOGGER.info("Natural-tick slip diagnostic (3 ticks): FakePlayer baseline start={}, delta={}, "
                                + "velocity={}, onGround={}, localControl={}, immobile={}; stunned start={}, "
                                + "launch={}, delta={}, velocity={}, onGround={}, localControl={}, immobile={}; "
                                + "Villager baseline start={}, delta={}, velocity={}, onGround={}, localControl={}, "
                                + "immobile={}; stunned start={}, launch={}, delta={}, velocity={}, onGround={}, "
                                + "localControl={}, immobile={}",
                        playerBaselineStart, playerBaselineDelta, playerBaselineVelocity, playerBaseline.onGround(),
                        playerBaseline.isControlledByLocalInstance(), isImmobile(playerBaseline),
                        playerStart, playerLaunch, playerDelta, playerVelocity, player.onGround(),
                        player.isControlledByLocalInstance(), isImmobile(player),
                        villagerBaselineStart, villagerBaselineDelta, villagerBaselineVelocity,
                        villagerBaseline.onGround(), villagerBaseline.isControlledByLocalInstance(),
                        isImmobile(villagerBaseline), villagerStart, villagerLaunch, villagerDelta,
                        villagerVelocity, villager.onGround(), villager.isControlledByLocalInstance(),
                        isImmobile(villager));

                // FakePlayer movement is diagnostic only: this synthetic fixture's
                // unstunned FakePlayer does not naturally advance, so it cannot serve
                // as evidence about connected-player movement physics.
                require(CharacterControlSystem.isStunned(villager)
                                && villager.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                        "Villager must retain its stun status throughout natural-tick observation");
                require(villagerVelocity.lengthSqr() > 1.0e-12d,
                        "stunned Villager must retain nonzero velocity; values are in diagnostic log; velocity="
                                + villagerVelocity);
                helper.succeed();
            });
        });
    }

    private static void fillSpaceLube(GameTestHelper helper, BlockPos puddlePos, float units) {
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(puddlePos));
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(ModReagents.createKey("spacelube"), units, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void slowContactDoesNotApplyStatus(GameTestHelper helper) {
        java.util.concurrent.ConcurrentHashMap<java.util.UUID, AtomicInteger> counts = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> observer = event ->
                counts.computeIfAbsent(event.target().getUUID(), ignored -> new AtomicInteger()).incrementAndGet();
        require(SlipSystem.addListener(observer), "test slip observer registration");
        helper.runAfterDelay(29, () -> SlipSystem.removeListener(observer));
        BlockPos floor = new BlockPos(2, 0, 2);
        BlockPos puddlePos = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(puddlePos));
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(ModReagents.createKey("spacelube"), 16f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);
        Villager target = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        helper.runAfterDelay(3, () -> {
            target.setDeltaMovement(.005, 0, 0);
            putAt(target, helper, new BlockPos(2, 1, 2));
            target.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(.05, 0, 0));
            helper.runAfterDelay(1, () -> {
                require(!target.hasData(ModDataAttachments.STATUS_EFFECT.get()), "slow contact must remain inert");
                require(slipCount(counts, target) == 0, "slow contact must not invoke the slip hook");
                target.setDeltaMovement(.12, 0, 0);
                target.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(.1, 0, 0));
                helper.runAfterDelay(1, () -> {
                    require(CharacterControlSystem.isStunned(target),
                            "a slow contact must remain eligible for a later fast move on the same puddle");
                    require(slipCount(counts, target) == 1, "later fast movement must invoke the slip hook once");
                    SlipSystem.removeListener(observer);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cancelledAttemptLatchesUntilVillagerLeavesPuddle(GameTestHelper helper) {
        AtomicInteger slipEvents = new AtomicInteger();
        AtomicInteger gateCalls = new AtomicInteger();
        AtomicReference<UUID> targetId = new AtomicReference<>();
        java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> observer =
                event -> {
                    if (event.target().getUUID().equals(targetId.get())) slipEvents.incrementAndGet();
                };
        java.util.function.Predicate<SlipSystem.SlipAttempt> gate = attempt -> {
            if (!attempt.target().getUUID().equals(targetId.get())) return true;
            gateCalls.incrementAndGet();
            return false;
        };
        require(SlipSystem.addListener(observer), "test slip observer registration");
        require(SlipSystem.addAttemptGate(gate), "test cancellable attempt gate registration");
        // A timeout fallback covers failures that occur before a scheduled callback can clean up.
        helper.runAfterDelay(39, () -> cleanupSlipHooks(observer, gate));

        BlockPos floor = new BlockPos(2, 0, 2);
        BlockPos puddlePos = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(puddlePos));
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(ModReagents.createKey("spacelube"), 16f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);

        Villager target = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        targetId.set(target.getUUID());
        target.setNoAi(true);
        putAt(target, helper, new BlockPos(1, 1, 2));
        require(CharacterControlSystem.canAct(target), "villager must be slip eligible");
        helper.runAfterDelay(2, () -> {
            try {
                target.setDeltaMovement(.12, 0, 0);
                target.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(1, 0, 0));
                helper.runAfterDelay(1, () -> {
                    try {
                        require(gateCalls.get() == 1, "qualifying real movement must invoke the cancellable gate");
                        require(slipEvents.get() == 0, "cancelled attempt must not emit a SlipEvent");
                        require(!target.hasData(ModDataAttachments.STATUS_EFFECT.get())
                                        && !CharacterControlSystem.isStunned(target),
                                "cancelled attempt must not apply status or chemistry response");
                        require(SlipSystem.removeAttemptGate(gate), "temporary attempt gate must unregister");

                        // The target remains inside the same puddle. StepTrigger's latch is upstream
                        // of target/source cancellation, so faster repeated movement cannot retry.
                        target.setDeltaMovement(.12, 0, 0);
                        target.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(.1, 0, 0));
                        helper.runAfterDelay(1, () -> {
                            try {
                                require(gateCalls.get() == 1, "unregistered gate must not be called again");
                                require(slipEvents.get() == 0
                                                && !target.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                                        "same-contact movement must remain latched after cancellation");

                                target.setDeltaMovement(-.12, 0, 0);
                                target.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(-2, 0, 0));
                                helper.runAfterDelay(2, () -> {
                                    try {
                                        target.setDeltaMovement(.12, 0, 0);
                                        target.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(2, 0, 0));
                                        helper.runAfterDelay(1, () -> {
                                            try {
                                                require(slipEvents.get() == 1,
                                                        "leaving and re-entering must admit exactly one slip");
                                                require(CharacterControlSystem.isStunned(target)
                                                                && target.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                                                        "re-entered slip must apply its timed stun status");
                                                helper.succeed();
                                            } finally {
                                                cleanupSlipHooks(observer, gate);
                                            }
                                        });
                                    } catch (RuntimeException | Error exception) {
                                        cleanupSlipHooks(observer, gate);
                                        throw exception;
                                    }
                                });
                            } catch (RuntimeException | Error exception) {
                                cleanupSlipHooks(observer, gate);
                                throw exception;
                            }
                        });
                    } catch (RuntimeException | Error exception) {
                        cleanupSlipHooks(observer, gate);
                        throw exception;
                    }
                });
            } catch (RuntimeException | Error exception) {
                cleanupSlipHooks(observer, gate);
                throw exception;
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void subsequentSuperSlipperyPuddleObservesPreEventSlidingState(GameTestHelper helper) {
        BlockPos firstFloor = new BlockPos(2, 0, 2);
        BlockPos firstPos = firstFloor.above();
        BlockPos secondFloor = new BlockPos(4, 0, 2);
        BlockPos secondPos = secondFloor.above();
        // Put the actor outside both sources before either solution or observer exists.
        // Reactive Touch is a production SlipEvent listener and can consume source
        // solution on incidental movement during fixture construction.
        Villager target = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 2));
        target.setNoAi(true);
        require(CharacterControlSystem.canAct(target), "bound villager must be slip eligible");
        UUID targetId = target.getUUID();
        helper.setBlock(firstFloor, Blocks.STONE);
        helper.setBlock(secondFloor, Blocks.STONE);
        helper.setBlock(firstPos, ModBlocks.PUDDLE.get().defaultBlockState());
        helper.setBlock(secondPos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity first = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(firstPos));
        PuddleBlockEntity second = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(secondPos));
        ReagentAttachment firstSolution = new ReagentAttachment();
        firstSolution.specificAdd(ModReagents.createKey("spacelube"), 16f, first.getCapacity());
        MS14Provider.update(first, MS14Bridges.REAGENT, firstSolution);
        ReagentAttachment superSlip = new ReagentAttachment();
        superSlip.specificAdd(ModReagents.createKey("spacelube"), 16f, second.getCapacity());
        MS14Provider.update(second, MS14Bridges.REAGENT, superSlip);

        java.util.List<com.juicyslew.moonstation14.ms14.slip.SlipEvent> events = new java.util.ArrayList<>();
        java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> observer = event -> {
            if (event.target().getUUID().equals(targetId)) events.add(event);
        };
        require(SlipSystem.addListener(observer), "test slip observer registration");
        helper.runAfterDelay(29, () -> SlipSystem.removeListener(observer));
        helper.runAfterDelay(2, () -> {
            putAt(target, helper, firstPos);
            target.setDeltaMovement(.2, 0, 0);
            SlipSystem.onPuddleContact(helper.getLevel(), helper.absolutePos(firstPos), target);
            require(events.size() == 1 && events.get(0).target() == target && !events.get(0).wasSliding(),
                    "exactly one observer event must precede starting the first sliding response; count="
                            + events.size() + ", position=" + target.position());
            require(!events.get(0).wasSliding() && SlipSystem.isSliding(target),
                    "first event must see the absent pre-event flag and activate it after listeners");
            var firstSliding = target.getExistingDataOrNull(ModDataAttachments.SLIDING.get());
            target.setPos(helper.absolutePos(secondPos).getX() + .5,
                    helper.absolutePos(secondPos).getY(), helper.absolutePos(secondPos).getZ() + .5);
            target.setDeltaMovement(.2, 0, 0);
            SlipSystem.reconcileTarget(target);
            SlipSystem.onPuddleContact(helper.getLevel(), helper.absolutePos(secondPos), target);
            require(events.size() == 2 && events.get(1).wasSliding() && target.hasData(ModDataAttachments.SLIDING.get())
                            && target.getExistingDataOrNull(ModDataAttachments.SLIDING.get()) == firstSliding,
                    "second qualifying super-slippery puddle observes prior sliding state");
            // Reconciliation after knockdown expires removes the volatile marker; no status read mutates it.
            require(target.hasData(ModDataAttachments.STATUS_EFFECT.get()), "slip status exists before expiry");
            com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem.clearAll(
                    ((com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait) target).toHandleSelf(),
                    helper.getLevel());
            SlipSystem.reconcileTarget(target);
            require(!SlipSystem.isSliding(target) && !target.hasData(ModDataAttachments.SLIDING.get()),
                    "expired knockdown removes sliding attachment");
            SlipSystem.removeListener(observer);
            helper.succeed();
        });
    }

    private static void putAt(net.minecraft.world.entity.Entity entity, GameTestHelper helper, BlockPos position) {
        BlockPos absolute = helper.absolutePos(position);
        entity.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
    }

    private static boolean isImmobile(net.minecraft.world.entity.LivingEntity entity) {
        // Vanilla exposes LivingEntity.isImmobile() as protected. Reflect only for
        // this diagnostic observation; do not alter the entity or its tick path.
        try {
            java.lang.reflect.Method method = net.minecraft.world.entity.LivingEntity.class
                    .getDeclaredMethod("isImmobile");
            method.setAccessible(true);
            return (boolean) method.invoke(entity);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not observe LivingEntity.isImmobile", exception);
        }
    }

    private static int slipCount(java.util.Map<java.util.UUID, AtomicInteger> counts,
                                 net.minecraft.world.entity.Entity entity) {
        AtomicInteger count = counts.get(entity.getUUID());
        return count == null ? 0 : count.get();
    }

    private static void cleanupSlipHooks(
            java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> observer,
            java.util.function.Predicate<SlipSystem.SlipAttempt> gate) {
        SlipSystem.removeAttemptGate(gate);
        SlipSystem.removeListener(observer);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
