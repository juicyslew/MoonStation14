package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Matrix3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure deterministic oracle checks; no native physics/server is started. */
final class NativeGyroscopicResponseModelTest {
    @Test void independentPrincipalFrameAndPermutationOracleIsConsistent() {
        assertDoesNotThrow(NativeGyroscopicResponseGameTests::modelSelfCheck);
    }

    @Test void analyticTensorSpectrumAndAuthoredShapeGuardRejectWrongComposition() {
        var analytic = new Matrix3d(3,1,0, 1,3,0, 0,0,6);
        assertDoesNotThrow(() -> NativeGyroscopicResponseGameTests.guardLShapeTensor(analytic));
        var unequalPositive2x2 = new Matrix3d(3,1,0, 1,5,0, 0,0,6);
        assertDoesNotThrow(() -> NativeGyroscopicResponseGameTests.guardLShapeTensor(unequalPositive2x2));
        var spectrum=NativeGyroscopicResponseGameTests.principalValues(unequalPositive2x2);
        assertEquals(4-Math.sqrt(2),spectrum.x,1e-12);
        assertEquals(4+Math.sqrt(2),spectrum.y,1e-12);
        assertDoesNotThrow(() -> NativeGyroscopicResponseGameTests.modelSelfCheck());
        var badXZ = new Matrix3d(3,1,.02, 1,3,0, .02,0,6);
        var badYZ = new Matrix3d(3,1,0, 1,3,.02, 0,.02,6);
        assertThrows(net.minecraft.gametest.framework.GameTestAssertException.class,
                () -> NativeGyroscopicResponseGameTests.guardLShapeTensor(badXZ));
        assertThrows(net.minecraft.gametest.framework.GameTestAssertException.class,
                () -> NativeGyroscopicResponseGameTests.guardLShapeTensor(badYZ));
    }

    @Test void boundedFixtureFitsBuildHeightAndRejectsFormerOutOfWorldPlacement() {
        int sceneMin=NativeGyroscopicResponseGameTests.SCENE_BASE_Y-1;
        int sceneMax=NativeGyroscopicResponseGameTests.SCENE_BASE_Y
                +NativeGyroscopicResponseGameTests.SCENE_HEIGHT-1;
        assertTrue(NativeGyroscopicResponseGameTests.validSceneBounds(-64,320,sceneMin,sceneMax));
        assertTrue(sceneMin>=-64 && sceneMax<320);
        assertTrue(NativeGyroscopicResponseGameTests.SPAWN_Y<sceneMax);
        assertTrue(NativeGyroscopicResponseGameTests.MIN_HULL_Y>=NativeGyroscopicResponseGameTests.SCENE_BASE_Y+20);
        assertFalse(NativeGyroscopicResponseGameTests.validSceneBounds(-64,320,299,369));
    }

    @Test void eventFailureDispatchClosesObserverAndReportsWithoutThrowingIntoEvent() {
        var closed=new AtomicInteger(); var reported=new AtomicInteger();
        var cause=new IllegalStateException("fixture failure");
        assertDoesNotThrow(() -> NativeGyroscopicResponseGameTests.dispatchTerminalFailure(
                closed::incrementAndGet, failure -> { assertSame(cause,failure); reported.incrementAndGet(); }, cause));
        assertEquals(1,closed.get()); assertEquals(1,reported.get());
    }

    @Test void gyroClassifierOnlyPassesFullFrameCorrectResponse() {
        var correct=NativeGyroscopicResponseGameTests.classifyGyro(1e-6,
                new double[]{.014,.015,.016,.017,.018,.019},.01,true);
        assertEquals("FRAME_CORRECT",correct.verdict()); assertTrue(correct.pass());
        var knownR29WrongBackend=NativeGyroscopicResponseGameTests.classifyGyro(.019,
                new double[]{.014,.015,7e-7,.017,.018,.019},.01,true);
        assertEquals("BACKEND_PRINCIPAL_FRAME_OMISSION",knownR29WrongBackend.verdict()); assertFalse(knownR29WrongBackend.pass());
        assertFalse(NativeGyroscopicResponseGameTests.classifyGyro(1e-6,
                new double[]{1e-6,.015,.016,.017,.018,.019},.01,true).pass());
        assertFalse(NativeGyroscopicResponseGameTests.classifyGyro(1e-6,
                new double[]{.014,.015,.016,.017,.018,.019},1e-5,true).pass());
        assertFalse(NativeGyroscopicResponseGameTests.classifyGyro(Double.NaN,
                new double[]{.014,.015,.016,.017,.018,.019},.01,true).pass());
        assertFalse(NativeGyroscopicResponseGameTests.classifyGyro(1e-6,
                new double[]{.014,.015,.016,.017,.018,.019},.01,false).pass());
    }
}
