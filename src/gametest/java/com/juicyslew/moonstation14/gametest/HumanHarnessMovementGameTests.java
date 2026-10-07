package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

@PrefixGameTestTemplate(false)
@GameTestTags({"movement", "character"})
public final class HumanHarnessMovementGameTests {
    private static final double DT = .05d;

    private HumanHarnessMovementGameTests() { }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 40)
    @GameTestTags({"merge-main-regression"})
    public static void disposalContainmentLookupAndDeadBodyRelease(GameTestHelper helper) {
        var first = BodyContainmentService.forLevel(helper.getLevel());
        require(first == BodyContainmentService.forLevel(helper.getLevel()), "level lookup must share service identity");
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
        waitForEntity(helper, body, "initial bind", () -> {
        body.setInvisible(false);
        body.noPhysics = false;
        body.setNoGravity(false);
        require(CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID),
                "loaded Villager is enrolled as the supported human character");
        var owner = owner(helper, body.getUUID(), body.position(), ContainedBodyOwner.Stage.TRANSIT,
                new AtomicInteger());
        require(first.bind(body, owner), "loaded body should bind");
        require(((com.juicyslew.moonstation14.ms14.containment.ContainedBodyVisual) body)
                .moonstation14$isContainedBodyVisual(), "containment marker must sync true while bound");
        require(DamageSystem.applyHealthChange(body, java.util.Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                        == DamageSystem.Result.APPLIED && MobStateSystem.isDead(body),
                "typed damage retains the supported human in medical Dead state");
        require(first.ownerOf(body).orElse(null) == owner, "retained medical corpse remains contained");
        require(((com.juicyslew.moonstation14.ms14.containment.ContainedBodyVisual) body)
                .moonstation14$isContainedBodyVisual(), "retained corpse keeps containment presentation");
        require(body.isInvisible() && body.noPhysics && body.isNoGravity(), "retained corpse keeps containment flags");

        var unsupported = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 1, 3));
        unsupported.setNoAi(true);
        unsupported.setInvisible(false);
        unsupported.noPhysics = false;
        unsupported.setNoGravity(false);
        var ordinaryOwner = owner(helper, unsupported.getUUID(), unsupported.position(),
                ContainedBodyOwner.Stage.TRANSIT, new AtomicInteger());
        require(first.bind(unsupported, ordinaryOwner), "unsupported native mob can bind for cleanup coverage");
        unsupported.setHealth(0);
        require(first.ownerOf(unsupported).isEmpty(), "unsupported native dead mob is released");
        require(!((com.juicyslew.moonstation14.ms14.containment.ContainedBodyVisual) unsupported)
                .moonstation14$isContainedBodyVisual(), "unsupported dead mob release clears presentation marker");
        require(!unsupported.isInvisible() && !unsupported.noPhysics && !unsupported.isNoGravity(),
                "unsupported dead mob release restores containment flags");
        helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 40)
    public static void disposalContainmentExportsAndRestoresContainedBodyFlags(GameTestHelper helper) {
        var service = BodyContainmentService.forLevel(helper.getLevel());
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
        waitForEntity(helper, body, "initial bind", () -> {
        body.setInvisible(false);
        body.noPhysics = false;
        body.setNoGravity(false);
        Vec3 position = body.position();
        var escapes = new AtomicInteger();
        var owner = owner(helper, body.getUUID(), position, ContainedBodyOwner.Stage.TRANSIT, escapes);
        var next = owner(helper, body.getUUID(), position, ContainedBodyOwner.Stage.UNIT, escapes);
        require(service.bind(body, owner), "initial bind must succeed");
        var flags = service.export(body, owner).orElseThrow();
        require(!flags.invisible() && !flags.noPhysics() && !flags.noGravity(), "export must capture pre-entry flags");
        require(service.transfer(body, owner, next), "transfer must preserve membership");
        require(service.export(body, next).orElseThrow().equals(flags), "transfer must preserve original flags");
        require(service.unbind(body, next), "transferred membership must unbind");
        body.setInvisible(true);
        body.noPhysics = true;
        body.setNoGravity(true);
        require(service.bindRestored(body, owner, flags), "restored bind must accept persisted flags");
        require(service.unbind(body, owner), "restored membership must unbind");
        require(!body.isInvisible() && !body.noPhysics && !body.isNoGravity(), "restored flags must be exact originals");
        helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 40)
    public static void disposalContainmentTickFollowsOwnerWithoutUnitTick(GameTestHelper helper) {
        var service = BodyContainmentService.forLevel(helper.getLevel());
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
        waitForEntity(helper, body, "NPC bind", () -> {
        Vec3 destination = helper.absolutePos(new BlockPos(7, 1, 7)).getCenter();
        var owner = owner(helper, body.getUUID(), destination, ContainedBodyOwner.Stage.TRANSIT,
                new AtomicInteger());
        require(service.bind(body, owner), "NPC body should bind to holder owner");
        service.tick();
        require(body.position().distanceToSqr(destination) < 1.0e-6,
                "single level-service tick must pin a holder-owned NPC independently of unit ticks");
        require(service.unbind(body, owner), "test fixture releases only its own membership");
        helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void realHumanVillagerMovesAndJumpsThroughHarnessMotor(GameTestHelper helper) {
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }

        BlockPos spawn = new BlockPos(3, 1, 3);
        Villager villager = helper.spawn(EntityType.VILLAGER, spawn);
        villager.setNoAi(true);
        require(CharacterIdentitySystem.resolve(villager)
                        .filter(ModCharacters.require(helper.getLevel(), ModCharacters.HUMAN_ID)::equals)
                        .isPresent(),
                "real Villager must resolve its enrolled moonstation14:human character before motor use");

        BlockPos absoluteSpawn = helper.absolutePos(spawn);
        double floorY = absoluteSpawn.getY();
        placeAndGround(villager, absoluteSpawn.getX() + .5d, floorY, absoluteSpawn.getZ() + .5d);
        require(villager.onGround(), "real Villager must begin grounded on stone");

        GroundedHarnessMotor motor = new GroundedHarnessMotor(com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.fromCharacterData(
                ModCharacters.require(helper.getLevel(), ModCharacters.HUMAN_ID)));
        double startZ = villager.position().z();
        for (int tick = 0; tick < 20; tick++) {
            CharacterMovementState result = tick(villager, motor, 0, 1_000, false, 0d,
                    "forward-" + tick);
            require(result.onGround() && villager.onGround(),
                    "forward movement must retain stable stone support at tick " + tick);
            require(Math.abs(villager.position().y() - floorY) < 1e-9d,
                    "forward movement must remain at floor height at tick " + tick);
            require(villager.position().z() > startZ, "forward input must move the real Villager");
            require(Math.hypot(villager.getDeltaMovement().x(), villager.getDeltaMovement().z())
                            <= 2.5d + 1e-9d,
                    "observed horizontal speed must remain bounded by HUMAN speed");
        }

        double beforeJumpY = villager.position().y();
        CharacterMovementState jump = tick(villager, motor, 0, 0, true, 0d, "grounded-jump");
        double rise = villager.position().y() - beforeJumpY;
        require(Math.abs(rise - .42d) <= 1e-9d,
                "first grounded jump tick must rise by approximately .42 blocks; rise=" + rise);
        require(!jump.onGround() && !villager.onGround(), "jump result and real Villager must be airborne");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void sharedWorldStepAppliesHumanAndPigProfiles(GameTestHelper helper) {
        for (int x = 1; x <= 20; x++) {
            for (int z = 1; z <= 20; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        BlockPos humanSpawn = new BlockPos(3, 1, 3);
        BlockPos pigSpawn = new BlockPos(6, 1, 6);
        Villager human = helper.spawn(EntityType.VILLAGER, humanSpawn);
        var pig = helper.spawn(EntityType.PIG, pigSpawn);
        BlockPos absoluteHuman = helper.absolutePos(humanSpawn);
        BlockPos absolutePig = helper.absolutePos(pigSpawn);
        placeAndGround(human, absoluteHuman.getX() + .5d, absoluteHuman.getY(), absoluteHuman.getZ() + .5d);
        placeAndGround(pig, absolutePig.getX() + .5d, absolutePig.getY(), absolutePig.getZ() + .5d);
        require(human.onGround() && pig.onGround(), "both real mobs begin grounded on stone");
        human.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        pig.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        var humanLease = GroundedHarnessLease.tryAcquire(human).orElseThrow();
        var pigLease = GroundedHarnessLease.tryAcquire(pig).orElseThrow();
        GroundedHarnessWorldStep sharedStep = new GroundedHarnessWorldStep();
        // Keep the probe centered and before any test-structure boundary; acceleration reaches cap quickly.
        for (int tick = 0; tick < 5; tick++) {
            require(sharedStep.step(human, 0, 1000, false, false, 0f).isPresent(),
                    "shared world step accepts owned HUMAN Villager");
            require(sharedStep.step(pig, 0, 1000, false, false, 0f).isPresent(),
                    "same shared world step accepts owned Pig");
        }
        require(Math.abs(human.getDeltaMovement().z * 20d - 2.5d) < 1e-8,
                "shared adapter resolves HUMAN walk policy at 2.5 blocks/second");
        require(Math.abs(pig.getDeltaMovement().z * 20d - 4d) < .02d,
                "shared adapter resolves Pig walk policy at 4 blocks/second; actual="
                        + pig.getDeltaMovement().z * 20d + ", position=" + pig.position()
                        + ", grounded=" + pig.onGround());
        humanLease.close();
        pigLease.close();
        helper.succeed();
    }


    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 40)
    public static void ownedWorldStepAllowsRealDescendingLandingOntoResolvedDisposalPipe(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos slab = new BlockPos(10, 1, 12);
        BlockPos pipe = new BlockPos(11, 1, 12);
        for (int x = 8; x <= 12; x++) helper.setBlock(new BlockPos(x, 0, 12), Blocks.STONE);
        helper.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        helper.setBlock(pipe, ModBlocks.DISPOSAL_TUBE.get());
        var tubeRecord = new DisposalTubeRecord(java.util.UUID.randomUUID(), helper.absolutePos(pipe),
                "moonstation14:disposal_pipe", Direction.NORTH, true, "", "", 0);
        require(DisposalTubeWorldData.forMutation(level).add(level, tubeRecord,
                        pos -> level.getBlockState(pos).is(ModBlocks.DISPOSAL_TUBE.get())),
                "real resolved straight pipe record installs in the physical collision block");
        var resolved = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals()
                .get(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.id("disposal_pipe"));
        require(resolved != null && resolved.tube() != null, "straight pipe resolved prototype is available");
        require(!ModBlocks.DISPOSAL_TUBE.get().getCollisionShape(level.getBlockState(helper.absolutePos(pipe)), level,
                        helper.absolutePos(pipe), net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty(),
                "installed record must supply the tube's actual resolved collision shape");

        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 1, 12));
        body.setNoAi(true);
        body.setPos(helper.absolutePos(slab).getX() + .70d, helper.absolutePos(slab).getY() + .52d,
                helper.absolutePos(slab).getZ() + .5d);
        body.setDeltaMovement(.20d, -.06d, 0d);
        require(!body.onGround(), "descending setup must begin airborne just above slab top");
        body.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        var lease = GroundedHarnessLease.tryAcquire(body).orElseThrow();
        try {
            Vec3 before = body.position();
            double requestedDown = body.getDeltaMovement().y;
            require(body.hurt(level.damageSources().generic(), 1f), "real generic damage must be accepted during airborne descent");
            require(body.hurtTime > 0, "damage must leave a live hurt window before movement");
            body.setDeltaMovement(.20d, -.06d, 0d);
            var accepted = new GroundedHarnessWorldStep().step(body, 0, 0, false, false, 0f).orElseThrow(
                    () -> new GameTestAssertException("actual Entity.move descending pipe landing was rejected"));
            double actualY = body.position().y - before.y;
            require(accepted.onGround() && body.onGround(), "landing result and entity must be grounded");
            require(actualY > 0d && actualY > requestedDown,
                    "vanilla descending collision must step upward beyond requested downward movement; actualY=" + actualY);
            require(actualY <= body.maxUpStep() + 1e-6d, "actual pipe step must remain within configured max step: " + actualY);
            require(body.position().x > before.x, "body must make horizontal progress onto tube");
            require(Double.isFinite(body.position().x) && Double.isFinite(body.position().y)
                            && Double.isFinite(body.position().z), "resolved body position remains finite");
        } finally {
            lease.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 40)
    public static void ownedWorldStepAllowsDescendingLandingFromResolvedPipeOntoResolvedTrunk(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos previous = new BlockPos(10, 1, 12);
        BlockPos next = new BlockPos(11, 1, 12);
        for (int x = 8; x <= 13; x++) helper.setBlock(new BlockPos(x, 0, 12), Blocks.STONE);
        level.setBlock(helper.absolutePos(previous), ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(next), ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
        var worldData = DisposalTubeWorldData.forMutation(level);
        var previousRecord = new DisposalTubeRecord(java.util.UUID.randomUUID(), helper.absolutePos(previous),
                "moonstation14:disposal_pipe", Direction.EAST, true, "", "", 0);
        var nextRecord = new DisposalTubeRecord(java.util.UUID.randomUUID(), helper.absolutePos(next),
                "moonstation14:disposal_trunk", Direction.WEST, true, "", "", 0);
        require(worldData.add(level, previousRecord, pos -> level.getBlockState(pos).is(ModBlocks.DISPOSAL_TUBE.get()))
                        && worldData.add(level, nextRecord, pos -> level.getBlockState(pos).is(ModBlocks.DISPOSAL_TUBE.get())),
                "both adjacent physical tube hosts must have live records");
        require(com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals()
                        .get(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.id("disposal_pipe")) != null
                        && com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals()
                        .get(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.id("disposal_trunk")) != null,
                "both straight pipe and trunk records resolve prototypes");

        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 1, 12));
        body.setNoAi(true);
        double topOfPrevious = helper.absolutePos(previous).getY() + .9375d;
        body.setPos(helper.absolutePos(previous).getX() + .70d, topOfPrevious + .02d,
                helper.absolutePos(previous).getZ() + .5d);
        body.setDeltaMovement(.20d, -.06d, 0d);
        require(!body.onGround(), "pipe-to-trunk landing begins descending and airborne");
        body.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        var lease = GroundedHarnessLease.tryAcquire(body).orElseThrow();
        try {
            Vec3 before = body.position();
            require(body.hurt(level.damageSources().generic(), 1f), "real generic damage must be accepted before pipe-to-trunk step");
            require(body.hurtTime > 0, "pipe-to-trunk collision must run inside the actual damage hurt window");
            // Keep the intended descending contact deterministic after damage handling.
            body.setDeltaMovement(.20d, -.06d, 0d);
            var accepted = new GroundedHarnessWorldStep().step(body, 0, 0, false, false, 0f).orElseThrow(
                    () -> new GameTestAssertException("actual Entity.move pipe-to-trunk landing was rejected"));
            double rise = body.position().y - before.y;
            require(accepted.onGround() && body.onGround(), "actual pipe-to-trunk collision must land the entity");
            require(rise > 0d && rise <= body.maxUpStep() + 1e-6d,
                    "trunk's inherited resolved geometry must produce a positive bounded landing rise: " + rise);
            require(body.position().x > before.x, "landing must make horizontal progress into trunk collision shape");
            require(Math.abs(body.position().y - (helper.absolutePos(next).getY() + 1d)) < .01d,
                    "final feet must resolve to trunk top");
        } finally {
            lease.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 40)
    public static void containedOwnedMovementIsStationaryAndTransitCannotEscape(GameTestHelper helper) {
        for (int x = 1; x <= 9; x++) for (int z = 1; z <= 9; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
        waitForEntity(helper, body, "lease acquisition and containment admission", () -> {
        body.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        var lease = GroundedHarnessLease.tryAcquire(body).orElseThrow();
        var containment = BodyContainmentService.forLevel(helper.getLevel());
        var destination = new Vec3(helper.absolutePos(new BlockPos(6, 1, 6)).getX() + .5,
                helper.absolutePos(new BlockPos(6, 1, 6)).getY(), helper.absolutePos(new BlockPos(6, 1, 6)).getZ() + .5);
        java.util.concurrent.atomic.AtomicInteger escapes = new java.util.concurrent.atomic.AtomicInteger();
        ContainedBodyOwner transit = owner(helper, body.getUUID(), destination, ContainedBodyOwner.Stage.TRANSIT, escapes);
        require(containment.bind(body, transit), "exact loaded body should bind to transit owner");
        var accepted = new GroundedHarnessWorldStep().step(body, 0, 1000, true, true, 0f).orElseThrow();
        require(accepted.position().z() == destination.z && body.position().equals(destination),
                "contained movement returns owner-pinned authoritative state rather than ordinary walk result");
        require(accepted.velocity().equals(MovementVector.ZERO), "contained accepted state must be stationary");
        require(escapes.get() == 0, "transit input must never invoke voluntary escape");
        containment.unbind(body, transit);

        ContainedBodyOwner unit = owner(helper, body.getUUID(), destination, ContainedBodyOwner.Stage.UNIT, escapes);
        require(containment.bind(body, unit), "body can bind again after exact owner unbind");
        var idleRun = new GroundedHarnessWorldStep().step(body, 0, 0, false, true, 0f).orElseThrow();
        require(escapes.get() == 0, "idle sprint/run intent must not invoke voluntary escape");
        require(containment.ownerOf(body).orElseThrow() == unit && idleRun.position().z() == destination.z,
                "idle run intent keeps body bound and owner-pinned");
        new GroundedHarnessWorldStep().step(body, 0, 0, false, false, 0f).orElseThrow();
        require(escapes.get() == 0, "look-only input must not invoke voluntary escape");
        new GroundedHarnessWorldStep().step(body, 1000, 0, false, false, 0f).orElseThrow();
        require(escapes.get() == 1, "unit directional movement is delegated to owner escape callback");
        containment.unbind(body, unit);

        Vec3 safeExit = new Vec3(destination.x + 2, destination.y, destination.z + 2);
        ContainedBodyOwner[] exitingUnitRef = new ContainedBodyOwner[1];
        ContainedBodyOwner exitingUnit = owner(helper, body.getUUID(), destination,
                ContainedBodyOwner.Stage.UNIT, escapes, escapedBody -> {
                    require(containment.unbind(escapedBody, exitingUnitRef[0]), "escape callback unbinds exact owner");
                    escapedBody.teleportTo(safeExit.x, safeExit.y, safeExit.z);
                });
        exitingUnitRef[0] = exitingUnit;
        require(containment.bind(body, exitingUnit), "body binds to self-exiting unit");
        var escaped = new GroundedHarnessWorldStep().step(body, 1000, 0, false, false, 0f).orElseThrow();
        require(escaped.position().equals(vector(safeExit)) && body.position().equals(safeExit),
                "accepted movement state preserves actual safe-exit position instead of stale unit pin");
        require(containment.ownerOf(body).isEmpty(), "successful escape leaves no containment owner");
        lease.close();
        helper.succeed();
        });
    }

    private static ContainedBodyOwner owner(GameTestHelper helper, java.util.UUID id, Vec3 position,
            ContainedBodyOwner.Stage stage, java.util.concurrent.atomic.AtomicInteger escapes) {
        return owner(helper, id, position, stage, escapes, ignored -> { });
    }

    private static ContainedBodyOwner owner(GameTestHelper helper, java.util.UUID id, Vec3 position,
            ContainedBodyOwner.Stage stage, java.util.concurrent.atomic.AtomicInteger escapes,
            java.util.function.Consumer<net.minecraft.world.entity.LivingEntity> escapeAction) {
        return new ContainedBodyOwner() {
            public java.util.UUID identity() { return id; }
            public net.minecraft.server.level.ServerLevel level() { return helper.getLevel(); }
            public Vec3 position() { return position; }
            public Stage stage() { return stage; }
            public boolean isValid() { return true; }
            public boolean tryEscape(net.minecraft.world.entity.LivingEntity body) {
                escapes.incrementAndGet(); escapeAction.accept(body); return true;
            }
        };
    }

    private static CharacterMovementState tick(Villager villager, GroundedHarnessMotor motor,
                                                int wishX, int wishZ, boolean jump, double yaw, String label) {
        Vec3 before = villager.position();
        Vec3 delta = villager.getDeltaMovement();
        CharacterMovementState state = new CharacterMovementState(vector(before),
                new MovementVector(delta.x * 20d, delta.y * 20d, delta.z * 20d), villager.onGround());
        AtomicInteger calls = new AtomicInteger();
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            require(calls.incrementAndGet() == 1, label + " must resolve collision exactly once");
            Vec3 actualBefore = villager.position();
            villager.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
            Vec3 actualAfter = villager.position();
            return new MovementCollisionResolver.CollisionResult(
                    new MovementVector(actualAfter.x - actualBefore.x, actualAfter.y - actualBefore.y,
                            actualAfter.z - actualBefore.z), villager.onGround());
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, villager.maxUpStep(), resolver);
        CharacterMovementState result = motor.tick(state, wishX, wishZ, jump, false, yaw, environment, false);
        require(calls.get() == 1, label + " must invoke one real entity move");
        Vec3 after = villager.position();
        require(close(result.position().x(), after.x) && close(result.position().y(), after.y)
                        && close(result.position().z(), after.z),
                label + " motor position must match the single real collision move");
        require(result.onGround() == villager.onGround(), label + " ground result must match the real Villager");
        villager.setDeltaMovement(result.velocity().x() / 20d, result.velocity().y() / 20d,
                result.velocity().z() / 20d);
        return result;
    }

    private static void placeAndGround(Mob mob, double x, double y, double z) {
        mob.setPos(x, y, z);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.move(MoverType.SELF, new Vec3(0d, -.05d, 0d));
    }

    private static void waitForEntity(GameTestHelper helper, net.minecraft.world.entity.Entity entity,
                                     String operation, Runnable action) {
        waitForEntity(helper, entity, operation, action, 0);
    }

    private static void waitForEntity(GameTestHelper helper, net.minecraft.world.entity.Entity entity,
                                      String operation, Runnable action, int waited) {
        var level = helper.getLevel();
        var chunk = level.getChunkSource().getChunkNow(entity.chunkPosition().x, entity.chunkPosition().z);
        boolean full = chunk != null && chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);
        boolean exact = level.getEntity(entity.getUUID()) == entity;
        if (entity.isAddedToLevel() && entity.isAlive() && !entity.isRemoved() && exact && full) {
            action.run();
            return;
        }
        if (waited >= 10) {
            throw new GameTestAssertException(operation + " entity readiness timeout: added=" + entity.isAddedToLevel()
                    + ", alive=" + entity.isAlive() + ", removed=" + entity.isRemoved()
                    + ", exactUuidLookup=" + exact + ", chunk=" + entity.chunkPosition()
                    + ", chunkStatus=" + (chunk == null ? "missing" : chunk.getFullStatus()));
        }
        helper.runAfterDelay(1, () -> waitForEntity(helper, entity, operation, action, waited + 1));
    }

    private static MovementVector vector(Vec3 value) {
        return new MovementVector(value.x, value.y, value.z);
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) <= 1e-9d;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
