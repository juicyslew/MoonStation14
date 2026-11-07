package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** Transaction facade over resolved shared pipe-network mixtures. */
public final class LoadedPipeGasWork {
    public enum Outcome { UNCHANGED, STALE, REJECTED, COMMITTED }
    public record Port(PipeTopology.NodeKey key) { public Port { Objects.requireNonNull(key); } }

    /** nodes() is deliberately diagnostic-only; ordinary captured gas work never expands membership. */
    public static final class Component {
        private final PipeTopology.NodeKey root;
        private final PipeGasInventory inventory;
        private final PipeGasInventory.Network network;
        private final GasMixture aggregate;
        private volatile List<LoadedPipeComponent.Node> nodes;
        private Component(PipeTopology.NodeKey root, PipeGasInventory inventory, PipeGasInventory.Network network) {
            this.root=Objects.requireNonNull(root); this.inventory=inventory; this.network=network;
            this.aggregate=network.mixture();
        }
        /** Detached fixture/compatibility constructor; runtime capture never uses it. */
        public Component(PipeTopology.NodeKey root, List<LoadedPipeComponent.Node> nodes, GasMixture aggregate) {
            this.root=Objects.requireNonNull(root); this.inventory=null; this.network=null;
            this.aggregate=Objects.requireNonNull(aggregate); this.nodes=List.copyOf(nodes);
        }
        public PipeTopology.NodeKey root() { return root; }
        public GasMixture aggregate() { return aggregate; }
        public PipeGasInventory.Network network() { return network; }
        public List<LoadedPipeComponent.Node> nodes() {
            List<LoadedPipeComponent.Node> result=nodes;
            if(result==null) synchronized(this) {
                result=nodes;
                if(result==null) {
                    List<LoadedPipeComponent.Node> expanded=new ArrayList<>();
                    for(PipeTopology.NodeKey key:network.members()) {
                        PipeGasInventory.Entry e=inventory.get(key).orElseThrow();
                        expanded.add(new LoadedPipeComponent.Node(key,e.incarnation(),e.volume(),e));
                    }
                    nodes=result=List.copyOf(expanded);
                }
            }
            return result;
        }
    }
    private record NetCapture(PipeGasInventory.Network network) { }
    public static final class Snapshot {
        private final PipeGasInventory inventory;
        private final Map<PipeTopology.NodeKey,Component> components;
        private final Map<PipeTopology.NodeKey,PipeTopology.NodeKey> portComponents;
        private final Map<PipeTopology.NodeKey,NetCapture> nets;
        private final boolean aliases;
        public Snapshot(PipeGasInventory inventory, Map<PipeTopology.NodeKey,Component> components,
                        Map<PipeTopology.NodeKey,PipeTopology.NodeKey> portComponents, boolean aliases) {
            this(inventory,components,portComponents,aliases,Map.of());
        }
        private Snapshot(PipeGasInventory inventory, Map<PipeTopology.NodeKey,Component> components,
                         Map<PipeTopology.NodeKey,PipeTopology.NodeKey> portComponents, boolean aliases,
                         Map<PipeTopology.NodeKey,NetCapture> nets) {
            this.inventory=inventory; this.components=Map.copyOf(components);
            this.portComponents=Map.copyOf(portComponents); this.aliases=aliases; this.nets=Map.copyOf(nets);
        }
        public PipeGasInventory inventory() { return inventory; }
        public Map<PipeTopology.NodeKey,Component> components() { return components; }
        public Map<PipeTopology.NodeKey,PipeTopology.NodeKey> portComponents() { return portComponents; }
        public boolean aliases() { return aliases; }
        public Optional<Component> componentFor(PipeTopology.NodeKey port) {
            PipeTopology.NodeKey root=portComponents.get(port);
            return root==null?Optional.empty():Optional.ofNullable(components.get(root));
        }
        private Map<PipeTopology.NodeKey,NetCapture> nets() { return nets; }
    }
    private LoadedPipeGasWork() { }

