package com.juicyslew.moonstation14.ms14.animals.flight;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"animals", "character", "movement", "animals-flight-controls"})
public final class AnimalFlightGameTests {
    private AnimalFlightGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 45)
    public static void resolvedFlyerTakesOffDescendsAndLandsOnLoadedFloor(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var flyer = createResolvedFlyer(level, origin);
        require(flyer != null, "resolved prototype catalog contains a Flight-capable animal host");
        flyer.setNoAi(true);
        flyer.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(0, -.01, 0));
        require(FlightSystem.state(flyer) == FlightState.GROUNDED, "late-created body starts grounded");
        require(FlightSystem.requestTakeoff(flyer), "resolved live capability authorizes takeoff");
        var step = new com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep();
        var startY = flyer.getY();
        require(step.stepNaturalFlight(flyer).isPresent(), "ordinary non-owned NPC uses shared grounded world motor");
        require(flyer.getY() > startY, "takeoff gains height through real collision-resolved movement");
        require(FlightSystem.requestLanding(flyer), "flight API accepts landing request");
        awaitLanding(helper, flyer, step, origin, 0);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void containedAndDeadFlyersCannotTakeOff(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        var flyer = createResolvedFlyer(level, origin);
        require(flyer != null, "resolved prototype catalog contains a Flight-capable animal host");
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.launch(flyer,
                new net.minecraft.world.phys.Vec3(0, .2, 0), 5);
        require(!FlightSystem.requestTakeoff(flyer), "natural flight cannot appropriate an external throw lease");
        com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.cancel(flyer);
        var service = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        var owner = new com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner() {
            private final java.util.UUID id = java.util.UUID.randomUUID();
            @Override public java.util.UUID identity() { return id; }
            @Override public net.minecraft.server.level.ServerLevel level() { return level; }
            @Override public net.minecraft.world.phys.Vec3 position() { return flyer.position(); }
            @Override public Stage stage() { return Stage.STATIONARY; }
            @Override public boolean isValid() { return true; }
            @Override public boolean tryEscape(net.minecraft.world.entity.LivingEntity ignored) { return false; }
        };
        require(service.bind(flyer, owner), "containment service binds exact flyer");
        require(!FlightSystem.requestTakeoff(flyer), "contained body cannot self-launch");
        require(service.unbind(flyer, owner), "release exact flyer from test containment");
        flyer.setData(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get(),
                new com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment(
                        new com.juicyslew.moonstation14.ms14.mobs.MobStateData(
                                com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL, 1)));
        require(!FlightSystem.requestTakeoff(flyer), "critical prototype body cannot take off");
        flyer.setData(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get(),
                new com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment());
        require(FlightSystem.requestTakeoff(flyer), "released living flyer may request flight");
        flyer.kill();
        require(FlightSystem.state(flyer) == FlightState.GROUNDED, "death drops natural-flight state for corpse gravity");
        require(!FlightSystem.requestTakeoff(flyer), "dead flyer cannot launch");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void playerOwnedGroundedFlyerIsNeverAutonomouslyLaunched(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var flyer = createResolvedFlyer(level, helper.absolutePos(new BlockPos(8, 2, 8)));
        require(flyer instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob,
                "resolved animal host has canonical movement-ownership marker");
        flyer.setNoAi(true);
        ((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) (Object) flyer)
                .moonstation14$setMovementOwned(true);
        FlightSystem.tick(level);
        require(FlightSystem.state(flyer) == FlightState.GROUNDED,
                "player-owned grounded flyer waits for jump or authorized takeoff instead of AI launch");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 35)
    public static void heldNpcFlightAscendsThenSlowDescendsWithoutFallDamage(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var flyer = createResolvedNpcFlyer(level, origin, "bat");
        require(flyer != null, "resolved Flight + NpcBehavior flyer exists");
        var ascentCorridor = flyer.getBoundingBox().expandTowards(0, 6, 0);
        require(level.hasChunksAt(net.minecraft.core.BlockPos.containing(ascentCorridor.minX, ascentCorridor.minY, ascentCorridor.minZ),
                        net.minecraft.core.BlockPos.containing(ascentCorridor.maxX, ascentCorridor.maxY, ascentCorridor.maxZ)),
                "six-block ascent corridor must be loaded");
        require(level.noCollision(flyer, ascentCorridor), "six-block ascent corridor must be clear for the full flyer body; box="
                + ascentCorridor + " species=" + flyer.getType().builtInRegistryHolder().key().location());
        flyer.setNoAi(true);
        var driven = (com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven) flyer;
        driven.moonstation14$setNpcMovementDriven(true);
        var step = new com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep();
        for (int i = 0; i < 40; i++)
            require(step.stepNpc(flyer, 0, 0, true, false, 0f, () -> true).isPresent(),
                    "held jump goes through NPC movement entry");
        require(flyer.getY() >= origin.getY() + 6, "held jump ascends at least six blocks through Entity.move; species="
                + flyer.getType().builtInRegistryHolder().key().location() + " startY=" + origin.getY() + " endY=" + flyer.getY()
                + " deltaY=" + (flyer.getY() - origin.getY()) + " velocity=" + flyer.getDeltaMovement()
                + " flight=" + FlightSystem.state(flyer) + " onGround=" + flyer.onGround() + " box=" + flyer.getBoundingBox());
        double highWater = flyer.getY();
        require(FlightSystem.requestLanding(flyer), "landing request accepted before airborne jump cancellation");
        require(step.stepNpc(flyer, 0, 0, true, false, 0f, () -> true).isPresent(), "airborne held jump cancels landing through movement entry");
        require(FlightSystem.state(flyer) == FlightState.FLYING && flyer.getY() > highWater,
                "airborne jump resumes powered ascent");
        require(step.stepNpc(flyer, 0, 0, false, false, 0f, 0d, 0d, () -> true).isPresent(),
                "neutral input enters release-hover");
        require(FlightSystem.state(flyer) == FlightState.FLYING, "released vertical input remains in flight");
        float health = flyer.getHealth();
        var damageBaseline = java.util.Map.copyOf(
                com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(flyer));
        double flightSpeed = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(flyer)
                .orElseThrow().component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class)
                .orElseThrow().walkSpeed() * com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.MINECRAFT_GROUND_SPEED_SCALE;
        double descentPerTick = flightSpeed * .1d * com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS;
        int descentLimit = (int) Math.ceil((flyer.getY() - origin.getY()) / descentPerTick) + 10;
        for (int i = 0; i < descentLimit && !flyer.onGround(); i++)
            require(step.stepNpc(flyer, 0, 0, false, false, 0f, 0d, -.1d, () -> true).isPresent(),
                    "held downward intent uses accepted NPC movement step");
        require(flyer.onGround() && flyer.getY() >= origin.getY() - .01,
                "slow descent reaches the real floor; y=" + flyer.getY() + " limit=" + descentLimit
                        + " initial speed=" + flightSpeed + " per-tick descent=" + descentPerTick);
        require(flyer.getHealth() == health, "powered slow descent does not damage health");
        require(com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(flyer)
                        .equals(damageBaseline), "powered slow descent does not add typed damage");
        require(flyer.fallDistance <= 0f, "eligible flight movement resets fall distance before movement");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void canonicalNpcFlightTransitionsBelongToNpcBehaviorMotor(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var flyer = createResolvedNpcFlyer(level, helper.absolutePos(new BlockPos(8, 2, 8)));
        require(flyer != null, "catalog contains a resolved Flight + NpcBehavior animal");
        flyer.setNoAi(true);
        var driven = (com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven) flyer;
        driven.moonstation14$setNpcMovementDriven(true);
        FlightSystem.tick(level);
        require(FlightSystem.state(flyer) == FlightState.GROUNDED,
                "flight world step does not autonomously transition NPC intent state");
        require(!FlightSystem.predictTakeoff(flyer), "client prediction entry rejects server-world entities");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 35)
    public static void autonomousNegativeWishFliesUntilExplicitLanding(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var flyer = createResolvedNpcFlyer(level, origin, "bat");
        require(flyer != null, "resolved bat has Flight + NpcBehavior");
        flyer.setNoAi(true);
        var driven = (com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven) flyer;
        driven.moonstation14$setNpcMovementDriven(true);
        var step = new com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep();
        double groundedY = flyer.getY();
        require(step.stepNpcAutonomousFlight(flyer, 0, 0, true, false, 0f, 0d, 1d,
                FlightState.LANDING, () -> true).isPresent(), "grounded landing-intent step is processed");
        require(FlightSystem.state(flyer) == FlightState.GROUNDED && flyer.getY() <= groundedY,
                "landing intent takes precedence over jump and positive altitude wish on ground");
        require(FlightSystem.requestTakeoff(flyer), "bat authorized for flight");
        double takeoffY = flyer.getY();
        for (int i = 0; i < 12 && flyer.getY() < groundedY + 2; i++)
            require(step.stepNpcAutonomousFlight(flyer, 0, 0, false, false, 0f, 0d, 1d,
                    FlightState.FLYING, () -> true).isPresent(), "positive wish advances powered takeoff");
        double startY = flyer.getY();
        require(startY >= groundedY + 2 && startY > takeoffY,
                "real motor establishes airborne clearance before descent; ground=" + groundedY + " y=" + startY);
        for (int i = 0; i < 12; i++) {
            require(step.stepNpcAutonomousFlight(flyer, 0, 0, false, false, 0f, 0d, -.1d,
                    FlightState.FLYING, () -> true).isPresent(), "negative wish advances autonomous FLYING step");
            require(FlightSystem.state(flyer) == FlightState.FLYING, "negative altitude wish does not request landing");
        }
        require(flyer.getY() < startY && flyer.getY() > groundedY + 1,
                "negative wish descends above floor while retaining powered flight; y=" + flyer.getY()
                        + " startY=" + startY + " floor=" + groundedY);
        require(step.stepNpcAutonomousFlight(flyer, 0, 0, true, false, 0f, 0d, 1d,
                FlightState.LANDING, () -> true).isPresent(), "airborne explicit landing step accepted");
        require(FlightSystem.state(flyer) == FlightState.LANDING && flyer.getY() < startY,
                "airborne landing intent overrides jump and positive wish with descent");
        var health = flyer.getHealth();
        require(FlightSystem.requestLanding(flyer), "explicit landing accepted");
        int limit = 100;
        while (!flyer.onGround() && limit-- > 0) {
            require(step.stepNpcAutonomousFlight(flyer, 0, 0, false, false, 0f, 0d, 1d,
                    FlightState.LANDING, () -> true).isPresent(), "landing intent advances shared motor");
        }
        require(flyer.onGround() && FlightSystem.state(flyer) == FlightState.GROUNDED,
                "explicit landing safely reaches floor");
        require(flyer.getHealth() == health && flyer.fallDistance <= 0f,
                "powered descent and landing cause no fall damage");
        helper.succeed();
    }

    private static com.juicyslew.moonstation14.entities.AnimalMob createResolvedFlyer(
            net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        for (var holder : com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().values()) {
            var flyer = holder.get().create(level);
            if (flyer == null) continue;
            flyer.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            if (!level.addFreshEntity(flyer)) { flyer.discard(); continue; }
            if (FlightSystem.capability(flyer).isPresent()) return flyer;
            flyer.discard();
        }
        return null;
    }

    static com.juicyslew.moonstation14.entities.AnimalMob createResolvedNpcFlyer(
            net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        return createResolvedNpcFlyer(level, pos, null);
    }

    static com.juicyslew.moonstation14.entities.AnimalMob createResolvedNpcFlyer(
            net.minecraft.server.level.ServerLevel level, BlockPos pos, String requestedSpecies) {
        var hosts = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types();
        var candidates = requestedSpecies == null ? hosts.values() : java.util.List.of(hosts.get(requestedSpecies));
        for (var holder : candidates) {
            if (holder == null) continue;
            var flyer = holder.get().create(level);
            if (flyer == null) continue;
            flyer.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            if (!level.addFreshEntity(flyer)) { flyer.discard(); continue; }
            var data = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(flyer).orElse(null);
            if (data != null && data.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).isPresent()
                    && data.component(com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent.class).isPresent())
                return flyer;
            flyer.discard();
        }
        return null;
    }

    private static void awaitLanding(GameTestHelper helper, com.juicyslew.moonstation14.entities.AnimalMob flyer,
                                     com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep step,
                                     BlockPos origin, int ticks) {
        if (FlightSystem.state(flyer) == FlightState.GROUNDED) {
            require(flyer.getY() >= origin.getY() - .01 && flyer.onGround(),
                    "landing occurs on real loaded floor without teleporting through it");
            helper.succeed();
            return;
        }
        if (ticks >= 35) throw new GameTestAssertException("flight failed to land: state=" + FlightSystem.state(flyer)
                + " pos=" + flyer.position() + " velocity=" + flyer.getDeltaMovement());
        require(step.stepNaturalFlight(flyer).isPresent(), "each flight tick advances through the shared collision motor");
        helper.runAfterDelay(1, () -> awaitLanding(helper, flyer, step, origin, ticks + 1));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
