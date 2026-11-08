package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;

import java.util.*;

/** Read-only projection of the currently loaded, active gas nodes at one target block. */
public final class GasAnalyzerPipeSamples {
    public static final int MAX_ENTRIES = 32;
    private GasAnalyzerPipeSamples() { }

    public enum Source { VISIBLE_PIPE, DEVICE, UNARY_DEVICE }
    private static final String LABEL_PREFIX="gui.moonstation14.gas_analyzer.";
    public static String visiblePipeLabel(PipeTopology.Layer layer){
        return LABEL_PREFIX+"layer."+Objects.requireNonNull(layer).name().toLowerCase(Locale.ROOT);
    }
    /** Shared semantic name projection for analyzer transport and deterministic preview fixtures. */
    public static String deviceLabel(AtmosMachineProfile.Kind kind,String portId,PipeTopology.Layer layer,Direction direction){
        Objects.requireNonNull(portId);Objects.requireNonNull(layer);
        String id=portId.toLowerCase(Locale.ROOT);
        if(kind==AtmosMachineProfile.Kind.MIXER){
            if(id.equals("inlet"))return LABEL_PREFIX+"port.first_inlet";
            if(id.equals("filter"))return LABEL_PREFIX+"port.second_inlet";
            if(id.equals("outlet"))return LABEL_PREFIX+"port.outlet";
        }
        if(kind==AtmosMachineProfile.Kind.FILTER){
            if(id.equals("inlet"))return LABEL_PREFIX+"port.first_inlet";
            if(id.equals("filter"))return LABEL_PREFIX+"port.filtered_outlet";
            if(id.equals("outlet"))return LABEL_PREFIX+"port.outlet";
        }
        if(id.equals("inlet")||id.equals("input")||id.equals("in"))return LABEL_PREFIX+"port.first_inlet";
        if(id.equals("outlet")||id.equals("output")||id.equals("out"))return LABEL_PREFIX+"port.outlet";
        if(direction==null)return LABEL_PREFIX+"port."+layer.name().toLowerCase(Locale.ROOT)+"_gas_port";
        return LABEL_PREFIX+"port."+layer.name().toLowerCase(Locale.ROOT)+"_"+direction.getName().toLowerCase(Locale.ROOT);
    }
    /** Exact saved-owner identity; later session code can reject replacement/reconfiguration. */
    public record Stamp(PipeTopology.NodeKey node, UUID incarnation, long version, double volumeCubicMeters) {
        public Stamp {
            Objects.requireNonNull(node); Objects.requireNonNull(incarnation);
            if (version < 1 || !Double.isFinite(volumeCubicMeters) || volumeCubicMeters <= 0)
                throw new IllegalArgumentException("invalid pipe sample identity");
        }
    }
    public record Entry(Stamp stamp, String labelKey, GasAnalyzerSample sample) {
        public Entry(Stamp stamp,GasAnalyzerSample sample){
            this(stamp,stamp.node().devicePort()
                    ?deviceLabel(null,stamp.node().portId(),stamp.node().layer(),null)
                    :visiblePipeLabel(stamp.node().layer()),sample);
        }
        public Entry {
            Objects.requireNonNull(stamp); Objects.requireNonNull(sample);
            if(labelKey==null||!labelKey.startsWith(LABEL_PREFIX)||labelKey.length()>128)
                throw new IllegalArgumentException("invalid analyzer display label");
            if (Math.abs(sample.volumeLitres() - stamp.volumeCubicMeters() * 1000) > 1e-6)
                throw new IllegalArgumentException("sample volume disagrees with node identity");
        }
    }
    public record Readout(Source source, List<Entry> entries,String bindingIdentity) {
        public Readout(Source source,List<Entry> entries){this(source,entries,"");}
        public Readout {
            Objects.requireNonNull(source); entries = List.copyOf(entries);
            if(bindingIdentity==null||bindingIdentity.length()>256)throw new IllegalArgumentException("invalid analyzer source binding");
            if (entries.isEmpty() || entries.size() > MAX_ENTRIES || source==Source.UNARY_DEVICE&&entries.size()!=1
                    || entries.stream().anyMatch(Objects::isNull)
                    || entries.stream().map(e -> e.stamp().node()).distinct().count() != entries.size())
                throw new IllegalArgumentException("invalid analyzer node readout");
        }
    }

