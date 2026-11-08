package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FinitePipeSpeciesAuditTest {
    @Test void networkGasRevisionsCountUniqueNetworksAndRejectTopologyChanges() {
        UUID id=UUID.randomUUID(); var a=new com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey(new net.minecraft.core.BlockPos(0,0,0),com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY);
        var b=new com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey(new net.minecraft.core.BlockPos(1,0,0),com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY);
        var before=Map.of(id,new FinitePipeSpeciesAudit.NetworkCounter(2,Set.of(a,b),4L));
        var unchanged=FinitePipeSpeciesAudit.assessNetworks(before,before,43,43,43);
        assertEquals(0,unchanged.nodeWrites()); assertEquals(0,unchanged.bound());
        var changed=Map.of(id,new FinitePipeSpeciesAudit.NetworkCounter(2,Set.of(a,b),5L));
        var one=FinitePipeSpeciesAudit.assessNetworks(before,changed,43,43.00001,43);
        assertEquals(1,one.nodeWrites()); assertTrue(one.bound()>0);
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assessNetworks(before,
                Map.of(UUID.randomUUID(),changed.get(id)),43,43,43));
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assessNetworks(before,
                Map.of(id,new FinitePipeSpeciesAudit.NetworkCounter(3,Set.of(a,b),5)),43,43,43));
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assessNetworks(before,
                Map.of(id,new FinitePipeSpeciesAudit.NetworkCounter(2,Set.of(a),5)),43,43,43));
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assessNetworks(changed,before,43,43,43));
    }
    @Test void zeroWritesRequireExactConservation() {
        var report=FinitePipeSpeciesAudit.assess(Map.of("a",4L),Map.of("a",4L),43,43,43);
        assertEquals(0,report.bound());assertTrue(report.conserved());
        assertFalse(FinitePipeSpeciesAudit.assess(Map.of("a",4L),Map.of("a",4L),43,43.000001,43).conserved());
    }
    @Test void derivedAllowanceScalesWithNodeVersionWritesAndFloatOperandUlp() {
        var one=FinitePipeSpeciesAudit.assess(Map.of("a",1L),Map.of("a",2L),43,43.00001,43);
        var three=FinitePipeSpeciesAudit.assess(Map.of("a",1L),Map.of("a",4L),43,43.00001,43);
        assertEquals(1,one.nodeWrites());assertEquals(3,three.nodeWrites());
        assertEquals(one.bound()*3,three.bound());assertTrue(one.conserved());
    }
    @Test void materialLossAndBeyondBoundResidualAreRejected() {
        var basis=FinitePipeSpeciesAudit.assess(Map.of("a",1L),Map.of("a",2L),43,43,43);
        assertFalse(FinitePipeSpeciesAudit.assess(Map.of("a",1L),Map.of("a",2L),43,42,43).conserved());
        assertFalse(FinitePipeSpeciesAudit.assess(Map.of("a",1L),Map.of("a",2L),43,43+basis.bound()+0.001,43).conserved());
    }
    @Test void changedTrackedNodesAndVersionRollbackAreRejected() {
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assess(Map.of("a",1L),Map.of("b",1L),1,1,1));
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assess(Map.of("a",2L),Map.of("a",1L),1,1,1));
    }
    @Test void globalInventoryPlusExportUsesOnlyOwnedVersionWritesAndDerivedBound() {
        var before=Map.of("owned-a",2L,"owned-b",7L);
        var unchanged=FinitePipeSpeciesAudit.assess(before,before,43,43,43);
        assertEquals(0,unchanged.nodeWrites());assertEquals(0,unchanged.bound());assertTrue(unchanged.conserved());
        assertFalse(FinitePipeSpeciesAudit.assess(before,before,43,42.999,43).conserved());

        var one=FinitePipeSpeciesAudit.assess(before,Map.of("owned-a",3L,"owned-b",7L),43,43.00042248,43.00042248);
        var two=FinitePipeSpeciesAudit.assess(before,Map.of("owned-a",4L,"owned-b",7L),43,43.00042248,43.00042248);
        assertEquals(1,one.nodeWrites());assertEquals(2,two.nodeWrites());assertEquals(one.bound()*2,two.bound());
        assertFalse(one.conserved());
        var representative=FinitePipeSpeciesAudit.assess(before,Map.of("owned-a",16L,"owned-b",7L),43,43.00042248,43.00042248);
        assertEquals(14,representative.nodeWrites());assertTrue(representative.conserved());assertTrue(representative.absoluteError()>0);
        assertFalse(FinitePipeSpeciesAudit.assess(before,Map.of("owned-a",16L,"owned-b",7L),43,43+representative.bound()+1e-6,43+representative.bound()+1e-6).conserved());
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assess(before,Map.of("owned-a",3L,"foreign",7L),43,43,43));
    }
    @Test void invalidMagnitudeAndNonfiniteSpeciesInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assess(Map.of("a",1L),Map.of("a",1L),Double.NaN,1,1));
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assess(Map.of("a",1L),Map.of("a",1L),1,1,Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->FinitePipeSpeciesAudit.assess(Map.of("a",1L),Map.of("a",1L),1,1,Double.MAX_VALUE));
    }
}
