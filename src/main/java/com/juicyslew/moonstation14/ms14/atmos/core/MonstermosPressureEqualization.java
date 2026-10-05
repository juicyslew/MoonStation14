package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

/** Bounded, pressure-directed normal Monstermos transport. */
public final class MonstermosPressureEqualization {
    private static final double R = 8.3144626;
    private MonstermosPressureEqualization() { }

    public record Settings(int routes, int searchWork, int transferOperations, double absoluteToleranceKpa,
                           double relativeTolerance) {
        public Settings {
            if (routes < 0 || searchWork < 0 || transferOperations < 0 || !Double.isFinite(absoluteToleranceKpa)
                    || !Double.isFinite(relativeTolerance) || absoluteToleranceKpa < 0 || relativeTolerance < 0)
                throw new IllegalArgumentException("Invalid pressure solver settings");
        }
    }
    // This is a numerical stopping floor, not a species/chemistry cutoff. Keep it below the
    // pressure represented by trace inventories that must still propagate through vacuum.
    public static final Settings DEFAULTS = new Settings(256, MonstermosEqualization.MAX_EDGE_SEARCH_WORK,
            MonstermosEqualization.MAX_TRANSFER_OPERATIONS, 1e-6, .001);
    public record Diagnostics(double pressureSpanBefore, double pressureSpanAfter, int routes,
                              int scalarPreviewSteps, boolean converged) { }
    public record Result(MonstermosEqualization.Result transport, Diagnostics diagnostics) { }

    public static Result equalizeDiscovered(Map<BlockPos, GasMixture> all, Map<BlockPos, Set<BlockPos>> adjacency,
                                             BlockPos seed, Set<BlockPos> immutable) {
        return equalizeDiscovered(all, adjacency, seed, immutable, DEFAULTS);
    }

