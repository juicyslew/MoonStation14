package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;

import java.util.*;

/** Pure equalization plan for a caller-validated connected component of exposed pipe nodes. */
public final class PipeComponentEqualization {
    private PipeComponentEqualization() { }

    public record Node(PipeTopology.NodeKey key, UUID incarnation, long version, double volume, GasMixture mixture) {
        public Node {
            Objects.requireNonNull(key); Objects.requireNonNull(incarnation); Objects.requireNonNull(mixture);
            if (version <= 0 || !Double.isFinite(volume) || volume <= 0) throw new IllegalArgumentException("invalid pipe node");
        }
    }

    public static Map<PipeTopology.NodeKey, PipeGasInventory.ExpectedIdentityVersionAndGasAfter> plan(List<Node> component) {
        Objects.requireNonNull(component);
        if (component.isEmpty() || component.size() > PipeGasLedger.MAX_RECIPIENTS) throw new IllegalArgumentException("invalid component size");
        Set<PipeTopology.NodeKey> keys = new HashSet<>();
        List<GasMixture> mixtures = new ArrayList<>(component.size());
        double[] volumes = new double[component.size()];
        for (int i = 0; i < component.size(); i++) {
            Node node = Objects.requireNonNull(component.get(i));
            if (!keys.add(node.key())) throw new IllegalArgumentException("duplicate pipe node");
            mixtures.add(node.mixture()); volumes[i] = node.volume();
        }
        GasMixture shared = PipeGasLedger.merge(mixtures);
        List<GasMixture> split = PipeGasLedger.split(shared, volumes);
        Map<PipeTopology.NodeKey, PipeGasInventory.ExpectedIdentityVersionAndGasAfter> result = new LinkedHashMap<>();
        for (int i = 0; i < component.size(); i++) {
            Node node = component.get(i);
            result.put(node.key(), new PipeGasInventory.ExpectedIdentityVersionAndGasAfter(node.incarnation(), node.version(), split.get(i)));
        }
        return Collections.unmodifiableMap(result);
    }
}

