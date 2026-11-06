package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativeMassChangeResponseModelTest {
    @Test void firstCompletedPostEditBoundaryConsumesGenerationAndNeverRetriesFailedProbe(){
        var first=NativeMassChangeResponseGameTests.EditPhase.ADD_PENDING;
        assertFalse(NativeMassChangeResponseGameTests.validFirstCompletedBoundary(first,true,false),
                "updated tracker with stale native inertia must fail at first completed boundary");
        var consumed=NativeMassChangeResponseGameTests.EditPhase.NONE;
        assertFalse(NativeMassChangeResponseGameTests.validFirstCompletedBoundary(consumed,true,true),
                "later native catch-up cannot turn the consumed failed generation into a pass");
        assertFalse(NativeMassChangeResponseGameTests.validFirstCompletedBoundary(first,false,true),
                "missing expected tracker update fails even if old native response happens to match");
    }

    @Test void removalAlsoRequiresExpectedTrackerAndNativeResponseAtItsFirstCompletedBoundary(){
        var remove=NativeMassChangeResponseGameTests.EditPhase.REMOVE_PENDING;
        assertTrue(NativeMassChangeResponseGameTests.validFirstCompletedBoundary(remove,true,true));
        assertFalse(NativeMassChangeResponseGameTests.validFirstCompletedBoundary(remove,true,false));
    }

    @Test void sourceOperationErrorBudgetCoversRoundTripDriftButNotPhysicalAddOrFailedNativeProbe(){
        double x=2.0481032833333332e7,z=2.04810325e7;
        var baseCom=new Vector3d(x,128.83333333333331,z);
        var addCom=new Vector3d(x+.4166666666666667,128.75,z);
        var centers=new Vector3d[]{new Vector3d(x-.3333333333333333,128.5,z),
                new Vector3d(x+.6666666666666667,128.5,z),new Vector3d(x-.3333333333333333,129.5,z),
                new Vector3d(x+1.6666666666666667,128.5,z)};
        var baseI=new Matrix3d(2.333333333333333,.666666666666667,0,.666666666666667,2.333333333333333,0,0,0,3.666666666666667);
        var addedI=new Matrix3d(2.833333333333333,1.5,0,1.5,6.833333333333333,0,0,0,8.333333333333334);
        var budget=NativeMassChangeResponseGameTests.roundTripBudget(6,8,baseCom,addCom,centers,baseI,addedI);
        assertTrue(Double.isFinite(budget.maxTensorDrift())&&budget.maxTensorDrift()>6.208816127895034e-9,
                "captured R42 return drift is within independently derived source bound: "+budget);
        assertTrue(NativeMassChangeResponseGameTests.tensorMatchesEdit(
                NativeMassChangeResponseGameTests.EditPhase.REMOVE_PENDING,6.208816127895034e-9,budget.maxTensorDrift()));
        assertTrue(NativeMassChangeResponseGameTests.tensorMatchesEdit(
                NativeMassChangeResponseGameTests.EditPhase.ADD_PENDING,4.5,budget.maxTensorDrift()));
        assertFalse(NativeMassChangeResponseGameTests.tensorMatchesEdit(
                NativeMassChangeResponseGameTests.EditPhase.ADD_PENDING,6.208816127895034e-9,budget.maxTensorDrift()),
                "roundoff-scale delta cannot masquerade as physical add");
        assertFalse(NativeMassChangeResponseGameTests.validFirstCompletedBoundary(
                NativeMassChangeResponseGameTests.EditPhase.ADD_PENDING,true,false),
                "tracker inside budget cannot mask stale native inertia response");
        assertThrows(IllegalArgumentException.class,()->NativeMassChangeResponseGameTests.roundTripBudget(
                Double.NaN,8,baseCom,addCom,centers,baseI,addedI));
    }
}
