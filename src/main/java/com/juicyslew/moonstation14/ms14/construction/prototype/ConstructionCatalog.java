package com.juicyslew.moonstation14.ms14.construction.prototype;

import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Cross-validated composed graph/recipe view for server and client consumers. */
public final class ConstructionCatalog {
    public record InitialStage(String node, String entity, List<ConstructionGraph.Step> steps, double durationSeconds) {
        public InitialStage { Objects.requireNonNull(node); Objects.requireNonNull(entity); steps=List.copyOf(steps); }
    }
    private final Map<ResourceLocation, ConstructionGraph> graphs;
    private final PrototypeCatalog<ConstructionRecipeData> recipes;
    public ConstructionCatalog(PrototypeCatalog<ConstructionGraphData> graphData, PrototypeCatalog<ConstructionRecipeData> recipes) {
        Map<ResourceLocation,ConstructionGraph> resolved=new LinkedHashMap<>(); graphData.asMap().forEach((id,data)->resolved.put(id,data.resolve()));
        for(var recipe:recipes.asMap().entrySet()) {
            ResourceLocation graphId=parse(recipe.getValue().graph()); ConstructionGraph graph=resolved.get(graphId);
            if(graph==null)throw new IllegalArgumentException("recipe "+recipe.getKey()+" references missing graph "+graphId);
            if(!graph.nodes().containsKey(recipe.getValue().startingNode())||!graph.nodes().containsKey(recipe.getValue().targetNode()))throw new IllegalArgumentException("recipe "+recipe.getKey()+" references missing graph node");
            if(graph.nodes().get(recipe.getValue().startingNode()).edges().stream().noneMatch(e->e.to().equals(recipe.getValue().targetNode())))throw new IllegalArgumentException("recipe initial target is not an edge from starting node: "+recipe.getKey());
        }
        this.graphs=Map.copyOf(resolved);this.recipes=recipes;
    }
    private static ResourceLocation parse(String value){return value.indexOf(':')<0?ModConstruction.id(value):ResourceLocation.parse(value);}
    public ConstructionGraph graph(ResourceLocation id){return graphs.get(id);}
    public ConstructionRecipeData recipe(ResourceLocation id){return recipes.get(id);}
    public Collection<ResourceLocation> recipeIds(){return recipes.keys();}
    public List<ConstructionGraph.Step> startingRequirements(ResourceLocation id){var recipe=Objects.requireNonNull(recipes.get(id),"unknown recipe");var graph=Objects.requireNonNull(graphs.get(parse(recipe.graph())),"missing graph");return graph.nodes().get(recipe.startingNode()).edges().stream().filter(e->e.to().equals(recipe.targetNode())).findFirst().orElseThrow().steps();}
    /** Resolve the first physical entity on a valid route to the selected goal, not the finished goal itself. */
    public Optional<InitialStage> initialStage(ResourceLocation id) {
        var recipe=recipes.get(id); if(recipe==null||recipe.outputKind()!=ConstructionRecipeData.OutputKind.STRUCTURE)return Optional.empty();
        var graph=graphs.get(parse(recipe.graph())); if(graph==null)return Optional.empty();
        var route=graph.guidance(graph.start(),recipe.targetNode(),true);
        if(route.status()!=ConstructionGraph.GuidanceStatus.ROUTE)route=graph.guidance(graph.start(),recipe.targetNode(),false);
        if(route.status()!=ConstructionGraph.GuidanceStatus.ROUTE||route.route().isEmpty())return Optional.empty();
        var edge=route.route().getFirst(); var node=graph.nodes().get(edge.to());
        if(node==null||node.entity().isEmpty())return Optional.empty();
        double seconds=edge.steps().stream().mapToDouble(ConstructionGraph.Step::seconds).sum();
        return Optional.of(new InitialStage(edge.to(),node.entity().get(),edge.steps(),seconds));
    }
    /** Stack count applied when the selected recipe's target node is completed. */
    public int outputStackCount(ResourceLocation id){var recipe=Objects.requireNonNull(recipes.get(id),"unknown recipe");var graph=Objects.requireNonNull(graphs.get(parse(recipe.graph())),"missing graph");return graph.nodes().get(recipe.targetNode()).outputStackCount();}
}