    /** Empty means no safely readable active gas nodes; this method never creates or loads ownership. */
    public static Optional<Readout> read(ServerLevel level, BlockPos pos, GasAnalyzerProfile profile) {
        Objects.requireNonNull(level); Objects.requireNonNull(pos); Objects.requireNonNull(profile);
        if (!level.getServer().isSameThread() || !level.hasChunkAt(pos)) return Optional.empty();
        var result = new ArrayList<Entry>();
        var be = level.getBlockEntity(pos);
        if (be instanceof VisualPipeBlockEntity visual) {
            int[] lanes = { VisualPipeBlockEntity.PRIMARY, VisualPipeBlockEntity.SECONDARY, VisualPipeBlockEntity.TERTIARY };
            for (int lane : lanes) {
                if (!visual.hasLayer(lane)) continue;
                var owner = LoadedPipeGasOwner.activeSnapshot(level, pos, lane).orElse(null);
                if (owner == null) return Optional.empty();
                if(!add(level,result, owner, profile,visiblePipeLabel(switch(lane){case VisualPipeBlockEntity.PRIMARY->PipeTopology.Layer.PRIMARY;case VisualPipeBlockEntity.SECONDARY->PipeTopology.Layer.SECONDARY;default->PipeTopology.Layer.TERTIARY;})))return Optional.empty();
            }
            return result.isEmpty() ? Optional.empty() : Optional.of(new Readout(Source.VISIBLE_PIPE, result));
        }
        if(be instanceof VentilationDeviceBlockEntity device)return readUnaryDevice(level,pos,device,profile);
        var host = LoadedDevicePortGasOwner.host(level, pos).orElse(null);
        if (host == null) return Optional.empty();
        List<PipeGasPortHost.Port> ports = host.gasPorts();
        if (ports == null || ports.size() > MAX_ENTRIES) return Optional.empty();
        for (var port : ports) {
            if (port == null) return Optional.empty();
            var owner = LoadedDevicePortGasOwner.activeSnapshot(level, pos, port.id()).orElse(null);
            if (owner == null) return Optional.empty();
            AtmosMachineProfile.Kind machineKind=be instanceof com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity machine
                    ?java.util.Optional.ofNullable(machine.resolvedProfile()).map(AtmosMachineProfile::kind).orElse(null):null;
            Direction direction=port.directions().stream().sorted().findFirst().orElse(null);
            if(!add(level,result, owner, profile,deviceLabel(machineKind,port.id(),port.layer(),direction)))return Optional.empty();
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(new Readout(Source.DEVICE, result));
    }

    private static Optional<Readout> readUnaryDevice(ServerLevel level,BlockPos pos,VentilationDeviceBlockEntity device,
                                                       GasAnalyzerProfile profile){
        if(device.getLevel()!=level||level.getBlockEntity(pos)!=device||!device.settingsValid()||device.prototype()==null
                ||!(device.getBlockState().getBlock() instanceof VentilationDeviceBlock block)||block.kind()!=device.kind())
            return Optional.empty();
        var settings=device.config().settings();
        PipeTopology.Layer layer;
        try{layer=PipeTopology.Layer.valueOf(settings.selectedLayer().name());}
        catch(RuntimeException invalid){return Optional.empty();}
        int lane=1<<layer.ordinal();
        Direction towardDevice=device.roomFacePos();
        BlockPos pipePos=device.pipePortPos();
        if(pipePos.equals(pos)||!pipePos.relative(towardDevice).equals(pos)||!level.hasChunkAt(pipePos))return Optional.empty();
        PipeGasInventory.Entry owner;
        if(level.getBlockEntity(pipePos) instanceof VisualPipeBlockEntity visual){
            if(!visual.hasLayer(lane))return Optional.empty();
            // Unary ventilation endpoints use the exact configured facing/port position
            // consumed by FiniteVentilationTransfer; they are not generic PipeGasPortHosts.
            owner=LoadedPipeGasOwner.activeSnapshot(level,pipePos,lane).orElse(null);
        }else{
            var segment=LoadedSealedWallSegment.matchingLive(level,pipePos,layer).orElse(null);
            if(segment==null||(segment.ports()&(1<<towardDevice.ordinal()))==0)return Optional.empty();
            owner=LoadedSealedWallGasOwner.activeSnapshot(level,pipePos,layer).orElse(null);
            if(owner!=null&&!owner.incarnation().equals(segment.id()))return Optional.empty();
        }
        if(owner==null||!owner.node().position().equals(pipePos)||owner.node().layer()!=layer
                ||owner.node().devicePort()||Double.compare(owner.volume(),Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3)!=0)
            return Optional.empty();
        String label=visiblePipeLabel(layer);
        String binding=device.kind().name()+":"+towardDevice.name()+":"+layer.name();
        var share=nodeShare(level,owner).orElse(null);
        if(share==null)return Optional.empty();
        return Optional.of(new Readout(Source.UNARY_DEVICE,List.of(new Entry(
                new Stamp(owner.node(),owner.incarnation(),owner.version(),owner.volume()),label,
                 GasAnalyzerSample.fromMixture(share,owner.volume(),profile))),binding));
    }

    private static boolean add(ServerLevel level,List<Entry> result, PipeGasInventory.Entry owner, GasAnalyzerProfile profile,String labelKey) {
        if (result.size() >= MAX_ENTRIES) throw new IllegalArgumentException("too many analyzer pipe nodes");
        var share=nodeShare(level,owner).orElse(null);
        if(share==null)return false;
        result.add(new Entry(new Stamp(owner.node(), owner.incarnation(), owner.version(), owner.volume()),labelKey,
                GasAnalyzerSample.fromMixture(share, owner.volume(), profile)));
        return true;
    }

    private static Optional<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> nodeShare(ServerLevel level, PipeGasInventory.Entry entry) {
        var resolved=LoadedPipeNetworks.network(level,entry.node()).orElse(null);
        if(resolved==null)return Optional.empty();
        var inventory=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
        if(inventory==null)return Optional.empty();
        var current=inventory.get(entry.node()).orElse(null);
        var currentNetwork=inventory.network(entry.node()).orElse(null);
        if(current==null||!current.active()||!current.incarnation().equals(entry.incarnation())
                ||currentNetwork==null||!currentNetwork.id().equals(resolved.id())
                ||currentNetwork.topologyGeneration()!=resolved.topologyGeneration())return Optional.empty();
        try{return Optional.of(inventory.nodeShare(entry.node()));}
        catch(RuntimeException unavailable){return Optional.empty();}
    }
}
