package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GasAnalyzerSessionPolicyTest {
    @Test void sessionRequiresExactHeldAnalyzerIdentityAndCurrentBodyEpochDimension() {
        assertTrue(GasAnalyzerSessionPolicy.exactHeldSource("token-a", "token-a", true));
        assertFalse(GasAnalyzerSessionPolicy.exactHeldSource("token-a", "token-b", true));
        assertFalse(GasAnalyzerSessionPolicy.exactHeldSource("token-a", "token-a", false));
        assertTrue(GasAnalyzerSessionPolicy.sourceHeldInEitherHand("token-a", java.util.List.of(
                new GasAnalyzerSessionPolicy.HandSource("other", false),
                new GasAnalyzerSessionPolicy.HandSource("token-a", true))));
        assertFalse(GasAnalyzerSessionPolicy.sourceHeldInEitherHand("token-a", java.util.List.of(
                new GasAnalyzerSessionPolicy.HandSource("token-a", false))));
        UUID body = UUID.randomUUID();
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        assertTrue(GasAnalyzerSessionPolicy.sameBody(body, 4, dimension, body, 4, dimension));
        assertFalse(GasAnalyzerSessionPolicy.sameBody(body, 4, dimension, body, 5, dimension));
        assertFalse(GasAnalyzerSessionPolicy.sameBody(body, 4, dimension, body,
                4, ResourceLocation.withDefaultNamespace("the_nether")));
    }

    @Test void controlsAreCorrelatedAndDistanceIsBoundedByResolvedProfile() {
        UUID id = UUID.randomUUID();
        var control = new GasAnalyzerControl(id, 3, 8, 2, 6, false);
        assertEquals(0,new GasAnalyzerControl(id,3,8,2,0,false).distance());
        assertThrows(IllegalArgumentException.class,()->new GasAnalyzerControl(id,3,8,2,-1,false));
        assertTrue(GasAnalyzerSessionPolicy.controlCurrent(id, 3, 8, 1, control));
        assertFalse(GasAnalyzerSessionPolicy.controlCurrent(id, 3, 8, 2, control));
        assertFalse(GasAnalyzerSessionPolicy.controlCurrent(UUID.randomUUID(), 3, 8, 1, control));
        assertTrue(GasAnalyzerSessionPolicy.validDistance(6, new GasAnalyzerProfile(6, 1, 20, .01)));
        assertFalse(GasAnalyzerSessionPolicy.validDistance(7, new GasAnalyzerProfile(6, 1, 20, .01)));
        assertTrue(GasAnalyzerSessionPolicy.validDistance(0, new GasAnalyzerProfile(6, 0, 20, .01)));
        assertFalse(GasAnalyzerSessionPolicy.validDistance(-1, new GasAnalyzerProfile(6, 0, 20, .01)));
        assertFalse(GasAnalyzerSessionPolicy.validDistance(7, new GasAnalyzerProfile(6, 0, 20, .01)));
    }

    @Test void changedSelectionRefreshesImmediatelyButStationarySelectionKeepsResolvedCadence() {
        var cell=new net.minecraft.core.BlockPos(-2,64,3);
        var same=new GasAnalyzerCellSelection.Result(GasAnalyzerCellSelection.Status.SELECTED,cell);
        var changed=new GasAnalyzerCellSelection.Result(GasAnalyzerCellSelection.Status.SELECTED,cell.west());
        assertFalse(GasAnalyzerSessionPolicy.needsRefresh(11,20,same,same));
        assertTrue(GasAnalyzerSessionPolicy.needsRefresh(11,20,same,changed));
        assertTrue(GasAnalyzerSessionPolicy.needsRefresh(20,20,same,same));
        assertTrue(GasAnalyzerSessionPolicy.needsRefresh(11,20,same,
                new GasAnalyzerCellSelection.Result(GasAnalyzerCellSelection.Status.BLOCKED,cell)));
    }
}
