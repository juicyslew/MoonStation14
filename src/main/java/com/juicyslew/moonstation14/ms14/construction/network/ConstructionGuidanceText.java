package com.juicyslew.moonstation14.ms14.construction.network;

import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldData;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionCatalog;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.Locale;
import java.util.function.Function;

/** Pure, bounded advisory formatting from current persisted graph progress and remembered goal. */
public final class ConstructionGuidanceText {
    public record Content(String goal,String requirement) { }
    private ConstructionGuidanceText() { }

    /** Catalog-backed production producer: derive unfinished first-stage roles from resolved recipes. */
    public static Content content(ConstructionCatalog catalog,ConstructionWorldData.Record record) {
        if(catalog==null||record==null)return null;
        ResourceLocation recipeId=parse(record.recipeId());var recipe=recipeId==null?null:catalog.recipe(recipeId);
        if(recipe==null)return null;
        ResourceLocation graphId=parse(recipe.graph());var graph=graphId==null?null:catalog.graph(graphId);
        if(graph==null)return null;
        var finished=new java.util.HashSet<String>();var unfinished=new java.util.HashSet<String>();
        for(var id:catalog.recipeIds()) {
            var other=catalog.recipe(id);
            if(other==null||other.outputKind()!=ConstructionRecipeData.OutputKind.STRUCTURE||!graphId.equals(parse(other.graph())))continue;
            finished.add(other.targetNode());
            var first=catalog.initialStage(id);
            if(first.isPresent()&&!first.get().node().equals(other.targetNode()))unfinished.add(first.get().node());
        }
        finished.removeAll(unfinished);
        return content(graph,record,finished,node->catalog.recipeIds().stream().map(catalog::recipe)
                .filter(other->other!=null&&other.outputKind()==ConstructionRecipeData.OutputKind.STRUCTURE
                        &&graphId.equals(parse(other.graph()))&&other.targetNode().equals(node))
                .map(ConstructionRecipeData::name).findFirst().orElseGet(()->humanize(node)));
    }
    public static String generate(ConstructionCatalog catalog,ConstructionWorldData.Record record) { var c=content(catalog,record);return c==null?null:c.goal()+"\n"+c.requirement(); }
    private static ResourceLocation parse(String id) {
        if(id==null||id.isBlank())return null;
        return id.indexOf(':')<0?ModConstruction.id(id):ResourceLocation.tryParse(id);
    }

    public static String generate(ConstructionGraph graph, ConstructionWorldData.Record record,
                                  Set<String> finishedTargets) {
        return generate(graph, record, finishedTargets, ConstructionGuidanceText::humanize);
    }
    public static Content content(ConstructionGraph graph,ConstructionWorldData.Record record,Set<String> finishedTargets,Function<String,String> labels) {
        if(graph==null||record==null||finishedTargets==null||!graph.nodes().containsKey(record.current())||!graph.nodes().containsKey(record.goal()))return null;
        if(finishedTargets.contains(record.current())) {
            long forward=java.util.stream.Stream.concat(graph.eligibleEdges(record.current(),record.anchored()).stream(),
                    record.anchored()?java.util.stream.Stream.empty():graph.eligibleEdges(record.current(),true).stream())
                    .filter(e->!e.to().equals(graph.start())).map(ConstructionGraph.Edge::to).distinct().count();
            if(!record.current().equals(record.goal())||forward<2)return null;
        }
        String requirement=null;
        var route=graph.guidance(record.current(),record.goal(),record.anchored());
        if(route.status()==ConstructionGraph.GuidanceStatus.UNREACHABLE&&!record.anchored()&&graph.guidance(record.current(),record.goal(),true).status()==ConstructionGraph.GuidanceStatus.ROUTE) requirement="Use Anchoring tool";
        else if(route.status()==ConstructionGraph.GuidanceStatus.AT_GOAL) {
            var options=graph.eligibleEdges(record.current(),record.anchored());
            if(options.stream().noneMatch(e->!e.to().equals(graph.start()))&&!record.anchored()) {
                options=graph.eligibleEdges(record.current(),true);
                if(options.stream().anyMatch(e->!e.to().equals(graph.start()))) requirement="Use Anchoring tool";
            }
            if(requirement==null)for(var edge:options) if(!edge.to().equals(graph.start())) { route=new ConstructionGraph.Guidance(ConstructionGraph.GuidanceStatus.ROUTE,List.of(edge));break; }
        }
        if(requirement==null&&route.status()==ConstructionGraph.GuidanceStatus.ROUTE) {
            var edge=route.route().get(0);int ix=graph.nodes().get(record.current()).edges().indexOf(edge);int from=edge.to().equals(record.goal())&&record.current().equals(record.goal())?0:0;
            if(ix==record.edgeIndex())from=record.stepIndex();
            if(from<edge.steps().size()) {var step=edge.steps().get(from);requirement=step instanceof ConstructionGraph.MaterialStep m?m.amount()+" "+humanize(m.material().name()):"Use "+humanize(((ConstructionGraph.ToolStep)step).quality().name())+" tool";}
        }
        return requirement==null?null:new Content(labels.apply(record.goal()),requirement);
    }
    public static String generate(ConstructionGraph graph, ConstructionWorldData.Record record,
                                  Set<String> finishedTargets, Function<String,String> labels) {
        var c=content(graph,record,finishedTargets,labels);
        return c==null?null:c.goal()+"\n"+c.requirement();
    }

    private static void appendOptions(StringBuilder out,ConstructionGraph graph,String current,boolean anchored,Function<String,String> labels) {
        boolean anchoringNeeded=false;
        for(var edge:graph.nodes().get(current).edges()) {
            String label=edge.to().equals(graph.start())?"Dismantle":labels.apply(edge.to());
            if(edge.eligible(anchored))out.append(' ').append(label);
            else if(!anchored&&edge.eligible(true)){out.append(' ').append(label).append(" (after anchoring)");anchoringNeeded=true;}
        }
        if(anchoringNeeded)out.append(" · Use Anchoring tool for marked options");
    }
    private static void appendSteps(StringBuilder out,List<ConstructionGraph.Step> steps,int from) {
        for(int i=Math.max(0,Math.min(from,steps.size()));i<steps.size();i++) {
            var step=steps.get(i);out.append(" · ");
            if(step instanceof ConstructionGraph.MaterialStep material)
                out.append(material.amount()).append(' ').append(humanize(material.material().name()));
            else out.append("Use ").append(humanize(((ConstructionGraph.ToolStep)step).quality().name())).append(" tool");
        }
    }
    private static String cap(StringBuilder text){return text.length()>900?text.substring(0,900):text.toString();}
    public static String humanize(String value) {
        StringJoiner result=new StringJoiner(" ");
        for(String word:value.replace('_',' ').trim().split("\\s+"))
            result.add(word.isEmpty()?word:Character.toUpperCase(word.charAt(0))+word.substring(1).toLowerCase(Locale.ROOT));
        return result.toString();
    }
}

