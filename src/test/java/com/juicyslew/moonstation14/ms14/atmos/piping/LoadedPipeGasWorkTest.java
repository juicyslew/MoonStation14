package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoadedPipeGasWorkTest {
    @Test void snapshotComponentLookupReturnsEmptyForUncapturedPortAndResolvesMappedPort() {
        var root=new PipeTopology.NodeKey(new BlockPos(38,0,0),PipeTopology.Layer.PRIMARY,"pipe");
        var uncaptured=new PipeTopology.NodeKey(new BlockPos(39,0,0),PipeTopology.Layer.PRIMARY,"pipe");
        var component=new LoadedPipeGasWork.Component(root,List.of(),new GasMixture(Map.of(),300));
        var snapshot=new LoadedPipeGasWork.Snapshot(new PipeGasInventory(),Map.of(root,component),Map.of(root,root),false);

        assertSame(component,snapshot.componentFor(root).orElseThrow());
        assertTrue(snapshot.componentFor(uncaptured).isEmpty(),"uncaptured non-null port keys have no component mapping");
    }

    @Test void detachedAliasPortsShareOneAggregateAndDistinctNetworksRemainDistinct() {
        var first = new PipeTopology.NodeKey(new BlockPos(50, 0, 0), PipeTopology.Layer.PRIMARY, "a");
        var alias = new PipeTopology.NodeKey(new BlockPos(51, 0, 0), PipeTopology.Layer.PRIMARY, "b");
        var other = new PipeTopology.NodeKey(new BlockPos(60, 0, 0), PipeTopology.Layer.PRIMARY, "c");
        UUID firstId = UUID.randomUUID(), aliasId = UUID.randomUUID(), otherId = UUID.randomUUID();
        var inventory = new PipeGasInventory().admit(first, firstId, 1).admit(alias, aliasId, 1)
                .admit(other, otherId, 1).remapNetworks(List.of(java.util.Set.of(first, alias), java.util.Set.of(other)));
        assertEquals(inventory.network(first).orElseThrow().id(), inventory.network(alias).orElseThrow().id());
        assertNotEquals(inventory.network(first).orElseThrow().id(), inventory.network(other).orElseThrow().id());

        var shared = new LoadedPipeGasWork.Component(first, List.of(), inventory.mixture(first));
        var separate = new LoadedPipeGasWork.Component(other, List.of(), inventory.mixture(other));
        var snapshot = new LoadedPipeGasWork.Snapshot(inventory, Map.of(first, shared, other, separate),
                Map.of(first, first, alias, first, other, other), true);
        assertSame(snapshot.componentFor(first).orElseThrow(), snapshot.componentFor(alias).orElseThrow());
        assertNotSame(snapshot.componentFor(first).orElseThrow(), snapshot.componentFor(other).orElseThrow());
        assertEquals(0, shared.aggregate().moles(GasType.OXYGEN) + separate.aggregate().moles(GasType.OXYGEN));
    }

    @Test void productionRelocationImagePairsAtomicallyWithHostLayerAndCompensatesFailedPair() {
        var pos = new BlockPos(41, 0, 0);
        var in = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY, "in");
        var out = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY, "out");
        var nextIn = new PipeTopology.NodeKey(pos, PipeTopology.Layer.SECONDARY, "in");
        var nextOut = new PipeTopology.NodeKey(pos, PipeTopology.Layer.SECONDARY, "out");
        UUID inId = UUID.randomUUID(), outId = UUID.randomUUID();
        PipeGasInventorySavedData saved = new PipeGasInventorySavedData();
        admitAndPublish(saved, in, inId, .1);
        admitAndPublish(saved, out, outId, .2);
        seedAndPublish(saved, in, inId, new GasMixture(Map.of(GasType.PLASMA, 2d), 420, .1));
        seedAndPublish(saved, out, outId, new GasMixture(Map.of(GasType.OXYGEN, 3d), 280, .2));
        PipeGasInventory before = saved.snapshot();
        PipeGasInventory after = before.relocateBatch(Map.of(
                in, new PipeGasInventory.ExpectedIdentityVersionAndDestination(inId, before.get(in).orElseThrow().version(), nextIn),
                out, new PipeGasInventory.ExpectedIdentityVersionAndDestination(outId, before.get(out).orElseThrow().version(), nextOut)));
        PipeGasTransaction.PreparedMutation raw = LoadedPipeGasWork.preparedRawSwap(saved, before, after, () -> true);
        PipeTopology.Layer[] layer = {PipeTopology.Layer.PRIMARY};
        int[] publishes = {0};
        Object hostIdentity = new Object();
        PipeGasTransaction.PreparedMutation failingHost = new PipeGasTransaction.PreparedMutation() {
            public Object identity() { return hostIdentity; }
            public boolean validate() { return layer[0] == PipeTopology.Layer.PRIMARY; }
            public boolean writeRaw() {
                layer[0] = PipeTopology.Layer.SECONDARY;
                assertThrows(IllegalStateException.class, () -> saved.save(new net.minecraft.nbt.CompoundTag(), null));
                return false;
            }
            public boolean rollbackRaw() { layer[0] = PipeTopology.Layer.PRIMARY; return true; }
            public void publish() { publishes[0]++; }
        };
        assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(raw, failingHost));
        assertSame(before, saved.snapshot()); assertEquals(before.revision(), saved.revision());
        assertTrue(saved.snapshot().get(in).isPresent()); assertTrue(saved.snapshot().get(out).isPresent());
        assertEquals(2d, saved.snapshot().mixture(in).moles(GasType.PLASMA));
        assertEquals(inId, saved.snapshot().get(in).orElseThrow().incarnation());
        assertEquals(PipeTopology.Layer.PRIMARY, layer[0]); assertEquals(0, publishes[0]);
        saved.save(new net.minecraft.nbt.CompoundTag(), null);

        PipeGasTransaction.PreparedMutation accepted = LoadedPipeGasWork.preparedRawSwap(saved, before, after, () -> true);
        PipeGasTransaction.PreparedMutation host = new PipeGasTransaction.PreparedMutation() {
            public Object identity() { return hostIdentity; }
            public boolean validate() { return layer[0] == PipeTopology.Layer.PRIMARY; }
            public boolean writeRaw() { layer[0] = PipeTopology.Layer.SECONDARY; return true; }
            public boolean rollbackRaw() { layer[0] = PipeTopology.Layer.PRIMARY; return true; }
            public void publish() { publishes[0]++; }
        };
        PipeGasTransaction.commit(accepted, host);
        assertSame(after, saved.snapshot()); assertEquals(before.revision() + 1, saved.revision());
        assertTrue(saved.snapshot().get(in).isEmpty()); assertTrue(saved.snapshot().get(out).isEmpty());
        assertEquals(inId, saved.snapshot().get(nextIn).orElseThrow().incarnation());
        assertEquals(2d, saved.snapshot().mixture(nextIn).moles(GasType.PLASMA));
        assertEquals(3d, saved.snapshot().mixture(nextOut).moles(GasType.OXYGEN));
        assertEquals(PipeTopology.Layer.SECONDARY, layer[0]); assertEquals(1, publishes[0]);

        PipeGasTransaction.PreparedMutation stale = LoadedPipeGasWork.preparedRawSwap(saved, before, after, () -> true);
        int[] staleWrites = {0};
        PipeGasTransaction.PreparedMutation untouchedHost = new PipeGasTransaction.PreparedMutation() {
            public Object identity() { return new Object(); }
            public boolean validate() { return true; }
            public boolean writeRaw() { staleWrites[0]++; return true; }
            public boolean rollbackRaw() { return true; }
            public void publish() { fail("stale pair published"); }
        };
        assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(stale, untouchedHost));
        assertEquals(0, staleWrites[0]); assertEquals(PipeTopology.Layer.SECONDARY, layer[0]);
        assertSame(after, saved.snapshot()); assertEquals(before.revision() + 1, saved.revision());
    }

    private static void admitAndPublish(PipeGasInventorySavedData saved, PipeTopology.NodeKey key, UUID id, double volume) {
        PipeGasInventory before = saved.snapshot();
        PipeGasInventory after = before.admit(key, id, volume);
        var token = saved.beginRawSwap(before, after);
        assertNotNull(token, "single admission revision should be accepted");
        saved.publishRawSwap(token);
    }

    private static void seedAndPublish(PipeGasInventorySavedData saved, PipeTopology.NodeKey key, UUID id, GasMixture mixture) {
        PipeGasInventory before = saved.snapshot();
        PipeGasInventory.Entry entry = before.get(key).orElseThrow();
        PipeGasInventory after = before.withMixture(key, id, entry.version(), mixture);
        var token = saved.beginRawSwap(before, after);
        assertNotNull(token, "single mixture revision should be accepted");
        saved.publishRawSwap(token);
    }

    @Test void directCommitPublicationFailureIsRecoveryRequiredNotStale() {
        var key=new PipeTopology.NodeKey(new BlockPos(39,0,0),PipeTopology.Layer.PRIMARY);
        var saved=new PipeGasInventorySavedData();
        var before=saved.snapshot();
        var after=before.admit(key,UUID.randomUUID(),1.0);
        var raw=LoadedPipeGasWork.preparedRawSwap(saved,before,after,()->true);
        var failingPublish=new PipeGasTransaction.PreparedMutation() {
            public Object identity(){return raw.identity();}
            public boolean validate(){return raw.validate();}
            public boolean writeRaw(){return raw.writeRaw();}
            public boolean rollbackRaw(){return raw.rollbackRaw();}
            public void publish(){throw new IllegalStateException("injected publication failure");}
        };
        var failure=assertThrows(PipeGasTransaction.RecoveryRequiredException.class,
                ()->LoadedPipeGasWork.commitPrepared(failingPublish));
        assertTrue(failure.getMessage().contains("publication failed"));
        assertSame(after,saved.snapshot(),"accepted raw image remains for explicit recovery");
        assertTrue(raw.rollbackRaw());
        assertSame(before,saved.snapshot());
    }

    @Test void preparedInventoryRawSwapIsCompensatedAndPublishesOnlyAfterPairAcceptance() {
        var key=new PipeTopology.NodeKey(new BlockPos(40,0,0),PipeTopology.Layer.PRIMARY);
        UUID id=UUID.randomUUID();
        PipeGasInventorySavedData saved=new PipeGasInventorySavedData();
        admitAndPublish(saved, key, id, 1.0);
        seedAndPublish(saved, key, id, new GasMixture(Map.of(GasType.OXYGEN,2.0),300));
        PipeGasInventory before = saved.snapshot();
        PipeGasInventory committedBefore=saved.snapshot();
        var changed=committedBefore.withMixture(key,id,committedBefore.get(key).orElseThrow().version(),
                new GasMixture(Map.of(GasType.OXYGEN,4.0),300));
        // Update image is a valid immutable inventory revision; prepare the exact production raw owner adapter.
        var pipe=LoadedPipeGasWork.preparedRawSwap(saved,committedBefore,changed,()->true);
        Object environmentOwner=new Object();
        var failingEnvironment=new PipeGasTransaction.PreparedMutation() {
            public Object identity(){return environmentOwner;}
            public boolean validate(){return true;}
            public boolean writeRaw(){
                assertThrows(IllegalStateException.class,
                        ()->saved.save(new net.minecraft.nbt.CompoundTag(),null),
                        "pending unpublished raw image must not be serialized");
                return false;
            }
            public boolean rollbackRaw(){return true;}
            public void publish(){fail("failed environment must not publish");}
        };
        assertThrows(IllegalStateException.class,()->PipeGasTransaction.commit(pipe,failingEnvironment));
        assertSame(committedBefore,saved.snapshot());
        assertEquals(committedBefore.revision(),saved.revision());
        var restoredTag=saved.save(new net.minecraft.nbt.CompoundTag(),null);
        var expectedTag=PipeGasInventory.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,committedBefore)
                .getOrThrow(IllegalStateException::new);
        assertEquals(expectedTag,restoredTag.get("inventory"),"rollback permits saving the original inventory image");

        var accepted=LoadedPipeGasWork.preparedRawSwap(saved,committedBefore,changed,()->true);
        var environment=new PipeGasTransaction.PreparedMutation() {
            public Object identity(){return new Object();}
            public boolean validate(){return true;}
            public boolean writeRaw(){return true;}
            public boolean rollbackRaw(){return true;}
            public void publish(){}
        };
        PipeGasTransaction.commit(accepted,environment);
        assertSame(changed,saved.snapshot());
        assertEquals(committedBefore.revision()+1,saved.revision());
        assertFalse(accepted.rollbackRaw(), "published token is no longer reusable");

        var stale=LoadedPipeGasWork.preparedRawSwap(saved,committedBefore,changed,()->true);
        int[] environmentWrites={0};
        var untouchedEnvironment=new PipeGasTransaction.PreparedMutation() {
            public Object identity(){return new Object();}
            public boolean validate(){return true;}
            public boolean writeRaw(){environmentWrites[0]++;return true;}
            public boolean rollbackRaw(){return true;}
            public void publish(){}
        };
        assertThrows(IllegalStateException.class,()->PipeGasTransaction.commit(stale,untouchedEnvironment));
        assertEquals(0,environmentWrites[0],"stale pipe owner rejected before environment write");
    }

    @Test void componentAggregationPreservesNetworkBoundaryAndPhysicalVolume() {
        var oxygen=component(0, new double[]{1,3}, GasType.OXYGEN);
        var nitrogen=component(10, new double[]{2}, GasType.NITROGEN);
        assertEquals(4, oxygen.aggregate().volumeCubicMeters());
        assertEquals(3, oxygen.aggregate().moles(GasType.OXYGEN));
        assertEquals(0, oxygen.aggregate().moles(GasType.NITROGEN));
        assertEquals(1, nitrogen.aggregate().moles(GasType.NITROGEN));
        assertNotEquals(oxygen.root(), nitrogen.root());
    }

    @Test void componentVolumesCanDriveLedgerRedistributionWithoutJoiningOtherNetwork() {
        var component=component(20,new double[]{1,3},GasType.OXYGEN);
        var portions=PipeGasLedger.split(component.aggregate(),component.nodes().stream().mapToDouble(LoadedPipeComponent.Node::volume).toArray());
        assertEquals(0.75,portions.get(0).moles(GasType.OXYGEN),1e-5);
        assertEquals(2.25,portions.get(1).moles(GasType.OXYGEN),1e-5);
    }

    private static LoadedPipeGasWork.Component component(int x,double[] volumes,GasType gas) {
        PipeGasInventory inventory=new PipeGasInventory();
        var nodes=new java.util.ArrayList<LoadedPipeComponent.Node>();
        var mixtures=new java.util.ArrayList<GasMixture>();
        for(int i=0;i<volumes.length;i++) {
            var key=new PipeTopology.NodeKey(new BlockPos(x+i,0,0),PipeTopology.Layer.PRIMARY);
            UUID id=UUID.randomUUID(); inventory=inventory.admit(key,id,volumes[i]);
            GasMixture mixture=new GasMixture(Map.of(gas,(double)(i+1)),300,volumes[i]);
            inventory=inventory.withMixture(key,id,inventory.get(key).orElseThrow().version(),mixture);
            var entry=inventory.get(key).orElseThrow(); nodes.add(new LoadedPipeComponent.Node(key,id,volumes[i],entry));
            mixtures.add(mixture);
        }
        GasMixture merged=PipeGasLedger.merge(mixtures);
        return new LoadedPipeGasWork.Component(nodes.getFirst().key(),List.copyOf(nodes),
                new GasMixture(merged.gasMoles(),merged.temperatureKelvin(),java.util.Arrays.stream(volumes).sum()));
    }
}