    public static Result equalizeDiscovered(Map<BlockPos, GasMixture> all, Map<BlockPos, Set<BlockPos>> adjacency,
                                             BlockPos seed, Set<BlockPos> immutable, Settings settings) {
        Objects.requireNonNull(all); Objects.requireNonNull(adjacency); Objects.requireNonNull(seed);
        Objects.requireNonNull(immutable); Objects.requireNonNull(settings);
        if (all.size() > MonstermosEqualization.MAX_DISCOVERED_CELLS || !all.containsKey(seed)
                || !all.keySet().containsAll(immutable)) throw new IllegalArgumentException("Invalid discovered graph");
        List<BlockPos> bfs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(seed);
        seen.add(seed);
        while (!q.isEmpty()) {
            BlockPos position = q.remove();
            bfs.add(position);
            if (immutable.contains(position) && !position.equals(seed)) continue;
            Set<BlockPos> connected = adjacency.getOrDefault(position, Set.of());
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = position.relative(direction);
                if (connected.contains(neighbor) && seen.add(neighbor)) q.add(neighbor);
            }
        }
        if (bfs.size() != all.size()) throw new IllegalArgumentException("Discovered graph must be connected");
        for(var e: adjacency.entrySet()) for(BlockPos n:e.getValue())
            if (!all.containsKey(e.getKey()) || !all.containsKey(n) || !adjacency.getOrDefault(n,Set.of()).contains(e.getKey())
                    || Math.abs(e.getKey().getX()-n.getX())+Math.abs(e.getKey().getY()-n.getY())+Math.abs(e.getKey().getZ()-n.getZ())!=1)
                throw new IllegalArgumentException("Invalid six-face adjacency");
        List<BlockPos> nodes = bfs.subList(0, Math.min(MonstermosEqualization.UPSTREAM_SOFT_LIMIT, bfs.size()));
        Set<BlockPos> selected = new LinkedHashSet<>(nodes);
        Map<BlockPos, GasMixture> state = new LinkedHashMap<>();
        nodes.forEach(position -> state.put(position, all.get(position)));
        double[] pressure = new double[nodes.size()];
        Map<BlockPos, Integer> nodeIndex = new HashMap<>(nodes.size() * 2);
        for (int i = 0; i < nodes.size(); i++) {
            nodeIndex.put(nodes.get(i), i);
            pressure[i] = state.get(nodes.get(i)).pressureKpa();
        }
        int[][] neighbors = buildIncludedAdjacency(nodes, nodeIndex, adjacency);
        boolean[] fixed = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) fixed[i] = immutable.contains(nodes.get(i));
        int[] queue = new int[nodes.size()];
        int[] predecessor = new int[nodes.size()];
        int[] visitedGeneration = new int[nodes.size()];
        int generation = 0;
        double before=span(pressure); int searches=0, ops=0, routes=0, previews=0; Map<MonstermosEqualization.DirectedEdge,Double> flows=new LinkedHashMap<>();
        EnumMap<GasType,Double> ledger=new EnumMap<>(GasType.class); double energyLedger=0;
        while(routes<settings.routes && searches<settings.searchWork && ops<settings.transferOperations) {
            BlockPos hi = null, lo = null;
            int highIndex = -1, lowIndex = -1;
            double hp = Double.NEGATIVE_INFINITY, lp = Double.POSITIVE_INFINITY;
            for (int i = 0; i < nodes.size(); i++) {
                double value = pressure[i];
                if (value > hp) { hp = value; hi = nodes.get(i); highIndex = i; }
                if (value < lp) { lp = value; lo = nodes.get(i); lowIndex = i; }
            }
            if (hi == null || hi.equals(lo) || hp - lp <= Math.max(settings.absoluteToleranceKpa,
                    Math.max(Math.abs(hp), Math.abs(lp)) * settings.relativeTolerance)) break;

            generation++;
            int head = 0, tail = 0;
            queue[tail++] = highIndex;
            visitedGeneration[highIndex] = generation;
            boolean found = false;
            while (head < tail && searches < settings.searchWork && !found) {
                int current = queue[head++];
                searches++;
                for (int neighbor : neighbors[current]) {
                    if (neighbor < 0 || visitedGeneration[neighbor] == generation) continue;
                    visitedGeneration[neighbor] = generation;
                    predecessor[neighbor] = current;
                    if (neighbor == lowIndex) { found = true; break; }
                    if (!fixed[neighbor]) queue[tail++] = neighbor;
                }
            }
            if (!found) break;
            int pathLength = 1;
            for (int current = lowIndex; current != highIndex; current = predecessor[current]) pathLength++;
            int[] path = new int[pathLength];
            int current = lowIndex;
            for (int i = pathLength - 1; i >= 0; i--) {
                path[i] = current;
                if (current != highIndex) current = predecessor[current];
            }
            if (pathLength < 2 || pathLength - 1 > settings.transferOperations - ops) break;
            boolean fixedDonor = fixed[highIndex], fixedReceiver = fixed[lowIndex];
            double max = state.get(hi).totalMoles();
            PreviewNode[] previewNodes = new PreviewNode[pathLength];
            for (int i = 0; i < pathLength; i++) previewNodes[i] = new PreviewNode(state.get(nodes.get(path[i])));
            double amount = Double.NaN;
            if (isIsothermal(previewNodes)) {
                double candidate = isothermalTransfer(previewNodes[0], previewNodes[pathLength - 1],
                        fixedDonor, fixedReceiver);
                candidate = Math.max(0, Math.min(max, candidate));
                if (searches + pathLength <= settings.searchWork) {
                    previews += pathLength;
                    searches += pathLength;
                    double endpointGap = Math.abs(preview(previewNodes, candidate, fixedDonor, fixedReceiver));
                    double endpointTolerance = Math.max(settings.absoluteToleranceKpa,
                            Math.max(Math.abs(previewNodes[0].pressure),
                                    Math.abs(previewNodes[pathLength - 1].pressure)) * settings.relativeTolerance);
                    if (endpointGap <= endpointTolerance) amount = candidate;
                }
            }
            if (Double.isNaN(amount)) {
                int iterations = 14;
                if ((long) searches + (long) iterations * pathLength > settings.searchWork) break;
                double low = 0, high = max;
                for (int i = 0; i < iterations; i++) {
                    double mid = (low + high) / 2;
                    previews += pathLength;
                    double difference = preview(previewNodes, mid, fixedDonor, fixedReceiver);
                    if (difference > 0) low = mid;
                    else high = mid;
                }
                searches += iterations * pathLength;
                amount = (low + high) / 2;
            }
            if (!(amount > 0)) break;
            GasMixture packet=fixedDonor?scaledPacket(state.get(hi),amount):
                    state.get(hi).withScaledMoles(amount/state.get(hi).totalMoles());
            for(int i=1;i<pathLength;i++){
                int fromIndex = path[i - 1], toIndex = path[i];
                BlockPos from = nodes.get(fromIndex), to = nodes.get(toIndex);
                if (!fixed[fromIndex]) {
                    GasMixture src=state.get(from); double fraction=Math.min(1,packet.totalMoles()/src.totalMoles());
                    GasMixture.Removal split=src.splitRatio(fraction); packet=split.removed(); state.put(from,split.remaining());
                }
                if(!fixed[toIndex]) state.put(to,thermalMerge(state.get(to),packet));
                else { packet.gasMoles().forEach((g,v)->ledger.merge(g,v,Double::sum)); energyLedger+=packet.thermalEnergy(); }
                if(fixed[fromIndex]){packet.gasMoles().forEach((g,v)->ledger.merge(g,-v,Double::sum));energyLedger-=packet.thermalEnergy();}
                flows.merge(new MonstermosEqualization.DirectedEdge(from,to),packet.totalMoles(),Double::sum); ops++;
            }
            for (int index : path) pressure[index] = state.get(nodes.get(index)).pressureKpa();
            routes++;
        }
        double after=span(pressure); double lowP=Double.POSITIVE_INFINITY, highP=Double.NEGATIVE_INFINITY;
        for(double value:pressure){lowP=Math.min(lowP,value);highP=Math.max(highP,value);}
        boolean converged=after<=Math.max(settings.absoluteToleranceKpa,Math.max(Math.abs(lowP),Math.abs(highP))*settings.relativeTolerance);
        var transport=new MonstermosEqualization.Result(Map.copyOf(state),Map.copyOf(flows),
                new MonstermosEqualization.Work(bfs.size(),searches,ops,converged,converged?"pressure-tolerance":"pressure-budget-or-residual"),
                new MonstermosEqualization.BoundaryExchange(ledger,energyLedger),Set.copyOf(selected));
        return new Result(transport,new Diagnostics(before,after,routes,previews,converged));
    }

    private static int[][] buildIncludedAdjacency(List<BlockPos> nodes, Map<BlockPos, Integer> nodeIndex,
                                                  Map<BlockPos, Set<BlockPos>> adjacency) {
        int[][] neighbors = new int[nodes.size()][Direction.values().length];
        for (int i = 0; i < neighbors.length; i++) {
            Arrays.fill(neighbors[i], -1);
            BlockPos position = nodes.get(i);
            Set<BlockPos> connected = adjacency.getOrDefault(position, Set.of());
            int directionIndex = 0;
            for (Direction direction : Direction.values()) {
                Integer neighborIndex = nodeIndex.get(position.relative(direction));
                if (neighborIndex != null && connected.contains(nodes.get(neighborIndex))) {
                    neighbors[i][directionIndex] = neighborIndex;
                }
                directionIndex++;
            }
        }
        return neighbors;
    }

    private record PreviewNode(double moles,double capacity,double energy,double temperature,double volume,double pressure) {
        PreviewNode(GasMixture mixture){this(mixture.totalMoles(),mixture.heatCapacity(),mixture.thermalEnergy(),
                mixture.temperatureKelvin(),mixture.volumeCubicMeters(),mixture.pressureKpa());}
    }

    private static boolean isIsothermal(PreviewNode[] nodes) {
        double sourceTemperature = nodes[0].temperature;
        float tolerance = Math.ulp((float) sourceTemperature) * 2;
        for (PreviewNode node : nodes) {
            if (node.moles > 0 && Math.abs(node.temperature - sourceTemperature) > tolerance) return false;
        }
        return true;
    }

    private static double isothermalTransfer(PreviewNode source, PreviewNode receiver,
                                             boolean fixedDonor, boolean fixedReceiver) {
        if (source.volume == 0 || receiver.volume == 0) return Double.NaN;
        double inverseVolume = (fixedDonor ? 0 : 1 / source.volume)
                + (fixedReceiver ? 0 : 1 / receiver.volume);
        if (inverseVolume == 0) return Double.NaN;
        return (source.pressure - receiver.pressure) * 1000
                / (R * source.temperature * inverseVolume);
    }
    private static double preview(PreviewNode[] nodes,double amount,boolean fixedDonor,boolean fixedReceiver){
        PreviewNode source=nodes[0]; double cp=source.moles==0?0:source.capacity/source.moles;
        double temp=source.temperature;
        double sourcePressure=fixedDonor?source.pressure:(source.moles-amount)*R*temp/source.volume/1000;
        for(int i=1;i<nodes.length-1;i++){PreviewNode m=nodes[i]; double c=m.capacity+amount*cp;
            temp=c==0?temp:(m.energy+amount*cp*temp)/c; cp=(m.capacity+amount*cp)/(m.moles+amount);}
        PreviewNode end=nodes[nodes.length-1]; double c=end.capacity+amount*cp;
        double t=c==0?end.temperature:(end.energy+amount*cp*temp)/c;
        double endPressure=fixedReceiver?end.pressure:(end.moles+amount)*R*t/end.volume/1000;
        return sourcePressure-endPressure;
    }
    private static GasMixture thermalMerge(GasMixture receiver,GasMixture packet){
        EnumMap<GasType,Double> gas=new EnumMap<>(GasType.class); receiver.gasMoles().forEach(gas::put);
        packet.gasMoles().forEach((g,v)->gas.merge(g,v,Double::sum)); double cap=receiver.heatCapacity()+packet.heatCapacity();
        double t=cap==0?receiver.temperatureKelvin():(receiver.thermalEnergy()+packet.thermalEnergy())/cap;
        return new GasMixture(gas,t,receiver.volumeCubicMeters());
    }
    private static GasMixture scaledPacket(GasMixture source, double amount) {
        EnumMap<GasType,Double> species=new EnumMap<>(GasType.class);
        double total=source.totalMoles();
        if(total>0) source.gasMoles().forEach((type,moles)->species.put(type,moles*(amount/total)));
        return new GasMixture(species,source.temperatureKelvin(),source.volumeCubicMeters());
    }
    private static double span(double[] pressures){double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(double p:pressures){lo=Math.min(lo,p);hi=Math.max(hi,p);}return pressures.length==0?0:hi-lo;}
}
