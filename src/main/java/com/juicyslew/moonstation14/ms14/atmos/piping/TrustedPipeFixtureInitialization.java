package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Trusted disposable-fixture seed boundary. Never callable through gameplay networking. */
public final class TrustedPipeFixtureInitialization {
    public record PortSeed(BlockPos host, String portId, UUID incarnation, GasMixture mixture) {
        public PortSeed {
            host = new BlockPos(Objects.requireNonNull(host));
            if (portId == null || portId.isBlank()) throw new IllegalArgumentException("port id required");
            Objects.requireNonNull(incarnation); Objects.requireNonNull(mixture);
        }
        @Override public BlockPos host() { return new BlockPos(host); }
    }
    public enum Failure {
        NONE, INVALID_CALL, EMPTY_SEED_SET, INVALID_SEED, DUPLICATE_PORT, HOST_NOT_LOADED,
        HOST_OR_PORT_MISMATCH, INVENTORY_NOT_FOUND, OWNER_NOT_FOUND, OWNER_INACTIVE,
        INCARNATION_MISMATCH, VOLUME_MISMATCH, PORT_NOT_VACUUM, UPDATE_REJECTED, CAS_REJECTED
    }
    /** completedPorts is zero on every failure; on success it counts distinct supplied port seeds. */
    public record Result(Failure failure, String detail, int completedPorts) {
        public Result { Objects.requireNonNull(failure); Objects.requireNonNull(detail); }
        public boolean successful() { return failure == Failure.NONE; }
        private static Result fail(Failure failure,String detail,int completed) { return new Result(failure,detail,completed); }
        private static Result pass(int completed) { return new Result(Failure.NONE,"accepted",completed); }
    }
    private record Resolved(PipeGasPortHost.Port port,PipeTopology.NodeKey key) { }

    private TrustedPipeFixtureInitialization() { }

    /**
     * Seeds the shared networks targeted by exact active vacuum ports once. Each seed contributes
     * its finite per-port inventory to that network; a single representative seed contributes
     * only its own amount, not an amount multiplied by network membership. The caller must persist
     * and check its fixture's one-time initialization token before invoking this trusted setup seam.
     */
    public static Result validateVacuumPorts(ServerLevel level, Iterable<PortSeed> seeds) {
        List<PortSeed> snapshot=copySeeds(seeds);
        if(snapshot==null)return Result.fail(Failure.INVALID_CALL,"invalid or unreadable seed collection",0);
        return preflight(level,snapshot);
    }

    public static Result initializeVacuumPortsOnce(ServerLevel level, Iterable<PortSeed> seeds) {
        List<PortSeed> snapshot=copySeeds(seeds);
        if(snapshot==null)return Result.fail(Failure.INVALID_CALL,"invalid or unreadable seed collection",0);
        Result preflight=preflight(level,snapshot);
        if(!preflight.successful())return preflight;
        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElse(null);
        if(data==null)return Result.fail(Failure.INVENTORY_NOT_FOUND,"pipe gas SavedData disappeared after preflight",0);
        PipeGasInventory before=data.snapshot();
        Result ready=preflight(level,snapshot,before);
        if(!ready.successful())return ready;
        Map<PipeTopology.NodeKey,List<GasMixture>> contributions=new LinkedHashMap<>();
        Map<PipeTopology.NodeKey,PortSeed> nodeSeeds=new LinkedHashMap<>();
        for(PortSeed seed:snapshot) {
            PipeTopology.NodeKey key=resolve(level,seed).key();
            nodeSeeds.put(key,seed);
            PipeGasInventory.Network network=before.network(key).orElseThrow();
            contributions.computeIfAbsent(network.members().stream().min(java.util.Comparator.comparing(PipeTopology.NodeKey::toString)).orElseThrow(),ignored->new ArrayList<>()).add(seed.mixture());
        }
        Map<PipeTopology.NodeKey,GasMixture> updates=new LinkedHashMap<>();
        for(var group:contributions.entrySet()) {
            PipeTopology.NodeKey representative=group.getKey();
            var network=before.network(representative).orElseThrow();
            double volume=network.members().stream().map(before::get).map(java.util.Optional::orElseThrow).mapToDouble(PipeGasInventory.Entry::volume).sum();
            GasMixture wholeNetwork=mergeContributions(group.getValue(),volume);
            updates.put(representative,wholeNetwork);
        }
        PipeGasInventory after;
        try { after=before.withNetworkMixtures(updates); }
        catch(RuntimeException invalid) { return Result.fail(Failure.UPDATE_REJECTED,"network gas update rejected: "+invalid.getMessage(),0); }
        for(PipeTopology.NodeKey key:nodeSeeds.keySet()) {
            var expected=before.network(key).orElse(null);
            var readyNetwork=LoadedPipeNetworks.network(level,key).orElse(null);
            if(expected==null||readyNetwork==null||!expected.id().equals(readyNetwork.id())
                    ||!expected.members().equals(readyNetwork.members()))
                return Result.fail(Failure.OWNER_NOT_FOUND,"target pipe network readiness changed before fixture seed commit",0);
        }
        if(data.snapshot()!=before||!data.replace(before.revision(),before,after))
            return Result.fail(Failure.CAS_REJECTED,"atomic pipe gas inventory replacement rejected",0);
        return Result.pass(nodeSeeds.size());
    }

