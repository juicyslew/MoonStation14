package com.juicyslew.moonstation14.ms14.construction.domain;

import java.util.*;

/** Pure, single-edge work state. Caller persists state and performs validated inventory/world effects. */
public record ConstructionGraphProgress(String node, int edgeIndex, int stepIndex) {
    public ConstructionGraphProgress { Objects.requireNonNull(node); if(edgeIndex<0||stepIndex<0)throw new IllegalArgumentException("negative progress index"); }
    public record Result(ConstructionGraphProgress progress, Optional<String> completedNode, List<ConstructionGraph.MaterialReturn> returns) { public Result{returns=List.copyOf(returns);} }
    public Result completeStep(ConstructionGraph graph, boolean anchored) {
        var nodeData=graph.nodes().get(node); if(nodeData==null||edgeIndex>=nodeData.edges().size())throw new IllegalStateException("invalid current edge");
        var edge=nodeData.edges().get(edgeIndex); if(!edge.eligible(anchored))throw new IllegalStateException("edge condition not met");
        if(stepIndex+1<edge.steps().size())return new Result(new ConstructionGraphProgress(node,edgeIndex,stepIndex+1),Optional.empty(),List.of());
        return new Result(new ConstructionGraphProgress(edge.to(),0,0),Optional.of(edge.to()),edge.returns());
    }
}
