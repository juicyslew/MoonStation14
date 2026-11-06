package com.juicyslew.moonstation14.ms14.movement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterMovementMotorTest {
    private static final MovementCollisionResolver OPEN = (position, displacement, grounded) ->
            new MovementCollisionResolver.CollisionResult(displacement, grounded);

    @Test
    void clippedSmallUpwardRequestStillAppliesGravityAfterZeroingBlockedVelocity() {
        MovementCollisionResolver ceiling = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(MovementVector.ZERO, false);
        var environment = new CharacterMovementEnvironment(.05d, 32d, 0d, .98d,
                MovementVector.ZERO, 0d, 0d, ceiling);
        var result = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO, new MovementVector(0d, .001d, 0d), false),
                new CharacterMovementCommand(0d, 0d, false, false), environment, false);
        assertEquals(0d, result.position().y(), 0d);
        assertEquals(-1.568d, result.velocity().y(), 1e-12,
                "blocked upward velocity is zeroed first, then gravity advances the next velocity");
    }

    @Test
    void motorContextAttestsOnlyGroundedEffectiveAllowedJumpAndPreservesIdleContext() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        boolean[] idle = {false}, jump = {false};
        int[] calls = {0};
        MovementCollisionResolver resolver = MovementCollisionResolver.withMotorContext(
                (position, requested, grounded, idleProbe, jumpInitiated) -> {
                    calls[0]++;
                    idle[0] = idleProbe;
                    jump[0] = jumpInitiated;
                    return new MovementCollisionResolver.CollisionResult(requested, grounded);
                });
        CharacterMovementEnvironment environment = env(.05d, 32d, 8.4d, MovementVector.ZERO, 0d, resolver);
        CharacterMovementCommand jumpCommand = new CharacterMovementCommand(0d, 0d, true, false);

        motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true), jumpCommand, environment, false);
        assertTrue(jump[0]);
        assertTrue(!idle[0]);
        assertEquals(1, calls[0]);

        motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true), jumpCommand, environment, true);
        assertTrue(!jump[0], "stun-filtered jump is not motor-initiated");
        motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, false), jumpCommand, environment, false);
        assertTrue(!jump[0], "airborne jump request does not attest");
        motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true), jumpCommand, environment, false, true);
        assertTrue(!jump[0], "external flight suppresses motor jump");

        var impulse = new CharacterMovementEnvironment(.05d, 32d, 8.4d, 1d,
                MovementVector.ZERO, 1d, 0d, resolver, 1d, 1d, new MovementVector(0d, 1d, 0d));
        motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false), impulse, false);
        assertTrue(!jump[0], "positive constraint impulse is not a jump");

        motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false), environment, false);
        assertTrue(idle[0], "idle support-probe attestation remains intact");
        assertTrue(!jump[0]);
        assertEquals(6, calls[0], "each motor tick resolves exactly once");

        int[] adaptedCalls = {0};
        MovementCollisionResolver adapted = MovementCollisionResolver.withMotorContext(
                (position, requested, grounded, idleProbe, jumpInitiated) -> {
                    int call = ++adaptedCalls[0];
                    if (call == 1) assertTrue(!idleProbe && !jumpInitiated, "3-argument call defaults both contexts");
                    if (call == 2) assertTrue(idleProbe && !jumpInitiated,
                            "4-argument call preserves supplied idle context and defaults jump");
                    if (call == 3) assertTrue(!idleProbe && jumpInitiated,
                            "5-argument call forwards both contexts");
                    if (call == 4) assertTrue(idleProbe && jumpInitiated,
                            "5-argument call forwards both true contexts");
                    return new MovementCollisionResolver.CollisionResult(requested, grounded);
                });
        adapted.resolve(MovementVector.ZERO, MovementVector.ZERO, true);
        adapted.resolve(MovementVector.ZERO, MovementVector.ZERO, true, true);
        adapted.resolve(MovementVector.ZERO, MovementVector.ZERO, true, false, true);
        adapted.resolve(MovementVector.ZERO, MovementVector.ZERO, true, true, true);
        assertEquals(4, adaptedCalls[0], "each supported arity is forwarded exactly once");
    }

    @Test
    void idleSupportProbeContextRequiresActualUnforcedZeroRequestAndLegacyCallsStayFalse() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        boolean[] observed = {false};
        int[] calls = {0};
        MovementCollisionResolver contextual = MovementCollisionResolver.withIdleSupportProbeContext(
                (position, requested, grounded, idleProbe) -> {
                    calls[0]++;
                    observed[0] = idleProbe;
                    if (calls[0] == 1) assertEquals(new MovementVector(0d, -.001d, 0d), requested);
                    return new MovementCollisionResolver.CollisionResult(MovementVector.ZERO, true);
                });
        var idleEnvironment = env(.05d, 32d, 0d, MovementVector.ZERO, 0d, contextual);
        motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false), idleEnvironment, false);
        assertTrue(observed[0]);
        assertEquals(1, calls[0]);

        MovementCollisionResolver legacy = (position, requested, grounded) -> {
            calls[0]++;
            return new MovementCollisionResolver.CollisionResult(MovementVector.ZERO, true);
        };
        legacy.resolve(MovementVector.ZERO, MovementVector.ZERO, true);
        assertEquals(2, calls[0], "legacy functional resolver remains one-call compatible");
        contextual.resolve(MovementVector.ZERO, MovementVector.ZERO, true);
        assertEquals(3, calls[0], "factory legacy invocation is forwarded once");
        assertTrue(!observed[0], "factory maps legacy invocation to false");

        assertContextFalse(motor, contextual, state(MovementVector.ZERO, new MovementVector(0d, -1d, 0d), false),
                new CharacterMovementCommand(0d, 0d, false, false), idleEnvironment, false);
        assertContextFalse(motor, contextual, state(MovementVector.ZERO, new MovementVector(0d, -.02d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false), idleEnvironment, false);
        assertContextFalse(motor, contextual, state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, true, false), idleEnvironment, false);
        assertContextFalse(motor, contextual, state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, false, false),
                new CharacterMovementEnvironment(.05d, 32d, 0d, 1d, MovementVector.ZERO, 0d, 0d,
                        contextual, 1d, 0d), false);
        assertContextFalse(motor, contextual, state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false),
                new CharacterMovementEnvironment(.05d, 32d, 0d, 1d, new MovementVector(1d, 0d, 0d),
                        1d, 0d, contextual), false);
        assertContextFalse(motor, contextual, state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false), idleEnvironment, true);
        assertContextFalse(motor, contextual, state(MovementVector.ZERO, new MovementVector(-1d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false),
                new CharacterMovementEnvironment(.05d, 32d, 0d, 1d, new MovementVector(1d, 0d, 0d),
                        1d, 0d, contextual), false);
        assertContextFalse(motor, contextual, state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false),
                new CharacterMovementEnvironment(.05d, 32d, 0d, 1d, MovementVector.ZERO, 1d, 0d,
                        contextual, 1d, 1d, new MovementVector(1d, 0d, 0d)), false);
        assertContextFalse(motor, contextual, state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, false, false),
                new CharacterMovementEnvironment(.05d, 32d, 0d, 1d, MovementVector.ZERO, 0d, 0d,
                        contextual, 0d, 1d), false);
        assertContextFalse(motor, contextual, state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false),
                new CharacterMovementEnvironment(.05d, 0d, 0d, 1d, MovementVector.ZERO, 0d, 0d,
                        contextual), false);
    }

    private static void assertContextFalse(CharacterMovementMotor motor, MovementCollisionResolver ignored,
                                           CharacterMovementState initial, CharacterMovementCommand command,
                                           CharacterMovementEnvironment environment, boolean externalFlight) {
        boolean[] observed = {true};
        MovementCollisionResolver context = MovementCollisionResolver.withIdleSupportProbeContext(
                    (position, requested, grounded, idleProbe) -> {
                    observed[0] = idleProbe;
                    return new MovementCollisionResolver.CollisionResult(requested, true);
                });
        CharacterMovementEnvironment adapted = new CharacterMovementEnvironment(environment.secondsPerTick(),
                environment.gravityPerSecondSquared(), environment.jumpVelocityPerSecond(), environment.verticalDrag(),
                environment.externalImpulse(), environment.maximumExternalImpulsePerTick(),
                environment.maxUpwardStepBlocks(), context, environment.surfaceMovementFactor(),
                environment.voluntarySpeedFactor(), environment.constraintImpulse());
        motor.tick(initial, command, adapted, false, externalFlight);
        assertTrue(!observed[0], "non-idle/unauthenticated support context must be false");
    }

    @Test
    void certifiedFloorCorrectionAddsOnlyGeometryPositionAndRequiresExactBoundedRequest() {
        MovementVector request = new MovementVector(0, -.001, 0);
        MovementVector bounded = MovementVector.ZERO;
        var geometry = new MovementVector(.0002, .001, -.0003);
        var evidence = ContactPositionCorrectionEvidence.validateSupportedFloor(request, bounded, geometry).orElseThrow();
        assertEquals(geometry, evidence.geometryCorrection());
        assertTrue(evidence.matches(request, bounded, true));
        assertTrue(!evidence.matches(new MovementVector(0, -.002, 0), bounded, true));
        assertTrue(!evidence.matches(request, bounded, false), "floor correction requires resolved support");
        assertTrue(ContactPositionCorrectionEvidence.validateSupportedFloor(request, new MovementVector(0, .001, 0), geometry).isEmpty());
        assertTrue(ContactPositionCorrectionEvidence.validateSupportedFloor(new MovementVector(0, .001, 0), bounded, geometry).isEmpty());
        assertTrue(ContactPositionCorrectionEvidence.validateSupportedFloor(request, bounded, MovementVector.ZERO).isEmpty());

        MovementCollisionResolver floor = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(bounded, true, null, evidence);
        CharacterMovementEnvironment environment = env(.05, 32, 0, MovementVector.ZERO, 0, floor);
        CharacterMovementState result = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0, 0, false, false), environment, false);
        assertEquals(geometry, result.position());
        assertEquals(MovementVector.ZERO, result.velocity(), "geometry correction must not inject own velocity");

        MovementCollisionResolver unbackedExcess = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0, .001, 0), true);
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0, 0, false, false),
                env(.05, 32, 0, MovementVector.ZERO, 0, unbackedExcess), false));

        MovementCollisionResolver mismatched = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(bounded, true, null,
                        ContactPositionCorrectionEvidence.validateSupportedFloor(new MovementVector(0, -.002, 0), bounded,
                                new MovementVector(0, .001, 0)).orElseThrow());
        CharacterMovementState noCorrection = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true), new CharacterMovementCommand(0, 0, false, false),
                env(.05, 32, 0, MovementVector.ZERO, 0, mismatched), false);
        assertEquals(0, noCorrection.position().y(), 0);
    }

    @Test
    void sidePositionCorrectionRequiresZeroYAndExactContactBindingWithoutGroundClaim() {
        MovementVector request = new MovementVector(.05d, 0d, 0d);
        MovementVector bounded = new MovementVector(.02d, 0d, 0d);
        HorizontalContactEvidence contact = HorizontalContactEvidence.validate(request, bounded, -1d, 0d).orElseThrow();
        MovementVector correction = new MovementVector(.001d, 0d, 0d);
        var evidence = ContactPositionCorrectionEvidence.validateSide(request, bounded, correction, contact).orElseThrow();
        assertTrue(evidence.matches(request, bounded, false), "side correction makes no grounding claim");
        assertTrue(!evidence.matches(new MovementVector(.06d, 0d, 0d), bounded, false));
        assertTrue(ContactPositionCorrectionEvidence.validateSide(request, bounded,
                new MovementVector(0d, .001d, 0d), contact).isEmpty());
        assertTrue(ContactPositionCorrectionEvidence.validateSide(request, bounded, correction, null).isEmpty());
        assertTrue(ContactPositionCorrectionEvidence.validateSide(request, bounded, correction,
                HorizontalContactEvidence.validate(request, new MovementVector(.03d, 0d, 0d), -1d, 0d).orElseThrow()).isEmpty());
        assertTrue(ContactPositionCorrectionEvidence.validateSide(request, request, correction, null).isPresent(),
                "unchanged horizontal own movement needs no contact certificate");
    }

    @Test
    void boundedPureSideVerticalRemainderCorrectsPositionButNotJumpVelocity() {
        MovementVector request = new MovementVector(0, .42, 0);
        MovementVector bounded = request;
        MovementVector correction = new MovementVector(0, -8.0e-8, 0);
        var evidence = ContactPositionCorrectionEvidence.validateSide(request, bounded, correction, null)
                .orElseThrow();
        MovementCollisionResolver resolver = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(bounded, false, null, evidence);
        var environment = new CharacterMovementEnvironment(1d, .08d, .42d, .98d, MovementVector.ZERO,
                0d, 0d, resolver);
        CharacterMovementState result = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0, 0, true, false), environment, false);
        assertEquals(.42-8.0e-8, result.position().y(), 1e-15);
        assertEquals(.3332, result.velocity().y(), 1e-15,
                "the measured small position remainder must not become a vertical blocked-axis signal");
    }

    @Test
    void geometryBackedAirborneSideRemainderDoesNotBecomeMotorYRejection() {
        var correction=new MovementVector(0,9.893394e-6,0);
        MovementCollisionResolver resolver=(position,asked,grounded)->{
            MovementVector bounded=new MovementVector(asked.x(),asked.y(),asked.z()*.01);
            var contact=HorizontalContactEvidence.validate(asked,bounded,0,0,-1).orElseThrow();
            var evidence=ContactPositionCorrectionEvidence.validateAirborneSideGeometry(asked,bounded,correction,contact).orElseThrow();
            return new MovementCollisionResolver.CollisionResult(bounded,false,contact,evidence);
        };
        var result=new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(state(MovementVector.ZERO,new MovementVector(0,0,.15625),false),
                new CharacterMovementCommand(0,0,false,false),env(.05,.08,0,MovementVector.ZERO,0,resolver),false);
        assertTrue(result.position().z()>0);
        assertEquals(correction.y(),result.position().y(),0d);
        assertTrue(result.velocity().y()<0,"airborne gravity proceeds; geometry correction does not zero vertical velocity");
        MovementVector rawRequest=new MovementVector(0,0,.1), rawBounded=new MovementVector(0,0,.001);
        var rawContact=HorizontalContactEvidence.validate(rawRequest,rawBounded,0,0,-1).orElseThrow();
        assertTrue(ContactPositionCorrectionEvidence.validateAirborneSideGeometry(rawRequest,
                new MovementVector(0,9e-6,.001),correction,rawContact).isEmpty(),"raw unbounded Y remains rejected");
        MovementCollisionResolver rawNativeY=(position,asked,grounded)->new MovementCollisionResolver.CollisionResult(
                new MovementVector(asked.x(),asked.y()+9.893394e-6,asked.z()),false);
        assertThrows(IllegalArgumentException.class,()->new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO,new MovementVector(0,0,.15625),false),new CharacterMovementCommand(0,0,false,false),
                env(.05,.08,0,MovementVector.ZERO,0,rawNativeY),false),"motor still rejects unproved raw native Y");
    }

    @Test
    void diagonalAirControlCanDifferAfterWallNormalVelocityIsRemoved() {
        double length = Math.sqrt(1d + .25d*.25d);
        double wishX = 1d/length, wishZ = .25d/length;
        MovementVector freeAtWishSpeed = new MovementVector(2.5d*wishX, 0, 2.5d*wishZ);
        MovementVector wallAfterNormalClip = new MovementVector(0, 0, freeAtWishSpeed.z());
        var command = new CharacterMovementCommand(1d, .25d, false, false);
        var environment = env(.05, 0, 0, MovementVector.ZERO, 0, OPEN);
        var motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        var free = motor.tick(state(MovementVector.ZERO, freeAtWishSpeed, false), command, environment, false);
        var wall = motor.tick(state(MovementVector.ZERO, wallAfterNormalClip, false), command, environment, false);
        assertEquals(freeAtWishSpeed.z(), free.velocity().z(), 1e-12,
                "free actor already at requested wish speed receives no further acceleration");
        assertTrue(wall.velocity().z() > free.velocity().z(),
                "normal clipping changes wish-direction dot product, so legitimate air control changes tangent motion");
        assertEquals(wall.velocity().z()*.05, wall.position().z(), 1e-12);
    }

    @Test
    void certifiedWorldFloorClipDoesNotRestoreTheDownwardWishAsPositionCorrection() {
        MovementVector request=new MovementVector(.1,-.001,0);
        MovementVector bounded=MovementVector.ZERO;
        var contact=HorizontalContactEvidence.validate(request,bounded,-1,0).orElseThrow();
        var positionCorrection=ContactPositionCorrectionEvidence.validateSide(request,bounded,
                MovementVector.ZERO,contact).orElseThrow();
        MovementCollisionResolver resolver=(position,requested,grounded)->{
            assertEquals(request,requested);
            return new MovementCollisionResolver.CollisionResult(bounded,true,contact,positionCorrection);
        };
        var result=new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO,request,false),new CharacterMovementCommand(0,0,false,false),
                env(1d,.08d,0,MovementVector.ZERO,0,resolver),false);
        assertEquals(MovementVector.ZERO,result.position(),"authentic world-floor clipping keeps the body on the floor");
        assertEquals(0d,result.velocity().y(),0d,"ground clipping cannot resume an unbounded downward own wish");
        assertTrue(result.onGround());
    }

    @Test
    void certifiedWorldFloorPartialDescentRetainsActualPositionAndStopsNextDownwardVelocity() {
        MovementVector request=new MovementVector(.1,-.426909648,0);
        MovementVector bounded=new MovementVector(0,-.0443874444,0);
        var contact=HorizontalContactEvidence.validate(request,bounded,-1,0).orElseThrow();
        var positionCorrection=ContactPositionCorrectionEvidence.validateSide(request,bounded,
                MovementVector.ZERO,contact).orElseThrow();
        MovementCollisionResolver resolver=(position,requested,grounded)->
                new MovementCollisionResolver.CollisionResult(bounded,true,contact,positionCorrection);
        var result=new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO,request,false),new CharacterMovementCommand(0,0,false,false),
                env(1d,.08d,0,MovementVector.ZERO,0,resolver),false);
        assertEquals(bounded,result.position());
        assertEquals(0d,result.velocity().y(),0d,"ground contact stops subsequent downward velocity");
        assertTrue(result.onGround());
    }

    @Test
    void outwardOwnNormalVelocitySurvivesWorldFloorSidePositionCorrection() {
        MovementVector request=new MovementVector(-7.97017e-7,-.001,.15625);
        MovementVector bounded=new MovementVector(request.x(),0,request.z());
        MovementVector geometry=new MovementVector(-1.051449999e-6,0,0);
        var positionCorrection=ContactPositionCorrectionEvidence.validateSide(request,bounded,geometry,null)
                .orElseThrow();
        MovementCollisionResolver resolver=(position,requested,grounded)->
                new MovementCollisionResolver.CollisionResult(bounded,true,null,positionCorrection);
        var result=new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO,request,false),new CharacterMovementCommand(0,0,false,false),
                env(1d,.08d,0,MovementVector.ZERO,0,resolver),false);
        assertEquals(bounded.x()+geometry.x(),result.position().x(),1e-15);
        assertEquals(request.x(),result.velocity().x(),0d,
                "same-direction own wish remains velocity; native separation only moves position");
        assertEquals(request.z(),result.velocity().z(),0d);
        assertEquals(0d,result.velocity().y(),0d);
        assertTrue(result.onGround());
    }

    @Test
    void supportedMixedGeometryOnlyCorrectionMovesPositionWithoutOwnMotion() {
        MovementVector request = new MovementVector(0, -.001, 0);
        MovementVector bounded = MovementVector.ZERO;
        MovementVector correction = new MovementVector(.002, .00002, -.003);
        var evidence = ContactPositionCorrectionEvidence.validateSupportedFloorAndSide(
                request, bounded, correction, null).orElseThrow();
        assertTrue(evidence.matches(request, bounded, true));
        assertTrue(!evidence.matches(request, bounded, false), "mixed correction requires resolved floor support");
        assertTrue(ContactPositionCorrectionEvidence.validateSupportedFloorAndSide(request,
                new MovementVector(.001, 0, 0), correction, null).isEmpty(),
                "unproved horizontal own clipping cannot use contact-free mixed correction");

        int[] resolverCalls = {0};
        MovementCollisionResolver resolver = (position, requested, grounded) -> {
            resolverCalls[0]++;
            assertEquals(request, requested);
            return new MovementCollisionResolver.CollisionResult(bounded, true, null, evidence);
        };
        CharacterMovementState result = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0, 0, false, false), env(.05, 32, 0,
                        MovementVector.ZERO, 0, resolver), false);
        assertEquals(correction, result.position(), "certified native geometry adjusts position only");
        assertEquals(MovementVector.ZERO, result.velocity(), "geometry-only response must not create own velocity");
        assertEquals(1, resolverCalls[0], "motor does not repeat the physical collision move");
    }

    @Test
    void tetherConstraintActsAfterStandingFrictionAndVoluntaryWish() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(.05d, 32d, 8.4d,
                .98d, MovementVector.ZERO, 3d, 0d, OPEN, 1d, 1d,
                new MovementVector(-3d, 0d, 0d));
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO,
                        new MovementVector(2.5d, 0d, 0d), true),
                new CharacterMovementCommand(1d, 0d, false, false), environment, false);
        assertEquals(-.5d, result.velocity().x(), 1e-12,
                "standing friction and voluntary acceleration must not erase/reverse the opposing tether impulse");
        assertEquals(-.025d, result.position().x(), 1e-12);

        CharacterMovementEnvironment ordinary = new CharacterMovementEnvironment(.05d, 32d, 8.4d,
                .98d, MovementVector.ZERO, 0d, 0d, OPEN);
        CharacterMovementState unchanged = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false), ordinary, false);
        assertEquals(MovementVector.ZERO, unchanged.velocity(),
                "the compatibility constructor retains neutral constraint input");

        CharacterMovementEnvironment upwardTether = new CharacterMovementEnvironment(.05d, 32d, 8.4d,
                .98d, MovementVector.ZERO, 1d, 0d, OPEN, 1d, 1d,
                new MovementVector(0d, 1d, 0d));
        CharacterMovementState jump = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, true, false), upwardTether, false);
        assertEquals(.47d, jump.position().y(), 1e-12,
                "tether force augments the ordinary jump after jump velocity is selected");

        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementEnvironment(.05d, 32d, 8.4d,
                .98d, MovementVector.ZERO, 0d, 0d, OPEN, 1d, 1d,
                new MovementVector(Double.NaN, 0d, 0d)));
    }

    @Test
    void playerAndVillagerHostsUseTheSameHumanMotorPolicy() {
        CharacterMovementPolicy playerProfile = CharacterMovementPolicy.forCharacterPrototype("moonstation14:human");
        CharacterMovementPolicy villagerProfile = CharacterMovementPolicy.forCharacterPrototype("moonstation14:human");
        CharacterMovementMotor playerMotor = new CharacterMovementMotor(playerProfile);
        CharacterMovementMotor villagerMotor = new CharacterMovementMotor(villagerProfile);
        CharacterMovementState initial = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementCommand input = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementEnvironment environment = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);

        assertEquals(playerMotor.policy(), villagerMotor.policy());
        assertEquals(playerMotor.tick(initial, input, environment, false),
                villagerMotor.tick(initial, input, environment, false));
        assertEquals(20d, CharacterMovementPolicy.HUMAN.accelerationPerSecondSquared());
    }

    @Test
    void typedHumanAndPigProfilesUseTheSameMotorWithDifferentConfiguredSpeeds() throws IOException {
        CharacterMovementPolicy human = CharacterMovementPolicy.fromCharacterData(readCharacter("human.json"));
        CharacterMovementPolicy pig = CharacterMovementPolicy.fromCharacterData(readCharacter("pig.json"));
        CharacterMovementMotor humanMotor = new CharacterMovementMotor(human);
        CharacterMovementMotor pigMotor = new CharacterMovementMotor(pig);
        CharacterMovementEnvironment environment = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState humanWalk = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState pigWalk = humanWalk;
        CharacterMovementState humanSprint = humanWalk;
        CharacterMovementState pigSprint = humanWalk;

        for (int tick = 0; tick < 120; tick++) {
            humanWalk = humanMotor.tick(humanWalk, new CharacterMovementCommand(1d, 0d, false, false), environment, false);
            pigWalk = pigMotor.tick(pigWalk, new CharacterMovementCommand(1d, 0d, false, false), environment, false);
            humanSprint = humanMotor.tick(humanSprint, new CharacterMovementCommand(1d, 0d, false, true), environment, false);
            pigSprint = pigMotor.tick(pigSprint, new CharacterMovementCommand(1d, 0d, false, true), environment, false);
        }

        assertEquals(3.125d, humanWalk.velocity().x(), 1e-9);
        assertEquals(5d, pigWalk.velocity().x(), 1e-9);
        assertEquals(5.625d, humanSprint.velocity().x(), 1e-9);
        assertEquals(5d, pigSprint.velocity().x(), 1e-9);
        assertEquals(20d, human.accelerationPerSecondSquared());
        assertEquals(20d, pig.accelerationPerSecondSquared());
        assertEquals(20d, human.groundFrictionWithInputPerSecond());
        assertEquals(20d, human.groundFrictionNoInputPerSecond());
        assertEquals(3.125d, human.walkSpeedPerSecond());
        assertEquals(5.625d, human.sprintSpeedPerSecond());
    }

    private static CharacterData readCharacter(String name) throws IOException {
        String path = "data/moonstation14/moonstation14/character/" + name;
        try (var stream = CharacterMovementMotorTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource " + path);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return CharacterData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        }
    }

    @Test
    void walkAndSprintSpeedsUseTheConfiguredRatio() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementEnvironment environment = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState walk = initial;
        CharacterMovementState sprint = initial;
        for (int i = 0; i < 120; i++) {
            walk = motor.tick(walk, new CharacterMovementCommand(1d, 0d, false, false), environment, false);
            sprint = motor.tick(sprint, new CharacterMovementCommand(1d, 0d, false, true), environment, false);
        }
        assertEquals(2.5d, walk.velocity().x(), 1e-9);
        assertEquals(4.5d, sprint.velocity().x(), 1e-9);
        assertEquals(1.8d, sprint.velocity().x() / walk.velocity().x(), 1e-9);
    }

    @Test
    void knockdownScalesVoluntaryTargetSpeedButDoesNotBlockInputOrStunMomentum() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState normal = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState crawling = normal;
        CharacterMovementEnvironment ordinary = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementEnvironment knockedDown = envWithVoluntarySpeed(.05d, 0d, 0d,
                CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR, OPEN);
        CharacterMovementCommand input = new CharacterMovementCommand(1d, 0d, false, false);
        for (int i = 0; i < 120; i++) {
            normal = motor.tick(normal, input, ordinary, false);
            crawling = motor.tick(crawling, input, knockedDown, false);
        }
        assertEquals(2.5d, normal.velocity().x(), 1e-9);
        assertEquals(1d, crawling.velocity().x(), 1e-9);
        assertTrue(crawling.velocity().x() > 0d, "knockdown alone preserves voluntary input");

        CharacterMovementState stunned = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true), input,
                knockedDown, true);
        assertEquals(0d, stunned.velocity().x(), 0d, "full stun still blocks voluntary input");
    }

    @Test
    void knockdownDoesNotScaleImpulseOrChangeNoInputCoasting() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment knockedDown = envWithVoluntarySpeed(.05d, 0d, 0d,
                CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR, OPEN);
        CharacterMovementState impulse = motor.tick(state(MovementVector.ZERO,
                        new MovementVector(2d, 0d, 0d), false),
                new CharacterMovementCommand(1d, 0d, false, false),
                new CharacterMovementEnvironment(.05d, 0d, 0d, 1d, new MovementVector(1d, 0d, 0d),
                        1d, 0d, OPEN, 1d, CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR), false);
        assertEquals(3d, impulse.velocity().x(), 1e-12,
                "external impulse and pre-existing velocity remain unscaled when above target speed");

        CharacterMovementEnvironment ordinary = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState coasting = state(MovementVector.ZERO, new MovementVector(.004d, 0d, 0d), true);
        assertEquals(motor.tick(coasting, new CharacterMovementCommand(0d, 0d, false, false), ordinary, false),
                motor.tick(coasting, new CharacterMovementCommand(0d, 0d, false, false), knockedDown, false));
    }

    @Test
    void diagonalWishIsNormalizedAndAccelerationIsBoundedPerTick() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementCommand diagonal = new CharacterMovementCommand(1d, 1d, false, false);
        assertEquals(1d, Math.hypot(diagonal.wishX(), diagonal.wishZ()), 1e-12);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true), diagonal,
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(2.5d, result.velocity().horizontalLength(), 1e-12); // accel * tick * wishSpeed
    }

    @Test
    void fractionalWishScalesTopSpeedWhileFullDiagonalRetainsFullSpeed() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment environment = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState fractional = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState diagonal = fractional;
        for (int i = 0; i < 120; i++) {
            fractional = motor.tick(fractional, new CharacterMovementCommand(.5d, 0d, false, false),
                    environment, false);
            diagonal = motor.tick(diagonal, new CharacterMovementCommand(1d, 1d, false, false),
                    environment, false);
        }
        assertEquals(CharacterMovementPolicy.HUMAN.walkSpeedPerSecond() * .5d, fractional.velocity().x(), 1e-9);
        assertEquals(0d, fractional.velocity().z(), 1e-12);
        assertEquals(CharacterMovementPolicy.HUMAN.walkSpeedPerSecond(), diagonal.velocity().horizontalLength(), 1e-9);
    }

    @Test
    void groundFrictionAndExplicitGravityAreAppliedInTickUnits() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver leavesGround = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(displacement, false);
        CharacterMovementEnvironment environment = env(.1d, 10d, 0d, MovementVector.ZERO, 0d, leavesGround);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false), environment, false);
        assertEquals(0d, result.velocity().x(), 0d);
        assertEquals(-1d, result.velocity().y(), 1e-12);
        assertEquals(0d, result.position().x(), 0d);
        assertEquals(-.001d, result.position().y(), 1e-12);
    }

    @Test
    void humanNeutralGroundFrictionStopsOnTheNextTwentyHertzNoWishTick() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState result = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2.5d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(1d / 20d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(MovementVector.ZERO, result.velocity());
    }

    @Test
    void customLowFrictionPolicyRetainsTheExplicitPointSevenFiveExample() {
        CharacterMovementPolicy lowFriction = new CharacterMovementPolicy(20d, 2.5d, 4.5d,
                2.5d, 2.5d, .005d);
        CharacterMovementMotor motor = new CharacterMovementMotor(lowFriction);
        CharacterMovementState result = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(.1d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(1.5d, result.velocity().x(), 1e-12);
    }

    @Test
    void belowMinimumFrictionSpeedPreservesStunnedSlipAndEqualityStillAppliesFriction() {
        CharacterMovementPolicy policy = CharacterMovementPolicy.HUMAN;
        CharacterMovementMotor motor = new CharacterMovementMotor(policy);
        double dt = .1d;
        double threshold = policy.minimumFrictionSpeed();
        CharacterMovementEnvironment environment = env(dt, 10d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementCommand noWish = new CharacterMovementCommand(0d, 0d, false, false);

        CharacterMovementState below = motor.tick(
                state(MovementVector.ZERO, new MovementVector(threshold / 2d, 0d, 0d), true),
                noWish, environment, true);
        assertEquals(threshold / 2d, below.velocity().x(), 0d);
        assertEquals(threshold / 2d * dt, below.position().x(), 0d);
        assertTrue(below.position().x() > 0d);
        assertEquals(-.001d, below.position().y(), 1e-12);

        CharacterMovementState atThreshold = motor.tick(
                state(MovementVector.ZERO, new MovementVector(threshold, 0d, 0d), true),
                noWish, environment, true);
        double frictionFactor = Math.max(0d, 1d - policy.groundFrictionNoInputPerSecond() * dt);
        assertEquals(threshold * frictionFactor, atThreshold.velocity().x(), 1e-12);
        assertEquals(threshold * frictionFactor * dt, atThreshold.position().x(), 1e-12);
        assertEquals(-.001d, atThreshold.position().y(), 1e-12);
    }

    @Test
    void stunnedWishUsesNoInputFrictionWhileUnstunnedWishUsesWithInputFriction() {
        CharacterMovementPolicy policy = new CharacterMovementPolicy(0d, 2.5d, 4.5d, 1d, 4d, .005d);
        CharacterMovementMotor motor = new CharacterMovementMotor(policy);
        CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true);
        CharacterMovementCommand wish = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementEnvironment environment = env(.1d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);

        CharacterMovementState moving = motor.tick(initial, wish, environment, false);
        CharacterMovementState stopped = motor.tick(initial, wish, environment, true);

        assertEquals(1.8d, moving.velocity().x(), 1e-12); // with-input friction
        assertEquals(1.2d, stopped.velocity().x(), 1e-12); // stun suppresses wish before friction selection
    }

    @Test
    void stunSuppressesVoluntaryWishButPreservesMomentumAndExternalImpulse() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(1d, 0d, 0d), false);
        CharacterMovementEnvironment environment = env(.1d, 0d, 0d, new MovementVector(2d, 0d, 0d), 2d, OPEN);
        CharacterMovementState stunned = motor.tick(initial,
                new CharacterMovementCommand(1d, 0d, true, true), environment, true);
        assertEquals(3d, stunned.velocity().x(), 0d);
        assertEquals(.3d, stunned.position().x(), 1e-12);
        assertEquals(0d, stunned.velocity().y(), 0d);

        CharacterMovementState unstunned = motor.tick(initial,
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.1d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertNotEquals(initial.velocity().x(), unstunned.velocity().x());
    }

    @Test
    void collisionWallBlocksDisplacementAndVelocityIntoWall() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver wall = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, requested.y(), requested.z()), grounded);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, wall), false);
        assertEquals(0d, result.position().x(), 0d);
        assertEquals(0d, result.velocity().x(), 0d);
    }

    @Test
    void repeatedInputsFromSameStateAreDeterministic() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(new MovementVector(2d, 3d, 4d), new MovementVector(.1d, -.2d, .3d), false);
        CharacterMovementCommand command = new CharacterMovementCommand(.4d, -.8d, false, true);
        CharacterMovementEnvironment environment = env(.05d, 9.8d, 0d, new MovementVector(.2d, 0d, -.1d), .3d, OPEN);
        assertEquals(motor.tick(initial, command, environment, false), motor.tick(initial, command, environment, false));
    }

    @Test
    void multiTickReplayFromIdenticalStateAndCommandHistoryIsDeterministic() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(new MovementVector(2d, 3d, 4d),
                new MovementVector(.1d, -.2d, .3d), true);
        CharacterMovementCommand[] history = {
                new CharacterMovementCommand(.4d, -.8d, false, true),
                new CharacterMovementCommand(.5d, 0d, false, false),
                new CharacterMovementCommand(0d, 1d, false, true),
                new CharacterMovementCommand(0d, 0d, false, false)
        };
        CharacterMovementEnvironment environment = env(.05d, 9.8d, 0d,
                new MovementVector(.2d, 0d, -.1d), .3d, OPEN);

        CharacterMovementState first = initial;
        CharacterMovementState replay = initial;
        for (int i = 0; i < 20; i++) {
            CharacterMovementCommand command = history[i % history.length];
            first = motor.tick(first, command, environment, false);
            replay = motor.tick(replay, command, environment, false);
            assertEquals(first, replay);
        }
    }

    @Test
    void rejectsNonfiniteAndOutOfBoundInputsAndCollisionDestinations() {
        assertThrows(IllegalArgumentException.class, () -> new MovementVector(Double.NaN, 0d, 0d));
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementCommand(Double.NaN, 0d, false, false));
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementCommand(Double.POSITIVE_INFINITY, 0d, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterMovementPolicy(Double.NaN, 1d, 1d, 1d, 1d, .005d));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterMovementPolicy(1d, 1d, 1d, 1d, -1d, .005d));
        assertThrows(IllegalArgumentException.class,
                () -> env(Double.NaN, 0d, 0d, MovementVector.ZERO, 0d, OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> env(.05d, 0d, 0d, new MovementVector(2d, 0d, 0d), 1d, OPEN));
        MovementCollisionResolver teleport = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(100d, 0d, 0d), grounded);
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true), new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, teleport), false));
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementCommand(1.01d, 0d, false, false));
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementCommand(0d, -1.01d, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> CharacterMovementPolicy.forCharacterPrototype("minecraft:zombie"));
    }

    @Test
    void directionChangeAcceleratesAlongWishWithoutRemovingLateralMomentum() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(0d, 0d, 3d), false);
        CharacterMovementState result = motor.tick(initial, new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(2.5d, result.velocity().x(), 1e-12);
        assertEquals(3d, result.velocity().z(), 1e-12);
    }

    @Test
    void gravityAirSteeringCannotBuildResultantSpeedByRotatingWish() throws IOException {
        for (String prototype : new String[]{"human.json", "pig.json"}) {
            CharacterMovementPolicy policy = CharacterMovementPolicy.fromCharacterData(readCharacter(prototype));
            CharacterMovementMotor motor = new CharacterMovementMotor(policy);
            for (boolean sprint : new boolean[]{false, true}) {
                double target = sprint ? policy.sprintSpeedPerSecond() : policy.walkSpeedPerSecond();
                MovementCollisionResolver takeoffResolver = (position, displacement, grounded) ->
                        new MovementCollisionResolver.CollisionResult(displacement, false);
                CharacterMovementEnvironment environment = env(.05d, 32d, 8.4d, MovementVector.ZERO, 0d,
                        takeoffResolver);
                CharacterMovementCommand takeoff = new CharacterMovementCommand(Math.sqrt(.5d), Math.sqrt(.5d), true, sprint);
                CharacterMovementState current = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                        takeoff, environment, false);
                assertTrue(current.velocity().horizontalLength() <= target + 1e-9,
                        prototype + " grounded takeoff exceeded declared speed");
                for (int tick = 0; tick < 1000; tick++) {
                    // Unit-length wishes rotate rapidly through cardinal and diagonal headings.
                    double angle = tick * Math.PI / 4d;
                    double x = Math.cos(angle);
                    double z = Math.sin(angle);
                    double before = current.velocity().horizontalLength();
                    current = motor.tick(current, new CharacterMovementCommand(x, z, false, sprint),
                            environment, false);
                    assertTrue(current.velocity().horizontalLength() <= Math.max(before, target) + 1e-9,
                            prototype + " gravity-air resultant grew beyond prior/target speed at tick " + tick);
                }
            }
        }
    }

    @Test
    void gravityAirBoundPreservesMomentumWithoutInputAndIncludesImpulseBeforeSteering() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment gravity = env(.05d, 32d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState fast = state(MovementVector.ZERO, new MovementVector(6d, 0d, 0d), false);
        CharacterMovementState coast = motor.tick(fast, new CharacterMovementCommand(0d, 0d, false, false), gravity, false);
        assertEquals(fast.velocity().x(), coast.velocity().x(), 0d);
        assertEquals(fast.velocity().z(), coast.velocity().z(), 0d,
                "no-input must not change airborne horizontal momentum");
        CharacterMovementState steered = motor.tick(fast,
                new CharacterMovementCommand(0d, 1d, false, true), gravity, false);
        assertTrue(steered.velocity().horizontalLength() <= 6d + 1e-12,
                "active steering may redirect but not add speed above carried momentum");

        CharacterMovementEnvironment impulse = env(.05d, 32d, 0d, new MovementVector(0d, 2d, 9d),
                10d, OPEN);
        CharacterMovementState fromImpulse = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, false),
                new CharacterMovementCommand(1d, 0d, false, true), impulse, false);
        assertEquals(9d, fromImpulse.velocity().horizontalLength(), 1e-12,
                "same-tick above-cap impulse is preserved as the air-steering envelope");
        assertEquals(2d - 32d * .05d, fromImpulse.velocity().y(), 1e-12,
                "horizontal bound preserves vertical impulse/gravity result");

        CharacterMovementEnvironment constrained = new CharacterMovementEnvironment(.05d, 32d, 0d, 1d,
                MovementVector.ZERO, 8d, 0d, OPEN, 1d, 1d, new MovementVector(8d, 0d, 0d));
        CharacterMovementState externalFlight = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, false),
                new CharacterMovementCommand(1d, 0d, false, true), constrained, false, true);
        assertEquals(8d, externalFlight.velocity().x(), 1e-12,
                "external flight bypasses voluntary steering bound");
        CharacterMovementState constrainedVoluntary = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, false),
                new CharacterMovementCommand(1d, 0d, false, true), constrained, false, false);
        assertEquals(12.5d, constrainedVoluntary.velocity().x(), 1e-12,
                "constraint impulse remains outside active voluntary gravity-air bound");
    }

    @Test
    void weightlessAirbornePerpendicularImpulseRetainsProjectedWishSnaking() throws IOException {
        CharacterMovementCommand forwardFast = new CharacterMovementCommand(1d, 0d, false, true);
        for (String prototype : new String[]{"human.json", "pig.json"}) {
            CharacterMovementPolicy policy = CharacterMovementPolicy.fromCharacterData(readCharacter(prototype));
            CharacterMovementMotor motor = new CharacterMovementMotor(policy);
            // Already carrying 3 blocks/s sideways; a separate 1 block/s impulse arrives this tick.
            CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(0d, 0d, 3d), false);
            CharacterMovementEnvironment impulse = env(.05d, 0d, 0d,
                    new MovementVector(0d, 0d, 1d), 1d, OPEN);
            CharacterMovementState moving = motor.tick(initial, forwardFast, impulse, false);
            double wish = policy.sprintSpeedPerSecond();

            assertEquals(wish, moving.velocity().x(), 1e-12, prototype + " projected wish");
            assertEquals(4d, moving.velocity().z(), 1e-12, prototype + " retained sideways speed");
            assertEquals(Math.hypot(wish, 4d), moving.velocity().horizontalLength(), 1e-12,
                    prototype + " resultant speed is not the projected-wish limit");
            assertEquals(wish * .05d, moving.position().x(), 1e-12);
            assertEquals(.2d, moving.position().z(), 1e-12);

            CharacterMovementState coasting = motor.tick(moving,
                    new CharacterMovementCommand(0d, 0d, false, false),
                    env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
            assertEquals(moving.velocity().horizontalLength(), coasting.velocity().horizontalLength(), 1e-12,
                    prototype + " no-input air tick retains both horizontal components");
        }
    }

    @Test
    void jumpingIntoOneBlockWallClipsBlockedAxisUntilClearanceButKeepsLateralMomentum() throws IOException {
        MovementCollisionResolver wallUntilOneBlockHigh = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(
                        position.y() + requested.y() >= 1d ? requested.x() : 0d,
                        requested.y(), requested.z()), false);
        for (String prototype : new String[]{"human.json", "pig.json"}) {
            CharacterMovementPolicy policy = CharacterMovementPolicy.fromCharacterData(readCharacter(prototype));
            CharacterMovementMotor motor = new CharacterMovementMotor(policy);
            CharacterMovementEnvironment environment = new CharacterMovementEnvironment(.05d, 32d, 8.4d,
                    .98d, MovementVector.ZERO, 0d, 0d, wallUntilOneBlockHigh);
            CharacterMovementEnvironment midairImpulse = new CharacterMovementEnvironment(.05d, 32d, 8.4d,
                    .98d, new MovementVector(0d, 0d, 3d), 3d, 0d, wallUntilOneBlockHigh);
            CharacterMovementState current = state(MovementVector.ZERO, MovementVector.ZERO, true);
            double wish = policy.sprintSpeedPerSecond();
            for (int tick = 0; tick < 2; tick++) {
                current = motor.tick(current, new CharacterMovementCommand(1d, 0d, tick == 0, true),
                        tick == 0 ? environment : midairImpulse, false);
                assertEquals(0d, current.position().x(), 0d, prototype + " wall blocks horizontal request");
                assertEquals(0d, current.velocity().x(), 0d, prototype + " blocked velocity cleared");
                assertEquals(tick == 0 ? 0d : 3d * wish / Math.hypot(wish, 3d), current.velocity().z(), 1e-12,
                        prototype + " blocked-axis momentum remains within gravity-air bound");
            }
            assertEquals(.7532d, current.position().y(), 1e-12);
            current = motor.tick(current, new CharacterMovementCommand(1d, 0d, false, true), environment, false);
            assertEquals(1.001336d, current.position().y(), 1e-12,
                    "third jump displacement crosses fixture clearance");
            assertTrue(current.velocity().x() > 0d, prototype + " clears wall with voluntary acceleration");
            assertTrue(current.velocity().horizontalLength() <= wish + 1e-12,
                    prototype + " gravity-air resultant is bounded after clearance");
            assertTrue(current.position().x() > 0d, prototype + " wall clearance permits movement");
            assertTrue(current.position().z() > 0d, prototype + " lateral movement remains");
        }
    }

    @Test
    void landingRetainsAirborneHorizontalSpeedThenNeutralGroundFrictionStopsIt() throws IOException {
        MovementCollisionResolver floor = (position, requested, grounded) -> {
            double y = position.y() + requested.y() < 0d ? -position.y() : requested.y();
            return new MovementCollisionResolver.CollisionResult(
                    new MovementVector(requested.x(), y, requested.z()), y != requested.y() || grounded);
        };
        for (String prototype : new String[]{"human.json", "pig.json"}) {
            CharacterMovementPolicy policy = CharacterMovementPolicy.fromCharacterData(readCharacter(prototype));
            CharacterMovementMotor motor = new CharacterMovementMotor(policy);
            CharacterMovementEnvironment environment = env(.05d, 32d, 8.4d,
                    MovementVector.ZERO, 0d, floor);
            double wish = policy.sprintSpeedPerSecond();
            CharacterMovementState falling = state(new MovementVector(0d, .1d, 0d),
                    new MovementVector(wish, -4d, 3d), false);
            CharacterMovementCommand noInput = new CharacterMovementCommand(0d, 0d, false, false);
            CharacterMovementState landed = motor.tick(falling, noInput, environment, false);
            assertTrue(landed.onGround());
            assertEquals(0d, landed.position().y(), 1e-12);
            assertEquals(0d, landed.velocity().y(), 0d);
            assertEquals(wish, landed.velocity().x(), 1e-12);
            assertEquals(3d, landed.velocity().z(), 1e-12);
            assertEquals(Math.hypot(wish, 3d), landed.velocity().horizontalLength(), 1e-12,
                    prototype + " landing tick still uses airborne friction rule");
            assertEquals(wish * .05d, landed.position().x(), 1e-12);
            assertEquals(.15d, landed.position().z(), 1e-12);

            CharacterMovementState stopped = motor.tick(landed, noInput, environment, false);
            assertTrue(stopped.onGround());
            assertEquals(0d, stopped.velocity().horizontalLength(), 0d,
                    prototype + " 20/s friction at .05s gives factor zero");
            assertEquals(landed.position().x(), stopped.position().x(), 0d);
            assertEquals(landed.position().z(), stopped.position().z(), 0d);
        }
    }

    @Test
    void groundedStunPreservesSlipUnderLinearFrictionAndGravity() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver wallAndGround = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, 0d, displacement.z()), true);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, new MovementVector(2d, 0d, 2d), true),
                new CharacterMovementCommand(1d, 0d, true, true),
                env(.1d, 10d, 7d, MovementVector.ZERO, 0d, wallAndGround), true);
        assertEquals(0d, result.velocity().x(), 0d); // wall blocked the horizontal axis
        assertEquals(0d, result.velocity().y(), 0d); // grounded collision clears downward velocity
        assertEquals(0d, result.position().z(), 0d); // high neutral-ground friction applies before stunned movement
    }

    @Test
    void slipperySurfaceReducesGroundCoastingAndRightAngleSteeringWithoutChangingTargetSpeed() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(2.5d, 0d, 0d), true);
        CharacterMovementCommand noWish = new CharacterMovementCommand(0d, 0d, false, false);
        CharacterMovementState normalCoast = motor.tick(initial, noWish,
                envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN), false);
        CharacterMovementState lubeCoast = motor.tick(initial, noWish,
                envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, .05d, OPEN), false);
        assertEquals(0d, normalCoast.velocity().x(), 0d);
        assertEquals(2.375d, lubeCoast.velocity().x(), 1e-12);
        assertTrue(lubeCoast.velocity().horizontalLength() < initial.velocity().horizontalLength(),
                "contact friction still brakes, but must not add speed");

        CharacterMovementState lateral = state(MovementVector.ZERO, new MovementVector(0d, 0d, 2d), true);
        CharacterMovementCommand turn = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementState normalTurn = motor.tick(lateral, turn,
                envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN), false);
        CharacterMovementState lubeTurn = motor.tick(lateral, turn,
                envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, .05d, OPEN), false);
        assertEquals(2.5d, normalTurn.velocity().x(), 1e-12);
        assertEquals(.125d, lubeTurn.velocity().x(), 1e-12);
        assertTrue(lubeTurn.velocity().z() > normalTurn.velocity().z());
        assertEquals(1.9d, lubeTurn.velocity().z(), 1e-12, "contact does not erase lateral momentum");
    }

    @Test
    void stationaryGroundedTicksProbeSupportAndKeepLubeAccelerationBounded() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver floor = (position, displacement, grounded) -> {
            if (displacement.y() < 0d && position.y() <= 0d) {
                return new MovementCollisionResolver.CollisionResult(
                        new MovementVector(displacement.x(), 0d, displacement.z()), true);
            }
            return new MovementCollisionResolver.CollisionResult(displacement, false);
        };
        CharacterMovementEnvironment lube = new CharacterMovementEnvironment(1d / 20d, 32d, 0d, 1d,
                MovementVector.ZERO, 0d, 0d, floor, .05d,
                CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR);
        CharacterMovementCommand knockdownWish = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementState state = state(MovementVector.ZERO, MovementVector.ZERO, true);

        for (int tick = 0; tick < 8; tick++) {
            double previousSpeed = state.velocity().x();
            state = motor.tick(state, knockdownWish, lube, false);
            assertTrue(state.onGround(), "a supporting floor must survive a stationary grounded tick");
            assertEquals(0d, state.position().y(), 0d);
            assertTrue(state.velocity().x() - previousSpeed <= .09d,
                    "per-tick lube-scaled acceleration must not jump to 1.8 blocks/s");
        }
        assertEquals(.05d * (1d - Math.pow(.95d, 8)) / .05d, state.velocity().x(), 1e-12,
                "lube-scaled knockdown acceleration remains bounded each tick instead of jumping to 1.8 blocks/s");

        CharacterMovementEnvironment lubeNoInput = envWithSurface(1d / 20d, 32d, 0d,
                MovementVector.ZERO, 0d, .05d, floor);
        CharacterMovementState coasting = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false), lubeNoInput, false);
        assertEquals(1.9d, coasting.velocity().x(), 1e-12,
                "grounded no-input momentum retains the configured .95 lube friction factor");
        assertTrue(coasting.onGround());
    }

    @Test
    void groundedSupportProbeDoesNotOverrideUpwardImpulseOrJumpDisplacement() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment environment = env(.05d, 32d, 8.4d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState jump = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, true, false), environment, false);
        assertEquals(.42d, jump.position().y(), 1e-12);

        CharacterMovementEnvironment upwardImpulseEnvironment = new CharacterMovementEnvironment(.05d, 32d,
                8.4d, .98d, new MovementVector(0d, 1d, 0d), 1d, 0d,
                (position, displacement, grounded) -> {
                    assertTrue(displacement.y() > 0d, "an upward impulse must not receive a downward probe");
                    return new MovementCollisionResolver.CollisionResult(displacement, false);
                });
        motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false), upwardImpulseEnvironment, false);
    }

    @Test
    void stickySurfaceIncreasesGroundDampingAndAccelerationWithoutChangingTargetSpeed() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment normal = envWithSurface(.025d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN);
        CharacterMovementEnvironment sticky = envWithSurface(.025d, 0d, 0d, MovementVector.ZERO, 0d, 1.5d, OPEN);

        CharacterMovementState normalCoast = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false), normal, false);
        CharacterMovementState stickyCoast = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false), sticky, false);
        assertEquals(1d, normalCoast.velocity().x(), 1e-12);
        assertEquals(.5d, stickyCoast.velocity().x(), 1e-12);

        CharacterMovementCommand forward = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementEnvironment normalAccelerationEnvironment =
                envWithSurface(.01d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN);
        CharacterMovementEnvironment stickyAccelerationEnvironment =
                envWithSurface(.01d, 0d, 0d, MovementVector.ZERO, 0d, 1.5d, OPEN);
        CharacterMovementState normalAccelerating = motor.tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true), forward, normalAccelerationEnvironment, false);
        CharacterMovementState stickyAccelerating = motor.tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true), forward, stickyAccelerationEnvironment, false);
        assertTrue(stickyAccelerating.velocity().x() > normalAccelerating.velocity().x());

        CharacterMovementState normalAtTarget = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState stickyAtTarget = normalAtTarget;
        for (int i = 0; i < 1200; i++) {
            normalAtTarget = motor.tick(normalAtTarget, forward, normalAccelerationEnvironment, false);
            stickyAtTarget = motor.tick(stickyAtTarget, forward, stickyAccelerationEnvironment, false);
        }
        assertEquals(CharacterMovementPolicy.HUMAN.walkSpeedPerSecond(), normalAtTarget.velocity().x(), 1e-9);
        assertEquals(normalAtTarget.velocity().x(), stickyAtTarget.velocity().x(), 1e-9);

        assertEquals(1d, env(.1d, 0d, 0d, MovementVector.ZERO, 0d, OPEN).surfaceMovementFactor());
    }

    @Test
    void surfaceMovementFactorMustBeFiniteAndNonnegative() {
        assertThrows(IllegalArgumentException.class,
                () -> envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, -0.1d, OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, Double.NaN, OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, Double.POSITIVE_INFINITY, OPEN));
    }

    @Test
    void stunnedSlipMomentumGetsContactFrictionAndContactExitImmediatelyRestoresNormalFriction() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementCommand blockedWish = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementState slipping = state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true);
        CharacterMovementState stunnedLube = motor.tick(slipping, blockedWish,
                envWithSurface(.1d, 0d, 0d, MovementVector.ZERO, 0d, .05d, OPEN), true);
        CharacterMovementState stunnedNormal = motor.tick(slipping, blockedWish,
                envWithSurface(.1d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN), true);
        assertEquals(1.8d, stunnedLube.velocity().x(), 1e-12);
        assertEquals(0d, stunnedNormal.velocity().x(), 0d);
        assertEquals(0d, stunnedLube.velocity().z(), 0d);
        assertTrue(stunnedLube.position().x() > 0d, "friction must not fabricate an immediate stop");

        // Environment snapshots are sampled per tick: once contact is gone the next tick is neutral,
        // with no retained surface factor or post-contact glide timer.
        CharacterMovementState leftSource = motor.tick(stunnedLube,
                new CharacterMovementCommand(0d, 0d, false, false),
                envWithSurface(.1d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN), true);
        assertEquals(0d, leftSource.velocity().x(), 0d);
    }

    @Test
    void nonzeroJumpUsesInjectedVelocityAndGravity() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, true, false),
                env(.1d, 10d, 5d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(.5d, result.position().y(), 1e-12);
        assertEquals(4d, result.velocity().y(), 1e-12);
    }

    @Test
    void vanillaJumpUsesPreGravityVelocityAndFallsBackToGround() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        double dt = 1d / 20d;
        double gravity = .08d * 20d * 20d;
        double jump = .42d * 20d;
        MovementCollisionResolver floor = (position, displacement, grounded) -> {
            if (position.y() + displacement.y() < 0d) {
                return new MovementCollisionResolver.CollisionResult(
                        new MovementVector(displacement.x(), -position.y(), displacement.z()), true);
            }
            return new MovementCollisionResolver.CollisionResult(displacement, false);
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(dt, gravity, jump, .98d,
                MovementVector.ZERO, 0d, 0d, floor);
        CharacterMovementState state = state(MovementVector.ZERO, MovementVector.ZERO, true);
        state = motor.tick(state, new CharacterMovementCommand(0d, 0d, true, false), environment, false);
        assertEquals(.42d, state.position().y(), 1e-12);
        double maximumRise = state.position().y();
        for (int tick = 0; tick < 100 && !state.onGround(); tick++) {
            state = motor.tick(state, new CharacterMovementCommand(0d, 0d, false, false), environment, false);
            maximumRise = Math.max(maximumRise, state.position().y());
        }
        assertTrue(maximumRise >= 1.20d, "vanilla jump should rise about 1.25 blocks: " + maximumRise);
        assertTrue(maximumRise <= 1.30d, "vanilla jump rise should remain near 1.25 blocks: " + maximumRise);
        assertTrue(state.onGround(), "falling jump should resolve back onto the floor");
        assertEquals(0d, state.velocity().y(), 0d);

        CharacterMovementState nextJump = motor.tick(state,
                new CharacterMovementCommand(0d, 0d, true, false), environment, false);
        assertEquals(.42d, nextJump.position().y() - state.position().y(), 1e-12,
                "a fresh grounded jump input should apply its full first-tick displacement");
    }

    @Test
    void jumpClearsOneBlockObstacleWithAValidatedCollisionFixture() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver oneBlockObstacle = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(
                        new MovementVector(position.y() + displacement.y() >= 1d ? displacement.x() : 0d,
                                displacement.y(), displacement.z()), false);
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(.05d, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, 0d, oneBlockObstacle);
        CharacterMovementState state = state(MovementVector.ZERO, MovementVector.ZERO, true);
        for (int tick = 0; tick < 20 && state.position().x() == 0d; tick++) {
            state = motor.tick(state, new CharacterMovementCommand(1d, 0d, tick == 0, false), environment, false);
        }
        assertTrue(state.position().y() >= 1d, "horizontal progress should begin only after clearing the block height");
        assertTrue(state.position().x() > 0d, "the jump should make horizontal progress beyond a one-block obstacle");
    }

    @Test
    void groundedResolverMayReportBoundedStepUpForDownwardRequest() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver step = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(displacement.x(), .4d, displacement.z()), true);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 10d, 0d, MovementVector.ZERO, 0d, .5d, step), false);
        assertEquals(.4d, result.position().y(), 0d);
        assertEquals(0d, result.velocity().y(), 0d);

        MovementCollisionResolver tooHigh = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(displacement.x(), .6d, displacement.z()), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 10d, 0d, MovementVector.ZERO, 0d, .5d, tooHigh), false));

        MovementCollisionResolver stationaryRise = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, .4d, 0d), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 10d, 0d, MovementVector.ZERO, 0d, .5d, stationaryRise), false));
    }

    @Test
    void coordinateSubtractionRoundingDoesNotRejectMovementOrSuppressMomentum() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementVector position = new MovementVector(1e7, 1e7, 1e7);
        MovementCollisionResolver entityMoveRounding = (before, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(
                        (before.x() + requested.x()) - before.x(),
                        (before.y() + requested.y()) - before.y(),
                        (before.z() + requested.z()) - before.z()), grounded);
        CharacterMovementState result = motor.tick(
                state(position, new MovementVector(1d, 0d, 1d), false),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, entityMoveRounding), true);

        assertEquals((position.x() + .05d) - position.x(), result.position().x() - position.x(), 0d);
        assertEquals(1d, result.velocity().x(), 0d);
        assertEquals(1d, result.velocity().z(), 0d);
    }

    @Test
    void coordinateSubtractionRoundingIsAllowedNearMinecraftWorldBorder() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementVector position = new MovementVector(29_999_999.9d, 29_999_999.9d, 29_999_999.9d);
        MovementCollisionResolver entityMoveRounding = (before, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(
                        (before.x() + requested.x()) - before.x(),
                        (before.y() + requested.y()) - before.y(),
                        (before.z() + requested.z()) - before.z()), grounded);
        CharacterMovementState result = motor.tick(
                state(position, new MovementVector(1d, 0d, 1d), false),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, entityMoveRounding), true);

        assertEquals((position.x() + .05d) - position.x(), result.position().x() - position.x(), 0d);
        assertEquals(1d, result.velocity().x(), 0d);
        assertEquals(1d, result.velocity().z(), 0d);
    }

    @Test
    void coordinateRoundingAllowanceDoesNotGrowAtAbsurdFinitePositions() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver oneBlockTeleport = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(1d, requested.y(), requested.z()), grounded);

        assertThrows(IllegalArgumentException.class, () -> motor.tick(
                state(new MovementVector(1e20d, 0d, 0d), MovementVector.ZERO, false),
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, oneBlockTeleport), true));
    }

    @Test
    void groundedStepUsesLargerOfRequestedRiseAndStepHeightAndRequiresHorizontalStep() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver step = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(
                        new MovementVector(displacement.x(), .5d, displacement.z()), true);
        CharacterMovementState stepped = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, step), false);
        assertEquals(.5d, stepped.position().y(), 0d);

        MovementCollisionResolver tooHigh = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(displacement.x(), .6d, displacement.z()), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, tooHigh), false));

        MovementCollisionResolver noHorizontalStep = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, .6d, 0d), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, noHorizontalStep), false));

        MovementCollisionResolver wallStep = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, .6d, displacement.z()), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, wallStep), false));

        MovementCollisionResolver teleportStep = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(displacement.x(), 5d, displacement.z()), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, teleportStep), false));
    }

    @Test
    void descendingLandingMayResolveBoundedHorizontalStepWhenPreviouslyAirborne() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState falling = state(MovementVector.ZERO, new MovementVector(0d, -2d, 0d), false);
        CharacterMovementCommand walking = new CharacterMovementCommand(1d, 0d, false, false);
        MovementCollisionResolver landingStep = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(
                        new MovementVector(requested.x(), .4d, requested.z()), true);
        CharacterMovementState stepped = motor.tick(falling, walking,
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, .5d, landingStep), false);
        assertEquals(.4d, stepped.position().y(), 0d);
        assertTrue(stepped.onGround());

        MovementCollisionResolver notLanded = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(
                        new MovementVector(requested.x(), .4d, requested.z()), false);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(falling, walking,
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, .5d, notLanded), false));

        CharacterMovementState ascending = state(MovementVector.ZERO, new MovementVector(0d, 2d, 0d), false);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(ascending, walking,
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, .5d, landingStep), false));

        MovementCollisionResolver tooHigh = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(
                        new MovementVector(requested.x(), .6d, requested.z()), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(falling, walking,
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, .5d, tooHigh), false));
    }

    @Test
    void externalFlightRetainsLaunchMomentumAndSuppressesInputAndGroundFriction() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState start = state(MovementVector.ZERO, new MovementVector(10d, 0d, 0d), true);
        CharacterMovementState flight = start;
        for (int tick = 0; tick < 6; tick++) {
            flight = motor.tick(flight, new CharacterMovementCommand(1d, 0d, true, true),
                    env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false, true);
            assertEquals(10d, flight.velocity().x(), 1e-12, "external momentum is not accumulated or damped");
        }
        assertEquals(3d, flight.position().x(), 1e-12);
        assertEquals(0d, flight.position().y(), 1e-12);
        CharacterMovementState landed = motor.tick(flight,
                new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(0d, landed.velocity().x(), 0d, "ordinary ground friction resumes after flight ends");
    }

    @Test
    void ordinaryPhysicsMustTakeGroundedFrictionStepAfterAirborneThrowExpires() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver floor = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(
                        new MovementVector(displacement.x(), -position.y(), displacement.z()), true);
        CharacterMovementEnvironment environment = env(.05d, 32d, 0d, MovementVector.ZERO, 0d, floor);
        CharacterMovementState airborne = state(new MovementVector(0d, .1d, 0d),
                new MovementVector(10d, -4d, 0d), false);
        CharacterMovementCommand idle = new CharacterMovementCommand(0d, 0d, false, false);

        CharacterMovementState landed = motor.tick(airborne, idle, environment, false, false);
        assertTrue(landed.onGround());
        assertEquals(10d, landed.velocity().x(), 0d,
                "the collision landing tick retains horizontal momentum; tracking must not end yet");
        CharacterMovementState settled = motor.tick(landed, idle, environment, false, false);
        assertEquals(0d, settled.velocity().horizontalLength(), 0d,
                "the following normal grounded idle step applies native-profile friction");
    }

    @Test
    void westwardLandingWithNegativeZeroVelocityIsPhysicallySettled() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment environment = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementCommand idle = new CharacterMovementCommand(0d, 0d, false, false);
        CharacterMovementState landed = state(MovementVector.ZERO, new MovementVector(-10d, 0d, 0d), true);
        CharacterMovementState settled = motor.tick(landed, idle, environment, false);
        assertEquals(0d, settled.velocity().x(), 0d);
        assertEquals(0d, settled.velocity().x() * settled.velocity().x()
                        + settled.velocity().y() * settled.velocity().y()
                        + settled.velocity().z() * settled.velocity().z(), 0d,
                "negative zero from westward friction is still physically settled");
    }

    private static CharacterMovementState state(MovementVector position, MovementVector velocity, boolean grounded) {
        return new CharacterMovementState(position, velocity, grounded);
    }

    private static CharacterMovementEnvironment env(double dt, double gravity, double jump,
                                                    MovementVector impulse, double impulseBound,
                                                    MovementCollisionResolver resolver) {
        return env(dt, gravity, jump, impulse, impulseBound, 0d, resolver);
    }

    private static CharacterMovementEnvironment env(double dt, double gravity, double jump,
                                                     MovementVector impulse, double impulseBound, double maxStep,
                                                     MovementCollisionResolver resolver) {
        return new CharacterMovementEnvironment(dt, gravity, jump, 1d, impulse, impulseBound, maxStep, resolver);
    }

    private static CharacterMovementEnvironment envWithSurface(double dt, double gravity, double jump,
                                                                MovementVector impulse, double impulseBound,
                                                                double surfaceFactor,
                                                                MovementCollisionResolver resolver) {
        return new CharacterMovementEnvironment(dt, gravity, jump, 1d, impulse, impulseBound, 0d, resolver,
                surfaceFactor);
    }

    private static CharacterMovementEnvironment envWithVoluntarySpeed(double dt, double gravity, double jump,
                                                                        double speedFactor,
                                                                        MovementCollisionResolver resolver) {
        return new CharacterMovementEnvironment(dt, gravity, jump, 1d, MovementVector.ZERO,
                0d, 0d, resolver, 1d, speedFactor);
    }

    @Test
    void externalFlightRetainsVerticalLaunchMomentumThenResumesAdapterGravity() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(.05d, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, 0d, OPEN);
        CharacterMovementState current = state(MovementVector.ZERO, new MovementVector(0d, 10d, 0d), false);
        CharacterMovementCommand noInput = new CharacterMovementCommand(0d, 0d, false, false);

        for (int tick = 0; tick < 6; tick++) {
            current = motor.tick(current, noInput, environment, false, true);
            assertEquals(10d, current.velocity().y(), 0d, "flight lease retains vertical launch momentum");
        }
        assertEquals(3d, current.position().y(), 1e-12);

        current = motor.tick(current, noInput, environment, false, false);
        assertEquals(8.232d, current.velocity().y(), 1e-12,
                "adapter gravity and vertical drag resume when the lease expires");
    }

    @Test
    void externalFlightCollisionClipsUpwardVelocityWithoutReapplyingLaunchMomentum() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver ceiling = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(displacement.x(), 0d, displacement.z()), false);
        CharacterMovementEnvironment environment = env(.05d, 32d, 8.4d, MovementVector.ZERO, 0d, ceiling);
        CharacterMovementState blocked = motor.tick(state(MovementVector.ZERO,
                        new MovementVector(0d, 10d, 0d), false),
                new CharacterMovementCommand(0d, 0d, false, false), environment, false, true);

        assertEquals(0d, blocked.position().y(), 0d);
        assertEquals(0d, blocked.velocity().y(), 0d);
    }
}
