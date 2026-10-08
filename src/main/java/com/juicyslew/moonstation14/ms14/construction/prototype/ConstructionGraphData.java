package com.juicyslew.moonstation14.ms14.construction.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData;
import java.util.*;

public record ConstructionGraphData(String start, Map<String, Node> nodes) {
    public record Node(Optional<String> entity, List<Edge> edges, int outputStackCount) {
        public Node(Optional<String> entity, List<Edge> edges) { this(entity, edges, 1); }
        public Node { entity=Objects.requireNonNull(entity); edges=List.copyOf(edges); if(outputStackCount<=0)throw new IllegalArgumentException("output stack count must be positive"); }
    }
    public record Edge(String to, Optional<Boolean> anchored, List<Step> steps, List<ReturnedMaterial> returns) { public Edge { anchored=Objects.requireNonNull(anchored); steps=List.copyOf(steps); returns=List.copyOf(returns); } }
    public record Step(String type,String requirement,int amount,int fuel,double seconds) {}
    public record ReturnedMaterial(String material,int amount) {}
    private static final Codec<Step> STEP=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("type").forGetter(Step::type),Codec.STRING.fieldOf("requirement").forGetter(Step::requirement),Codec.INT.optionalFieldOf("amount",1).forGetter(Step::amount),Codec.INT.optionalFieldOf("fuel",10).forGetter(Step::fuel),Codec.DOUBLE.optionalFieldOf("seconds",0.0).forGetter(Step::seconds)).apply(i,Step::new));
    private static final Codec<ReturnedMaterial> RETURN=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("material").forGetter(ReturnedMaterial::material),Codec.INT.fieldOf("amount").forGetter(ReturnedMaterial::amount)).apply(i,ReturnedMaterial::new));
    private static final Codec<Edge> EDGE=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("to").forGetter(Edge::to),Codec.BOOL.optionalFieldOf("anchored").forGetter(Edge::anchored),STEP.listOf().fieldOf("steps").forGetter(Edge::steps),RETURN.listOf().optionalFieldOf("returns",List.of()).forGetter(Edge::returns)).apply(i,Edge::new));
    private static final Codec<Node> NODE=RecordCodecBuilder.create(i->i.group(Codec.STRING.optionalFieldOf("entity").forGetter(Node::entity),EDGE.listOf().optionalFieldOf("edges",List.of()).forGetter(Node::edges),Codec.INT.optionalFieldOf("outputStackCount",1).forGetter(Node::outputStackCount)).apply(i,Node::new));
    public static final Codec<ConstructionGraphData> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("start").forGetter(ConstructionGraphData::start),Codec.unboundedMap(Codec.STRING,NODE).fieldOf("nodes").forGetter(ConstructionGraphData::nodes)).apply(i,ConstructionGraphData::new));
    public ConstructionGraphData { nodes=Map.copyOf(nodes); }
    public ConstructionGraph resolve(){var map=new HashMap<String,ConstructionGraph.Node>();nodes.forEach((id,n)->{List<ConstructionGraph.Edge> edges=n.edges().stream().map(e->{List<ConstructionGraph.Step> steps=e.steps().stream().<ConstructionGraph.Step>map(s->switch(s.type()){case "material"->new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.valueOf(s.requirement()),s.amount(),s.seconds());case "tool"->new ConstructionGraph.ToolStep(ToolPrototypeData.Quality.valueOf(s.requirement()),s.fuel(),s.seconds());default->throw new IllegalArgumentException("unknown step type "+s.type());}).toList();return new ConstructionGraph.Edge(e.to(),e.anchored().map(ConstructionGraph.Anchored::new),steps,e.returns().stream().map(r->new ConstructionGraph.MaterialReturn(ConstructionGraph.MaterialId.valueOf(r.material()),r.amount())).toList());}).toList();map.put(id,new ConstructionGraph.Node(n.entity(),edges,n.outputStackCount()));});return new ConstructionGraph(start,map);}
}