    /** Resolves only the supplied ports. Network metadata is already cached by the topology owner. */
    public static Optional<Snapshot> capture(ServerLevel level, Collection<Port> ports) {
        if(level==null||level.isClientSide||level.getServer()==null||!level.getServer().isSameThread()||ports==null||ports.isEmpty()) return Optional.empty();
        try {
            PipeGasInventorySavedData saved=PipeGasInventorySavedData.existing(level).orElse(null);
            if(saved==null||!LoadedPipeNetworks.reconcile(level)) return Optional.empty();
            PipeGasInventory inventory=saved.snapshot();
            Map<UUID,Component> byNetwork=new HashMap<>();
            Map<PipeTopology.NodeKey,Component> components=new LinkedHashMap<>();
            Map<PipeTopology.NodeKey,PipeTopology.NodeKey> portMap=new HashMap<>();
            Map<PipeTopology.NodeKey,NetCapture> netCaptures=new HashMap<>();
            boolean aliases=false;
            for(Port port:ports) {
                PipeTopology.NodeKey key=Objects.requireNonNull(port).key();
                PipeGasInventory.Entry entry=inventory.get(key).filter(PipeGasInventory.Entry::active).orElse(null);
                if(entry==null||LoadedPipeNetworks.network(level,key).filter(n->n.id().equals(inventory.network(key).map(PipeGasInventory.Network::id).orElse(null))).isEmpty()) return Optional.empty();
                PipeGasInventory.Network network=inventory.network(key).orElse(null);
                if(network==null)return Optional.empty();
                Component c=byNetwork.get(network.id());
                if(c==null) {
                    PipeTopology.NodeKey root=network.members().iterator().next();
                    c=new Component(root,inventory,network); byNetwork.put(network.id(),c);
                    components.put(root,c); netCaptures.put(root,new NetCapture(network));
                } else aliases=true;
                portMap.put(key,c.root());
            }
            if(saved.snapshot()!=inventory)return Optional.empty();
            return Optional.of(new Snapshot(inventory,components,portMap,aliases,netCaptures));
        } catch(RuntimeException invalid) { return Optional.empty(); }
    }

    public static Outcome commit(ServerLevel level, Snapshot snapshot, Map<PipeTopology.NodeKey,GasMixture> proposed, double externalThermalJ) {
        BatchPreparation p=prepareBatch(level,snapshot,proposed,externalThermalJ);
        return p.outcome()!=Outcome.COMMITTED?p.outcome():commitPrepared(p.mutation());
    }
    static Outcome commitPrepared(PipeGasTransaction.PreparedMutation mutation) {
        if(mutation==null||!mutation.validate())return Outcome.STALE;
        if(!mutation.writeRaw())return Outcome.STALE;
        try { mutation.publish(); } catch(Throwable failure) {
            throw new PipeGasTransaction.RecoveryRequiredException("pipe gas publication failed after raw swap acceptance",failure,null);
        }
        return Outcome.COMMITTED;
    }
    private record BatchPreparation(Outcome outcome, PipeGasTransaction.PreparedMutation mutation) { }
    public static Optional<PipeGasTransaction.PreparedMutation> prepare(ServerLevel level, Snapshot snapshot,
            Map<PipeTopology.NodeKey,GasMixture> proposed, double externalThermalJ) {
        BatchPreparation result=prepareBatch(level,snapshot,proposed,externalThermalJ);
        return result.outcome()==Outcome.COMMITTED?Optional.of(result.mutation()):Optional.empty();
    }

