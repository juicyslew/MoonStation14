package com.juicyslew.moonstation14.ms14.construction.client;

import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData;
import java.util.*;

/** Builds the complete instructional route from the resolved recipe graph. */
final class ConstructionRecipeGuide {
    record Line(String text) { }
    static List<Line> build(ConstructionGraph graph, ConstructionRecipeData recipe, int outputCount) {
        if(graph==null||recipe==null)return List.of();
        String routeStart=recipe.outputKind()==ConstructionRecipeData.OutputKind.STRUCTURE?graph.start():recipe.startingNode();
        var route=graph.guidance(routeStart,recipe.targetNode(),true).route();
        if(recipe.outputKind()==ConstructionRecipeData.OutputKind.ITEM) {
            List<String> materials=new ArrayList<>();
            for(var edge:route) for(var action:edge.steps()) if(action instanceof ConstructionGraph.MaterialStep material)
                materials.add(material.amount()+" "+materialName(material.material()));
            String outputName=recipe.name();
            if(outputCount>1&&!outputName.endsWith("s"))outputName+="s";
            return List.of(new Line(String.join(" + ",materials)+" → "+outputCount+" "+outputName));
        }
        List<String> steps=new ArrayList<>();
        for(var edge:route){
            for(var action:edge.steps()){
                String text;
                if(action instanceof ConstructionGraph.MaterialStep m)text=m.amount()+" "+materialName(m.material());
                else {var t=(ConstructionGraph.ToolStep)action;String tool=switch(t.quality()){case Anchoring->"Wrench";case Screwing->"Screwdriver";case Welding->"Welder";case Prying->"Crowbar";};text=tool+(t.fuel()>0?" · fuel "+t.fuel():"");}
                steps.add(text);
            }
        }
        List<Line> lines=new ArrayList<>();
        for(int i=0;i<steps.size();i++)lines.add(new Line((steps.size()>1?(i+1)+") ":"")+steps.get(i)));
        return List.copyOf(lines);
    }
    private static String materialName(ConstructionGraph.MaterialId material) {
        String name=material.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0))+name.substring(1);
    }
}