    private static List<PortSeed> copySeeds(Iterable<PortSeed> seeds) {
        if(seeds==null)return null;
        try { List<PortSeed> copy=new ArrayList<>();for(PortSeed seed:seeds)copy.add(seed);return List.copyOf(copy); }
        catch(RuntimeException invalid) { return null; }
    }

    private static Result preflight(ServerLevel level,Iterable<PortSeed> seeds) {
        if(level==null||level.isClientSide||level.getServer()==null||!level.getServer().isSameThread()||seeds==null)
            return Result.fail(Failure.INVALID_CALL,"server-thread fixture initialization required",0);
        if(!LoadedPipeNetworks.reconcile(level))
            return Result.fail(Failure.OWNER_NOT_FOUND,"pipe topology is unresolved",0);
        PipeGasInventorySavedData data=PipeGasInventorySavedData.existing(level).orElse(null);
        if(data==null)return Result.fail(Failure.INVENTORY_NOT_FOUND,"pipe gas SavedData not yet created",0);
        PipeGasInventory inventory=data.snapshot();
        Result result=preflight(level,seeds,inventory);
        if(data.snapshot()!=inventory)return Result.fail(Failure.OWNER_NOT_FOUND,"pipe inventory changed during topology preflight",0);
        return result;
    }

    private static Result preflight(ServerLevel level,Iterable<PortSeed> seeds,PipeGasInventory inventory) {
        Map<PipeTopology.NodeKey,PortSeed> unique=new LinkedHashMap<>();
        Map<java.util.UUID,Set<PipeTopology.NodeKey>> targetedNetworks=new LinkedHashMap<>();
        try {
            for(PortSeed seed:seeds) {
                if(seed==null||isVacuum(seed.mixture()))return Result.fail(Failure.INVALID_SEED,"seed must contain finite non-vacuum gas",0);
                Resolved resolved=resolve(level,seed);
                if(resolved==null)return Result.fail(Failure.HOST_OR_PORT_MISMATCH,"loaded host/port identity does not match "+seed.host()+"/"+seed.portId(),0);
                if(!resolved.port().incarnation().equals(seed.incarnation()))return Result.fail(Failure.INCARNATION_MISMATCH,"live port incarnation mismatch at "+seed.host()+"/"+seed.portId(),0);
                if(!matchesCanonicalMixtureVolume(seed.mixture().volumeCubicMeters(),resolved.port().volume()))
                    return Result.fail(Failure.VOLUME_MISMATCH,"seed mixture volume "+seed.mixture().volumeCubicMeters()+" != live port volume "+resolved.port().volume()+" at "+seed.host()+"/"+seed.portId(),0);
                PipeTopology.NodeKey key=resolved.key();
                if(unique.putIfAbsent(key,seed)!=null)return Result.fail(Failure.DUPLICATE_PORT,"duplicate port seed "+seed.host()+"/"+seed.portId(),0);
                var network=inventory.network(key).orElse(null);
                if(network==null)return Result.fail(Failure.OWNER_NOT_FOUND,"port has no resolved gas network at "+seed.host()+"/"+seed.portId(),0);
                var readyNetwork=LoadedPipeNetworks.network(level,key).orElse(null);
                if(readyNetwork==null||!readyNetwork.id().equals(network.id())||!readyNetwork.members().equals(network.members()))
                    return Result.fail(Failure.OWNER_NOT_FOUND,"port gas network is not ready or no longer matches inventory at "+seed.host()+"/"+seed.portId(),0);
                targetedNetworks.put(network.id(),network.members());
            }
        } catch(RuntimeException invalid) { return Result.fail(Failure.INVALID_SEED,"invalid fixture seed: "+invalid.getMessage(),0); }
        if(unique.isEmpty())return Result.fail(Failure.EMPTY_SEED_SET,"no port seeds supplied",0);
        for(var item:unique.entrySet()) {
            PortSeed seed=item.getValue();var live=resolve(level,seed);
            if(live==null)return Result.fail(Failure.HOST_OR_PORT_MISMATCH,"loaded host/port identity changed at "+seed.host()+"/"+seed.portId(),0);
            if(!live.port().incarnation().equals(seed.incarnation()))return Result.fail(Failure.INCARNATION_MISMATCH,"live port incarnation changed at "+seed.host()+"/"+seed.portId(),0);
            var owner=inventory.get(item.getKey()).orElse(null);
            if(owner==null)return Result.fail(Failure.OWNER_NOT_FOUND,"no active inventory owner at "+seed.host()+"/"+seed.portId(),0);
            if(!owner.active())return Result.fail(Failure.OWNER_INACTIVE,"inventory owner is inactive at "+seed.host()+"/"+seed.portId(),0);
            if(!owner.incarnation().equals(seed.incarnation()))return Result.fail(Failure.INCARNATION_MISMATCH,"inventory incarnation mismatch at "+seed.host()+"/"+seed.portId(),0);
            if(Double.compare(owner.volume(),live.port().volume())!=0)return Result.fail(Failure.VOLUME_MISMATCH,"inventory volume "+owner.volume()+" != live port volume "+live.port().volume()+" at "+seed.host()+"/"+seed.portId(),0);
            if(!isVacuum(inventory.mixture(item.getKey())))return Result.fail(Failure.PORT_NOT_VACUUM,"target network contains existing gas/thermal energy at "+seed.host()+"/"+seed.portId(),0);
        }
        for(var members:targetedNetworks.values())for(var key:members) {
            var owner=inventory.get(key).orElse(null);
            if(owner==null||!owner.active())return Result.fail(Failure.OWNER_NOT_FOUND,"target network member has no active gas owner: "+key,0);
            if(!isVacuum(inventory.mixture(key)))return Result.fail(Failure.PORT_NOT_VACUUM,"target network contains existing gas/thermal energy at "+key,0);
        }
        PipeGasInventorySavedData current=PipeGasInventorySavedData.existing(level).orElse(null);
        if(current==null||current.snapshot()!=inventory)
            return Result.fail(Failure.OWNER_NOT_FOUND,"pipe inventory changed during topology preflight",0);
        for(var item:unique.entrySet()) {
            var expected=inventory.network(item.getKey()).orElse(null);
            var ready=LoadedPipeNetworks.network(level,item.getKey()).orElse(null);
            if(expected==null||ready==null||!expected.id().equals(ready.id())||!expected.members().equals(ready.members()))
                return Result.fail(Failure.OWNER_NOT_FOUND,"target pipe network readiness changed during preflight",0);
        }
        return Result.pass(0);
    }

