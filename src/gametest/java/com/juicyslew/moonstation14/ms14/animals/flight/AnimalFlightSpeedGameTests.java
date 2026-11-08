package com.juicyslew.moonstation14.ms14.animals.flight;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime consumer coverage for resolved natural-flight voluntary movement speed. */
@PrefixGameTestTemplate(false)
@GameTestTags({"animals-flight-controls", "animals_flight_speed", "animals", "movement"})
public final class AnimalFlightSpeedGameTests {
    private AnimalFlightSpeedGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void poweredNpcFlightUsesResolvedAirSpeedFactor(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            for (int y = 2; y < 5; y++) level.setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        var bat = AnimalFlightGameTests.createResolvedNpcFlyer(level, origin, "bat");
        require(bat != null, "resolved bat with Flight and NpcBehavior is available");
        bat.setNoAi(true);
        ((com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven) bat)
                .moonstation14$setNpcMovementDriven(true);
        var data = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat).orElseThrow();
        var movement = data.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).orElseThrow();
        var flight = data.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
        require(Math.abs(flight.airborneSpeedMultiplier() - 0.80d) < 1e-9, "bat's resolved airborne multiplier is 0.80");
        var step = new com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep();
        require(FlightSystem.requestTakeoff(bat), "bat accepts powered takeoff");
        var policy = com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.fromCharacterData(data);
        double floorY = origin.getY();
        require(measure(bat, step, FlightState.FLYING, policy.walkSpeedPerSecond() * flight.airborneSpeedMultiplier(), false, floorY),
                "airborne walk motor speed matches resolved walk policy * air factor");
        require(measure(bat, step, FlightState.FLYING, policy.sprintSpeedPerSecond() * flight.airborneSpeedMultiplier(), true, floorY),
                "airborne sprint motor speed matches resolved sprint policy * air factor");
        require(FlightSystem.requestLanding(bat), "bat accepts landing for grounded control");
        for (int i = 0; i < 40 && !bat.onGround(); i++)
            require(step.stepNpcAutonomousFlight(bat, 0, 0, false, false, 0f, 0d, 0d,
                    FlightState.LANDING, () -> true).isPresent(), "landing motor reaches control floor");
        require(bat.onGround(), "bat reaches actual floor before grounded control");
        require(FlightSystem.state(bat) == FlightState.GROUNDED, "ground control starts in explicit grounded flight state");
        var groundBat = AnimalFlightGameTests.createResolvedNpcFlyer(level, origin.offset(10, 0, 0), "bat");
        require(groundBat != null, "fresh same-profile resolved bat for grounded measurement");
        groundBat.setNoAi(true);
        ((com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven) groundBat)
                .moonstation14$setNpcMovementDriven(true);
        FlightSystem.setState(groundBat, FlightState.GROUNDED);
        groundBat.setPos(origin.getX() + 10.5, floorY, origin.getZ() + .5);
        groundBat.setDeltaMovement(new net.minecraft.world.phys.Vec3(0, -.08d, 0));
        for (int i = 0; i < 10 && !groundBat.onGround(); i++)
            require(step.stepNpc(groundBat, 0, 0, false, false, 0f, 0d, () -> true).isPresent(),
                    "grounded motor settles fresh bat onto collision floor");
        var groundBox = groundBat.getBoundingBox();
        var floorBlock = level.getBlockState(net.minecraft.core.BlockPos.containing(groundBat.getX(), floorY - .01d, groundBat.getZ()));
        if (!groundBat.onGround() || Math.abs(groundBox.minY - floorY) > .01d || !floorBlock.isSolid())
            throw new GameTestAssertException("fresh grounded bat failed ordinary floor settling: pos=" + groundBat.position()
                    + " aabb=" + groundBox + " floorY=" + floorY + " floor=" + floorBlock + " onGround=" + groundBat.onGround());
        require(FlightSystem.state(groundBat) == FlightState.GROUNDED, "fresh grounded bat has grounded flight state");
        require(measure(groundBat, step, FlightState.GROUNDED, policy.walkSpeedPerSecond() * .25d, false, floorY),
                "grounded same-profile walk motor applies expected .25 voluntary factor");
        helper.succeed();
    }

    /** Repeated synchronous solver-consumer motor steps, not elapsed live game ticks. */
    private static boolean measure(net.minecraft.world.entity.Mob bat,
            com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep step,
            FlightState state, double expected, boolean sprint, double floorY) {
        double y0 = bat.getY();
        double z0 = bat.getZ();
        for (int i = 0; i < 20; i++) {
            var result = state == FlightState.GROUNDED
                    ? step.stepNpc(bat, 0, 1000, false, sprint, 0f, () -> true)
                    : step.stepNpcAutonomousFlight(bat, 0, 1000, false, sprint, 0f, 0d, 0d,
                            state, () -> true);
            if (result.isEmpty() || FlightSystem.state(bat) != state || Math.abs(bat.getY() - y0) > .01d
                    || Math.abs(bat.getDeltaMovement().y) > .01d || bat.getZ() <= z0) {
                throw new GameTestAssertException("flight speed step rejected/moved incorrectly: state=" + state
                        + " result=" + result + " actualState=" + FlightSystem.state(bat)
                        + " y=" + bat.getY() + " expectedY=" + y0 + " vy=" + bat.getDeltaMovement().y
                        + " z=" + bat.getZ() + " startZ=" + z0);
            }
        }
        double actual = Math.abs(bat.getDeltaMovement().z) * 20d;
        boolean speedMatches = Math.abs(actual - expected) <= .08d;
        boolean floorMatches = Math.abs(bat.getY() - floorY) <= .01d;
        if (!speedMatches || !floorMatches) throw new GameTestAssertException("flight speed measurement mismatch: state="
                + state + " expectedSpeed=" + expected + " actualSpeed=" + actual + " resultVelocity="
                + bat.getDeltaMovement() + " y=" + bat.getY() + " expectedFloorY=" + floorY
                + " speedMatches=" + speedMatches + " floorMatches=" + floorMatches);
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
