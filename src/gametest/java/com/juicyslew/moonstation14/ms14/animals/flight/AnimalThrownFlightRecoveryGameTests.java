package com.juicyslew.moonstation14.ms14.animals.flight;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"animals", "movement", "animals-flight-controls", "animals_flight_recovery"})
public final class AnimalThrownFlightRecoveryGameTests {
    private AnimalThrownFlightRecoveryGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 50)
    public static void portableBatThrowRecoversOnlyAfterBallisticReaction(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var bat = createResolvedBat(level, floor.above(5));
        var policy = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat)
                .orElseThrow().component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
        int reaction = policy.throwRecoveryTicks();
        require(reaction > 0 && reaction + 8 < 45, "resolved recovery policy is bounded for fixture timeout: " + reaction);
        Vec3 initial = new Vec3(.12, .30, 0);
        double y0 = bat.getY();
        float health = bat.getHealth();
        var damageBaseline = java.util.Map.copyOf(
                com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(bat));
        long launchGameTime = level.getGameTime();
        ExternalBodyFlight.launchPortableBodyThrow(bat, initial, reaction + 20);
        require(bat.getDeltaMovement().equals(initial), "portable launch preserves initial velocity");
        helper.runAfterDelay(reaction - 1, () -> {
            require(FlightSystem.state(bat) == FlightState.GROUNDED,
                    "bat remains unrecovered before configured reaction ticks");
            require(bat.getDeltaMovement().y < initial.y && bat.getY() != y0,
                    "external world ticking preserves ballistic motion before recovery");
            Vec3 preRecoveryVelocity = bat.getDeltaMovement();
            helper.runAfterDelay(1, () -> {
                require(level.getGameTime() >= launchGameTime + reaction,
                        "configured reaction elapsed in world game time");
                ExternalBodyFlight.tick(level);
                require(FlightSystem.state(bat) == FlightState.FLYING, "portable throw naturally transfers to powered flight");
                require(!bat.onGround() && bat.getY() > floor.getY(), "recovered bat avoids real floor collision");
                require(bat.getDeltaMovement().distanceToSqr(preRecoveryVelocity) < 4,
                        "transfer does not teleport or replace ballistic velocity with launch impulse");
                require(bat.getHealth() == health, "recovery avoids fall damage");
                Vec3 recoveredPosition = bat.position();
                ExternalBodyFlight.tick(level);
                new com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep()
                        .stepNpcFlight(bat);
                require(bat.position().equals(recoveredPosition),
                        "released flight marker deduplicates world tick and shared motor in recovery tick");
                helper.runAfterDelay(1, () -> {
                    ExternalBodyFlight.tick(level);
                    require(!ExternalBodyFlight.tracked(bat),
                            "released marker expires next tick despite ordinary movement advancement");
                    helper.runAfterDelay(12, () -> {
                        var brain = com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.debugSnapshot(bat);
                        require(FlightSystem.state(bat) == FlightState.FLYING && !bat.onGround()
                                        && bat.getY() > floor.getY(),
                                "autonomous flight remains powered and above floor after recovery");
                        require(brain != null && "AIR_TRAVEL".equals(brain.flightPhaseName())
                                        && Double.isFinite(brain.cruiseY()),
                                "recovery resumes autonomous AIR_TRAVEL with stable cruise anchor");
                        require(bat.getHealth() == health && com.juicyslew.moonstation14.ms14.damage.DamageSystem
                                        .existingOrVanillaBaseline(bat).equals(damageBaseline),
                                "continued recovery flight leaves health and typed damage ledger unchanged");
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 25)
    public static void ordinaryExternalBatLaunchNeverTriggersNaturalRecovery(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 4, 8));
        var bat = createResolvedBat(level, pos);
        var policy = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat)
                .orElseThrow().component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
        int reaction = policy.throwRecoveryTicks();
        ExternalBodyFlight.launch(bat, new Vec3(.1, .2, 0), reaction + 3);
        helper.runAfterDelay(reaction + 1, () -> {
            require(FlightSystem.state(bat) == FlightState.GROUNDED,
                    "unmarked external launch stays ballistic through portable recovery interval");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void portableThrowAdmissionRejectsIncapacitatedBody(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var bat = createResolvedBat(level, floor.above(5));
        var medical = com.juicyslew.moonstation14.ms14.mobs.MobStatePolicy.from(
                com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat).orElseThrow())
                .orElseThrow();
        double critical = medical.thresholds().thresholds().entrySet().stream()
                .filter(e -> e.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL)
                .mapToDouble(java.util.Map.Entry::getKey).min().orElseThrow();
        double dead = medical.thresholds().thresholds().entrySet().stream()
                .filter(e -> e.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD)
                .mapToDouble(java.util.Map.Entry::getKey).min().orElseThrow();
        float incapacitatingDamage = (float) ((critical + dead) / 2);
        com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(bat,
                java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, incapacitatingDamage), 1f, false);
        require(!bat.isDeadOrDying(), "incapacitation fixture remains alive");
        require(com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(bat),
                "bat is medically incapacitated before portable launch");
        assertPortableThrowRemainsBallistic(helper, bat, 4, true);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 25)
    public static void portableThrowAdmissionRejectsWetBody(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(8, 8, 8));
        var wet = createResolvedBat(level, center);
        // Enclose the full short reaction path in water so fluid admission remains true throughout.
        for (int x = -3; x <= 3; x++) for (int y = -2; y <= 2; y++) for (int z = -3; z <= 3; z++)
            level.setBlock(center.offset(x, y, z), Blocks.WATER.defaultBlockState(), 3);
        helper.runAfterDelay(2, () -> {
            require(wet.isInWaterOrBubble(), "bat fluid state updated before portable launch");
            var policy = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(wet)
                    .orElseThrow().component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
            int reaction = policy.throwRecoveryTicks();
            ExternalBodyFlight.launchPortableBodyThrow(wet, new Vec3(.02, 0, 0), reaction + 10);
            helper.runAfterDelay(reaction, () -> {
                require(wet.isInWaterOrBubble(), "bat remains wet at final recovery eligibility check");
                require(ExternalBodyFlight.tracked(wet), "wet rejection retains external throw ownership");
                require(FlightSystem.state(wet) != FlightState.FLYING, "wet portable throw never enters powered flight");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 25)
    public static void portableThrowAdmissionRejectsNonFlyer(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var cowType = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("cow");
        require(cowType != null, "registered cow prototype exists");
        var nonflyer = cowType.get().create(level);
        require(nonflyer != null, "resolved cow constructor succeeds");
        nonflyer.setNoAi(true);
        nonflyer.setPos(floor.getX() + .5, floor.getY() + 5, floor.getZ() + 2.5);
        require(level.addFreshEntity(nonflyer) && FlightSystem.capability(nonflyer).isEmpty(),
                "resolved cow is not flight-capable before launch");
        assertPortableThrowRemainsBallistic(helper, nonflyer, 8, true, new Vec3(.1, .25, 0));
    }

    private static void assertPortableThrowRemainsBallistic(GameTestHelper helper,
            net.minecraft.world.entity.Mob body, int delay, boolean last) {
        assertPortableThrowRemainsBallistic(helper, body, delay, last, new Vec3(.1, .25, 0));
    }

    private static void assertPortableThrowRemainsBallistic(GameTestHelper helper,
            net.minecraft.world.entity.Mob body, int delay, boolean last, Vec3 velocity) {
        ExternalBodyFlight.launchPortableBodyThrow(body, velocity, 24);
        helper.runAfterDelay(delay, () -> {
            require(FlightSystem.state(body) != FlightState.FLYING,
                    "ineligible portable throw never enters powered flight before ground contact");
            require(body.getDeltaMovement().y < velocity.y,
                    "ineligible portable throw remains ballistic during its external lease");
            if (last) helper.succeed();
        });
    }

    private static com.juicyslew.moonstation14.entities.AnimalMob createResolvedBat(
            net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var holder = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("bat");
        require(holder != null, "bat is a registered animal species");
        var bat = holder.get().create(level);
        require(bat != null, "resolved bat constructor succeeds");
        bat.setNoAi(true);
        bat.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(level.addFreshEntity(bat), "resolved bat enters live level");
        require(FlightSystem.capability(bat).isPresent(), "bat has resolved natural flight capability");
        return bat;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