    private static BatchPreparation prepareBatch(ServerLevel level, Snapshot s, Map<PipeTopology.NodeKey,GasMixture> proposals, double external) {
        if(level==null||s==null||proposals==null||!Double.isFinite(external)||level.isClientSide||level.getServer()==null||!level.getServer().isSameThread()) return new BatchPreparation(Outcome.REJECTED,null);
        try {
            PipeGasInventorySavedData saved=PipeGasInventorySavedData.existing(level).orElse(null);
            if(saved==null)return new BatchPreparation(Outcome.STALE,null);
            for(PipeTopology.NodeKey root:proposals.keySet()) if(!s.components().containsKey(root))return new BatchPreparation(Outcome.REJECTED,null);
            if(!readyFor(level,s,proposals.keySet()))return new BatchPreparation(Outcome.STALE,null);
            if(saved.snapshot()!=s.inventory())return new BatchPreparation(Outcome.STALE,null);
            if(proposals.isEmpty())return new BatchPreparation(external==0?Outcome.UNCHANGED:Outcome.REJECTED,null);
            Map<PipeTopology.NodeKey,GasMixture> updates=new LinkedHashMap<>();
            double beforeEnergy=0,afterEnergy=0;
            EnumMap<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Double> beforeGas=new EnumMap<>(com.juicyslew.moonstation14.ms14.atmos.core.GasType.class), afterGas=new EnumMap<>(com.juicyslew.moonstation14.ms14.atmos.core.GasType.class);
            for(var e:proposals.entrySet()) {
                NetCapture capture=s.nets().get(e.getKey());
                if(capture==null)return new BatchPreparation(Outcome.REJECTED,null);
                PipeGasInventory.Network current=s.inventory().network(e.getKey()).orElse(null);
                PipeGasInventory.Network expected=capture.network();
                if(current==null||!current.id().equals(expected.id())||current.topologyGeneration()!=expected.topologyGeneration()||current.gasRevision()!=expected.gasRevision()||!current.members().equals(expected.members()))return new BatchPreparation(Outcome.STALE,null);
                GasMixture before=current.mixture(), after=Objects.requireNonNull(e.getValue());
                if(Double.compare(before.volumeCubicMeters(),after.volumeCubicMeters())!=0)return new BatchPreparation(Outcome.REJECTED,null);
                if(before.gasMoles().equals(after.gasMoles())&&Double.compare(before.temperatureKelvin(),after.temperatureKelvin())==0)continue;
                beforeEnergy+=before.thermalEnergy(); afterEnergy+=after.thermalEnergy();
                before.gasMoles().forEach((g,n)->beforeGas.merge(g,n,Double::sum)); after.gasMoles().forEach((g,n)->afterGas.merge(g,n,Double::sum));
                updates.put(e.getKey(),after);
            }
            if(updates.isEmpty())return new BatchPreparation(external==0?Outcome.UNCHANGED:Outcome.REJECTED,null);
            for(var gas:com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()) if(!PipeGasLedger.conservedSpecies(beforeGas.getOrDefault(gas,0d),afterGas.getOrDefault(gas,0d)))return new BatchPreparation(Outcome.REJECTED,null);
            if(!PipeGasLedger.conservedEnergy(beforeEnergy+external,afterEnergy))return new BatchPreparation(Outcome.REJECTED,null);
            PipeGasInventory after=s.inventory().withNetworkMixtures(updates);
            PipeGasTransaction.PreparedMutation mutation=preparedRawSwap(saved,s.inventory(),after,()->{
                if(PipeGasInventorySavedData.existing(level).orElse(null)!=saved||!readyFor(level,s,proposals.keySet())||saved.snapshot()!=s.inventory())return false;
                for(var e:proposals.entrySet()) {
                    NetCapture c=s.nets().get(e.getKey()); PipeGasInventory.Network live=saved.snapshot().network(e.getKey()).orElse(null);
                    if(live==null||!live.id().equals(c.network().id())||live.topologyGeneration()!=c.network().topologyGeneration()||live.gasRevision()!=c.network().gasRevision()||!live.members().equals(c.network().members()))return false;
                }
                return true;
            });
            return new BatchPreparation(Outcome.COMMITTED,mutation);
        } catch(RuntimeException invalid) { return new BatchPreparation(Outcome.REJECTED,null); }
    }

    private static boolean readyFor(ServerLevel level, Snapshot snapshot, Collection<PipeTopology.NodeKey> roots) {
        for(PipeTopology.NodeKey root:roots) {
            NetCapture expected=snapshot.nets().get(root);
            if(expected==null||!LoadedPipeNetworks.ready(level,root))return false;
            PipeGasInventory.Network ready=LoadedPipeNetworks.network(level,root).orElse(null);
            if(ready==null||!ready.id().equals(expected.network().id())
                    ||ready.topologyGeneration()!=expected.network().topologyGeneration()
                    ||ready.gasRevision()!=expected.network().gasRevision()
                    ||!ready.members().equals(expected.network().members()))return false;
        }
        return true;
    }

    static PipeGasTransaction.PreparedMutation preparedRawSwap(PipeGasInventorySavedData saved, PipeGasInventory before,
            PipeGasInventory after, java.util.function.BooleanSupplier validator) {
        return new PipeGasTransaction.PreparedMutation() {
            private PipeGasInventorySavedData.PendingRawSwap token; private boolean published;
            @Override public Object identity(){return saved;}
            @Override public boolean validate(){return validator.getAsBoolean()&&saved.snapshot()==before;}
            @Override public boolean writeRaw(){if(!validate()||token!=null)return false;token=saved.beginRawSwap(before,after);return token!=null;}
            @Override public boolean rollbackRaw(){if(published)return false;if(token==null)return true;boolean ok=saved.rollbackRawSwap(token);if(ok)token=null;return ok;}
            @Override public void publish(){saved.publishRawSwap(token);token=null;published=true;}
        };
    }
}
