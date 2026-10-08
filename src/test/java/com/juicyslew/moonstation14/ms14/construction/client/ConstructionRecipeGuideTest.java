package com.juicyslew.moonstation14.ms14.construction.client;

import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionRecipeGuideTest {
    @Test void structureGuideListsFullRouteWithoutHeadingsTimesOrOutput() {
        var graph=new ConstructionGraph("start",Map.of(
            "start",new ConstructionGraph.Node(Optional.empty(),List.of(new ConstructionGraph.Edge("girder",Optional.empty(),List.of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,2,1)),List.of()))),
            "girder",new ConstructionGraph.Node(Optional.of("Girder"),List.of(new ConstructionGraph.Edge("wall",Optional.empty(),List.of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,2,2),new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,2,2)),List.of()))),
            "wall",new ConstructionGraph.Node(Optional.of("WallSolid"),List.of())));
        var recipe=new ConstructionRecipeData("Steel Wall","Build","Walls","walls","wall","g","girder","wall",ConstructionRecipeData.OutputKind.STRUCTURE);
        var lines=ConstructionRecipeGuide.build(graph,recipe,1).stream().map(ConstructionRecipeGuide.Line::text).toList();
        assertEquals(List.of("1) 2 Steel","2) 2 Steel","3) 2 Steel"),lines);
        assertTrue(lines.stream().noneMatch(s->s.contains("Step")||s.contains("Output")||s.contains("·")));
    }

    @Test void itemGuideRetainsMaterialToOutputLine() {
        var graph=new ConstructionGraph("start",Map.of(
            "start",new ConstructionGraph.Node(Optional.empty(),List.of(new ConstructionGraph.Edge("rods",Optional.empty(),List.of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,1,0)),List.of()))),
            "rods",new ConstructionGraph.Node(Optional.of("MetalRod"),List.of(),2)));
        var recipe=new ConstructionRecipeData("Metal Rod","Build","Items","items","metal_rod","g","start","rods",ConstructionRecipeData.OutputKind.ITEM);
        assertEquals("1 Steel → 2 Metal Rods",ConstructionRecipeGuide.build(graph,recipe,2).getFirst().text());
    }

    @Test void itemGuideStartsAtRecipeStartingNode() {
        var graph=new ConstructionGraph("root",Map.of(
            "root",new ConstructionGraph.Node(Optional.empty(),List.of(new ConstructionGraph.Edge("intermediate",Optional.empty(),List.of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,9,1)),List.of()))),
            "intermediate",new ConstructionGraph.Node(Optional.empty(),List.of(new ConstructionGraph.Edge("item",Optional.empty(),List.of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,1,0)),List.of()))),
            "item",new ConstructionGraph.Node(Optional.of("MetalRod"),List.of(),2)));
        var recipe=new ConstructionRecipeData("Metal Rod","Build","Items","items","metal_rod","g","intermediate","item",ConstructionRecipeData.OutputKind.ITEM);
        assertEquals("1 Steel → 1 Metal Rod",ConstructionRecipeGuide.build(graph,recipe,1).getFirst().text());
    }
}
