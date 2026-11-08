package com.juicyslew.moonstation14.ms14.construction.domain;

import java.util.*;

public record ConstructionGraph(String start, Map<String, Node> nodes) {
    public enum MaterialId { Steel, Plastic }
    public sealed interface Step permits MaterialStep, ToolStep { double seconds(); }
    public record MaterialStep(MaterialId material, int amount, double seconds) implements Step {
        public MaterialStep { Objects.requireNonNull(material); positive(amount); duration(seconds); }
    }
    public record ToolStep(com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData.Quality quality, int fuel, double seconds) implements Step {
        public ToolStep { Objects.requireNonNull(quality); if(fuel<0)throw new IllegalArgumentException("negative fuel"); duration(seconds); }
    }
    public sealed interface Condition permits Anchored {}
    public record Anchored(boolean required) implements Condition {}
    public record MaterialReturn(MaterialId material, int amount) {
        public MaterialReturn { Objects.requireNonNull(material); positive(amount); }
    }
    public record Edge(String to, Optional<Condition> condition, List<Step> steps, List<MaterialReturn> returns) {
        public Edge { Objects.requireNonNull(to); condition=Objects.requireNonNull(condition); steps=List.copyOf(steps); returns=List.copyOf(returns); if (steps.isEmpty()) throw new IllegalArgumentException("edge has no steps"); }
        public boolean eligible(boolean anchored) { return condition.isEmpty() || ((Anchored) condition.get()).required()==anchored; }
    }
    public record Node(Optional<String> entity, List<Edge> edges, int outputStackCount) {
        public Node(Optional<String> entity, List<Edge> edges) { this(entity, edges, 1); }
        public Node { entity=Objects.requireNonNull(entity); edges=List.copyOf(edges); if(outputStackCount<=0)throw new IllegalArgumentException("output stack count must be positive"); }
    }
    public ConstructionGraph { Objects.requireNonNull(start); nodes=Map.copyOf(nodes); if (!nodes.containsKey(start)) throw new IllegalArgumentException("missing graph start"); for (var entry:nodes.entrySet()) for (Edge e:entry.getValue().edges()) if (!nodes.containsKey(e.to())) throw new IllegalArgumentException("unknown endpoint: "+e.to()); }
    public List<Edge> eligibleEdges(String node, boolean anchored) { Node n=nodes.get(node); return n==null?List.of():n.edges().stream().filter(e->e.eligible(anchored)).toList(); }
    public Guidance guidance(String current,String goal,boolean anchored) {
        if (!nodes.containsKey(current)||!nodes.containsKey(goal)) return new Guidance(GuidanceStatus.INVALID_NODE,List.of());
        if (current.equals(goal)) return new Guidance(GuidanceStatus.AT_GOAL,List.of());
        record P(String node,List<Edge> edges){} var q=new ArrayDeque<P>(); var seen=new HashSet<String>(); q.add(new P(current,List.of()));
        while(!q.isEmpty()){var p=q.remove();if(!seen.add(p.node()))continue;for(var e:eligibleEdges(p.node(),anchored)){var l=new ArrayList<>(p.edges());l.add(e);if(e.to().equals(goal))return new Guidance(GuidanceStatus.ROUTE,List.copyOf(l));q.add(new P(e.to(),List.copyOf(l)));}}
        return new Guidance(GuidanceStatus.UNREACHABLE,List.of());
    }
    public enum GuidanceStatus { AT_GOAL, ROUTE, UNREACHABLE, INVALID_NODE }
    public record Guidance(GuidanceStatus status,List<Edge> route){ public Guidance{route=List.copyOf(route);} }
    private static void positive(int n){if(n<=0)throw new IllegalArgumentException("amount must be positive");}
    private static void duration(double n){if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException("duration must be finite and nonnegative");}
}

