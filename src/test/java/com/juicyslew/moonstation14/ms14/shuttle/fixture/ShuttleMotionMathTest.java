package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ShuttleMotionMathTest {
    private static final Quaterniond IDENTITY = new Quaterniond();
    private static final Matrix3d UNIT = new Matrix3d().identity();
    private static ShuttleMotionRequest request(double x,double y,double z,double yaw) {
        return new ShuttleMotionRequest(x,y,z,yaw,60);
    }
    private static void assertVector(double x,double y,double z, ShuttleMotionMath.Vector actual) {
        assertNotNull(actual);
        assertEquals(x,actual.x(),1e-10); assertEquals(y,actual.y(),1e-10); assertEquals(z,actual.z(),1e-10);
    }

    @Test void freightControlledPathCapsAndJerkLimitsEvenAcrossSuddenVelocityError() {
        var prior=(ShuttleMotionMath.CommandAcceleration)null;
        for(int i=0;i<80;i++) {
            double dt=1.0/120;
            var target=i<10?request(0,0,0,0):request(20,8,-12,.8);
            var result=ShuttleMotionMath.computeControlled(target,new Vector3d(-3,1,2),new Vector3d(-3,1,2),
                    new Vector3d(.3,-.1,.2),IDENTITY,8,UNIT,new Vector3d(),dt,new Vector3d(),0,
                    ShuttleFreightFlightProfile.CONTROL_LIMITS,prior);
            assertNotNull(result); var next=result.commanded();
            var linear=new Vector3d(next.linearWorld().x(),next.linearWorld().y(),next.linearWorld().z());
            var angular=new Vector3d(next.angularWorld().x(),next.angularWorld().y(),next.angularWorld().z());
            assertTrue(linear.length() <= .350000001); assertTrue(angular.length() <= .020000001);
            if(prior!=null) {
                var oldL=new Vector3d(prior.linearWorld().x(),prior.linearWorld().y(),prior.linearWorld().z());
                var oldA=new Vector3d(prior.angularWorld().x(),prior.angularWorld().y(),prior.angularWorld().z());
                assertTrue(linear.distance(oldL) <= .12*dt+1e-10);
                assertTrue(angular.distance(oldA) <= .01*dt+1e-10);
            }
            prior=next;
        }
    }

    @Test void legacyOverloadsStillMatchLegacyControlPathExactly() {
        var req=request(8,-3,4,.7); var v=new Vector3d(.2,.4,-.1); var w=new Vector3d(.3,.2,.1);
        var old=ShuttleMotionMath.compute(req,v,w,IDENTITY,4,UNIT,new Vector3d(0,-9.8,0),1.0/120);
        var controlled=ShuttleMotionMath.computeControlled(req,v,v,w,IDENTITY,4,UNIT,new Vector3d(0,-9.8,0),
                1.0/120,new Vector3d(),0,ShuttleMotionMath.LEGACY_LIMITS,null).impulses();
        assertEquals(old,controlled);
    }

    @Test void freightTakeoverSlewsFromActualAboveBudgetLegacyCommandBeforeEnteringCap() {
        var prior=new ShuttleMotionMath.CommandAcceleration(new ShuttleMotionMath.Vector(.5,0,0),
                new ShuttleMotionMath.Vector(.04,0,0));
        double dt=1.0/120;
        for(int i=0;i<300;i++) {
            var result=ShuttleMotionMath.computeControlled(request(0,0,0,0),new Vector3d(),new Vector3d(),
                    new Vector3d(),IDENTITY,2,UNIT,new Vector3d(),dt,new Vector3d(),0,
                    ShuttleFreightFlightProfile.CONTROL_LIMITS,prior);
            assertNotNull(result);
            var next=result.commanded();
            var previousLinear=new Vector3d(prior.linearWorld().x(),prior.linearWorld().y(),prior.linearWorld().z());
            var nextLinear=new Vector3d(next.linearWorld().x(),next.linearWorld().y(),next.linearWorld().z());
            var previousAngular=new Vector3d(prior.angularWorld().x(),prior.angularWorld().y(),prior.angularWorld().z());
            var nextAngular=new Vector3d(next.angularWorld().x(),next.angularWorld().y(),next.angularWorld().z());
            assertTrue(previousLinear.distance(nextLinear)<=.12*dt+1e-10);
            assertTrue(previousAngular.distance(nextAngular)<=.01*dt+1e-10);
            assertTrue(nextLinear.length()<=previousLinear.length()+1e-12);
            assertTrue(nextAngular.length()<=previousAngular.length()+1e-12);
            prior=next;
        }
        assertTrue(new Vector3d(prior.linearWorld().x(),prior.linearWorld().y(),prior.linearWorld().z()).length()<=.350000001);
        assertTrue(new Vector3d(prior.angularWorld().x(),prior.angularWorld().y(),prior.angularWorld().z()).length()<=.020000001);
    }

    @Test void bodyAlignedForecastUsesNormalizedCurrentTimesPreviousConjugateAndPreservesMagnitude() {
        Quaterniond previous = new Quaterniond().rotationXYZ(.3, -.2, .1).mul(7);
        Quaterniond current = new Quaterniond().rotationXYZ(-.1, .5, .4).mul(-3);
        var alpha = new Vector3d(.4, -.7, 1.2);
        var alphaBefore = new Vector3d(alpha);
        var previousBefore = new Quaterniond(previous);
        var currentBefore = new Quaterniond(current);
        Quaterniond p = new Quaterniond(previous).normalize();
        Quaterniond c = new Quaterniond(current).normalize();
        Quaterniond expectedRotation = c.mul(new Quaterniond(p).conjugate()).normalize();
        Vector3d expected = expectedRotation.transform(new Vector3d(alpha));
        var forecast = ShuttleMotionMath.forecastNextWorldAngularAcceleration(alpha, previous, current);
        assertTrue(expected.distance(forecast) < 1e-12);
        assertEquals(alpha.length(), forecast.length(), 1e-12);
        assertEquals(alphaBefore, alpha); assertEquals(previousBefore, previous); assertEquals(currentBefore, current);

        var signScaled = ShuttleMotionMath.forecastNextWorldAngularAcceleration(alpha,
                new Quaterniond(-previous.x, -previous.y, -previous.z, -previous.w),
                new Quaterniond(current.x*11, current.y*11, current.z*11, current.w*11));
        assertTrue(forecast.distance(signScaled) < 1e-12);
    }

    @Test void missingInvalidPoseFallsBackToRawCopyAndRawMeasurementDtRemainsUnchanged() {
        var raw = ShuttleMotionMath.measureUncontrolledAngularAcceleration(new Vector3d(1,2,3), .01,
                new Vector3d(1.2,1.8,3.4));
        assertVector(20, -20, 40, new ShuttleMotionMath.Vector(raw.x, raw.y, raw.z));
        var rawBefore = new Vector3d(raw);
        var fallback = ShuttleMotionMath.forecastNextWorldAngularAcceleration(raw, null, new Quaterniond());
        assertEquals(raw, fallback); assertNotSame(raw, fallback); assertEquals(rawBefore, raw);
        var invalidCurrent = ShuttleMotionMath.forecastNextWorldAngularAcceleration(raw, new Quaterniond(),
                new Quaterniond(0,0,0,1e-14));
        assertEquals(raw, invalidCurrent);
        assertNull(ShuttleMotionMath.forecastNextWorldAngularAcceleration(null, new Quaterniond(), new Quaterniond()));
        var invalidRaw = new Vector3d(Double.NaN, 0, 0);
        var invalidCopy = ShuttleMotionMath.forecastNextWorldAngularAcceleration(invalidRaw, new Quaterniond(), new Quaterniond());
        assertNotSame(invalidRaw, invalidCopy); assertTrue(Double.isNaN(invalidCopy.x));
        assertVector(10,-10,20,new ShuttleMotionMath.Vector(
                ShuttleMotionMath.measureUncontrolledAngularAcceleration(new Vector3d(1,2,3),.02,new Vector3d(1.2,1.8,3.4)).x,
                ShuttleMotionMath.measureUncontrolledAngularAcceleration(new Vector3d(1,2,3),.02,new Vector3d(1.2,1.8,3.4)).y,
                ShuttleMotionMath.measureUncontrolledAngularAcceleration(new Vector3d(1,2,3),.02,new Vector3d(1.2,1.8,3.4)).z));
    }

    @Test void smallCapturedNativePairsShowNextAlphaForecastImprovementWithoutClaimingNativeStability() throws Exception {
        JsonObject root;
        try (var stream = ShuttleMotionMathTest.class.getResourceAsStream("/fixtures/shuttle/yaw-forecast-captured-pairs.json")) {
            assertNotNull(stream);
            root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var provenance = root.getAsJsonObject("provenance");
        assertEquals("run_ba98bf7830b7f7ac6c97fcc1", provenance.get("runId").getAsString());
        assertEquals("bd15e258d60b984b864575cb1f39b8a6b01b37d65c2399e71597c70ec1a83534",
                provenance.get("inputSha256").getAsString());
        JsonArray pairs = root.getAsJsonArray("pairs");
        assertEquals(9, pairs.size());
        double[][] squared = new double[3][2];
        int[] counts = new int[3];
        int[] expectedGroupCounts = {3,3,3};
        for (var element : pairs) {
            JsonObject pair = element.getAsJsonObject();
            int previousOrdinal = pair.get("previousOrdinal").getAsInt();
            int targetOrdinal = pair.get("targetOrdinal").getAsInt();
            assertEquals(previousOrdinal + 1, targetOrdinal, "only adjacent source ordinals are represented");
            assertTrue(targetOrdinal <= 7853, "terminal callback 7854 is not fabricated");
            boolean clamped = pair.get("clamped").getAsBoolean();
            double tilt = pair.get("targetPreTiltDeg").getAsDouble();
            int group = clamped ? 2 : tilt < 1 ? 0 : tilt < 2 ? 1 : -1;
            assertTrue(group >= 0, "excerpt intentionally samples only low-angle and clamped bins");
            counts[group]++;
            Quaterniond previousQ = quaternion(pair.getAsJsonArray("previousPostQ"));
            Quaterniond currentQ = quaternion(pair.getAsJsonArray("targetPreQ"));
            Vector3d measuredPreviousAlpha = vector(pair.getAsJsonArray("previousMeasuredWorldAlpha"));
            Vector3d measuredNextAlpha = vector(pair.getAsJsonArray("targetMeasuredWorldAlpha"));
            Vector3d rawForecast = new Vector3d(measuredPreviousAlpha);
            Vector3d alignedForecast = ShuttleMotionMath.forecastNextWorldAngularAcceleration(
                    measuredPreviousAlpha, previousQ, currentQ);
            squared[group][0] += rawForecast.distanceSquared(measuredNextAlpha);
            squared[group][1] += alignedForecast.distanceSquared(measuredNextAlpha);
            assertEquals(measuredPreviousAlpha.length(), alignedForecast.length(), 1e-12);
        }
        assertArrayEquals(expectedGroupCounts, counts);
        for (int group = 0; group < counts.length; group++)
            assertTrue(squared[group][1] < squared[group][0], "captured next-alpha forecast should improve group="+group);

        // The transform is intentionally a body-fixed-torque hypothesis, not a universal world-fixed-alpha estimator.
        var worldFixedAlpha = new Vector3d(1, 0, 0);
        var rotated = ShuttleMotionMath.forecastNextWorldAngularAcceleration(worldFixedAlpha,
                new Quaterniond(), new Quaterniond().rotationY(Math.PI / 2));
        assertTrue(rotated.distance(worldFixedAlpha) > 1.4);
        assertEquals(1, rotated.length(), 1e-12);
    }

    private static Quaterniond quaternion(JsonArray q) {
        return new Quaterniond(q.get(0).getAsDouble(), q.get(1).getAsDouble(), q.get(2).getAsDouble(), q.get(3).getAsDouble());
    }
    private static Vector3d vector(JsonArray v) { return new Vector3d(v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble()); }

    @Test void gravityCounterImpulseAndZeroErrorYaw() {
        var out=ShuttleMotionMath.compute(request(0,0,0,0),new Vector3d(),new Vector3d(),IDENTITY,
                5,UNIT,new Vector3d(0,-9.8,0),0.1);
        assertNotNull(out);
        assertVector(0,4.9,0,out.linearLocal());
        assertVector(0,0,0,out.angularLocal());
    }

    @Test void oneShotBrakeUsesFullTensorAndBodyPose() {
        Quaterniond q=new Quaterniond().rotationY(Math.PI/2);
        Matrix3d tensor=new Matrix3d().zero().m00(2).m11(3).m22(4).m01(.4).m10(.4);
        Vector3d v=new Vector3d(2,-1,3), w=new Vector3d(.2,.8,-.1);
        Vector3d originalV=new Vector3d(v), originalW=new Vector3d(w);
        Quaterniond originalQ=new Quaterniond(q); Matrix3d originalTensor=new Matrix3d(tensor);
        var out=ShuttleMotionMath.counterImpulse(v,w,q,5,tensor);
        assertNotNull(out);
        Vector3d appliedLinear=q.transform(new Vector3d(out.linearLocal().x(),out.linearLocal().y(),out.linearLocal().z())).div(5);
        assertTrue(appliedLinear.add(v).length()<1e-10);
        Vector3d angularMomentum=tensor.transform(q.transformInverse(new Vector3d(w)),new Vector3d());
        Vector3d appliedLocal=new Vector3d(out.angularLocal().x(),out.angularLocal().y(),out.angularLocal().z());
        assertTrue(appliedLocal.add(angularMomentum).length()<1e-10);
        assertEquals(originalV,v); assertEquals(originalW,w); assertEquals(originalQ,q); assertEquals(originalTensor,tensor);
        assertNull(ShuttleMotionMath.counterImpulse(v,w,q,0,tensor));
        assertNull(ShuttleMotionMath.counterImpulse(new Vector3d(Double.NaN,0,0),w,q,5,tensor));
        assertNull(ShuttleMotionMath.counterImpulse(v,w,new Quaterniond(0,0,0,0),5,tensor));
        assertNull(ShuttleMotionMath.counterImpulse(v,w,q,5,new Matrix3d().zero()));
    }

    @Test void accelerationClampDoesNotCancelVelocity() {
        var out=ShuttleMotionMath.compute(request(0,0,0,0),new Vector3d(10,0,0),new Vector3d(),IDENTITY,
                2,UNIT,new Vector3d(),0.5);
        assertVector(-2,0,0,out.linearLocal());
    }

    @Test void rotatesWorldAccelerationExactlyOnceAndUsesAsymmetricInertia() {
        Quaterniond q=new Quaterniond().rotationY(Math.PI/2);
        var out=ShuttleMotionMath.compute(request(0,0,0,0.1),new Vector3d(),new Vector3d(),q,
                1,new Matrix3d().zero().m00(2).m11(3).m22(4),new Vector3d(0,-1,0),0.5);
        assertNotNull(out);
        assertVector(0,0.5,0,out.linearLocal());
        // Yaw acceleration is 0.2 rad/s^2, so local Y angular impulse is 3 * 0.2 * 0.5.
        assertVector(0,0.3,0,out.angularLocal());
    }

    @Test void equalConstantStateSubstepsScaleWithElapsedTime() {
        var one=ShuttleMotionMath.compute(request(1,0,0,0),new Vector3d(),new Vector3d(),IDENTITY,2,UNIT,new Vector3d(),1.0/20);
        var sub=ShuttleMotionMath.compute(request(1,0,0,0),new Vector3d(),new Vector3d(),IDENTITY,2,UNIT,new Vector3d(),1.0/120);
        assertEquals(one.linearLocal().x(),sub.linearLocal().x()*6,1e-12);
        assertEquals(one.angularLocal().y(),sub.angularLocal().y()*6,1e-12);
    }

    @Test void boundedWorldRateFeedbackUsesFullTensorWithoutGyroFeedforward() {
        Matrix3d inertia = new Matrix3d().zero().m00(2).m11(3).m22(4).m01(0.4).m10(0.4);
        Vector3d omega = new Vector3d(0.2, 0.8, -0.1);
        Quaterniond q = new Quaterniond().rotationY(0.6);
        var out = ShuttleMotionMath.compute(request(0,0,0,0.2),new Vector3d(),omega,q,1,inertia,new Vector3d(),0.5);
        assertNotNull(out);
        Vector3d expectedWorldAlpha = new Vector3d(-2*omega.x, 0, -2*omega.z);
        if (expectedWorldAlpha.length() > 0.25) expectedWorldAlpha.normalize().mul(0.25);
        double yawBudget=Math.sqrt(Math.max(0,0.25*0.25-expectedWorldAlpha.lengthSquared()));
        expectedWorldAlpha.y=Math.max(-yawBudget,Math.min(yawBudget,2*(0.2-omega.y)));
        Vector3d expectedLocalTorque = inertia.transform(q.transformInverse(expectedWorldAlpha), new Vector3d()).mul(0.5);
        assertVector(expectedLocalTorque.x, expectedLocalTorque.y, expectedLocalTorque.z, out.angularLocal());
        Vector3d recoveredWorldAlpha = q.transform(inertia.invert(new Matrix3d())
                .transform(new Vector3d(out.angularLocal().x(),out.angularLocal().y(),out.angularLocal().z()).div(0.5),new Vector3d()));
        assertTrue(recoveredWorldAlpha.length() <= 0.2500000001);
    }

    @Test void matchedWorldYawTargetProducesNoAngularControlEvenWithAsymmetricInertia() {
        Matrix3d inertia = new Matrix3d().zero().m00(2).m11(3).m22(4).m01(0.4).m10(0.4);
        Quaterniond q = new Quaterniond().rotationY(0.6).rotateX(0.25);
        var out = ShuttleMotionMath.compute(request(0,0,0,0.8),new Vector3d(),new Vector3d(0,0.8,0),q,
                1,inertia,new Vector3d(),0.5);
        assertNotNull(out);
        assertVector(0,0,0,out.angularLocal());
    }

    @Test void zeroAngularVelocityPreservesLegacyTorqueAndTiltNeverGetsRestoringTerm() {
        Quaterniond q = new Quaterniond(Math.sin(0.3),0,0,Math.cos(0.3));
        var out=ShuttleMotionMath.compute(request(0,0,0,0),new Vector3d(),new Vector3d(),q,1,
                new Matrix3d().zero().m00(2).m11(3).m22(4),new Vector3d(),0.5);
        assertVector(0,0,0,out.angularLocal());
    }

    @Test void overBudgetWorldRateFeedbackIsClampedToExactAccelerationBudget() {
        Matrix3d inertia=new Matrix3d().zero().m00(2).m11(3).m22(4).m01(0.4).m10(0.4);
        Quaterniond q=new Quaterniond(Math.sin(0.25),0,0,Math.cos(0.25));
        Vector3d omega=new Vector3d(0,20,12);
        var out=ShuttleMotionMath.compute(request(0,0,0,0),new Vector3d(),omega,q,1,inertia,new Vector3d(),0.2);
        assertNotNull(out);
        Vector3d expectedWorldAlpha = new Vector3d(-2*omega.x,0,-2*omega.z);
        if(expectedWorldAlpha.length()>0.25) expectedWorldAlpha.normalize().mul(0.25);
        double yawBudget=Math.sqrt(Math.max(0,0.25*0.25-expectedWorldAlpha.lengthSquared()));
        expectedWorldAlpha.y=Math.max(-yawBudget,Math.min(yawBudget,-2*omega.y));
        Vector3d expectedLocalTorque = inertia.transform(q.transformInverse(expectedWorldAlpha),new Vector3d()).mul(0.2);
        assertVector(expectedLocalTorque.x,expectedLocalTorque.y,expectedLocalTorque.z,out.angularLocal());
        Vector3d appliedWorldAlpha = q.transform(inertia.invert(new Matrix3d()).transform(
                new Vector3d(out.angularLocal().x(),out.angularLocal().y(),out.angularLocal().z()).div(0.2),new Vector3d()));
        assertEquals(0.25,appliedWorldAlpha.length(),1e-10);
    }

    @Test void offAxisCorrectionGetsPriorityWithinCombinedAccelerationCap() {
        Vector3d omega=new Vector3d(-0.02804,2.589496,-0.1054);
         var out=ShuttleMotionMath.compute(request(0,0,0,3),new Vector3d(),omega,IDENTITY,1,UNIT,new Vector3d(),1);
        assertNotNull(out);
        Vector3d alpha=new Vector3d(out.angularLocal().x(),out.angularLocal().y(),out.angularLocal().z());
        assertEquals(0.25,alpha.length(),1e-10);
         assertEquals(-2*omega.x,alpha.x,1e-10);
         assertEquals(-2*omega.z,alpha.z,1e-10);
         double crossSquared=alpha.x*alpha.x+alpha.z*alpha.z;
         assertEquals(Math.sqrt(0.25*0.25-crossSquared),alpha.y,1e-10);
         Vector3d oldUniformClamp=new Vector3d(-2*omega.x,2*(3-omega.y),-2*omega.z);
         oldUniformClamp.normalize().mul(0.25);
         assertTrue(oldUniformClamp.x < alpha.x && oldUniformClamp.z < alpha.z);
    }

    @Test void nextIntervalForecastUsesCapturedBodyPoseProgressionWithoutMutatingInputs() {
        Quaterniond previous = new Quaterniond().rotationXYZ(.3, -.2, .1).mul(7);
        Quaterniond current = new Quaterniond().rotationXYZ(-.1, .5, .4).mul(-3);
        var alpha = new Vector3d(.4, -.7, 1.2);
        var alphaBefore = new Vector3d(alpha);
        var previousBefore = new Quaterniond(previous);
        var currentBefore = new Quaterniond(current);
        Quaterniond normalizedPrevious = new Quaterniond(previous).normalize();
        Quaterniond normalizedCurrent = new Quaterniond(current).normalize();
        Quaterniond rotation = normalizedCurrent.mul(new Quaterniond(normalizedPrevious).conjugate()).normalize();
        Vector3d expected = rotation.transform(new Vector3d(alpha));
        Vector3d forecast = ShuttleMotionMath.forecastNextWorldAngularAcceleration(alpha, previous, current);
        assertTrue(expected.distance(forecast) < 1e-12);
        assertEquals(alpha.length(), forecast.length(), 1e-12);
        assertEquals(alphaBefore, alpha); assertEquals(previousBefore, previous); assertEquals(currentBefore, current);
        var signScaled = ShuttleMotionMath.forecastNextWorldAngularAcceleration(alpha,
                new Quaterniond(-previous.x, -previous.y, -previous.z, -previous.w),
                new Quaterniond(current.x*11, current.y*11, current.z*11, current.w*11));
        assertTrue(forecast.distance(signScaled) < 1e-12);
        assertEquals(new Vector3d(), ShuttleMotionMath.forecastNextWorldAngularAcceleration(new Vector3d(), previous, current));
    }

    @Test void absentOrInvalidPoseFallsBackToAnUnchangedRawMeasurementAndVariableDtStillMeasuresRawAlpha() {
        var previousOmega = new Vector3d(1,2,3);
        var currentOmega = new Vector3d(1.2,1.8,3.4);
        var rawAtSmallDt = ShuttleMotionMath.measureUncontrolledAngularAcceleration(previousOmega, .01, currentOmega);
        var rawAtLargeDt = ShuttleMotionMath.measureUncontrolledAngularAcceleration(previousOmega, .02, currentOmega);
        assertVector(20,-20,40,new ShuttleMotionMath.Vector(rawAtSmallDt.x,rawAtSmallDt.y,rawAtSmallDt.z));
        assertVector(10,-10,20,new ShuttleMotionMath.Vector(rawAtLargeDt.x,rawAtLargeDt.y,rawAtLargeDt.z));
        var rawBefore = new Vector3d(rawAtSmallDt);
        var fallback = ShuttleMotionMath.forecastNextWorldAngularAcceleration(rawAtSmallDt, null, new Quaterniond());
        assertEquals(rawAtSmallDt, fallback); assertNotSame(rawAtSmallDt, fallback); assertEquals(rawBefore, rawAtSmallDt);
        assertEquals(rawAtSmallDt, ShuttleMotionMath.forecastNextWorldAngularAcceleration(rawAtSmallDt,
                new Quaterniond(), new Quaterniond(0,0,0,1e-14)));
        assertEquals(rawAtSmallDt, ShuttleMotionMath.forecastNextWorldAngularAcceleration(rawAtSmallDt,
                new Quaterniond(0,0,0,0), new Quaterniond()));
        assertEquals(rawAtSmallDt, ShuttleMotionMath.forecastNextWorldAngularAcceleration(rawAtSmallDt,
                new Quaterniond(Double.NaN,0,0,1), new Quaterniond()));
        assertEquals(rawAtSmallDt, ShuttleMotionMath.forecastNextWorldAngularAcceleration(rawAtSmallDt,
                new Quaterniond(Double.MAX_VALUE,0,0,0), new Quaterniond()));
        assertNull(ShuttleMotionMath.forecastNextWorldAngularAcceleration(null, new Quaterniond(), new Quaterniond()));
        var invalidRaw = new Vector3d(Double.NaN,0,0);
        var invalidCopy = ShuttleMotionMath.forecastNextWorldAngularAcceleration(invalidRaw, new Quaterniond(), new Quaterniond());
        assertNotSame(invalidRaw, invalidCopy); assertTrue(Double.isNaN(invalidCopy.x));
    }

    @Test void capturedBa98ActualAdjacentPairsImproveBodyAlignedNextAlphaForecastWithoutClaimingClosedLoopStability() throws Exception {
        com.google.gson.JsonObject root;
        try (var stream = ShuttleMotionMathTest.class.getResourceAsStream("/fixtures/shuttle/yaw-forecast-captured-pairs.json")) {
            assertNotNull(stream);
            root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        var provenance = root.getAsJsonObject("provenance");
        assertEquals("run_ba98bf7830b7f7ac6c97fcc1", provenance.get("runId").getAsString());
        assertEquals("bd15e258d60b984b864575cb1f39b8a6b01b37d65c2399e71597c70ec1a83534",
                provenance.get("inputSha256").getAsString());
        assertEquals("94beed7a401692e6b3442abba3092d7e66cc452b1b56a420246279769e18cb1d",
                provenance.get("localLogSha256").getAsString());
        assertEquals(9, root.getAsJsonArray("pairs").size());
        double[][] squaredErrors = new double[3][2]; int[] counts = new int[3];
        for (var element : root.getAsJsonArray("pairs")) {
            var pair = element.getAsJsonObject();
            assertEquals(pair.get("previousOrdinal").getAsLong()+1, pair.get("targetOrdinal").getAsLong());
            assertTrue(pair.get("targetOrdinal").getAsLong() <= 7853, "callback 7854 is not synthesized");
            double tilt = pair.get("targetPreTiltDeg").getAsDouble();
            boolean clamped = pair.get("clamped").getAsBoolean();
            int group = clamped ? 2 : tilt < 1 ? 0 : tilt < 2 ? 1 : -1;
            assertTrue(group >= 0, "small excerpt bins are <1°, 1–2°, or captured clamped intervals");
            counts[group]++;
            var previousQ = quaternion(pair.getAsJsonArray("previousPostQ"));
            var targetQ = quaternion(pair.getAsJsonArray("targetPreQ"));
            var raw = vector(pair.getAsJsonArray("previousMeasuredWorldAlpha"));
            var target = vector(pair.getAsJsonArray("targetMeasuredWorldAlpha"));
            var rawForecast = new Vector3d(raw);
            var alignedForecast = ShuttleMotionMath.forecastNextWorldAngularAcceleration(raw, previousQ, targetQ);
            squaredErrors[group][0] += rawForecast.distanceSquared(target);
            squaredErrors[group][1] += alignedForecast.distanceSquared(target);
            assertEquals(raw.length(), alignedForecast.length(), 1e-12);
        }
        assertArrayEquals(new int[] {3,3,3}, counts);
        for (int group = 0; group < counts.length; group++)
            assertTrue(squaredErrors[group][1] < squaredErrors[group][0], "captured next-alpha RMS improves group="+group);
        // External world-fixed alpha is a counterexample to this bounded body-frame hypothesis.
        var worldFixed = new Vector3d(1,0,0);
        var transported = ShuttleMotionMath.forecastNextWorldAngularAcceleration(worldFixed, new Quaterniond(),
                new Quaterniond().rotationY(Math.PI/2));
        assertEquals(1, transported.length(), 1e-12);
        assertTrue(transported.distance(worldFixed) > 1.4);
    }

    @Test void dampingFeedForwardRemovesApparentRatePlateauWithoutExpandingCommandBudget() {
        double damping = .09;
        for (int substeps : new int[]{2, 6, 10}) {
            double dt = 1.0 / (20.0 * substeps);
            double omega = 0;
            double maximumNetCommand = 0;
            for (int step = 0; step < 20 * substeps * 30; step++) {
                var out = ShuttleMotionMath.compute(request(0,0,0,5), new Vector3d(), new Vector3d(0,omega,0),
                        IDENTITY, 1, UNIT, new Vector3d(), dt, new Vector3d(), damping);
                assertNotNull(out);
                double impulse = out.angularLocal().y();
                // Recover net requested acceleration from delta=dt*d*omega+dt*(1+dt*d)*a.
                double a = (impulse/dt - damping*omega)/(1+dt*damping);
                assertTrue(Math.abs(a) <= .2500000001);
                maximumNetCommand = Math.max(maximumNetCommand, Math.abs(a));
                omega = (omega + impulse) / (1 + dt*damping);
            }
            assertTrue(omega > 4.99, "substeps=" + substeps + " rate=" + omega);
            assertEquals(5, omega, 1e-8);
            assertTrue(maximumNetCommand <= .2500000001);

            // With no further controller impulses, the native damping recurrence still coasts down.
            double coasting = omega;
            for (int i=0; i<20*substeps; i++) coasting /= 1+dt*damping;
            assertTrue(coasting < omega);
        }
    }

    @Test void zeroDampingAndInvalidDampingBehavior() {
        var legacy = ShuttleMotionMath.compute(request(0,0,0,1),new Vector3d(),new Vector3d(0,.2,0),
                IDENTITY,1,UNIT,new Vector3d(),.01);
        var explicitZero = ShuttleMotionMath.compute(request(0,0,0,1),new Vector3d(),new Vector3d(0,.2,0),
                IDENTITY,1,UNIT,new Vector3d(),.01,new Vector3d(),0);
        assertEquals(legacy, explicitZero);
        assertNull(ShuttleMotionMath.compute(request(0,0,0,1),new Vector3d(),new Vector3d(),IDENTITY,
                1,UNIT,new Vector3d(),.01,new Vector3d(),-1));
        assertNull(ShuttleMotionMath.compute(request(0,0,0,1),new Vector3d(),new Vector3d(),IDENTITY,
                1,UNIT,new Vector3d(),.01,new Vector3d(),Double.NaN));
    }

    @Test void linearDampingFeedForwardTracksPoseTransportAcrossSolverSubsteps() {
        double h = 1.0 / 20.0, gravity = -11, drag = .09;
        for (int substeps : new int[]{1, 4, 8}) {
            for (double target : new double[]{0, 1, -1}) {
                double v = 0, position = 306, previousTransport = 0;
                for (int step = 0; step < 20 * 20; step++) {
                    double transport = step == 0 ? v : previousTransport;
                    var out = ShuttleMotionMath.compute(request(0,target,0,0),new Vector3d(0,transport,0),
                            new Vector3d(0,v,0),new Vector3d(),IDENTITY,1,UNIT,
                            new Vector3d(0,gravity,0),h,new Vector3d(),drag);
                    assertNotNull(out);
                    double acceleration = Math.max(-2, Math.min(2, 2 * (target - transport)));
                    double impulse = out.linearLocal().y();
                    double oldImpulse = h * (acceleration - gravity);
                    assertEquals(h * (acceleration - gravity + drag * v), impulse, 1e-10);
                    // Independent solver model: impulse, N gravity/pose substeps, then one outer-step damping.
                    double next = v + impulse;
                    double nextPosition = position;
                    for (int i=0; i<substeps; i++) {
                        next += gravity * h / substeps;
                        nextPosition += next * h / substeps;
                    }
                    next /= 1 + h * drag;
                    previousTransport = (nextPosition - position) / h;
                    position = nextPosition;
                    v = next;
                    if (step > 100) assertEquals(target, previousTransport, 3e-5,
                            "substeps="+substeps+" target="+target);
                    assertTrue(Math.abs(acceleration) <= 2.0000001);
                    assertTrue(Double.isFinite(oldImpulse));
                }
            }
        }
        // Drag feed-forward must use SDK velocity, not pose-derived transport.
        var distinct = ShuttleMotionMath.compute(request(0,0,0,0),new Vector3d(0,3,0),new Vector3d(0,5,0),
                new Vector3d(),IDENTITY,2,UNIT,new Vector3d(),.1,new Vector3d(),.09);
        assertEquals(-.31, distinct.linearLocal().y(), 1e-10);
        assertNull(ShuttleMotionMath.compute(request(0,0,0,0),new Vector3d(),new Vector3d(Double.NaN,0,0),
                new Vector3d(),IDENTITY,1,UNIT,new Vector3d(),.1,new Vector3d(),.09));
    }

    @Test void transportMeasurementUsesPhysicalPoseDeltaAndBootstrapsFromSdkVelocity() {
        var sdk = new Vector3d(4, -3, 2);
        assertVector(4, -3, 2, vector(ShuttleMotionMath.measureTransportVelocity(null, 1.0/120,
                new Vector3d(10, 20, 30), sdk)));
        assertVector(0, 0, 0, vector(ShuttleMotionMath.measureTransportVelocity(new Vector3d(1, 2, 3), 1.0/120,
                new Vector3d(1, 2, 3), sdk)));
        assertVector(1, -2, 3, vector(ShuttleMotionMath.measureTransportVelocity(new Vector3d(0, 0, 0), 1.0/120,
                new Vector3d(1.0/120, -2.0/120, 3.0/120), sdk)));
        assertVector(4, -3, 2, vector(ShuttleMotionMath.measureTransportVelocity(new Vector3d(), 0,
                new Vector3d(5, 5, 5), sdk)));
        assertVector(4, -3, 2, vector(ShuttleMotionMath.measureTransportVelocity(
                new Vector3d(Double.NaN, 0, 0), 1.0/120, new Vector3d(), sdk)));
        assertVector(4, -3, 2, vector(ShuttleMotionMath.measureTransportVelocity(
                new Vector3d(), 1.0/120, new Vector3d(Double.POSITIVE_INFINITY, 0, 0), sdk)));
        assertNull(ShuttleMotionMath.measureTransportVelocity(null, 0, null, null));
    }

    @Test void uncontrolledAngularAccelerationUsesZeroBootstrapAndCompletedIntervalDifference() {
        assertEquals(new Vector3d(), ShuttleMotionMath.measureUncontrolledAngularAcceleration(null, 1.0/120,
                new Vector3d(.1, .2, .3)));
        assertEquals(new Vector3d(2, 4, 6), ShuttleMotionMath.measureUncontrolledAngularAcceleration(
                new Vector3d(.1, .2, .3), .05, new Vector3d(.2, .4, .6)));
    }

    @Test void observedOffAxisFreeResponseIsCancelledButYawFreeResponseIsIgnoredAndBudgetRemainsBounded() {
        var q = new Quaterniond().rotationY(.7).rotateX(.3);
        var inertia = new Matrix3d().zero().m00(2).m11(3).m22(4).m01(.4).m10(.4);
        var omega = new Vector3d(.1, .8, -.05);
        var observed = new Vector3d(.2, 100, -.1);
        var out = ShuttleMotionMath.compute(request(0,0,0,.8), new Vector3d(), omega, q, 1, inertia,
                new Vector3d(), .1, observed);
        assertNotNull(out);
        Vector3d recovered = q.transform(inertia.invert(new Matrix3d()).transform(
                new Vector3d(out.angularLocal().x(),out.angularLocal().y(),out.angularLocal().z()).div(.1),new Vector3d()));
        assertEquals(-.25 * (2.0 / Math.sqrt(5.0)), recovered.x, 1e-10);
        assertEquals(.25 * (1.0 / Math.sqrt(5.0)), recovered.z, 1e-10);
        assertTrue(recovered.length() <= .2500000001);
        // Legacy overload remains identical to explicit zero observation.
        var legacy=ShuttleMotionMath.compute(request(0,0,0,.8),new Vector3d(),omega,q,1,inertia,new Vector3d(),.1);
        var explicitZero=ShuttleMotionMath.compute(request(0,0,0,.8),new Vector3d(),omega,q,1,inertia,new Vector3d(),.1,new Vector3d());
        assertEquals(legacy, explicitZero);
    }

    @Test void rotationPointRebaseBootstrapsWithoutSuppressingFollowingTransportSamples() {
        Vector3d sdkVelocity = new Vector3d(3, 0, 0);
        Vector3d previousCom = new Vector3d(10, 0, 0);
        Vector3d basis = new Vector3d(1, 0, 0);

        // A changed local rotation point may move native COM without moving the hull.
        Vector3d afterRebase = ShuttleFixtureMotionService.measureTransportVelocity(previousCom, 0.01,
                basis, new Vector3d(100, 0, 0), new Vector3d(10_000, 0, 0), sdkVelocity);
        assertEquals(sdkVelocity, afterRebase);

        // Repeated rebases still return a measurement for actuation, rather than pausing the controller.
        Vector3d secondRebase = ShuttleFixtureMotionService.measureTransportVelocity(new Vector3d(10_000, 0, 0), 0.01,
                new Vector3d(100, 0, 0), new Vector3d(200, 0, 0), new Vector3d(-5_000, 0, 0), sdkVelocity);
        assertEquals(sdkVelocity, secondRebase);

        // Once the current basis has been stored, physical movement is measured normally next sample.
        Vector3d resumed = ShuttleFixtureMotionService.measureTransportVelocity(new Vector3d(-5_000, 0, 0), 0.01,
                new Vector3d(200, 0, 0), new Vector3d(200, 0, 0), new Vector3d(-4_999.98, 0, 0), sdkVelocity);
        assertVector(2, 0, 0, vector(resumed));
    }

    private static ShuttleMotionMath.Vector vector(Vector3d v) {
        return v == null ? null : new ShuttleMotionMath.Vector(v.x, v.y, v.z);
    }

    @Test void rejectsInvalidPhysicsInputsAndRequestBounds() {
        assertDoesNotThrow(()->new ShuttleMotionRequest(1000,-250,500,-20,2400));
        assertDoesNotThrow(()->new ShuttleMotionRequest(-1000,250,-500,20,1));
        assertThrows(IllegalArgumentException.class,()->new ShuttleMotionRequest(0,0,0,0,2401));
        assertThrows(IllegalArgumentException.class,()->new ShuttleMotionRequest(Double.NaN,0,0,0,1));
        assertThrows(IllegalArgumentException.class,()->new ShuttleMotionRequest(0,0,0,Double.POSITIVE_INFINITY,1));
        assertNull(calc(0,IDENTITY,UNIT,0.1));
        assertNull(calc(1,new Quaterniond(0,0,0,0),UNIT,0.1));
        assertNull(calc(1,IDENTITY,new Matrix3d().zero(),0.1));
        assertNull(calc(1,IDENTITY,new Matrix3d().zero().m00(2).m11(3).m22(4).m01(0.5),0.1));
        assertNull(calc(1,IDENTITY,new Matrix3d().zero().m00(2).m11(3).m22(4).m01(0.01).m10(0.01+1e-8),0.1));
        assertNotNull(calc(1,IDENTITY,new Matrix3d().zero().m00(2).m11(3).m22(4).m01(0.01).m10(0.01+1e-14),0.1));
        assertNull(calc(1,IDENTITY,UNIT,Double.NaN));
    }

    private static ShuttleMotionMath.Impulses calc(double mass,Quaterniond q,Matrix3d i,double dt) {
        return ShuttleMotionMath.compute(request(0,0,0,0),new Vector3d(),new Vector3d(),q,mass,i,new Vector3d(),dt);
    }
}
