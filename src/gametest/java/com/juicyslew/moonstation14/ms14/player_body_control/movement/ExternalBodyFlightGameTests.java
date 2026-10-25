package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.disposal.transport.LoadedWorldDisposalRouting;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.lung.LungSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import com.juicyslew.moonstation14.gametest.support.CommittedMouseRoleSetup;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.entities.MouseMob;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"movement", "character"})
@PrefixGameTestTemplate(false)
public final class ExternalBodyFlightGameTests {
    private ExternalBodyFlightGameTests() { }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_external_flight", timeoutTicks = 40)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal"})
    public static void disposalEjectionPassivelyMovesBoundBodyAndExpiresFlight(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 1, 3));
        bindAndPrepare(body, level);
        UUID id = body.getUUID();
        Object hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        Vec3 before = body.position();
        var routing = new LoadedWorldDisposalRouting(level);
        require(routing.ejectBody(body, helper.absolutePos(new BlockPos(4, 1, 2)), Direction.WEST, 10), "real disposal routing ejects body");
        require(ExternalBodyFlight.active(body) && body.getDeltaMovement().x < 0, "ejection starts bounded westward flight");
        helper.runAfterDelay(1, () -> {
            require(body.getX() < before.x - .2, "world post hook passively advances native physics without input");
            require(body.getUUID().equals(id) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands,
                    "flight preserves body identity and hands");
            require(!body.isInvisible() && !body.noPhysics, "flight body remains an ordinary collidable visible entity");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 6, () -> {
                require(!ExternalBodyFlight.active(body) && ExternalBodyFlight.remainingTicks(body) == 0,
                        "flight lease expires on native world clock");
                require(body.getX() < before.x - 2.3, "body traveled through passive flight ticks");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_external_flight", timeoutTicks = 40)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"containment"})
    public static void worldStepDeduplicatesFlightAndContainmentCancelsIt(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 1, 3));
        bindAndPrepare(body, level);
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        ExternalBodyFlight.launch(body, new Vec3(.5, 0, 0), 6);
        var step = new com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep();
        step.step(body, 0, 0, false, false, 0).orElseThrow();
        Vec3 advanced = body.position();
        step.step(body, 0, 0, false, true, 180).orElseThrow();
        require(body.position().equals(advanced), "second same-tick input step cannot advance flight twice");
        ExternalBodyFlight.tick(level);
        require(body.position().equals(advanced), "world flight hook deduplicates an already advanced body");
        ExternalBodyFlight.cancel(body);
        require(!ExternalBodyFlight.active(body), "explicit ownership release cancels external flight");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_external_flight", timeoutTicks = 60)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal-regression"})
    public static void airborneFlightExpiryContinuesPassiveGravityToLanding(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 6, 4));
        bindAndPrepare(body, level);
        UUID id = body.getUUID();
        Object binding = body.playerCharacterBinding();
        Object hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        require(!body.isNoGravity() && !body.onGround(), "human starts airborne with ordinary gravity");
        ExternalBodyFlight.launch(body, new Vec3(.5, 0, 0), 6);
        helper.runAfterDelay(6, () -> {
            require(ExternalBodyFlight.remainingTicks(body) == 0, "bounded flight expires after six world ticks");
            double yAtExpiry = body.getY();
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2, () -> {
                require(body.getY() < yAtExpiry - .05 && body.getDeltaMovement().y < 0,
                        "ordinary passive gravity continues after the flight lease expires");
                pollForLanding(helper, body, id, binding, hands, 0);
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear",
            batch = "disposals_external_flight", timeoutTicks = 30)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"npc-flight"})
    public static void forcedFlightContinuesForStunnedBodyWithoutVoluntaryPermission(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        var body = PlayerCharacterHarnessRegistration.getEntityType().create(level);
        require(body != null, "bound character harness is created");
        BlockPos spawn = helper.absolutePos(new BlockPos(4, 1, 3));
        body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        require(level.addFreshEntity(body), "body joins level");
        bindAndPrepare(body, level);
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.applyStun(body, 20),
                "resolved human accepts typed stun");
        require(!com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canMove(body),
                "stun blocks voluntary movement permission");
        Vec3 start = body.position();
        ExternalBodyFlight.launch(body, new Vec3(.2, .15, 0), 6);
        helper.runAfterDelay(2, () -> {
            require(body.position().distanceToSqr(start) > .01,
                    "external flight still advances the stunned body's physical momentum");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear",
            batch = "disposals_external_flight", timeoutTicks = 50)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"npc-flight", "flight-packet-trace"})
    public static void airborneLeaseSamplesServerFlightAndVanillaTrackingPackets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        var body = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(body != null, "registered mouse constructor succeeds");
        body.setPos(helper.absolutePos(new BlockPos(4, 30, 4)).getX() + .5,
                helper.absolutePos(new BlockPos(4, 30, 4)).getY(),
                helper.absolutePos(new BlockPos(4, 30, 4)).getZ() + .5);
        body.setNoAi(true);
        require(level.addFreshEntity(body), "ordinary mouse joins level");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(body, level);
        require(com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(body).isPresent(),
                "mouse flight uses its normally resolved character profile");
        require(!body.isNoGravity() && !body.onGround(), "flight probe starts airborne with gravity");
        require(ExternalBodyFlight.canLaunch(body), "normally enrolled mouse is a shared flight consumer");
        require(body.getType().updateInterval() == 1, "mouse tracking cadence is one tick for its whole lifecycle");

        List<String> emitted = new ArrayList<>();
        List<Integer> movementPacketsPerTick = new ArrayList<>();
        ServerEntity tracker = new ServerEntity(level, body, body.getType().updateInterval(),
                body.getType().trackDeltas(), packet -> emitted.add(packet.getClass().getSimpleName()));
        long launchTick = level.getGameTime();
        long endTick = launchTick + 15;
        ExternalBodyFlight.launch(body, new Vec3(.5, .35, 0), 15);
        List<String> samples = new ArrayList<>();
        double startY = body.getY();
        recordFlightSample(helper, body, tracker, emitted, movementPacketsPerTick, samples, startY,
                new Vec3(.5, .35, 0), body.position(), launchTick, endTick, 0);
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear",
            batch = "disposals_external_flight", timeoutTicks = 120)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"movement", "jump-regression", "mouse-packet-trace"})
    public static void committedMouseHeldJumpSamplesAuthoritativeLandingAndVanillaPackets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        var fixture = RegisteredServerPlayerFixture.register(level, "held-jump-mouse");
        MouseMob mouse = (MouseMob) com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "canonical mouse constructed");
        BlockPos spawn = helper.absolutePos(new BlockPos(4, 1, 4));
        mouse.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        require(level.addFreshEntity(mouse), "mouse joins ordinary level before canonical role adoption");
        CommittedMouseRoleSetup.start(helper, fixture, mouse, committed -> {
            long epoch = LifecycleCharacterSessionControl.activeCharacterBody(fixture.player()).orElseThrow().epoch();
            List<String> packetTrace = new ArrayList<>();
            ServerEntity tracker = new ServerEntity(level, committed, committed.getType().updateInterval(),
                    committed.getType().trackDeltas(), packet -> packetTrace.add(packet.getClass().getSimpleName()
                            + "@y=" + committed.getY() + "@ground=" + committed.onGround()));
            heldMouseJumpSample(helper, fixture, committed, epoch, tracker, packetTrace,
                    new ArrayList<>(), spawn.getY(), 1);
        });
    }

    private static void heldMouseJumpSample(GameTestHelper helper, RegisteredServerPlayerFixture fixture, MouseMob mouse,
            long epoch, ServerEntity tracker, List<String> packetTrace, List<String> samples, double floorY, int sequence) {
        helper.runAfterDelay(1, () -> {
            boolean groundBefore = mouse.onGround();
            double yBefore = mouse.getY();
            RegisteredPlayerReadyDriver.submitAcceptedIntent(fixture, new GhostControlPayloads.Intent(epoch, sequence,
                    (short) 0, (short) 0, (byte) 0, GhostControlPayloads.BUTTON_JUMP,
                    mouse.getYRot(), mouse.getXRot()));
            Vec3 afterAcceptedStep = mouse.position();
            Vec3 velocityAfterAcceptedStep = mouse.getDeltaMovement();
            RegisteredPlayerReadyDriver.tickWithoutNewIntent(fixture);
            require(mouse.position().equals(afterAcceptedStep)
                            && mouse.getDeltaMovement().equals(velocityAfterAcceptedStep),
                    "already-consumed registered-player intent is not replayed by another post-tick owner invocation");
            int packetStart = packetTrace.size();
            tracker.sendChanges();
            boolean jumpStart = groundBefore && mouse.getDeltaMovement().y > 0;
            samples.add("seq=" + sequence + " yBefore=" + yBefore + " y=" + mouse.getY()
                    + " groundBefore=" + groundBefore + " groundAfter=" + mouse.onGround()
                    + " jumpStart=" + jumpStart + " packets=" + List.copyOf(packetTrace.subList(packetStart, packetTrace.size())));
            if (sequence < 70) {
                heldMouseJumpSample(helper, fixture, mouse, epoch, tracker, packetTrace, samples, floorY, sequence + 1);
                return;
            }
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("Committed mouse held-jump server/vanilla trace: {}", samples);
            long starts = samples.stream().filter(row -> row.contains("jumpStart=true")).count();
            long landings = samples.stream().filter(row -> row.contains("groundAfter=true")).count();
            require(starts >= 3, "committed mouse held jump starts=" + starts + " expected>=3; trace=" + samples);
            require(landings >= 2, "committed mouse landings=" + landings + " expected>=2; trace=" + samples);
            require(samples.stream().filter(row -> row.contains("jumpStart=true"))
                    .allMatch(row -> Math.abs(Double.parseDouble(row.split("yBefore=")[1].split(" ")[0]) - floorY) < .03),
                    "each jump begins from authoritative floor height; trace=" + samples);
            require(packetTrace.stream().anyMatch(row -> row.contains("Pos") || row.contains("PosRot") || row.contains("Teleport")),
                    "vanilla ServerEntity emits movement packet; trace=" + packetTrace);
            require(packetTrace.stream().anyMatch(row -> row.contains("ground=true") && row.contains("y=" + floorY)),
                    "vanilla packet samples authoritative landing at floor; trace=" + packetTrace);
            fixture.close();
            helper.succeed();
        });
    }

    private static void recordFlightSample(GameTestHelper helper,
            Mob body,
            ServerEntity tracker, List<String> emitted, List<Integer> movementPacketsPerTick,
            List<String> samples, double startY,
            Vec3 previousVelocity, Vec3 previousPosition, long launchTick, long endTick, int index) {
        helper.runAfterDelay(1, () -> {
            long tick = helper.getLevel().getGameTime();
            boolean active = ExternalBodyFlight.active(body);
            long remaining = Math.max(0L, endTick - tick);
            boolean expectedActive = remaining > 0;
            boolean advanced = ExternalBodyFlight.alreadyAdvancedThisTick(body);
            Vec3 position = body.position();
            Vec3 velocity = body.getDeltaMovement();
            int emittedBefore = emitted.size();
            tracker.sendChanges();
            List<String> tickPackets = List.copyOf(emitted.subList(emittedBefore, emitted.size()));
            int movementPackets = (int) tickPackets.stream().filter(type -> type.equals("Pos") || type.equals("PosRot")
                    || type.equals(ClientboundTeleportEntityPacket.class.getSimpleName())).count();
            movementPacketsPerTick.add(movementPackets);
            samples.add("t=" + tick + " phase=" + (active ? "lease-active" : "post-lease-expiry")
                    + " active=" + active + " advanced=" + advanced
                    + " pos=" + position + " vel=" + velocity + " packets=" + tickPackets);
            double expectedVerticalVelocity = (previousVelocity.y * 20d
                    - com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED
                            * com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS)
                    * com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.VERTICAL_DRAG / 20d;
            boolean verticalMatches = Math.abs(velocity.y - expectedVerticalVelocity) < 1e-8;
            double airDamping = com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics
                    .gravityAirControl(false).orElseThrow().horizontalRetentionPerTick();
            double expectedHorizontalVelocity = active ? .5d : previousVelocity.x * airDamping;
            // External-body velocity is already expressed in blocks per native world tick.
            double expectedDisplacement = previousVelocity.x;
            boolean horizontalMatches = Math.abs(velocity.x - expectedHorizontalVelocity) < 1e-8;
            boolean displacementMatches = Math.abs(position.x - previousPosition.x - expectedDisplacement) < 1e-8;
            boolean leaseMatches = ExternalBodyFlight.remainingTicks(body) == remaining && active == expectedActive;
            if (!verticalMatches || !horizontalMatches)
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error("Flight trace invariant mismatch: {}", samples);
            require(verticalMatches, "flight sample " + index + " tick=" + tick + " expectedVy="
                    + expectedVerticalVelocity + " actualVy=" + velocity.y + " active=" + active);
            String boundary = "sample=" + index + " tick=" + tick + " active=" + active + "/" + expectedActive
                    + " remaining=" + ExternalBodyFlight.remainingTicks(body) + "/" + remaining
                    + " vx=" + velocity.x + "/" + expectedHorizontalVelocity
                    + " dx=" + (position.x - previousPosition.x) + "/" + expectedDisplacement;
            require(leaseMatches, "flight lease boundary " + boundary);
            require(horizontalMatches, "flight horizontal invariant " + boundary);
            require(displacementMatches, "flight displacement invariant " + boundary);
            if (index < 17) {
                recordFlightSample(helper, body, tracker, emitted, movementPacketsPerTick, samples,
                        startY, velocity, position, launchTick, endTick, index + 1);
                return;
            }
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("Bounded vanilla flight tracking trace: {}", samples);
            long activeSamples = samples.stream().filter(row -> row.contains("active=true")).count();
            long advancedSamples = samples.stream().filter(row -> row.contains("advanced=true")).count();
            require(samples.size() == 18, "flight trace sample count=" + samples.size() + " expected=18");
            require(activeSamples >= 14, "active flight samples=" + activeSamples + " expected>=14");
            require(advancedSamples == 18, "advanced flight samples=" + advancedSamples + " expected=18");
            require(body.getY() < startY && body.getDeltaMovement().y < .35,
                    "server flight endpoint y=" + body.getY() + " startY=" + startY
                            + " vy=" + body.getDeltaMovement().y);
            require(movementPacketsPerTick.size() == 18
                            && (movementPacketsPerTick.get(0) == 0 || movementPacketsPerTick.get(0) == 1)
                            && movementPacketsPerTick.subList(1, movementPacketsPerTick.size()).stream()
                                    .allMatch(count -> count == 1),
                    "movement packet counts per tick=" + movementPacketsPerTick);
            helper.succeed();
        });
    }

    private static void pollForLanding(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            UUID id, Object binding, Object hands, int elapsed) {
        if (body.onGround()) {
            long firstLandingTime = helper.getLevel().getGameTime();
            double landingHorizontalSpeed = body.getDeltaMovement().horizontalDistance();
            if (body.getDeltaMovement().horizontalDistanceSqr() >= .001)
                require(ExternalBodyFlight.tracked(body),
                        "flight remains tracked at first landing until grounded friction runs");
            helper.runAfterDelay(1, () -> {
                require(helper.getLevel().getGameTime() > firstLandingTime,
                        "a subsequent world post-hook had an opportunity to run");
                require(body.getDeltaMovement().horizontalDistance() < landingHorizontalSpeed * .31,
                        "three resolved 60 Hz friction substeps strongly damp horizontal flight velocity");
                awaitSettled(helper, body, id, binding, hands, 20);
            });
            return;
        }
        if (elapsed >= 25) throw new GameTestAssertException("ordinary gravity did not land the body within the bounded wait");
        helper.runAfterDelay(1, () -> pollForLanding(helper, body, id, binding, hands, elapsed + 1));
    }

    private static void awaitSettled(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            UUID id, Object binding, Object hands, int remaining) {
        if (!ExternalBodyFlight.tracked(body)) {
            require(body.onGround() && body.getDeltaMovement().horizontalDistanceSqr() < .001,
                    "grounded substep friction eventually settles horizontal flight velocity");
            require(body.getUUID().equals(id) && body.playerCharacterBinding().equals(binding)
                            && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands,
                    "settling preserves original body identity, binding, and hands");
            require(((MindControlledMob) (Object) body).moonstation14$isMovementOwned(),
                    "passive flight settling preserves movement ownership");
            helper.succeed();
            return;
        }
        if (remaining <= 0) throw new GameTestAssertException("ground friction did not settle flight before bounded deadline");
        helper.runAfterDelay(1, () -> awaitSettled(helper, body, id, binding, hands, remaining - 1));
    }

    private static void bindAndPrepare(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                                      ServerLevel level) {
        CompoundTag saved = new CompoundTag(), binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID()); binding.putString("Profile", "main"); binding.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", binding); body.readAdditionalSaveData(saved);
        body.setNoAi(true);
        require(body.playerCharacterBinding() != null && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "normal human enrollment");
        require(BodySystem.reconcile(body) && LungSystem.reconcile(body)
                && BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED, "normal body and hands initialized");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