    private static boolean isVacuum(GasMixture mixture) {
        return mixture.gasMoles().isEmpty() && mixture.thermalEnergy() == 0.0;
    }

    /** Combines the caller's per-port inventory contributions without replicating them per network member. */
    static GasMixture mergeContributions(List<GasMixture> contributions,double networkVolume) {
        if(contributions==null||contributions.isEmpty())throw new IllegalArgumentException("network requires at least one seed contribution");
        GasMixture merged=PipeGasLedger.merge(contributions);
        return new GasMixture(merged.gasMoles(),merged.temperatureKelvin(),networkVolume);
    }

    /** GasMixture stores volume as float; compare against that exact canonical representation. */
    static boolean matchesCanonicalMixtureVolume(double mixtureVolume, double resolvedPortVolume) {
        if (!Double.isFinite(mixtureVolume) || mixtureVolume < 0
                || !Double.isFinite(resolvedPortVolume) || resolvedPortVolume < 0) return false;
        float canonicalPortVolume = (float) resolvedPortVolume;
        return Float.isFinite(canonicalPortVolume)
                && Double.compare(mixtureVolume, (double) canonicalPortVolume) == 0;
    }

    private static Resolved resolve(ServerLevel level,PortSeed seed) {
        BlockPos pos = seed.host();
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return null;
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunk loaded)) return null;
        var be=loaded.getBlockEntity(pos);
        if(!(be instanceof PipeGasPortHost host))return null;
        var ports=host.gasPorts();
        if(ports.stream().filter(p->p.id().equals(seed.portId())).count()!=1)return null;
        var port=ports.stream().filter(p->p.id().equals(seed.portId())).findFirst().orElse(null);
        if(port==null)return null;
        return new Resolved(port,port.key(pos));
    }
}
