package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipeGasInventoryRetiredArchiveTest {
    @Test void retiringSharedNetworkNodeArchivesItsFiniteShareAndConservesSurvivor() {
        var a=new PipeTopology.NodeKey(new BlockPos(0,0,0),PipeTopology.Layer.PRIMARY);
        var b=new PipeTopology.NodeKey(new BlockPos(1,0,0),PipeTopology.Layer.PRIMARY);
        UUID aid=UUID.randomUUID(), bid=UUID.randomUUID();
        var inv=new PipeGasInventory().admit(a,aid,1).admit(b,bid,3).remapNetworks(List.of(Set.of(a,b)));
        GasMixture full=new GasMixture(Map.of(GasType.OXYGEN,8.0),300,4);
        inv=inv.withNetworkMixtures(Map.of(a,full));
        var retired=inv.retire(a,aid);
        var archived=retired.retiredSnapshot().get(aid);
        assertEquals(2.0,archived.mixture().moles(GasType.OXYGEN),1e-6);
        assertEquals(300,archived.mixture().temperatureKelvin());
        assertEquals(6.0,retired.mixture(b).moles(GasType.OXYGEN),1e-6);
        assertEquals(3.0,retired.mixture(b).volumeCubicMeters(),1e-6);
        assertEquals(8.0,archived.mixture().moles(GasType.OXYGEN)+retired.mixture(b).moles(GasType.OXYGEN),1e-6);
        var json=PipeGasInventory.CODEC.encodeStart(JsonOps.INSTANCE,retired).getOrThrow(IllegalStateException::new);
        var decoded=PipeGasInventory.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow(IllegalStateException::new);
        var loadedArchive=decoded.retiredSnapshot().get(aid);
        assertEquals(archived.node(),loadedArchive.node()); assertEquals(archived.incarnation(),loadedArchive.incarnation());
        assertEquals(archived.volume(),loadedArchive.volume()); assertEquals(archived.version(),loadedArchive.version());
        assertEquals(archived.mixture().gasMoles(),loadedArchive.mixture().gasMoles());
        assertEquals(archived.mixture().temperatureKelvin(),loadedArchive.mixture().temperatureKelvin());
        assertEquals(archived.mixture().volumeCubicMeters(),loadedArchive.mixture().volumeCubicMeters());
        assertEquals(archived.mixture().thermalEnergy(),loadedArchive.mixture().thermalEnergy());
        var loadedNetwork=decoded.mixture(b); var savedNetwork=retired.mixture(b);
        assertEquals(savedNetwork.gasMoles(),loadedNetwork.gasMoles()); assertEquals(savedNetwork.temperatureKelvin(),loadedNetwork.temperatureKelvin());
        assertEquals(savedNetwork.volumeCubicMeters(),loadedNetwork.volumeCubicMeters()); assertEquals(savedNetwork.thermalEnergy(),loadedNetwork.thermalEnergy());
    }

    @Test void fractionalPhysicalNodeVolumeArchivesAndRoundTripsCanonicalFloatVolume() {
        var a=new PipeTopology.NodeKey(new BlockPos(4,1,0),PipeTopology.Layer.PRIMARY);
        var b=new PipeTopology.NodeKey(new BlockPos(5,1,0),PipeTopology.Layer.PRIMARY);
        UUID aid=UUID.randomUUID();
        var inv=new PipeGasInventory().admit(a,aid,.1).admit(b,UUID.randomUUID(),.2).remapNetworks(List.of(Set.of(a,b)));
        var net=inv.network(a).orElseThrow();
        inv=inv.withNetworkMixtures(Map.of(a,new GasMixture(Map.of(GasType.OXYGEN,3d),300,net.mixture().volumeCubicMeters())));
        var retired=inv.retire(a,aid); var saved=retired.retiredSnapshot().get(aid);
        assertEquals(.1,saved.volume()); assertEquals((double)(float).1,saved.mixture().volumeCubicMeters());
        var tag=PipeGasInventory.CODEC.encodeStart(JsonOps.INSTANCE,retired).getOrThrow(IllegalStateException::new);
        var loaded=PipeGasInventory.CODEC.parse(JsonOps.INSTANCE,tag).getOrThrow(IllegalStateException::new).retiredSnapshot().get(aid);
        assertEquals(saved.volume(),loaded.volume()); assertEquals(saved.mixture().volumeCubicMeters(),loaded.mixture().volumeCubicMeters());
        assertEquals(saved.mixture().gasMoles(),loaded.mixture().gasMoles()); assertEquals(saved.mixture().thermalEnergy(),loaded.mixture().thermalEnergy());
    }

    @Test void retiringEmptyNodeRetainsSurvivorNetworkTemperature() {
        var a=new PipeTopology.NodeKey(new BlockPos(7,1,0),PipeTopology.Layer.PRIMARY);
        var b=new PipeTopology.NodeKey(new BlockPos(8,1,0),PipeTopology.Layer.PRIMARY);
        UUID aid=UUID.randomUUID();
        var inv=new PipeGasInventory().admit(a,aid,1).admit(b,UUID.randomUUID(),2).remapNetworks(List.of(Set.of(a,b)));
        var net=inv.network(a).orElseThrow();
        var warmEmpty=inv.withNetworkMixtures(Map.of(a,new GasMixture(Map.of(),600,net.mixture().volumeCubicMeters())));
        var after=warmEmpty.retire(a,aid);
        assertEquals(600,after.mixture(b).temperatureKelvin());
        assertEquals(2,after.mixture(b).volumeCubicMeters());
    }

    @Test void fractionalRetirementBindsArchiveToCanonicalNodeVolumeAndConservesWholeNetwork() {
        var keys=List.of(new PipeTopology.NodeKey(new BlockPos(20,0,0),PipeTopology.Layer.PRIMARY),
                new PipeTopology.NodeKey(new BlockPos(21,0,0),PipeTopology.Layer.PRIMARY),
                new PipeTopology.NodeKey(new BlockPos(22,0,0),PipeTopology.Layer.PRIMARY),
                new PipeTopology.NodeKey(new BlockPos(23,0,0),PipeTopology.Layer.PRIMARY),
                new PipeTopology.NodeKey(new BlockPos(24,0,0),PipeTopology.Layer.PRIMARY));
        double[] volumes={.1,.2,.05,.2,.15}; UUID[] ids=new UUID[5];
        var inv=new PipeGasInventory();
        for(int i=0;i<keys.size();i++) { ids[i]=UUID.randomUUID(); inv=inv.admit(keys.get(i),ids[i],volumes[i]); }
        inv=inv.remapNetworks(List.of(Set.copyOf(keys)));
        double oldNetworkVolume=inv.mixture(keys.get(0)).volumeCubicMeters();
        inv=inv.withNetworkMixtures(Map.of(keys.get(0),new GasMixture(Map.of(GasType.OXYGEN,8.0,GasType.PLASMA,1.0),700,oldNetworkVolume)));
        var before=inv.network(keys.get(0)).orElseThrow().mixture();
        var survivorMetadata=inv.get(keys.get(0)).orElseThrow();
        var retired=inv.retire(keys.get(1),ids[1]);
        var archive=retired.retiredSnapshot().get(ids[1]);
        assertEquals((double)(float).2,archive.mixture().volumeCubicMeters());
        assertEquals(.2,archive.volume()); assertEquals(700,archive.mixture().temperatureKelvin());
        assertSame(survivorMetadata,retired.get(keys.get(0)).orElseThrow());
        assertEquals((double)(float)(.1+.05+.2+.15),retired.mixture(keys.get(0)).volumeCubicMeters());
        double oxygen=archive.mixture().moles(GasType.OXYGEN)+retired.mixture(keys.get(0)).moles(GasType.OXYGEN);
        double plasma=archive.mixture().moles(GasType.PLASMA)+retired.mixture(keys.get(0)).moles(GasType.PLASMA);
        double energy=archive.mixture().thermalEnergy()+retired.mixture(keys.get(0)).thermalEnergy();
        assertTrue(PipeGasLedger.conservedSpecies(before.moles(GasType.OXYGEN),oxygen));
        assertTrue(PipeGasLedger.conservedSpecies(before.moles(GasType.PLASMA),plasma));
        assertTrue(PipeGasLedger.conservedEnergy(before.thermalEnergy(),energy));
        var json=PipeGasInventory.CODEC.encodeStart(JsonOps.INSTANCE,retired).getOrThrow(IllegalStateException::new);
        var loaded=PipeGasInventory.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow(IllegalStateException::new);
        var archivedAgain=loaded.retiredSnapshot().get(ids[1]);
        assertEquals(archive.volume(),archivedAgain.volume());
        assertEquals(archive.mixture().volumeCubicMeters(),archivedAgain.mixture().volumeCubicMeters());
        assertEquals(archive.mixture().gasMoles(),archivedAgain.mixture().gasMoles());
        assertEquals(archive.mixture().temperatureKelvin(),archivedAgain.mixture().temperatureKelvin());
        assertEquals(archive.mixture().thermalEnergy(),archivedAgain.mixture().thermalEnergy());
        assertEquals(retired.mixture(keys.get(0)).gasMoles(),loaded.mixture(keys.get(0)).gasMoles());
        assertEquals(retired.mixture(keys.get(0)).volumeCubicMeters(),loaded.mixture(keys.get(0)).volumeCubicMeters());
        assertEquals(retired.mixture(keys.get(0)).thermalEnergy(),loaded.mixture(keys.get(0)).thermalEnergy());
    }

    @Test void thirteenTenthNetworkRetirementUsesCanonicalPointTwoArchiveVolume() {
        var keys=new java.util.ArrayList<PipeTopology.NodeKey>(); var ids=new java.util.ArrayList<UUID>();
        var volumes=new java.util.ArrayList<Double>(); var inv=new PipeGasInventory();
        for(int i=0;i<7;i++) {
            keys.add(new PipeTopology.NodeKey(new BlockPos(40+i,0,0),PipeTopology.Layer.PRIMARY));
            ids.add(UUID.randomUUID()); double v=i==6?.1:.2; volumes.add(v); inv=inv.admit(keys.get(i),ids.get(i),v);
        }
        inv=inv.remapNetworks(List.of(Set.copyOf(keys)));
        double volume=inv.mixture(keys.get(0)).volumeCubicMeters();
        inv=inv.withNetworkMixtures(Map.of(keys.get(0),new GasMixture(Map.of(GasType.OXYGEN,8.0,GasType.PLASMA,1.0),700,volume)));
        GasMixture before=inv.mixture(keys.get(0));
        var retired=inv.retire(keys.get(0),ids.get(0)); var archive=retired.retiredSnapshot().get(ids.get(0));
        assertEquals((double)(float).2,archive.mixture().volumeCubicMeters());
        assertEquals(.2,archive.volume());
        assertEquals((double)(float)1.1,retired.mixture(keys.get(1)).volumeCubicMeters());
        double oxygen=archive.mixture().moles(GasType.OXYGEN)+retired.mixture(keys.get(1)).moles(GasType.OXYGEN);
        double plasma=archive.mixture().moles(GasType.PLASMA)+retired.mixture(keys.get(1)).moles(GasType.PLASMA);
        assertTrue(PipeGasLedger.conservedSpecies(before.moles(GasType.OXYGEN),oxygen));
        assertTrue(PipeGasLedger.conservedSpecies(before.moles(GasType.PLASMA),plasma));
        assertTrue(PipeGasLedger.conservedEnergy(before.thermalEnergy(),archive.mixture().thermalEnergy()+retired.mixture(keys.get(1)).thermalEnergy()));
        var json=PipeGasInventory.CODEC.encodeStart(JsonOps.INSTANCE,retired).getOrThrow(IllegalStateException::new);
        var loaded=PipeGasInventory.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow(IllegalStateException::new);
        var loadedArchive=loaded.retiredSnapshot().get(ids.get(0));
        assertEquals(archive.volume(),loadedArchive.volume()); assertEquals(archive.mixture().volumeCubicMeters(),loadedArchive.mixture().volumeCubicMeters());
        assertEquals(archive.mixture().gasMoles(),loadedArchive.mixture().gasMoles()); assertEquals(archive.mixture().temperatureKelvin(),loadedArchive.mixture().temperatureKelvin());
        assertEquals(archive.mixture().thermalEnergy(),loadedArchive.mixture().thermalEnergy());
        assertEquals(retired.mixture(keys.get(1)).gasMoles(),loaded.mixture(keys.get(1)).gasMoles());
        assertEquals(retired.mixture(keys.get(1)).volumeCubicMeters(),loaded.mixture(keys.get(1)).volumeCubicMeters());
    }

    @Test void retiringSeveralMembersOfOneNetPartitionsOnceAndTombstoneRefusesGas() {
        var a=new PipeTopology.NodeKey(new BlockPos(0,1,0),PipeTopology.Layer.PRIMARY);
        var b=new PipeTopology.NodeKey(new BlockPos(1,1,0),PipeTopology.Layer.PRIMARY);
        var c=new PipeTopology.NodeKey(new BlockPos(2,1,0),PipeTopology.Layer.PRIMARY);
        UUID aid=UUID.randomUUID(),bid=UUID.randomUUID(),cid=UUID.randomUUID();
        var inv=new PipeGasInventory().admit(a,aid,1).admit(b,bid,1).admit(c,cid,2).remapNetworks(List.of(Set.of(a,b,c)));
        var seeded=inv.withNetworkMixtures(Map.of(a,new GasMixture(Map.of(GasType.NITROGEN,8.0),400,4)));
        assertThrows(IllegalStateException.class,()->seeded.tombstoneEmpty(a,aid));
        var result=seeded.retireBatch(Map.of(a,aid,b,bid));
        assertEquals(2.0,result.retiredSnapshot().get(aid).mixture().moles(GasType.NITROGEN),1e-6);
        assertEquals(2.0,result.retiredSnapshot().get(bid).mixture().moles(GasType.NITROGEN),1e-6);
        assertEquals(4.0,result.mixture(c).moles(GasType.NITROGEN),1e-6);
    }
}
