package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipeGasInventoryRemapTest {
    private static PipeTopology.NodeKey key(int x) { return new PipeTopology.NodeKey(new BlockPos(x,0,0),PipeTopology.Layer.PRIMARY); }

    @Test void simultaneousSplitMergeWeightsByFullReceiverVolumesAndConservesThermalState() {
        var a=key(0); var b=key(1); var c=key(2); var d=key(3);
        var inv=new PipeGasInventory().admit(a,UUID.randomUUID(),1).admit(b,UUID.randomUUID(),3)
                .admit(c,UUID.randomUUID(),1).admit(d,UUID.randomUUID(),5)
                .remapNetworks(List.of(Set.of(a,b),Set.of(c,d)));
        inv=inv.withNetworkMixtures(Map.of(a,new GasMixture(Map.of(GasType.OXYGEN,10d),300,4),c,new GasMixture(Map.of(GasType.NITROGEN,20d),600,6)));
        double energyBefore=inv.network(a).orElseThrow().mixture().thermalEnergy()+inv.network(c).orElseThrow().mixture().thermalEnergy();
        var result=inv.remapNetworks(List.of(Set.of(a,c),Set.of(b,d)));
        assertEquals(2, result.mixture(a).moles(GasType.OXYGEN),1e-5);
        assertEquals(4, result.mixture(a).moles(GasType.NITROGEN),1e-5);
        assertEquals(8, result.mixture(b).moles(GasType.OXYGEN),1e-5);
        assertEquals(16, result.mixture(b).moles(GasType.NITROGEN),1e-5);
        double energyAfter=result.network(a).orElseThrow().mixture().thermalEnergy()+result.network(b).orElseThrow().mixture().thermalEnergy();
        assertEquals(energyBefore,energyAfter,Math.max(1e-5,Math.ulp((float)energyBefore)*16));
    }

    @Test void vacuumAdditionNoopIdentityAndRelocationKeepMembershipCoherent() {
        var a=key(10); var b=key(11); var c=key(12); var d=key(13);
        var inv=new PipeGasInventory().admit(a,UUID.randomUUID(),1).admit(b,UUID.randomUUID(),3)
                .admit(c,UUID.randomUUID(),2).remapNetworks(List.of(Set.of(a,b),Set.of(c)));
        inv=inv.withNetworkMixtures(Map.of(a,new GasMixture(Map.of(GasType.PLASMA,4d),350,4)));
        var stable=inv.remapNetworks(List.of(Set.of(a,b),Set.of(c)));
        assertSame(inv,stable);
        var added=stable.admit(d,UUID.randomUUID(),5);
        var joined=added.remapNetworks(List.of(Set.of(a,b,d),Set.of(c)));
        assertEquals(4,joined.mixture(a).moles(GasType.PLASMA),1e-5);
        var entry=joined.get(a).orElseThrow(); var moved=key(20);
        var relocated=joined.relocateBatch(Map.of(a,new PipeGasInventory.ExpectedIdentityVersionAndDestination(entry.incarnation(),entry.version(),moved)));
        assertTrue(relocated.network(a).isEmpty());
        assertEquals(4,relocated.mixture(moved).moles(GasType.PLASMA),1e-5);
        assertTrue(relocated.network(moved).orElseThrow().members().contains(moved));
        assertEquals(4,relocated.remapNetworks(List.of(Set.of(moved,b,d),Set.of(c))).mixture(moved).moles(GasType.PLASMA),1e-5);
    }

    @Test void relocationPreservesUnaffectedNetworkAndOnlyChangesMovedEntry() {
        var a=key(40); var b=key(41); var c=key(42); var moved=key(43);
        var inv=new PipeGasInventory().admit(a,UUID.randomUUID(),1).admit(b,UUID.randomUUID(),2)
                .admit(c,UUID.randomUUID(),3).remapNetworks(List.of(Set.of(a,b),Set.of(c)));
        inv=inv.withNetworkMixtures(Map.of(a,new GasMixture(Map.of(GasType.OXYGEN,12d),320,3),
                c,new GasMixture(Map.of(GasType.NITROGEN,9d),280,3)));
        var affectedBefore=inv.network(a).orElseThrow();
        var unaffectedBefore=inv.network(c).orElseThrow();
        var entryBefore=inv.get(c).orElseThrow();
        var inventoryRevision=inv.revision();
        var source=inv.get(a).orElseThrow();

        var relocated=inv.relocateBatch(Map.of(a,new PipeGasInventory.ExpectedIdentityVersionAndDestination(
                source.incarnation(),source.version(),moved)));

        var affectedAfter=relocated.network(moved).orElseThrow();
        assertNotSame(affectedBefore,affectedAfter);
        assertEquals(Set.of(moved,b),affectedAfter.members());
        assertEquals(affectedBefore.mixture().gasMoles(),affectedAfter.mixture().gasMoles());
        assertEquals(affectedBefore.mixture().temperatureKelvin(),affectedAfter.mixture().temperatureKelvin());
        assertEquals(affectedBefore.mixture().volumeCubicMeters(),affectedAfter.mixture().volumeCubicMeters());
        assertEquals(affectedBefore.mixture().thermalEnergy(),affectedAfter.mixture().thermalEnergy(),1e-8);
        assertEquals(source.incarnation(),relocated.get(moved).orElseThrow().incarnation());
        assertEquals(source.version()+1,relocated.get(moved).orElseThrow().version());
        assertTrue(relocated.get(a).isEmpty());
        assertSame(unaffectedBefore,relocated.network(c).orElseThrow());
        assertSame(entryBefore,relocated.get(c).orElseThrow());
        assertSame(unaffectedBefore.mixture(),relocated.mixture(c));
        assertNotEquals(affectedBefore.id(),affectedAfter.id());
        assertEquals(affectedBefore.topologyGeneration()+1,affectedAfter.topologyGeneration());
        assertEquals(affectedBefore.gasRevision(),affectedAfter.gasRevision());
        assertEquals(unaffectedBefore.id(),relocated.network(c).orElseThrow().id());
        assertEquals(unaffectedBefore.topologyGeneration(),relocated.network(c).orElseThrow().topologyGeneration());
        assertEquals(unaffectedBefore.gasRevision(),relocated.network(c).orElseThrow().gasRevision());
        assertEquals(inventoryRevision+1,relocated.revision());
    }

    @Test void emptyNetworkRemapPreservesSs14ReceiverTemperatureAndSourceSplitTemperature() {
        var a=key(30); var b=key(31);
        var connected=new PipeGasInventory().admit(a,UUID.randomUUID(),1).admit(b,UUID.randomUUID(),2);
        var joined=connected.remapNetworks(List.of(Set.of(a,b)));
        assertEquals((double)(float)PipeGasInventory.DEFAULT_PIPE_TEMPERATURE_KELVIN,joined.mixture(a).temperatureKelvin());

        var seeded=joined.withNetworkMixtures(Map.of(a,new GasMixture(Map.of(),600,3)));
        var split=seeded.remapNetworks(List.of(Set.of(a),Set.of(b)));
        assertEquals(600,split.mixture(a).temperatureKelvin()); assertEquals(600,split.mixture(b).temperatureKelvin());

    }
}
