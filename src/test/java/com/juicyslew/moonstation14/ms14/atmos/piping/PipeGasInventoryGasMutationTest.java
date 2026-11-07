package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.teg.fixture.FinitePipeSpeciesAudit;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PipeGasInventoryGasMutationTest {
    @Test void fixtureSeedsUseWholeNetworkPhysicalVolumeWithoutChangingInventoryMetadata() {
        var one = new PipeTopology.NodeKey(new BlockPos(10,0,0),PipeTopology.Layer.PRIMARY);
        var oneNode = new PipeGasInventory().admit(one, UUID.randomUUID(), .2);
        assertFixtureSeed(oneNode, one, .2);

        var a = new PipeTopology.NodeKey(new BlockPos(20,0,0),PipeTopology.Layer.PRIMARY);
        var b = new PipeTopology.NodeKey(new BlockPos(21,0,0),PipeTopology.Layer.PRIMARY);
        var twoNodes = new PipeGasInventory().admit(a,UUID.randomUUID(),.2).admit(b,UUID.randomUUID(),.2)
                .remapNetworks(List.of(Set.of(a,b)));
        assertFixtureSeed(twoNodes, a, twoNodes.network(a).orElseThrow().mixture().volumeCubicMeters());
    }

    private static void assertFixtureSeed(PipeGasInventory inventory, PipeTopology.NodeKey key, double expectedVolume) {
        var networkBefore = inventory.network(key).orElseThrow();
        var entriesBefore = inventory.snapshot();
        var seeded = PipeGasFixtureTestSupport.atCurrentNetworkVolume(inventory, key,
                Map.of(GasType.OXYGEN, 2d, GasType.NITROGEN, 3d), 310);
        assertEquals(expectedVolume, seeded.volumeCubicMeters(), 1e-6);
        assertEquals(2d, seeded.moles(GasType.OXYGEN), 1e-6);
        assertEquals(3d, seeded.moles(GasType.NITROGEN), 1e-6);
        assertEquals(310d, seeded.temperatureKelvin(), 1e-5);
        var after = inventory.withNetworkMixtures(Map.of(key, seeded));
        assertEquals(expectedVolume, after.mixture(key).volumeCubicMeters(), 1e-6);
        assertSame(entriesBefore, after.snapshot());
        assertSame(networkBefore.members(), after.network(key).orElseThrow().members());
        assertEquals(networkBefore.gasRevision()+1, after.network(key).orElseThrow().gasRevision());
        var entry = inventory.get(key).orElseThrow();
        var single = inventory.withMixture(key, entry.incarnation(), entry.version(), seeded);
        assertEquals(expectedVolume, single.mixture(key).volumeCubicMeters(), 1e-6);
        assertEquals(networkBefore.gasRevision()+1, single.network(key).orElseThrow().gasRevision());
        assertSame(entry, single.get(key).orElseThrow());
    }

    @Test void gasMutationRetainsTopologyIdentityAndAliasUpdatesDeduplicate() {
        var a=new PipeTopology.NodeKey(new BlockPos(0,0,0),PipeTopology.Layer.PRIMARY);
        var b=new PipeTopology.NodeKey(new BlockPos(1,0,0),PipeTopology.Layer.PRIMARY);
        var c=new PipeTopology.NodeKey(new BlockPos(2,0,0),PipeTopology.Layer.PRIMARY);
        var inv=new PipeGasInventory().admit(a,UUID.randomUUID(),1).admit(b,UUID.randomUUID(),3).admit(c,UUID.randomUUID(),2)
                .remapNetworks(List.of(Set.of(a,b),Set.of(c)));
        var entryA=inv.get(a).orElseThrow(); var entryB=inv.get(b).orElseThrow();
        var nodes=inv.snapshot(); var members=inv.network(a).orElseThrow().members(); var other=inv.network(c).orElseThrow();
        var before=inv.network(a).orElseThrow();
        var changed=inv.withNetworkMixtures(Map.of(a,new GasMixture(Map.of(GasType.OXYGEN,4d),320,before.mixture().volumeCubicMeters()),
                b,new GasMixture(Map.of(GasType.OXYGEN,4d),320,before.mixture().volumeCubicMeters())));
        assertSame(nodes,changed.snapshot()); assertSame(entryA,changed.get(a).orElseThrow()); assertSame(entryB,changed.get(b).orElseThrow());
        assertSame(members,changed.network(a).orElseThrow().members()); assertSame(other,changed.network(c).orElseThrow());
        assertEquals(before.gasRevision()+1,changed.network(a).orElseThrow().gasRevision());
        assertEquals(1,changed.nodeShare(a).moles(GasType.OXYGEN),1e-6);
        assertEquals(320,changed.nodeShare(b).temperatureKelvin());
        assertSame(changed,changed.withNetworkMixtures(Map.of(a,changed.mixture(a))));
        assertEquals(293.15,inv.mixture(c).temperatureKelvin(),1e-3);
        assertThrows(IllegalArgumentException.class,()->inv.withNetworkMixtures(Map.of(a,
                new GasMixture(Map.of(GasType.OXYGEN,4d),320,before.mixture().volumeCubicMeters()),b,
                new GasMixture(Map.of(GasType.OXYGEN,5d),320,before.mixture().volumeCubicMeters()))));
        assertThrows(IllegalArgumentException.class,()->inv.withNetworkMixtures(Map.of(a,
                new GasMixture(Map.of(GasType.OXYGEN,4d),320,before.mixture().volumeCubicMeters()+1))));
    }

    @Test void gasRevisionAuditSeesRealGasMutationWithoutEntryVersionWrites() {
        var a=new PipeTopology.NodeKey(new BlockPos(8,0,0),PipeTopology.Layer.PRIMARY);
        var b=new PipeTopology.NodeKey(new BlockPos(9,0,0),PipeTopology.Layer.PRIMARY);
        var inventory=new PipeGasInventory().admit(a,UUID.randomUUID(),1).admit(b,UUID.randomUUID(),1)
                .remapNetworks(List.of(Set.of(a,b)));
        var network=inventory.network(a).orElseThrow();
        var before=new FinitePipeSpeciesAudit.NetworkCounter(network.topologyGeneration(),network.members(),network.gasRevision());
        var entryA=inventory.get(a).orElseThrow(); var entryB=inventory.get(b).orElseThrow();
        var mutated=inventory.withNetworkMixtures(Map.of(a,new GasMixture(Map.of(GasType.NITROGEN,2d),300,
                network.mixture().volumeCubicMeters())));
        assertSame(entryA,mutated.get(a).orElseThrow()); assertSame(entryB,mutated.get(b).orElseThrow());
        assertEquals(entryA.version(),mutated.get(a).orElseThrow().version());
        var afterNetwork=mutated.network(a).orElseThrow();
        var after=new FinitePipeSpeciesAudit.NetworkCounter(afterNetwork.topologyGeneration(),afterNetwork.members(),afterNetwork.gasRevision());
        var audit=FinitePipeSpeciesAudit.assessNetworks(Map.of(network.id(),before),Map.of(network.id(),after),2,2,2);
        assertEquals(1,audit.nodeWrites()); assertTrue(audit.bound()>0);
    }

    @Test void batchAuditsSharedNetworkOnceWithoutNodeVersionChanges() {
        var a=new PipeTopology.NodeKey(new BlockPos(4,0,0),PipeTopology.Layer.PRIMARY);
        var b=new PipeTopology.NodeKey(new BlockPos(5,0,0),PipeTopology.Layer.PRIMARY);
        UUID aid=UUID.randomUUID(),bid=UUID.randomUUID();
        var inv=new PipeGasInventory().admit(a,aid,1).admit(b,bid,1).remapNetworks(List.of(Set.of(a,b)));
        var n=inv.network(a).orElseThrow(); var gas=new GasMixture(Map.of(GasType.OXYGEN,2d),300,n.mixture().volumeCubicMeters());
        inv=inv.withNetworkMixtures(Map.of(a,gas));
        var ea=inv.get(a).orElseThrow(); var eb=inv.get(b).orElseThrow();
        var plan=Map.of(a,new PipeGasInventory.ExpectedIdentityVersionAndGasAfter(aid,ea.version(),gas),
                b,new PipeGasInventory.ExpectedIdentityVersionAndGasAfter(bid,eb.version(),gas));
        var after=inv.withBatchMixtures(plan);
        assertSame(ea,after.get(a).orElseThrow()); assertSame(eb,after.get(b).orElseThrow());
        assertEquals(n.gasRevision()+1,after.network(a).orElseThrow().gasRevision());
        assertEquals(2,after.mixture(a).moles(GasType.OXYGEN),1e-6);
    }
}
