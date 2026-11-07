package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ManifoldFixtureObservationTest {
    private static List<ManifoldFixtureObservation.Port> ports(double beforePl,double nowPl,double beforeN,double nowN) {
        var result=new ArrayList<ManifoldFixtureObservation.Port>();
        for(int i=0;i<6;i++)result.add(new ManifoldFixtureObservation.Port("port"+i,
                switch(i%3){case 0->"PRIMARY";case 1->"SECONDARY";default->"TERTIARY";},
                beforePl+beforeN,nowPl+nowN,beforePl,nowPl,beforeN,nowN,0,0));
        return result;
    }
    @Test void allSixPortsNeedFiniteBaselineAndPositivePlasmaAndNitrogenDeltas() {
        var success=ManifoldFixtureObservation.classify(ports(0,1,0,1));
        assertTrue(success.valid()); assertTrue(success.observed());
        assertFalse(ManifoldFixtureObservation.classify(ports(0,1,0,1).subList(0,5)).valid());
        var missing=new ArrayList<>(ports(0,1,0,1)); missing.set(4,new ManifoldFixtureObservation.Port("port4","SECONDARY",Double.NaN,2,0,1,0,1,0,0));
        assertFalse(ManifoldFixtureObservation.classify(missing).valid());
        var nonfinite=new ArrayList<>(ports(0,1,0,1)); nonfinite.set(2,new ManifoldFixtureObservation.Port("port2","TERTIARY",0,2,0,Double.POSITIVE_INFINITY,0,1,0,0));
        assertFalse(ManifoldFixtureObservation.classify(nonfinite).valid());
    }
    @Test void unmixedOrUnchangedSpeciesCannotProveNewCommonMixing() {
        var unmixed=new ArrayList<>(ports(0,1,0,1)); unmixed.set(5,new ManifoldFixtureObservation.Port("port5","TERTIARY",2,2,0,2,2,0,0,0));
        assertFalse(ManifoldFixtureObservation.classify(unmixed).observed());
        assertFalse(ManifoldFixtureObservation.classify(ports(1,1,1,1)).observed(),"already-mixed baseline is not new work");
        var unrelatedTotalChange=new ArrayList<>(ports(1,1,1,1)); unrelatedTotalChange.set(0,
                new ManifoldFixtureObservation.Port("port0","PRIMARY",2,9,1,1,1,1,0,0));
        assertFalse(ManifoldFixtureObservation.classify(unrelatedTotalChange).observed(),"other-gas/total change is not tracer transfer");
    }
}
