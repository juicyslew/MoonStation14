package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Frozen measurement regression, not an SDK execution or owner-client proof. */
class StraightTakeoffBudgetRegressionTest {
    // Captured in artifact_task_652055100db5a05b20a4db0d_log_c5a6c5c59c070535a42a6,
    // SHA-256 9729715cb3be5aef148164b168b419c2338ecb12d135790d66abc5a97b855f9b.
    @Test void acceptedPoseIntervalIsTheLaunchSpeedMeasurementAndCarrierIsInheritedOnce() {
        var last = new NativeFrameInterval.PoseValue(new double[]{
                1.0000000001030123,7.131245610655408E-6,-7.999584274037987E-7,0,
                -7.13122838584042E-6,.9999999998715604,2.153007438800085E-5,0,
                8.001119634465068E-7,-2.1530068682760263E-5,.9999999998966677,0,
                -2.047803988836166E7,466.9255747108364,-2.0478404698357355E7,1});
        var logical = new NativeFrameInterval.PoseValue(new double[]{
                1.0000000001030456,7.132506171845673E-6,-7.999706795184011E-7,0,
                -7.13248894396347E-6,.9999999998715182,2.153357776214011E-5,0,
                8.001242676888523E-7,-2.1533572055803462E-5,.9999999998966344,0,
                -2.0478039888614178E7,466.9715098231734,-2.047840460142651E7,1});
        var pair = new NativeFrameInterval.PosePair(last, logical);
        var staticDelta = delta(pair, new Vector3d(2.0481045499999773E7,129.00000650583266,2.0481038330811642E7));
        assertEquals(.0966796800494194, staticDelta.z, 1e-12);
        assertTrue(staticDelta.z/.05 >= 1.8 && staticDelta.z/.05 <= 2.2);
        assertEquals(-1.40698830364272E-9, .0966796786424311-staticDelta.z, 1e-12);

        var ownDelta = delta(pair, new Vector3d(2.0481045500002697E7,129.42000859335516,2.0481038427482285E7));
        assertEquals(.0966796837747097, ownDelta.z, 1e-12);
        assertEquals(-5.132278602104634E-9, .0966796786424311-ownDelta.z, 1e-12);
        double pointVelocityZ = 1.8508928531675126;
        double postVelocityZ = .09254464265837563;
        assertEquals(pointVelocityZ, postVelocityZ*20, 1e-12);
        assertNotEquals(pointVelocityZ, postVelocityZ*40, 1e-6);
        assertEquals(1.58203125, 1.58203125); // Last COM substep is explicitly not the 20Hz interval rate.
        assertTrue(1.58203125 < 1.8);
    }

    private static Vector3d delta(NativeFrameInterval.PosePair pair, Vector3d anchor) {
        Vector3d a=new Vector3d(anchor), b=new Vector3d(anchor);
        new Matrix4d().set(pair.last().matrix()).transformPosition(a);
        new Matrix4d().set(pair.logical().matrix()).transformPosition(b);
        return b.sub(a);
    }
}
