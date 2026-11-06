package com.juicyslew.moonstation14.ms14.movement;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HorizontalContactEvidenceTest {
    @Test void orderedEvidenceBindsEveryProjectionAndProjectsVelocityInRecordedOrder() {
        var request = new MovementVector(-1, 0, -1);
        var firstInput = new MovementVector(-.5, 0, -.5);
        var afterX = new MovementVector(0, 0, -.5);
        var secondInput = new MovementVector(-.5, 0, -1);
        var afterSecondX = new MovementVector(0, 0, -1);
        var accepted = new MovementVector(0, 0, 0);
        var path = List.of(
                new HorizontalContactEvidence.Projection(1, firstInput, afterX, new MovementVector(.5,0,0), 1, 0, 0),
                new HorizontalContactEvidence.Projection(2, secondInput, afterSecondX, new MovementVector(.5,0,0), 1, 0, 0),
                new HorizontalContactEvidence.Projection(2, afterSecondX, accepted, new MovementVector(0,0,1), 0, 0, 1));
        var evidence = HorizontalContactEvidence.validateOrdered(request, accepted, 2, path).orElseThrow();
        assertTrue(evidence.matches(request, accepted));
        assertFalse(evidence.matches(request, afterX));
        assertEquals(MovementVector.ZERO, evidence.projectVelocityTangent(new MovementVector(-2, 0, -3)));
        assertTrue(HorizontalContactEvidence.validateOrdered(request, accepted, 2,
                List.of(path.get(2))).isEmpty());
        assertTrue(HorizontalContactEvidence.validateOrdered(request, accepted, 2,
                List.of(path.get(0), new HorizontalContactEvidence.Projection(2, new MovementVector(0, 0, -.5),
                        accepted.add(new MovementVector(0, 0, 1e-5)), new MovementVector(0,0,1), 0, 0, 1))).isEmpty());
        assertTrue(HorizontalContactEvidence.validateOrdered(request, accepted, 2,
                List.of(path.get(0), path.get(1), new HorizontalContactEvidence.Projection(2, afterSecondX,
                        accepted, new MovementVector(0,0,1.0000001), 0, 0, 1))).isEmpty(),
                "a changed recorded clip delta cannot be used as an unverified movement impulse");
    }

    @Test void orderedEvidenceAllowsOnlyFloorOrCeilingPolicyYClippingTowardZero() {
        var floorRequest = new MovementVector(-1, -.2, -1);
        var floorInput = new MovementVector(-.5, 0, -.5);
        var floorOutput = new MovementVector(0, 0, -.5);
        var floorPath = List.of(new HorizontalContactEvidence.Projection(1, floorInput, floorOutput,
                new MovementVector(.5,0,0), 1,0,0),
                new HorizontalContactEvidence.Projection(2, new MovementVector(-.5,0,-1),
                        new MovementVector(-.5,0,-.5), new MovementVector(0,0,.5), 0,0,1));
        var floorAccepted = new MovementVector(-.5, -.2, -.5);
        assertTrue(HorizontalContactEvidence.validateOrdered(floorRequest, floorAccepted, 2, floorPath).isPresent());

        var ceilingRequest = new MovementVector(-1, .2, -1);
        var ceilingInput = new MovementVector(-.5, .05, -.5);
        var ceilingOutput = new MovementVector(0, .05, -.5);
        var ceilingPath = List.of(new HorizontalContactEvidence.Projection(1, ceilingInput, ceilingOutput,
                new MovementVector(.5,0,0), 1,0,0),
                new HorizontalContactEvidence.Projection(2, new MovementVector(-.5,.05,-1),
                        new MovementVector(-.5,.05,-.5), new MovementVector(0,0,.5), 0,0,1));
        assertTrue(HorizontalContactEvidence.validateOrdered(ceilingRequest,
                new MovementVector(-.5,.2,-.5), 2, ceilingPath).isPresent());

        assertTrue(HorizontalContactEvidence.validateOrdered(floorRequest, floorAccepted, 2,
                List.of(new HorizontalContactEvidence.Projection(1,
                        new MovementVector(-.5, -.000001, -.5), floorOutput,
                        new MovementVector(.5,0,0), 1,0,0))).isEmpty(),
                "floor-prefix Y cannot become oppositely signed");
        assertTrue(HorizontalContactEvidence.validateOrdered(floorRequest, floorAccepted, 2,
                List.of(new HorizontalContactEvidence.Projection(1,
                        new MovementVector(-.5, -.11, -.5), floorOutput,
                        new MovementVector(.5,0,0), 1,0,0))).isEmpty(),
                "floor-prefix cannot exceed the requested prefix");
        assertTrue(HorizontalContactEvidence.validateOrdered(ceilingRequest,
                new MovementVector(-.5,.2,-.5), 2, List.of(new HorizontalContactEvidence.Projection(1,
                        ceilingInput, new MovementVector(0, Math.nextUp(ceilingInput.y()), -.5),
                        new MovementVector(.5,0,0), 1,0,0))).isEmpty(),
                "contact projection cannot change vertical input");
        assertTrue(HorizontalContactEvidence.validateOrdered(floorRequest, floorAccepted, 2,
                List.of(new HorizontalContactEvidence.Projection(1, floorInput, floorOutput,
                        new MovementVector(.5,1e-12,0), 1,0,0))).isEmpty(),
                "recorded clip delta Y must remain exactly zero");
        var side = new HorizontalContactEvidence.Projection(1, floorInput, floorOutput,
                new MovementVector(.5,0,0), 1,0,0);
        var vertical = new HorizontalContactEvidence.Projection(2, new MovementVector(-.5,0,-1),
                new MovementVector(-.4999989,0,-1), new MovementVector(1.1e-6,0,0), 0,1,0);
        assertTrue(HorizontalContactEvidence.validateOrdered(floorRequest,
                new MovementVector(-.4999989,-.2,-1), 2, List.of(side,vertical)).isEmpty(),
                "a vertical-policy plane cannot contribute horizontal displacement beyond unchanged residual");

        var sideAtStepOne = new HorizontalContactEvidence.Projection(1, floorInput, floorOutput,
                new MovementVector(.5,0,0), 1,0,0);
        var verticalNormalX = 1.0e-6;
        var verticalNormalLength = Math.sqrt(1d + verticalNormalX * verticalNormalX);
        var smallResidueOne = new HorizontalContactEvidence.Projection(2,
                new MovementVector(-.5,0,-1), new MovementVector(-.49999925,0,-1),
                new MovementVector(.75e-6,0,0), verticalNormalX/verticalNormalLength,
                1d/verticalNormalLength, 0);
        var smallResidueTwo = new HorizontalContactEvidence.Projection(2,
                new MovementVector(-.49999925,0,-1), new MovementVector(-.4999985,0,-1),
                new MovementVector(.75e-6,0,0), verticalNormalX/verticalNormalLength,
                1d/verticalNormalLength, 0);
        var twoResiduePath = List.of(sideAtStepOne,smallResidueOne,smallResidueTwo);
        assertTrue(HorizontalContactEvidence.validateOrdered(floorRequest,
                new MovementVector(-.4999985,-.2,-1),2,twoResiduePath).isEmpty(),
                "multiple individually sub-slop vertical residues cannot exceed the global residual budget");
        var reverseRequest = new MovementVector(-1,-.2,-.5e-6);
        var reverseSide = new HorizontalContactEvidence.Projection(1,
                new MovementVector(-.5,0,-.25e-6),new MovementVector(0,0,-.25e-6),
                new MovementVector(.5,0,0),1,0,0);
        var verticalNormalZ = 1e-6;
        var verticalNormalZLength = Math.sqrt(1d+verticalNormalZ*verticalNormalZ);
        var oppositeOne = new HorizontalContactEvidence.Projection(2,
                new MovementVector(-.5,0,-.5e-6),new MovementVector(-.5,0,.25e-6),
                new MovementVector(0,0,.75e-6),0,1d/verticalNormalZLength,verticalNormalZ/verticalNormalZLength);
        var oppositeTwo = new HorizontalContactEvidence.Projection(2,
                new MovementVector(-.5,0,.25e-6),new MovementVector(-.5,0,-.5e-6),
                new MovementVector(0,0,-.75e-6),0,1d/verticalNormalZLength,-verticalNormalZ/verticalNormalZLength);
        assertTrue(HorizontalContactEvidence.validateOrdered(reverseRequest,
                new MovementVector(-.5,-.2,-.5e-6),2,List.of(reverseSide,oppositeOne,oppositeTwo)).isEmpty(),
                "opposite signed residues cannot cancel out of the global magnitude accounting");
        assertTrue(HorizontalContactEvidence.validateOrdered(floorRequest,
                new MovementVector(-.5,-.2,-1),2,List.of(sideAtStepOne,
                        new HorizontalContactEvidence.Projection(2,new MovementVector(-.5,0,-1),
                                new MovementVector(-.5,0,-1),MovementVector.ZERO,Double.NaN,1,0))).isEmpty(),
                "even zero-delta rows require finite unit normals");
        assertTrue(HorizontalContactEvidence.validateOrdered(floorRequest,
                new MovementVector(-.5,-.2,-1),2,List.of(sideAtStepOne,
                        new HorizontalContactEvidence.Projection(2,new MovementVector(-.5,0,-1),
                                new MovementVector(-.49999925,0,-1),new MovementVector(.75e-6,0,0),
                                1e-6,.5,0))).isEmpty(),"nonunit vertical normal is rejected");
        assertTrue(HorizontalContactEvidence.validateOrdered(floorRequest,
                new MovementVector(-.49999925,-.2,-1),2,List.of(sideAtStepOne,
                        new HorizontalContactEvidence.Projection(2,new MovementVector(-.5,0,-1),
                                new MovementVector(-.49999925,0,-1),new MovementVector(.75e-6,0,0),
                                -verticalNormalX/verticalNormalLength,1d/verticalNormalLength,0))).isEmpty(),
                "a response away from the supplied normal is rejected");
    }
    private static final double DIAGONAL = Math.sqrt(.5d);

    @Test
    void rejectsUnexplainedNearAxisMicrocontactBeyondExistingSlop() {
        MovementVector request = new MovementVector(-.01682160017, -.1506214528, .20414943073);
        MovementVector accepted = new MovementVector(.000001166064976, -.15062145606, .20414944568);
        assertTrue(HorizontalContactEvidence.validate(request, accepted, 1d, 9.132e-7).isEmpty(),
                "the observed +1.166e-6 X remains 1.353e-6 beyond exact full projection and cannot pass the 1e-6 margin");
    }

    @Test
    void usesFullNativeTiltedNormalWithoutIncreasingResidualAllowance() {
        MovementVector request = new MovementVector(.110485241434, .42d, .110485627686);
        MovementVector accepted = new MovementVector(-2.1566589e-6, .419999624366, .110485440931);
        double nx = -1d, ny = -3.781e-6, nz = -1.633e-6;
        var evidence = HorizontalContactEvidence.validate(request, accepted, nx, ny, nz).orElseThrow();
        assertTrue(HorizontalContactEvidence.validate(request, accepted, nx, 0d, nz).isEmpty());
        assertEquals(ny, evidence.normalY(), 1e-12);

        // A larger unexplained tangent correction and excessive normal clipping remain refused.
        assertTrue(HorizontalContactEvidence.validate(request,
                new MovementVector(accepted.x(), accepted.y(), accepted.z() + 1.352e-6), nx, ny, nz).isEmpty());
        assertTrue(HorizontalContactEvidence.validate(request,
                new MovementVector(-1e-3, accepted.y(), accepted.z()), nx, ny, nz).isEmpty());
        assertTrue(HorizontalContactEvidence.validate(request, accepted, .1d, Math.sqrt(.99d), 0d).isEmpty(),
                "near-floor/vertical normals are outside the horizontal side-plane certificate domain");
    }

    @Test
    void certifiesFullAndPartialObliqueClipsAndPreservesContactTangentVelocity() {
        MovementVector request = new MovementVector(-.3d, 0d, .1d);
        var full = HorizontalContactEvidence.validate(request, new MovementVector(-.2d, 0d, .2d), DIAGONAL, DIAGONAL)
                .orElseThrow();
        var partial = HorizontalContactEvidence.validate(request, new MovementVector(-.25d, 0d, .15d), DIAGONAL, DIAGONAL)
                .orElseThrow();
        assertVectorEquals(new MovementVector(-4d, 3d, 4d), full.projectVelocityTangent(new MovementVector(-6d, 3d, 2d)));
        assertVectorEquals(new MovementVector(-4d, 3d, 4d), partial.projectVelocityTangent(new MovementVector(-6d, 3d, 2d)));
        assertTrue(partial.matches(request, new MovementVector(-.25d, 0d, .15d)));
        assertFalse(partial.matches(request, new MovementVector(-.249d, 0d, .15d)));
        assertFalse(partial.matches(new MovementVector(-.3d, 0d, .101d),
                new MovementVector(-.25d, 0d, .15d)));
    }

    @Test
    void motorAllowsOnlyCertifiedHorizontalPlaneClippingAndProjectsVelocityTangentially() {
        MovementVector actual = new MovementVector(-.25d, 0d, .15d);
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        var env = environment((position, requested, grounded) -> {
            HorizontalContactEvidence evidence = HorizontalContactEvidence.validate(requested, actual, DIAGONAL, DIAGONAL)
                    .orElseThrow();
            return new MovementCollisionResolver.CollisionResult(actual, false, evidence);
        });
        CharacterMovementState result = motor.tick(new CharacterMovementState(MovementVector.ZERO,
                        new MovementVector(-6d, 0d, 2d), false), new CharacterMovementCommand(0d, 0d, false, false),
                env, false);
        assertEquals(actual, result.position());
        assertEquals(-4d, result.velocity().x(), 1e-12);
        assertEquals(4d, result.velocity().z(), 1e-12);

        var strict = environment((position, ignored, grounded) -> new MovementCollisionResolver.CollisionResult(actual, false));
        assertThrows(IllegalArgumentException.class, () -> motor.tick(new CharacterMovementState(MovementVector.ZERO,
                new MovementVector(-6d, 0d, 2d), false), new CharacterMovementCommand(0d, 0d, false, false), strict, false));
    }

    @Test
    void orderedCertificateIsConsumedByStrictMotorWithoutChangingDisplacementBounds() {
        CharacterMovementMotor motor=new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        var env=environment((position,request,grounded)->{
            var actual=new MovementVector(0,request.y(),request.z());
            var projection=new HorizontalContactEvidence.Projection(1,request,actual,
                    new MovementVector(actual.x()-request.x(),0,actual.z()-request.z()),1,0,0);
            var certificate=HorizontalContactEvidence.validateOrdered(request,actual,1,List.of(projection)).orElseThrow();
            return new MovementCollisionResolver.CollisionResult(actual,false,certificate);
        });
        var result=motor.tick(new CharacterMovementState(MovementVector.ZERO,new MovementVector(-6,0,2),false),
                new CharacterMovementCommand(0,0,false,false),env,false);
        assertEquals(0,result.position().x(),0);
        assertEquals(.1,result.position().z(),1e-12);
        assertEquals(0,result.velocity().x(),1e-12);
        assertEquals(2,result.velocity().z(),1e-12);
    }

    @Test
    void matchingHorizontalCertificateCannotAuthorizeVerticalOverrun() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        var env = environment((position, requested, grounded) -> {
            MovementVector actual = new MovementVector(0d, requested.y() + .01d, requested.z());
            HorizontalContactEvidence evidence = HorizontalContactEvidence.validate(
                    requested, actual, 1d, 0d, 0d).orElseThrow();
            assertTrue(evidence.matches(requested, actual));
            return new MovementCollisionResolver.CollisionResult(actual, false, evidence);
        });
        assertThrows(IllegalArgumentException.class, () -> motor.tick(
                new CharacterMovementState(MovementVector.ZERO, new MovementVector(-2d, 0d, 0d), false),
                new CharacterMovementCommand(0d, 0d, false, false), env, false));
    }

    @Test
    void rejectsWrongNormalsArbitraryMotionOppositeMotionZeroWishAndVerticalOverrun() {
        MovementVector request = new MovementVector(-.3d, 0d, .1d);
        MovementVector partial = new MovementVector(-.25d, 0d, .15d);
        assertTrue(HorizontalContactEvidence.validate(request, partial, 1d, 0d).isEmpty());
        assertTrue(HorizontalContactEvidence.validate(request, new MovementVector(.1d, 0d, .4d), DIAGONAL, DIAGONAL).isEmpty());
        assertTrue(HorizontalContactEvidence.validate(MovementVector.ZERO, new MovementVector(.1d, 0d, 0d), 1d, 0d).isEmpty());
        assertTrue(HorizontalContactEvidence.validate(new MovementVector(.3d, 0d, -.1d), partial,
                DIAGONAL, DIAGONAL).isEmpty());

        HorizontalContactEvidence valid = HorizontalContactEvidence.validate(request, partial, DIAGONAL, DIAGONAL)
                .orElseThrow();
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        for (MovementVector invalid : new MovementVector[]{new MovementVector(-.25d, .5d, .15d),
                new MovementVector(-.25d, 0d, -.5625d)}) {
            var env = environment((position, ignored, grounded) -> new MovementCollisionResolver.CollisionResult(invalid, false, valid));
            assertThrows(IllegalArgumentException.class, () -> motor.tick(new CharacterMovementState(MovementVector.ZERO,
                    new MovementVector(-6d, 0d, 2d), false), new CharacterMovementCommand(0d, 0d, false, false), env, false));
        }
        var zeroWishDrift = environment((position, ignored, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(.1d, 0d, 0d), false));
        assertThrows(IllegalArgumentException.class, () -> motor.tick(new CharacterMovementState(MovementVector.ZERO,
                MovementVector.ZERO, false), new CharacterMovementCommand(0d, 0d, false, false), zeroWishDrift, false));
        var stale = environment((position, ignored, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(-.24d, 0d, .15d), false, valid));
        assertThrows(IllegalArgumentException.class, () -> motor.tick(new CharacterMovementState(MovementVector.ZERO,
                new MovementVector(-6d, 0d, 2d), false), new CharacterMovementCommand(0d, 0d, false, false), stale, false));
    }

    @Test
    void movementVectorEnforcesFiniteAllAxisEvidenceInputs() {
        assertThrows(IllegalArgumentException.class, () -> new MovementVector(0d, Double.NaN, 0d));
        assertThrows(IllegalArgumentException.class, () -> new MovementVector(0d, 0d, Double.POSITIVE_INFINITY));
    }

    @Test
    void axisAlignedProofMatchesPriorBlockedAxisVelocityResponse() {
        MovementVector request = new MovementVector(-.3d, 0d, 0d);
        MovementVector actual = new MovementVector(0d, 0d, 0d);
        HorizontalContactEvidence evidence = HorizontalContactEvidence.validate(request, actual, 1d, 0d).orElseThrow();
        assertEquals(MovementVector.ZERO, evidence.projectVelocityTangent(new MovementVector(-6d, 0d, 0d)));
        assertEquals(new MovementVector(0d, 0d, 2d), evidence.projectVelocityTangent(new MovementVector(-6d, 0d, 2d)));
    }

    private static CharacterMovementEnvironment environment(MovementCollisionResolver resolver) {
        return new CharacterMovementEnvironment(.05d, 0d, 0d, 1d, MovementVector.ZERO,
                0d, 0d, resolver);
    }

    private static void assertVectorEquals(MovementVector expected, MovementVector actual) {
        assertEquals(expected.x(), actual.x(), 1e-12);
        assertEquals(expected.y(), actual.y(), 1e-12);
        assertEquals(expected.z(), actual.z(), 1e-12);
    }
}
